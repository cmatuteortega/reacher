import Foundation

/// Fechas como en la app de Android: dias sin hora (el ultimo contacto es "hoy",
/// no "hoy a las 10:32") y texto ISO en los archivos de copia.
enum Fechas {
    static var calendario: Calendar { Calendar.current }

    static func inicioDia(_ fecha: Date) -> Date { calendario.startOfDay(for: fecha) }

    static func sumar(dias: Int, a fecha: Date) -> Date {
        calendario.date(byAdding: .day, value: dias, to: fecha) ?? fecha
    }

    /// Dias de calendario de `a` a `b` (negativo si `b` es anterior).
    static func dias(de a: Date, a b: Date) -> Int {
        calendario.dateComponents([.day], from: inicioDia(a), to: inicioDia(b)).day ?? 0
    }

    static func esBisiesto(_ anio: Int) -> Bool {
        (anio % 4 == 0 && anio % 100 != 0) || anio % 400 == 0
    }

    /// El dia de `mes`/`dia` en `anio`. El 29 de febrero se celebra el 28 los anos que no son bisiestos.
    static func fecha(mes: Int, dia: Int, anio: Int) -> Date? {
        let d = (mes == 2 && dia == 29 && !esBisiesto(anio)) ? 28 : dia
        return calendario.date(from: DateComponents(year: anio, month: mes, day: d))
    }

    private static func formateador(_ patron: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = TimeZone.current
        f.dateFormat = patron
        return f
    }

    /// "2025-03-12", como `LocalDate.toString()`.
    static func iso(dia: Date) -> String { formateador("yyyy-MM-dd").string(from: dia) }

    static func dia(iso texto: String?) -> Date? {
        guard let texto, texto.count >= 10 else { return nil }
        return formateador("yyyy-MM-dd").date(from: String(texto.prefix(10)))
    }

    /// "2025-03-12T10:15:30", como `LocalDateTime.toString()` (sin zona).
    static func iso(momento: Date) -> String { formateador("yyyy-MM-dd'T'HH:mm:ss").string(from: momento) }

    static func momento(iso texto: String?) -> Date? {
        guard let texto, texto.count >= 16 else { return nil }
        // Android escribe segundos solo si no son 0 y a veces fracciones: nos quedamos con "yyyy-MM-ddTHH:mm[:ss]".
        let recorte = String(texto.prefix(19))
        if recorte.count == 19 { return formateador("yyyy-MM-dd'T'HH:mm:ss").date(from: recorte) }
        return formateador("yyyy-MM-dd'T'HH:mm").date(from: String(texto.prefix(16)))
    }

    /// "--03-12", como `MonthDay.toString()`.
    static func iso(mes: Int, dia: Int) -> String { String(format: "--%02d-%02d", mes, dia) }

    static func mesDia(iso texto: String?) -> (mes: Int, dia: Int)? {
        guard let texto, texto.hasPrefix("--"), texto.count >= 7 else { return nil }
        let partes = texto.dropFirst(2).split(separator: "-")
        guard partes.count == 2, let m = Int(partes[0]), let d = Int(partes[1]),
              (1...12).contains(m), (1...31).contains(d) else { return nil }
        return (m, d)
    }
}
