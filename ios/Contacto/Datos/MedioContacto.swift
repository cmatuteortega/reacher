import Foundation

/// Como se prefiere contactar con alguien. Decide que abre el aviso y el boton
/// de la ficha. Se guarda por nombre, igual que en Android (y en las copias):
/// no renombrar los casos.
enum MedioContacto: String, CaseIterable, Codable {
    case MARCADOR, LLAMADA, WHATSAPP, SMS, TELEGRAM

    /// iOS no marca solo (siempre pregunta antes), asi que `LLAMADA` no se ofrece:
    /// una copia que lo traiga de Android se trata como `MARCADOR`.
    static var elegibles: [MedioContacto] { [.MARCADOR, .WHATSAPP, .SMS, .TELEGRAM] }

    /// Un nombre desconocido o ausente vuelve al marcador.
    static func desde(_ nombre: String?) -> MedioContacto {
        guard let medio = MedioContacto(rawValue: nombre ?? "") else { return .MARCADOR }
        return medio == .LLAMADA ? .MARCADOR : medio
    }

    var etiqueta: String {
        switch self {
        case .MARCADOR, .LLAMADA: return t("boton_llamar")
        case .WHATSAPP: return t("whatsapp")
        case .SMS: return t("medio_sms")
        case .TELEGRAM: return t("telegram")
        }
    }

    /// Texto corto del boton de la ficha.
    var boton: String {
        switch self {
        case .MARCADOR, .LLAMADA: return t("boton_llamar")
        case .WHATSAPP: return t("whatsapp")
        case .SMS: return t("boton_mensaje")
        case .TELEGRAM: return t("telegram")
        }
    }

    /// Texto del aviso; lleva el nombre de la persona.
    func aviso(nombre: String) -> String {
        let clave: String.LocalizationValue
        switch self {
        case .MARCADOR, .LLAMADA: clave = "aviso_llamar"
        case .WHATSAPP: clave = "aviso_whatsapp"
        case .SMS: clave = "aviso_sms"
        case .TELEGRAM: clave = "aviso_telegram"
        }
        return String(format: String(localized: clave), nombre)
    }

    var icono: String {
        switch self {
        case .MARCADOR, .LLAMADA: return "phone.fill"
        case .WHATSAPP: return "bubble.left.and.bubble.right.fill"
        case .SMS: return "message.fill"
        case .TELEGRAM: return "paperplane.fill"
        }
    }
}
