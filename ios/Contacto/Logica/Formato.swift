import Foundation

/// Texto traducido de una clave del catalogo (`Localizable.xcstrings`, que sale de los strings.xml de Android).
func t(_ clave: String.LocalizationValue) -> String { String(localized: clave) }

/// Texto con marcadores posicionales ("%1$@") de una clave del catalogo.
func t(_ clave: String.LocalizationValue, _ argumentos: CVarArg...) -> String {
    String(format: String(localized: clave), arguments: argumentos)
}

/// Los plurales de cada gramatica. El `\(n)` hace que la clave sea "dias %lld", la del catalogo.
enum Plural {
    static func dias(_ n: Int) -> String { String(localized: "dias \(n)") }
    static func horas(_ n: Int) -> String { String(localized: "horas \(n)") }
    static func cadaDias(_ n: Int) -> String { n == 1 ? t("cada_dia") : String(localized: "cada_dias \(n)") }
    static func tocaEnDias(_ n: Int) -> String { String(localized: "toca_en_dias \(n)") }
    static func diasTarde(_ n: Int) -> String { String(localized: "dias_tarde \(n)") }
    static func enDias(_ n: Int) -> String { String(localized: "en_dias \(n)") }
    static func resumenAvisos(_ n: Int) -> String { String(localized: "resumen_avisos \(n)") }
    static func veces(_ n: Int) -> String { String(localized: "veces \(n)") }
    static func copiaExportada(_ n: Int) -> String { String(localized: "copia_exportada \(n)") }
}

enum Formato {
    /// "12 de marzo", "March 12"...: dia y mes en el orden y la forma de cada idioma.
    static func diaYMes(mes: Int, dia: Int) -> String {
        guard let fecha = Fechas.fecha(mes: mes, dia: dia, anio: 2024) else { return "" }
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("dMMMM")
        return f.string(from: fecha)
    }

    static func fecha(_ fecha: Date) -> String {
        fecha.formatted(date: .abbreviated, time: .omitted)
    }

    /// "9:00" / "09:00" segun el telefono, para las horas de aviso.
    static func hora(_ h: Int) -> String {
        guard let fecha = Fechas.calendario.date(bySettingHour: h, minute: 0, second: 0, of: Date()) else { return "\(h):00" }
        return fecha.formatted(date: .omitted, time: .shortened)
    }

    /// "toca hoy" / "toca mañana" / "toca en N días" / "N días de retraso" de una persona.
    static func cuandoToca(_ c: Contacto, hoy: Date = Date()) -> String {
        let hasta = Fechas.dias(de: hoy, a: c.proximoAviso())
        if hasta < 0 { return Plural.diasTarde(-hasta) }
        if hasta == 0 { return t("toca_hoy") }
        if hasta == 1 { return t("toca_manana") }
        return Plural.tocaEnDias(hasta)
    }
}
