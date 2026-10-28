import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Abre la forma de contacto elegida con el numero puesto. iOS siempre pide
/// confirmacion antes de marcar, asi que no hay "llamar directamente": el
/// marcador y la llamada son lo mismo.
enum Contactar {
    /// Lo que se abre para cada forma de contacto. `pais` (ISO, "ES") completa los numeros sin prefijo.
    static func url(telefono: String, medio: MedioContacto, pais: String) -> URL? {
        switch medio {
        case .MARCADOR, .LLAMADA: return URL(string: "tel:" + paraEsquema(telefono))
        case .SMS: return URL(string: "sms:" + paraEsquema(telefono))
        // Los enlaces web los abre la app si esta instalada, y si no el navegador.
        case .WHATSAPP: return URL(string: "https://wa.me/" + internacional(telefono, pais: pais))
        case .TELEGRAM: return URL(string: "https://t.me/+" + internacional(telefono, pais: pais))
        }
    }

    /// Cifras, "+", "*" y "#" (esta ultima codificada): lo que entienden `tel:` y `sms:`.
    static func paraEsquema(_ telefono: String) -> String {
        let limpio = telefono.filter { $0.isASCII && ($0.isNumber || "+*#".contains($0)) }
        return limpio.addingPercentEncoding(withAllowedCharacters: CharacterSet(charactersIn: "+0123456789*")) ?? limpio
    }

    static func paisActual() -> String {
        Locale.current.region?.identifier ?? ""
    }

    /// WhatsApp y Telegram quieren el numero con prefijo de pais y solo cifras.
    /// Los de la agenda suelen venir sin prefijo ("600 12 34 56"): se completa con `pais`.
    static func internacional(_ telefono: String, pais: String) -> String {
        let escrito = telefono.trimmingCharacters(in: .whitespaces)
        let cifras = escrito.filter { $0.isASCII && $0.isNumber }
        if escrito.hasPrefix("+") { return cifras }
        if cifras.hasPrefix("00") { return String(cifras.dropFirst(2)) }
        guard let prefijo = prefijos[pais.uppercased()] else { return cifras }
        // Ya con el prefijo y largo de mas para ser nacional: "34600123456".
        if cifras.hasPrefix(prefijo), cifras.count >= 11 { return cifras }
        // El 0 de marcar entre provincias no va en el internacional.
        let nacional = cifras.drop(while: { $0 == "0" })
        return prefijo + nacional
    }

    /// Prefijos telefonicos de los paises donde mas se usara la app.
    static let prefijos: [String: String] = [
        "ES": "34", "FR": "33", "DE": "49", "GB": "44", "IE": "353", "PT": "351", "IT": "39", "NL": "31",
        "BE": "32", "CH": "41", "AT": "43", "SE": "46", "NO": "47", "DK": "45", "FI": "358", "PL": "48",
        "CZ": "420", "GR": "30", "RU": "7", "UA": "380", "TR": "90", "US": "1", "CA": "1", "MX": "52",
        "AR": "54", "CL": "56", "CO": "57", "PE": "51", "VE": "58", "UY": "598", "EC": "593", "BO": "591",
        "PY": "595", "CR": "506", "PA": "507", "DO": "1", "BR": "55", "AU": "61", "NZ": "64", "IN": "91",
        "CN": "86", "JP": "81", "KR": "82", "ZA": "27", "MA": "212", "EG": "20", "IL": "972", "AE": "971",
    ]

#if canImport(UIKit)
    /// Abre el medio; false si no hay app para hacerlo (sin SMS en un iPad, por ejemplo).
    @MainActor
    @discardableResult
    static func abrir(telefono: String, medio: MedioContacto) async -> Bool {
        guard let url = url(telefono: telefono, medio: medio, pais: paisActual()) else { return false }
        return await UIApplication.shared.open(url)
    }
#endif
}
