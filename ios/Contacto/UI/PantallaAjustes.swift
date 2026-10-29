import SwiftData
import StoreKit
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import UserNotifications

/// Los ajustes de la app, los de Android que tienen sentido en iOS: recordatorios
/// (con pausa), horario, "Mas tarde", forma de contacto por defecto, apariencia,
/// copia de seguridad y opiniones. En lugar de la bateria (restricciones de los
/// fabricantes) hay un repaso de lo que puede frenar los avisos en iOS: permisos,
/// resumen programado, ahorro de energia y actualizacion en segundo plano. La
/// telemetria no existe aqui.
struct PantallaAjustes: View {
    @EnvironmentObject private var almacen: AlmacenAjustes
    @Query private var gente: [Contacto]

    @State private var preguntarPausa = false
    @State private var sistema: UNNotificationSettings?
    @State private var ahorroEnergia = false
    @State private var segundoPlano = UIBackgroundRefreshStatus.available
    @Environment(\.scenePhase) private var fase
    @Environment(\.requestReview) private var pedirValoracion
    @State private var exportando = false
    @State private var importando = false
    @State private var documento = ArchivoCopia(datos: Data())
    @State private var resultado: String?

    private static let plazosPausa = [1, 3, 7, 14, 30]

    var body: some View {
        Form {
            recordatorios
            salud
            horario
            masTarde
            contactosNuevos
            apariencia
            copia
            opiniones
            privacidad
        }
        .scrollContentBackground(.hidden)
        .background(Paleta.fondo)
        .navigationTitle(Text("ajustes"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await refrescarSalud() }
        // Se vuelve de los Ajustes del sistema con algo cambiado: se mira otra vez.
        .onChange(of: fase) {
            if fase == .active { Task { await refrescarSalud() } }
        }
        // Cualquier cambio de aviso rehace las notificaciones pendientes.
        .onChange(of: almacen.valores) { Avisos.shared.reprogramar() }
        .confirmationDialog(Text("recordatorios"), isPresented: $preguntarPausa, titleVisibility: .visible) {
            Button("hasta_reactivar") { apagar(hasta: nil) }
            ForEach(Self.plazosPausa, id: \.self) { dias in
                Button(Plural.dias(dias)) { apagar(hasta: Fechas.sumar(dias: dias, a: Date())) }
            }
            Button("cancelar", role: .cancel) {}
        }
        .fileExporter(isPresented: $exportando, document: documento, contentType: .json, defaultFilename: "contacto") { res in
            switch res {
            case .success: resultado = Plural.copiaExportada(gente.count)
            case .failure: resultado = t("copia_fallo")
            }
        }
        .fileImporter(isPresented: $importando, allowedContentTypes: [.json]) { res in
            importar(res)
        }
        .alert(Text(verbatim: resultado ?? ""), isPresented: Binding(get: { resultado != nil }, set: { if !$0 { resultado = nil } })) {
            Button("entendido") { resultado = nil }
        }
    }

    // MARK: Recordatorios

    private var recordatorios: some View {
        Section("recordatorios") {
            Toggle(isOn: Binding(
                get: { almacen.valores.avisosActivos },
                set: { encendido in
                    if encendido {
                        almacen.valores.avisosActivos = true
                        almacen.valores.pausaHasta = nil
                    } else {
                        // Hasta cuando: se pregunta antes de apagarlos.
                        preguntarPausa = true
                    }
                }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("recordatorios")
                    Text(verbatim: estadoRecordatorios).font(.footnote).foregroundStyle(Paleta.textoSecundario)
                }
            }
        }
        .listRowBackground(Paleta.tarjeta)
    }

    private var estadoRecordatorios: String {
        let v = almacen.valores
        if v.avisosActivos { return t("activados") }
        if let hasta = v.pausaHasta { return t("en_pausa_vuelven", Formato.fecha(hasta)) }
        return t("desactivados")
    }

    // MARK: Salud de los avisos

    /// Lo que en Android es la seccion de la bateria: aqui lo que puede frenar los
    /// avisos son los permisos, el resumen programado, el ahorro de energia y la
    /// actualizacion en segundo plano (que renueva los avisos sin abrir la app).
    private var salud: some View {
        Section("ios_salud_titulo") {
            let problemas = problemasDeSalud
            if problemas.isEmpty {
                Label { Text("ios_salud_ok") } icon: { Image(systemName: "checkmark.circle") }
                    .foregroundStyle(Paleta.textoSecundario)
            } else {
                ForEach(problemas, id: \.icono) { problema in
                    Label { Text(verbatim: problema.texto) } icon: { Image(systemName: problema.icono) }
                        .font(.subheadline)
                        .foregroundStyle(Paleta.acentoLegible)
                }
                if sistema?.authorizationStatus == .notDetermined {
                    Button {
                        Task {
                            _ = await Avisos.shared.pedirPermiso()
                            await refrescarSalud()
                            Avisos.shared.reprogramar()
                        }
                    } label: { Label("ios_pedir_permiso", systemImage: "bell.badge") }
                } else {
                    Button { abrirAjustesDeAvisos() } label: { Label("ios_abrir_ajustes_avisos", systemImage: "gearshape") }
                }
            }
        }
        .listRowBackground(Paleta.tarjeta)
    }

    private struct Problema {
        let icono: String
        let texto: String
    }

    private var problemasDeSalud: [Problema] {
        var r: [Problema] = []
        if let s = sistema {
            switch s.authorizationStatus {
            case .denied:
                r.append(Problema(icono: "bell.slash", texto: t("sin_permiso_avisos")))
            case .notDetermined:
                r.append(Problema(icono: "bell.badge", texto: t("ios_salud_permiso")))
            default:
                if s.alertSetting == .disabled || (s.notificationCenterSetting == .disabled && s.lockScreenSetting == .disabled) {
                    r.append(Problema(icono: "rectangle.badge.xmark", texto: t("ios_salud_sin_alertas")))
                }
                if s.soundSetting == .disabled {
                    r.append(Problema(icono: "speaker.slash", texto: t("ios_salud_sin_sonido")))
                }
                if s.scheduledDeliverySetting == .enabled {
                    r.append(Problema(icono: "clock.badge", texto: t("ios_salud_resumen")))
                }
            }
        }
        if segundoPlano != .available {
            r.append(Problema(icono: "arrow.triangle.2.circlepath", texto: t("ios_salud_sin_segundo_plano")))
        }
        if ahorroEnergia {
            r.append(Problema(icono: "battery.25", texto: t("ios_salud_ahorro")))
        }
        return r
    }

    private func refrescarSalud() async {
        sistema = await Avisos.shared.ajustesDelSistema()
        ahorroEnergia = ProcessInfo.processInfo.isLowPowerModeEnabled
        segundoPlano = UIApplication.shared.backgroundRefreshStatus
    }

    /// Directo a la pantalla de notificaciones de la app, no a la general.
    private func abrirAjustesDeAvisos() {
        if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    private func apagar(hasta: Date?) {
        almacen.valores.avisosActivos = false
        almacen.valores.pausaHasta = hasta
    }

    // MARK: Horario

    private var horario: some View {
        Section("horario_avisos") {
            DialFranja(desde: almacen.valores.horaDesde, hasta: almacen.valores.horaHasta) { desde, hasta in
                almacen.valores.horaDesde = desde
                almacen.valores.horaHasta = hasta
            }
            .frame(maxWidth: 280)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            Text(verbatim: resumenHorario).font(.footnote).foregroundStyle(Paleta.textoSecundario)
        }
        .listRowBackground(Paleta.tarjeta)
    }

    private var resumenHorario: String {
        let v = almacen.valores
        if v.horaDesde == v.horaHasta { return t("horario_todo_el_dia") }
        return t("horario_de_a", Formato.hora(v.horaDesde), Formato.hora(v.horaHasta))
    }

    // MARK: Mas tarde

    private var masTarde: some View {
        Section("boton_mas_tarde_titulo") {
            Stepper(value: $almacen.valores.horasPosponer, in: 1...24) {
                Text(verbatim: Plural.horas(almacen.valores.horasPosponer))
            }
            Text(verbatim: t("posponer_explicacion", Plural.horas(almacen.valores.horasPosponer), Formato.hora(almacen.valores.horaDesde)))
                .font(.footnote).foregroundStyle(Paleta.textoSecundario)
        }
        .listRowBackground(Paleta.tarjeta)
    }

    // MARK: Forma de contacto

    private var contactosNuevos: some View {
        Section("contactos_nuevos") {
            Picker(selection: $almacen.valores.medioPorDefecto) {
                ForEach(MedioContacto.elegibles, id: \.self) { m in
                    Label(m.etiqueta, systemImage: m.icono).tag(m)
                }
            } label: { Text("al_tocar_aviso") }
            Text(verbatim: t("medio_explicacion", almacen.valores.medioPorDefecto.etiqueta))
                .font(.footnote).foregroundStyle(Paleta.textoSecundario)
        }
        .listRowBackground(Paleta.tarjeta)
    }

    // MARK: Apariencia

    private var apariencia: some View {
        Section("apariencia") {
            Deslizador(
                opciones: TemaElegido.allCases,
                elegida: almacen.valores.tema,
                texto: { $0.etiqueta },
                alElegir: { almacen.valores.tema = $0 }
            )
            .padding(.vertical, 4)
        }
        .listRowBackground(Paleta.tarjeta)
    }

    // MARK: Copia de seguridad

    private var copia: some View {
        Section("copia_titulo") {
            Text("ios_copia_explicacion").font(.footnote).foregroundStyle(Paleta.textoSecundario)
            Button {
                do {
                    documento = ArchivoCopia(datos: try CopiaSeguridad.escribir(gente, ajustes: almacen.valores))
                    exportando = true
                } catch {
                    resultado = t("copia_fallo")
                }
            } label: { Label("copia_exportar", systemImage: "square.and.arrow.up") }
            Button { importando = true } label: { Label("copia_importar", systemImage: "square.and.arrow.down") }
        }
        .listRowBackground(Paleta.tarjeta)
    }

    private func importar(_ res: Result<URL, Error>) {
        guard case .success(let url) = res else { resultado = t("copia_fallo"); return }
        // El archivo vive fuera de la app: hay que pedir acceso mientras se lee.
        let acceso = url.startAccessingSecurityScopedResource()
        defer { if acceso { url.stopAccessingSecurityScopedResource() } }
        do {
            let leido = try CopiaSeguridad.leer(try Data(contentsOf: url))
            let r = Entorno.shared.importar(leido)
            resultado = t("copia_importada", r.nuevas, r.actualizadas)
        } catch is CopiaSeguridad.ArchivoNoValido {
            resultado = t("copia_no_valida")
        } catch {
            resultado = t("copia_fallo")
        }
    }

    // MARK: Opiniones y privacidad

    private var opiniones: some View {
        Section("opiniones") {
            Text("ios_opiniones_explicacion").font(.footnote).foregroundStyle(Paleta.textoSecundario)
            Button { escribir() } label: { Label("enviar_opiniones", systemImage: "envelope") }
            Button { pedirValoracion() } label: { Label("ios_valorar", systemImage: "star") }
        }
        .listRowBackground(Paleta.tarjeta)
    }

    /// Sin app de correo el enlace `mailto:` no hace nada: se copia la direccion y se avisa.
    private func escribir() {
        Task {
            let abierto = await UIApplication.shared.open(urlOpiniones)
            if !abierto {
                UIPasteboard.general.string = Self.correo
                resultado = t("ios_sin_correo", Self.correo)
            }
        }
    }

    private static let correo = "cmatuteortega@gmail.com"

    private var urlOpiniones: URL {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let asunto = t("opiniones_asunto", "Contacto")
        var partes = URLComponents()
        partes.scheme = "mailto"
        partes.path = Self.correo
        partes.queryItems = [
            URLQueryItem(name: "subject", value: asunto),
            URLQueryItem(name: "body", value: "\n\nContacto \(version) · iOS \(UIDevice.current.systemVersion) · \(UIDevice.current.model)"),
        ]
        return partes.url ?? URL(string: "mailto:\(Self.correo)")!
    }

    private var privacidad: some View {
        Section("privacidad_titulo") {
            Text("ios_privacidad_texto").font(.footnote).foregroundStyle(Paleta.textoSecundario)
            if let url = URL(string: t("enlace_privacidad")) {
                Link(destination: url) { Label("privacidad_politica", systemImage: "hand.raised") }
            }
        }
        .listRowBackground(Paleta.tarjeta)
    }
}
