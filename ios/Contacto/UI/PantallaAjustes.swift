import SwiftData
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import UserNotifications

/// Los ajustes de la app, los de Android que tienen sentido en iOS: recordatorios
/// (con pausa), horario, "Mas tarde", forma de contacto por defecto, apariencia,
/// copia de seguridad y opiniones. La bateria (restricciones de los fabricantes)
/// y la telemetria no existen aqui.
struct PantallaAjustes: View {
    @EnvironmentObject private var almacen: AlmacenAjustes
    @Query private var gente: [Contacto]

    @State private var preguntarPausa = false
    @State private var estadoAvisos: UNAuthorizationStatus = .notDetermined
    @State private var exportando = false
    @State private var importando = false
    @State private var documento = ArchivoCopia(datos: Data())
    @State private var resultado: String?

    private static let plazosPausa = [1, 3, 7, 14, 30]

    var body: some View {
        Form {
            recordatorios
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
        .task { estadoAvisos = await Avisos.shared.estado() }
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
            if estadoAvisos == .denied {
                Text("sin_permiso_avisos").font(.footnote).foregroundStyle(Paleta.acentoLegible)
                Button("activa_notificaciones") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
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

    private func apagar(hasta: Date?) {
        almacen.valores.avisosActivos = false
        almacen.valores.pausaHasta = hasta
    }

    // MARK: Horario

    private var horario: some View {
        Section("horario_avisos") {
            Picker("ios_desde", selection: $almacen.valores.horaDesde) {
                ForEach(0..<24, id: \.self) { Text(verbatim: Formato.hora($0)).tag($0) }
            }
            Picker("ios_hasta", selection: $almacen.valores.horaHasta) {
                ForEach(0..<24, id: \.self) { Text(verbatim: Formato.hora($0)).tag($0) }
            }
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
            Picker(selection: $almacen.valores.tema) {
                ForEach(TemaElegido.allCases, id: \.self) { Text(verbatim: $0.etiqueta).tag($0) }
            } label: { Text("apariencia") }
            .pickerStyle(.segmented)
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
            Link(destination: urlOpiniones) { Label("enviar_opiniones", systemImage: "envelope") }
        }
        .listRowBackground(Paleta.tarjeta)
    }

    private var urlOpiniones: URL {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let asunto = t("opiniones_asunto", "Contacto")
        var partes = URLComponents()
        partes.scheme = "mailto"
        partes.path = "cmatuteortega@gmail.com"
        partes.queryItems = [
            URLQueryItem(name: "subject", value: asunto),
            URLQueryItem(name: "body", value: "\n\nContacto \(version) · iOS \(UIDevice.current.systemVersion) · \(UIDevice.current.model)"),
        ]
        return partes.url ?? URL(string: "mailto:cmatuteortega@gmail.com")!
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
