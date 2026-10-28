import SwiftUI
import UIKit

/// La paleta de la app, la misma del icono y de Android (`ui/Tema.kt`), en claro y oscuro.
/// Sin colores del sistema salvo en controles estandar: la paleta es la identidad.
enum Paleta {
    // De oscuro a claro.
    static let noche = Color(hex: 0x0D2B45)
    static let marino = Color(hex: 0x203C56)
    static let ciruela = Color(hex: 0x544E68)
    static let malva = Color(hex: 0x8D697A)
    static let teja = Color(hex: 0xD08159)
    static let naranja = Color(hex: 0xFFAA5E)
    static let melocoton = Color(hex: 0xFFD4A3)
    static let crema = Color(hex: 0xFFECD6)

    static let fondo = Color.dinamico(claro: 0xFFF6EC, oscuro: 0x0D2B45)
    static let tarjeta = Color.dinamico(claro: 0xFFECD6, oscuro: 0x203C56)
    static let texto = Color.dinamico(claro: 0x0D2B45, oscuro: 0xFFECD6)
    static let textoSecundario = Color.dinamico(claro: 0x544E68, oscuro: 0xE6CDBD)
    static let borde = Color.dinamico(claro: 0xE2C8BA, oscuro: 0x4A4A66)
    /// Burbujas: melocoton en claro, ciruela en oscuro.
    static let burbuja = Color.dinamico(claro: 0xFFD4A3, oscuro: 0x544E68)
    /// El acento para el sol y los halos: teja en claro, naranja en oscuro.
    static let acento = Color.dinamico(claro: 0xD08159, oscuro: 0xFFAA5E)
    /// El acento para texto e iconos pequenos: la teja oscurecida hasta 4.5:1 en claro.
    static let acentoLegible = Color.dinamico(claro: 0xA4562E, oscuro: 0xFFAA5E)
    /// Color principal de botones: marino en claro, melocoton en oscuro.
    static let principal = Color.dinamico(claro: 0x203C56, oscuro: 0xFFD4A3)
    static let sobrePrincipal = Color.dinamico(claro: 0xFFECD6, oscuro: 0x0D2B45)
    /// Lo que se dibuja sobre el sol y las caras (siempre oscuro: los ojos).
    static let ojos = Color(hex: 0x0D2B45)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }

    /// Un color que cambia solo con el modo claro u oscuro de cada vista (el elegido en Ajustes tambien).
    static func dinamico(claro: UInt32, oscuro: UInt32) -> Color {
        Color(UIColor { rasgos in
            let h = rasgos.userInterfaceStyle == .dark ? oscuro : claro
            return UIColor(
                red: CGFloat((h >> 16) & 0xFF) / 255,
                green: CGFloat((h >> 8) & 0xFF) / 255,
                blue: CGFloat(h & 0xFF) / 255,
                alpha: 1
            )
        })
    }
}

extension TemaElegido {
    /// El esquema que se fuerza (nil = el del sistema).
    var esquema: ColorScheme? {
        switch self {
        case .SISTEMA: return nil
        case .CLARO: return .light
        case .OSCURO: return .dark
        }
    }
}

/// Boton lleno con la paleta, como los de Material de la version de Android.
struct EstiloBotonPrincipal: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(Paleta.sobrePrincipal)
            .frame(maxWidth: .infinity, minHeight: 48)
            .padding(.horizontal, 20)
            .background(Paleta.principal, in: Capsule())
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

struct EstiloBotonSecundario: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(Paleta.acentoLegible)
            .frame(maxWidth: .infinity, minHeight: 48)
            .padding(.horizontal, 20)
            .overlay(Capsule().stroke(Paleta.borde, lineWidth: 1.5))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Fondo de tarjeta de las fichas y los ajustes.
struct Tarjeta<Contenido: View>: View {
    @ViewBuilder var contenido: Contenido

    var body: some View {
        contenido
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Paleta.tarjeta, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}
