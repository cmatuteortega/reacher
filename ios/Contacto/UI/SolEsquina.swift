import SwiftUI
import UIKit

/// El sol de la pantalla principal, igual que el de Android (`SolEsquina.kt`):
/// metido en la esquina de arriba a la izquierda de la pantalla, con solo un
/// cuarto asomando y sus rayos ondulados girando alrededor. Los ojos van sobre
/// la esfera entera, con la cabeza girada hacia abajo a la derecha: asi miran
/// a las burbujas de al lado y se estrechan hacia el borde.
struct SolEsquinaView: View {
    /// Largo de cada onda del borde y cuanto sobresale.
    private static let onda: CGFloat = 44
    static let punta: CGFloat = 16

    /// Grados que gira la cabeza para asomar por la esquina: a la derecha y hacia abajo.
    private static let giroDerecha = 24.0
    private static let giroAbajo = 30.0
    /// Tamano de los ojos y de sus gestos respecto a una burbuja normal.
    private static let ojos: CGFloat = 0.5
    private static let gestos = 0.25

    /// Lo que mide el borde de arriba (la barra de estado o la isla), sin la barra de navegacion.
    static var arriba: CGFloat {
        let ventana = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.windows.first(where: \.isKeyWindow) }
            .first
        return ventana?.safeAreaInsets.top ?? 0
    }

    /// Radio del sol sin contar el borde de arriba, segun el ancho de la pantalla.
    static func radioBase(ancho: CGFloat) -> CGFloat { min(max(ancho * 0.3, 100), 140) }

    /// Radio del cuerpo del sol, con lo que entra bajo el borde de arriba.
    static func radio(ancho: CGFloat) -> CGFloat { radioBase(ancho: ancho) + arriba }

    /// Hasta donde llegan las puntas: lo que el resto de la pantalla le deja libre.
    static func radioLibre(ancho: CGFloat) -> CGFloat { radio(ancho: ancho) + punta }

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento

    var body: some View {
        GeometryReader { geo in
            let radio = SolEsquinaView.radio(ancho: geo.size.width)
            TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reducirMovimiento)) { linea in
                Canvas { contexto, _ in
                    let t = reducirMovimiento ? 0 : linea.date.timeIntervalSinceReferenceDate
                    SolEsquinaView.dibujar(&contexto, radio: radio, t: t)
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityElement()
        .accessibilityLabel(Text("tu_el_sol"))
    }

    private static func dibujar(_ contexto: inout GraphicsContext, radio: CGFloat, t: Double) {
        let centro = CGPoint.zero
        let hondura = Double(punta / radio) * (1 + 0.12 * sin(t * 1.7))
        // Entero: el corte no se ve ni aunque asome el sol entero.
        let puntas = (2 * CGFloat.pi * radio / onda).rounded()

        // El halo brilla hacia fuera: mas vivo junto al cuerpo y se apaga en las puntas.
        let radioHalo = radio * 1.06 * CGFloat(1 + hondura * 1.35)
        let halo = Gradient(stops: [
            .init(color: Paleta.acento.opacity(0.55), location: Double(min(radio / radioHalo, 0.9))),
            .init(color: Paleta.acento.opacity(0.08), location: 1),
        ])
        contexto.fill(
            ondas(centro: centro, radio: radio * 1.06, hondura: hondura * 1.35, puntas: puntas, fase: t * -0.2 + 0.35),
            with: .radialGradient(halo, center: centro, startRadius: 0, endRadius: radioHalo)
        )
        contexto.fill(
            ondas(centro: centro, radio: radio, hondura: hondura, puntas: puntas, fase: t * 0.35),
            with: .color(Paleta.acento)
        )

        // Mira alrededor, a un lado y al otro, y parpadea.
        let gx = giroDerecha + (sin(t * 0.7) * 22 + sin(t * 1.9 + 1) * 6) * gestos
        let gy = giroAbajo + cos(t * 0.5) * 8 * gestos
        let fase = t.truncatingRemainder(dividingBy: 4.5)
        let parpadeo = fase < 0.17 ? max(1 - sin(fase / 0.17 * .pi), 0.05) : 1
        dibujarOjos(&contexto, centro: centro, radio: radio, giroY: gx, giroX: -gy, parpadeo: parpadeo)
    }

    /// Silueta del sol: un circulo con `puntas` ondas redondeadas que sobresalen `hondura` radios.
    private static func ondas(centro: CGPoint, radio: CGFloat, hondura: Double, puntas: CGFloat, fase: Double) -> Path {
        var trazo = Path()
        let muestras = max(180, Int(puntas * 14))
        for i in 0...muestras {
            let giro = 2 * Double.pi * Double(i) / Double(muestras)
            let th = giro + Double.pi * 1.25
            let onda = pow(0.5 + 0.5 * cos(Double(puntas) * giro - fase), 1.8)
            let r = Double(radio) * (1 + hondura * onda)
            let p = CGPoint(x: centro.x + CGFloat(r * cos(th)), y: centro.y + CGFloat(r * sin(th)))
            if i == 0 { trazo.move(to: p) } else { trazo.addLine(to: p) }
        }
        trazo.closeSubpath()
        return trazo
    }

    /// Dos elipses pegadas a la esfera de `radio`, vistas con la cabeza girada
    /// `giroY` grados a la derecha y `giroX` hacia arriba: se deslizan por ella,
    /// se estrechan al acercarse al borde y se esconden detras.
    private static func dibujarOjos(_ contexto: inout GraphicsContext, centro: CGPoint, radio: CGFloat, giroY: Double, giroX: Double, parpadeo: Double) {
        let rad = Double.pi / 180
        let cy = cos(giroY * rad), sy = sin(giroY * rad)
        let cx = cos(giroX * rad), sx = sin(giroX * rad)
        let perspectiva = 0.3
        // Con perspectiva la silueta de la esfera se ve mayor: se escala para que ocupe el radio.
        let ajuste = (1 - perspectiva * perspectiva).squareRoot()

        // Ojos neutros de siempre, a `ojos` de su tamano.
        let k = Double(ojos)
        let separacion = 0.54 * k
        let a = 0.24 * k / 2
        let b = max(0.35 * k / 2 * parpadeo, 0.006)
        let puntos = 32

        for lado in [-1.0, 1.0] {
            let centroU = lado * separacion / 2
            var trazo = Path()
            var profundidad = 0.0
            for i in 0..<puntos {
                let th = 2 * Double.pi * Double(i) / Double(puntos)
                var u = centroU + a * cos(th)
                var v = b * sin(th)
                let d2 = u * u + v * v
                if d2 > 0.96 {
                    let f = (0.96 / d2).squareRoot()
                    u *= f
                    v *= f
                }
                // Punto de la esfera que queda debajo, mirando a camara.
                let w = max(0, 1 - u * u - v * v).squareRoot()
                // Giro: primero a los lados, luego arriba y abajo.
                let x1 = u * cy + w * sy
                let z1 = -u * sy + w * cy
                let y2 = v * cx - z1 * sx
                let z2 = v * sx + z1 * cx
                profundidad += z2
                let px: Double, py: Double
                if z2 < perspectiva {
                    // Detras del borde: se queda pegado a la silueta.
                    let l = max((x1 * x1 + y2 * y2).squareRoot(), 1e-4)
                    px = x1 / l
                    py = y2 / l
                } else {
                    let f = ajuste / (1 - perspectiva * z2)
                    px = x1 * f
                    py = y2 * f
                }
                let p = CGPoint(x: centro.x + CGFloat(px) * radio, y: centro.y + CGFloat(py) * radio)
                if i == 0 { trazo.move(to: p) } else { trazo.addLine(to: p) }
            }
            if profundidad / Double(puntos) > perspectiva * 0.6 {
                trazo.closeSubpath()
                contexto.fill(trazo, with: .color(Paleta.ojos))
            }
        }
    }
}
