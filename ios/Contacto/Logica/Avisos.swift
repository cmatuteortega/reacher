import Foundation
import SwiftData
import UserNotifications

/// Los recordatorios, con notificaciones locales. Android tiene un trabajo
/// diario por persona; iOS no deja ejecutar nada en segundo plano a una hora
/// fija, asi que aqui se programan por adelantado (`PlanAvisos`) y se rehacen
/// al abrir la app y tras cada cambio. El limite del sistema es de 64 avisos
/// pendientes, que se reparten entre la gente.
final class Avisos: NSObject, UNUserNotificationCenterDelegate {
    static let shared = Avisos()

    static let categoria = "AVISO"
    static let accionContactar = "contactar"
    static let accionMasTarde = "mas_tarde"
    static let accionMasOpciones = "mas_opciones"

    private static let limiteSistema = 64
    private static let diasPorAdelantado = 7

    private var centro: UNUserNotificationCenter { UNUserNotificationCenter.current() }

    func configurar() {
        guard !Entorno.enPruebas else { return }
        centro.delegate = self
        let acciones = [
            UNNotificationAction(identifier: Avisos.accionContactar, title: t("aviso_contactar"), options: [.foreground]),
            UNNotificationAction(identifier: Avisos.accionMasTarde, title: t("aviso_mas_tarde"), options: []),
            UNNotificationAction(identifier: Avisos.accionMasOpciones, title: t("aviso_mas_opciones"), options: [.foreground]),
        ]
        centro.setNotificationCategories([
            UNNotificationCategory(identifier: Avisos.categoria, actions: acciones, intentIdentifiers: [], options: []),
        ])
    }

    /// Pide permiso para avisar. Devuelve si se puede avisar.
    func pedirPermiso() async -> Bool {
        guard !Entorno.enPruebas else { return false }
        let concedido = (try? await centro.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        return concedido
    }

    func estado() async -> UNAuthorizationStatus {
        await centro.notificationSettings().authorizationStatus
    }

    /// Lo que hace falta de una persona para avisarle, sin atarlo a la base de datos.
    private struct Datos {
        let uuid: UUID
        let nombre: String
        let medio: MedioContacto
        let ultimo: Date
        let frecuencia: Int
        let pospuestoHasta: Date?
        let pausadoHasta: Date?
        let cumple: (mes: Int, dia: Int)?
    }

    /// Rehace todos los avisos pendientes con lo que hay ahora en la base y en los ajustes.
    @MainActor
    func reprogramar() {
        guard !Entorno.enPruebas else { return }
        let entorno = Entorno.shared
        let gente = entorno.todos().map {
            Datos(
                uuid: $0.uuid, nombre: $0.nombre, medio: $0.medio, ultimo: $0.ultimoContacto,
                frecuencia: $0.frecuenciaDias, pospuestoHasta: $0.pospuestoHasta, pausadoHasta: $0.pausadoHasta,
                cumple: $0.cumpleanos
            )
        }
        let peticiones = Avisos.peticiones(gente, ajustes: entorno.ajustes.valores, ahora: Date())
        Task {
            centro.removeAllPendingNotificationRequests()
            for p in peticiones { try? await centro.add(p) }
        }
    }

    private static func peticiones(_ gente: [Datos], ajustes: Ajustes, ahora: Date) -> [UNNotificationRequest] {
        guard !gente.isEmpty else { return [] }
        let cal = Fechas.calendario
        var resultado: [UNNotificationRequest] = []

        // Cumpleanos: uno por persona, anual. Apagados del todo no avisa; en pausa tampoco debe,
        // pero eso solo se sabe al rehacer (al abrir la app o al cambiar el ajuste).
        let cumples = (ajustes.avisosActivos || ajustes.pausaHasta != nil) ? gente.filter { $0.cumple != nil } : []
        for p in cumples {
            guard let cumple = p.cumple else { continue }
            let contenido = UNMutableNotificationContent()
            contenido.title = t("cumpleanos_hoy", p.nombre)
            contenido.body = t("cumpleanos_felicita")
            contenido.sound = .default
            contenido.threadIdentifier = "cumpleanos"
            contenido.categoryIdentifier = Avisos.categoria
            contenido.userInfo = ["id": p.uuid.uuidString, "tipo": "cumpleanos"]
            var componentes = DateComponents(month: cumple.mes, day: cumple.dia, hour: ajustes.horaDesde, minute: 0)
            var repite = true
            if cumple.mes == 2 && cumple.dia == 29 {
                // Solo un aviso, en el dia que toque: el 28 los anos que no son bisiestos.
                let anio = cal.component(.year, from: ahora)
                var siguiente = Fechas.fecha(mes: 2, dia: 29, anio: anio).flatMap { cal.date(bySettingHour: ajustes.horaDesde, minute: 0, second: 0, of: $0) }
                if siguiente == nil || siguiente! <= ahora {
                    siguiente = Fechas.fecha(mes: 2, dia: 29, anio: anio + 1).flatMap { cal.date(bySettingHour: ajustes.horaDesde, minute: 0, second: 0, of: $0) }
                }
                guard let fecha = siguiente else { continue }
                componentes = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fecha)
                repite = false
            }
            let disparo = UNCalendarNotificationTrigger(dateMatching: componentes, repeats: repite)
            resultado.append(UNNotificationRequest(identifier: "cumple-\(p.uuid.uuidString)", content: contenido, trigger: disparo))
        }

        // El resto del cupo, a partes iguales entre la gente (al menos un aviso cada uno).
        let libres = max(limiteSistema - cumples.count, gente.count)
        let porPersona = max(1, min(diasPorAdelantado, libres / gente.count))
        for p in gente {
            let fechas = PlanAvisos.fechas(
                ultimo: p.ultimo, frecuencia: p.frecuencia,
                pospuestoHasta: p.pospuestoHasta, pausadoHasta: p.pausadoHasta,
                ajustes: ajustes, ahora: ahora, cantidad: porPersona
            )
            for (i, fecha) in fechas.enumerated() {
                let contenido = UNMutableNotificationContent()
                contenido.title = p.nombre
                contenido.body = p.medio.aviso(nombre: p.nombre)
                contenido.sound = .default
                contenido.threadIdentifier = "personas"
                contenido.categoryIdentifier = Avisos.categoria
                contenido.userInfo = ["id": p.uuid.uuidString, "tipo": "aviso"]
                let componentes = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fecha)
                let disparo = UNCalendarNotificationTrigger(dateMatching: componentes, repeats: false)
                resultado.append(UNNotificationRequest(identifier: "aviso-\(p.uuid.uuidString)-\(i)", content: contenido, trigger: disparo))
            }
        }
        return Array(resultado.prefix(limiteSistema))
    }

    // MARK: UNUserNotificationCenterDelegate

    /// Con la app abierta tambien se ve y suena.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let texto = info["id"] as? String, let uuid = UUID(uuidString: texto) else { return }
        let accion = response.actionIdentifier
        await MainActor.run {
            let entorno = Entorno.shared
            switch accion {
            case Avisos.accionMasTarde:
                entorno.posponer(uuid: uuid)
            case Avisos.accionMasOpciones:
                entorno.router.pendiente = .ficha(uuid)
            case UNNotificationDismissActionIdentifier:
                break
            default:
                // Tocar el aviso o "Contactar": abre la forma de contacto, que cuenta como contacto.
                entorno.router.pendiente = .contactar(uuid)
            }
        }
    }
}
