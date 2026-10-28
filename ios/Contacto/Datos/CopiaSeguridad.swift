import Foundation
#if canImport(SwiftUI)
import SwiftUI
import UniformTypeIdentifiers
#endif

/// Copia en un archivo JSON para cambiar de telefono a mano. Es el mismo
/// formato que escribe la app de Android (`CopiaSeguridad.kt`): se puede
/// exportar en un telefono y abrir en el otro, sea cual sea cada uno.
/// Los campos que falten toman su valor por defecto y los que sobren se ignoran.
enum CopiaSeguridad {
    static let formato = 1
    private static let app = "contacto"

    struct ArchivoNoValido: Error {}

    struct PersonaCopia: Codable {
        var nombre: String
        var telefono: String
        var frecuenciaDias: Int?
        var ultimoContacto: String?
        var medio: String?
        var notas: String?
        var cumpleanos: String?
        var cumpleanosManual: Bool?
        var circulo: String?
        var pausadoHasta: String?

        enum CodingKeys: String, CodingKey {
            case nombre, telefono, medio, notas, cumpleanos, circulo
            case frecuenciaDias = "frecuencia_dias"
            case ultimoContacto = "ultimo_contacto"
            case cumpleanosManual = "cumpleanos_manual"
            case pausadoHasta = "pausado_hasta"
        }
    }

    struct AjustesCopia: Codable {
        var avisosActivos: Bool?
        var pausaHasta: String?
        var horaDesde: Int?
        var horaHasta: Int?
        var horasPosponer: Int?
        var medioPorDefecto: String?
        var tema: String?
        var vista: String?

        enum CodingKeys: String, CodingKey {
            case tema, vista
            case avisosActivos = "avisos_activos"
            case pausaHasta = "pausa_hasta"
            case horaDesde = "hora_desde"
            case horaHasta = "hora_hasta"
            case horasPosponer = "horas_posponer"
            case medioPorDefecto = "medio_por_defecto"
        }

        /// Los ajustes de ahora con los del archivo encima.
        func aplicar(a actuales: Ajustes) -> Ajustes {
            var a = actuales
            if let activos = avisosActivos {
                a.avisosActivos = activos
                // Una pausa solo tiene sentido con los avisos apagados.
                a.pausaHasta = activos ? nil : Fechas.dia(iso: pausaHasta)
            }
            if let h = horaDesde { a.horaDesde = min(max(h, 0), 23) }
            if let h = horaHasta { a.horaHasta = min(max(h, 0), 23) }
            if let h = horasPosponer { a.horasPosponer = min(max(h, 1), 24) }
            if let m = medioPorDefecto { a.medioPorDefecto = MedioContacto.desde(m) }
            if let t = tema, let tema = TemaElegido(rawValue: t) { a.tema = tema }
            if let v = vista, let vista = VistaPersonas(rawValue: v) { a.vista = vista }
            return a
        }
    }

    struct Archivo: Codable {
        var app: String?
        var formato: Int?
        var exportado: String?
        var ajustes: AjustesCopia?
        var personas: [PersonaCopia]?
    }

    /// Lo leido de un archivo, ya convertido a lo que guarda la app.
    struct Leido {
        var personas: [PersonaCopia]
        var ajustes: AjustesCopia?

        func aplicar(a actuales: Ajustes) -> Ajustes { ajustes?.aplicar(a: actuales) ?? actuales }
    }

    static func escribir(_ gente: [Contacto], ajustes: Ajustes, ahora: Date = Date()) throws -> Data {
        let archivo = Archivo(
            app: app,
            formato: formato,
            exportado: Fechas.iso(momento: ahora),
            ajustes: AjustesCopia(
                avisosActivos: ajustes.avisosActivos,
                pausaHasta: ajustes.pausaHasta.map { Fechas.iso(dia: $0) },
                horaDesde: ajustes.horaDesde,
                horaHasta: ajustes.horaHasta,
                horasPosponer: ajustes.horasPosponer,
                medioPorDefecto: ajustes.medioPorDefecto.rawValue,
                tema: ajustes.tema.rawValue,
                vista: ajustes.vista.rawValue
            ),
            personas: gente.map(persona)
        )
        let codificador = JSONEncoder()
        codificador.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try codificador.encode(archivo)
    }

    static func persona(_ c: Contacto) -> PersonaCopia {
        PersonaCopia(
            nombre: c.nombre,
            telefono: c.telefono,
            frecuenciaDias: c.frecuenciaDias,
            ultimoContacto: Fechas.iso(dia: c.ultimoContacto),
            medio: c.medio.rawValue,
            notas: c.notas,
            cumpleanos: c.cumpleanos.map { Fechas.iso(mes: $0.mes, dia: $0.dia) },
            cumpleanosManual: c.cumpleanosManual,
            circulo: c.circulo,
            pausadoHasta: c.pausadoHasta.map { Fechas.iso(momento: $0) }
        )
    }

    /// Lanza `ArchivoNoValido` si no es una copia de esta app (o de una version mas nueva).
    static func leer(_ datos: Data) throws -> Leido {
        guard let archivo = try? JSONDecoder().decode(Archivo.self, from: datos),
              archivo.app == app, (archivo.formato ?? 0) <= formato
        else { throw ArchivoNoValido() }
        let personas = (archivo.personas ?? []).filter {
            !($0.nombre.trimmingCharacters(in: .whitespaces).isEmpty && $0.telefono.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        return Leido(personas: personas, ajustes: archivo.ajustes)
    }

    /// La persona del archivo como la guarda la app.
    static func contacto(_ p: PersonaCopia) -> Contacto {
        let c = Contacto(
            nombre: p.nombre,
            telefono: p.telefono,
            frecuenciaDias: max(p.frecuenciaDias ?? 7, 1),
            ultimoContacto: Fechas.dia(iso: p.ultimoContacto) ?? Date(),
            medio: MedioContacto.desde(p.medio),
            notas: p.notas ?? "",
            cumpleanos: Fechas.mesDia(iso: p.cumpleanos),
            cumpleanosManual: p.cumpleanosManual ?? false,
            circulo: p.circulo?.trimmingCharacters(in: .whitespaces).nonVacio,
            pausadoHasta: Fechas.momento(iso: p.pausadoHasta)
        )
        return c
    }

    /// Solo las cifras (y el + inicial): el mismo numero escrito con o sin espacios es la misma persona.
    static func mismoTelefono(_ a: String, _ b: String) -> Bool {
        let na = normalizar(a)
        return !na.isEmpty && na == normalizar(b)
    }

    private static func normalizar(_ telefono: String) -> String {
        let cifras = telefono.filter { $0.isASCII && $0.isNumber }
        return telefono.trimmingCharacters(in: .whitespaces).hasPrefix("+") ? "+" + cifras : cifras
    }
}

extension String {
    var nonVacio: String? { isEmpty ? nil : self }
}

#if canImport(SwiftUI)
/// El archivo para el selector de archivos del sistema (`fileExporter`).
struct ArchivoCopia: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var datos: Data

    init(datos: Data) { self.datos = datos }

    init(configuration: ReadConfiguration) throws {
        datos = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: datos)
    }
}
#endif
