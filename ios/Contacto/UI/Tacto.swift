import SwiftUI
import UIKit

/// Los hapticos de `ui/Tacto.kt`, con los motores de iOS. Para lo que no es un
/// simple cambio de valor (arrastres, ruedas); donde basta con eso, mejor
/// `.sensoryFeedback`, que respeta lo que el usuario tenga apagado.
@MainActor
enum Haptico {
    private static let seleccion = UISelectionFeedbackGenerator()
    private static let suave = UIImpactFeedbackGenerator(style: .light)
    private static let firme = UIImpactFeedbackGenerator(style: .rigid)
    private static let medio = UIImpactFeedbackGenerator(style: .medium)
    private static let aviso = UINotificationFeedbackGenerator()

    /// Golpecito corto, como el diente de una rueda: para cada paso al arrastrar.
    static func tic() {
        seleccion.selectionChanged()
        seleccion.prepare()
    }

    /// Toque algo mas marcado: al agarrar un asa o elegir con un toque.
    static func toque() {
        suave.impactOccurred()
        suave.prepare()
    }

    /// Confirmacion firme: cuando algo se completa.
    static func confirmar() {
        aviso.notificationOccurred(.success)
    }

    /// Al coger una burbuja con el dedo: el inicio de un arrastre.
    static func agarrar() {
        medio.impactOccurred()
        medio.prepare()
    }

    /// Al lanzarla con fuerza: el final del gesto, un golpe seco.
    static func lanzar() {
        firme.impactOccurred()
        firme.prepare()
    }

    /// Un choque con `fuerza` de 0 a 1: un roce suena flojo y un golpe, fuerte.
    static func choque(_ fuerza: Double) {
        let f = min(max(fuerza, 0), 1)
        suave.impactOccurred(intensity: 0.15 + 0.85 * CGFloat(f))
    }
}

/// Fila de opciones con una pastilla que se desliza a la elegida, como
/// `Deslizador` de Android. Se puede tocar una o arrastrar el dedo por encima:
/// cada opcion que se cruza da un tic. Para VoiceOver es un selector normal.
struct Deslizador<Opcion: Hashable>: View {
    let opciones: [Opcion]
    let elegida: Opcion
    let texto: (Opcion) -> String
    let alElegir: (Opcion) -> Void

    private var indice: Int { opciones.firstIndex(of: elegida) ?? -1 }

    var body: some View {
        GeometryReader { geo in
            let cuantas = max(opciones.count, 1)
            let ancho = geo.size.width / CGFloat(cuantas)
            ZStack(alignment: .leading) {
                Capsule().fill(Paleta.borde.opacity(0.35))
                Capsule()
                    .fill(Paleta.principal)
                    .padding(4)
                    .frame(width: ancho)
                    .offset(x: ancho * CGFloat(max(indice, 0)))
                    .opacity(indice >= 0 ? 1 : 0)
                    .animation(.spring(response: 0.38, dampingFraction: 0.65), value: indice)
                HStack(spacing: 0) {
                    ForEach(Array(opciones.enumerated()), id: \.offset) { i, opcion in
                        Text(verbatim: texto(opcion))
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .foregroundStyle(i == indice ? Paleta.sobrePrincipal : Paleta.textoSecundario)
                            .frame(width: ancho)
                            .animation(.easeOut(duration: 0.2), value: indice)
                    }
                }
            }
            .contentShape(Capsule())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        let i = min(max(Int(v.location.x / ancho), 0), cuantas - 1)
                        if opciones.indices.contains(i), opciones[i] != elegida { alElegir(opciones[i]) }
                    }
            )
        }
        .frame(height: 52)
        .sensoryFeedback(.selection, trigger: elegida)
        .accessibilityRepresentation {
            Picker(selection: Binding(get: { elegida }, set: alElegir)) {
                ForEach(opciones, id: \.self) { Text(verbatim: texto($0)).tag($0) }
            } label: { EmptyView() }
            .pickerStyle(.segmented)
        }
    }
}
