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
        // Con icono (SF Symbols) para que en la pulsacion larga se vean como las acciones del sistema.
        let acciones = [
            UNNotificationAction(identifier: Avisos.accionContactar, title: t("aviso_contactar"), options: [.foreground], icon: UNNotificationActionIcon(systemImageName: "phone")),
            UNNotificationAction(identifier: Avisos.accionMasTarde, title: t("aviso_mas_tarde"), options: [], icon: UNNotificationActionIcon(systemImageName: "clock")),
            UNNotificationAction(identifier: Avisos.accionMasOpciones, title: t("aviso_mas_opciones"), options: [.foreground], icon: UNNotificationActionIcon(systemImageName: "ellipsis.circle")),
        ]
        centro.setNotificationCategories([
            UNNotificationCategory(
                identifier: Avisos.categoria, actions: acciones, intentIdentifiers: [],
                // Con la vista previa oculta en el bloqueo no salen los nombres.
                hiddenPreviewsBodyPlaceholder: t("ios_aviso_oculto"),
                // El sistema agrupa por hilo; esto es el "y 3 mas" del resumen plegado.
                categorySummaryFormat: t("ios_resumen_categoria"),
                // Sin esta opcion, quitar el aviso de la bandeja no llega a la app (el `DESCARTADO` de Android).
                options: [.customDismissAction]
            ),
        ])
    }

    /// Pide permiso para avisar. Devuelve si se puede avisar.
    func pedirPermiso() async -> Bool {
        guard !Entorno.enPruebas else { return false }
        // `providesAppNotificationSettings`: Ajustes del sistema ofrece "Ajustes de notificaciones de
        // Contacto" y aqui se abre la pantalla de Ajustes (el equivalente a los canales de Android).
        let concedido = (try? await centro.requestAuthorization(options: [.alert, .sound, .badge, .providesAppNotificationSettings])) ?? false
        return concedido
    }

    func estado() async -> UNAuthorizationStatus {
        await centro.notificationSettings().authorizationStatus
    }

    /// Todo lo que el usuario ha dejado (o quitado) a la app: alertas, sonido, resumen programado...
    func ajustesDelSistema() async -> UNNotificationSettings {
        await centro.notificationSettings()
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

    /// La reprogramacion que va en curso: cada una espera a la anterior, para que dos
    /// seguidas (un cambio y la vuelta a la app) no se pisen a medias.
    @MainActor private var enCurso: Task<Void, Never>?

    /// Rehace todos los avisos pendientes con lo que hay ahora en la base y en los ajustes.
    @MainActor
    func reprogramar() {
        guard !Entorno.enPruebas else { return }
        let anterior = enCurso
        anterior?.cancel()
        enCurso = Task {
            await anterior?.value
            await reprogramarAhora()
        }
    }

    /// Lo mismo, pero esperando a que termine: para la tarea de segundo plano, que no puede acabar antes.
    @MainActor
    func reprogramarAhora() async {
        guard !Entorno.enPruebas, !Task.isCancelled else { return }
        let entorno = Entorno.shared
        let personas = entorno.todos()
        let gente = personas.map {
            Datos(
                uuid: $0.uuid, nombre: $0.nombre, medio: $0.medio, ultimo: $0.ultimoContacto,
                frecuencia: $0.frecuenciaDias, pospuestoHasta: $0.pospuestoHasta, pausadoHasta: $0.pausadoHasta,
                cumple: $0.cumpleanos
            )
        }
        // A quien ya no le toca (se hablo con el, se pauso, se borro) se le quita el aviso que siga en la bandeja.
        let tocan = Set(personas.filter { $0.tocaLlamar() && !$0.pausado() }.map { $0.uuid.uuidString })
        let peticiones = Avisos.peticiones(gente, ajustes: entorno.ajustes.valores, ahora: Date())
        let entregados = await centro.deliveredNotifications()
        let obsoletos = entregados.filter { (n: UNNotification) -> Bool in
            let info = n.request.content.userInfo
            guard info["tipo"] as? String == "aviso", let id = info["id"] as? String else { return false }
            return !tocan.contains(id)
        }
        centro.removeDeliveredNotifications(withIdentifiers: obsoletos.map { $0.request.identifier })
        centro.removeAllPendingNotificationRequests()
        for p in peticiones {
            if Task.isCancelled { return }
            try? await centro.add(p)
        }
    }

    /// Cuanto pesa un aviso en el resumen del sistema (0 a 1): el que ya va tarde sube arriba.
    /// `urgencia` es la de Plazos (1 = toca hoy) en el momento en que suena.
    static func relevancia(urgencia: Double) -> Double {
        min(1, max(0.3, 0.3 + 0.35 * urgencia))
    }

    /// Los cumpleanos pesan mas que un aviso normal, aunque menos que uno muy atrasado.
    static let relevanciaCumple = 0.9

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
            contenido.summaryArgument = p.nombre
            contenido.interruptionLevel = .active
            contenido.relevanceScore = relevanciaCumple
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
                contenido.summaryArgument = p.nombre
                contenido.interruptionLevel = .active
                contenido.relevanceScore = relevancia(urgencia: Plazos.urgencia(ultimo: p.ultimo, frecuencia: p.frecuencia, hoy: fecha))
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

    /// Ajustes del sistema > Contacto > "Ajustes de notificaciones de Contacto".
    func userNotificationCenter(_ center: UNUserNotificationCenter, openSettingsFor notification: UNNotification?) {
        Task { @MainActor in Entorno.shared.router.pendiente = .ajustes }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let texto = info["id"] as? String, let uuid = UUID(uuidString: texto) else { return }
        let accion = response.actionIdentifier
        let esAviso = info["tipo"] as? String == "aviso"
        await MainActor.run {
            let entorno = Entorno.shared
            switch accion {
            case Avisos.accionMasTarde:
                entorno.posponer(uuid: uuid)
            case Avisos.accionMasOpciones:
                entorno.router.pendiente = .ficha(uuid)
            case UNNotificationDismissActionIdentifier:
                // Lo quito de la bandeja sin hacerle caso: cuenta como descarte, nada mas.
                if esAviso { entorno.descartar(uuid: uuid) }
            default:
                // Tocar el aviso o "Contactar": abre la forma de contacto, que cuenta como contacto.
                entorno.router.pendiente = .contactar(uuid)
            }
        }
    }
}
