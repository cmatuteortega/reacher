import UIKit
import SwiftUI

/// La cara de una burbuja: dos ojos sobre la esfera que miran alrededor y
/// parpadean. La expresion sale de lo urgente que sea hablar con la persona,
/// como en Android: duerme si va sobrada, mira alrededor en reposo, asiente
/// contenta cuando toca y se impacienta si se pasa mucho. Con "reducir
/// movimiento" del sistema se queda quieta.
struct CaraView: View {
    var urgencia: Double
    /// Desfasa a cada cara para que no parpadeen todas a la vez.
    var semilla: Double = 0

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reducirMovimiento)) { linea in
            Canvas { contexto, tamano in
                let t = reducirMovimiento ? 0 : linea.date.timeIntervalSinceReferenceDate + semilla * 7.31
                CaraView.dibujar(&contexto, tamano: tamano, t: t, urgencia: urgencia, semilla: semilla)
            }
        }
        .accessibilityHidden(true)
    }

    private static func dibujar(_ contexto: inout GraphicsContext, tamano: CGSize, t: Double, urgencia: Double, semilla: Double) {
        let w = tamano.width, h = tamano.height
        let dormida = urgencia < 0.3
        let contenta = urgencia >= 0.95 && urgencia < 1.6
        let impaciente = urgencia >= 1.6

        // A donde mira: una deriva lenta, mas nerviosa cuanto mas urgente.
        let ritmo = impaciente ? 2.2 : 1.0
        let mirax = sin(t * 0.7 * ritmo) * 0.55 + sin(t * 1.9 * ritmo + 1) * 0.2
        let miray = cos(t * 0.5 * ritmo) * 0.3
        let asiente = contenta ? sin(t * 5) * h * 0.018 : 0

        // Parpadeo: cada 4 a 6 s, 0.15 s (de 1 a 0 a 1).
        let periodo = 4 + (semilla.truncatingRemainder(dividingBy: 2) + 2).truncatingRemainder(dividingBy: 2)
        let fase = t.truncatingRemainder(dividingBy: periodo)
        let parpadeo = fase < 0.15 ? abs(fase / 0.075 - 1) : 1

        var abierto = 1.0
        if dormida { abierto = 0.16 + 0.12 * (sin(t * 0.9) * 0.5 + 0.5) }
        if impaciente { abierto = 0.8 }
        abierto *= parpadeo

        let ew = w * 0.065
        let eh = h * 0.11
        let cy = h * 0.46 + miray * h * 0.04 + asiente
        for lado in [-1.0, 1.0] {
            // Al girar la cabeza los ojos se estrechan hacia el borde: el aire de 3D.
            let estrecha = 1 - 0.25 * abs(mirax) * (lado * mirax > 0 ? 1 : 0.6)
            let cx = w / 2 + lado * w * 0.19 + mirax * w * 0.05
            if contenta {
                var arco = Path()
                arco.move(to: CGPoint(x: cx - ew * estrecha * 1.1, y: cy + eh * 0.35))
                arco.addQuadCurve(to: CGPoint(x: cx + ew * estrecha * 1.1, y: cy + eh * 0.35), control: CGPoint(x: cx, y: cy - eh * 0.95))
                contexto.stroke(arco, with: .color(Paleta.ojos), style: StrokeStyle(lineWidth: max(w * 0.045, 1.5), lineCap: .round))
            } else {
                let alto = max(eh * 2 * abierto, 1.5)
                let ojo = CGRect(x: cx - ew * estrecha, y: cy - alto / 2, width: ew * 2 * estrecha, height: alto)
                contexto.fill(Path(ellipseIn: ojo), with: .color(Paleta.ojos))
            }
        }
    }
}

/// El sol, que eres tu: una esfera con ojos y puntas onduladas que giran
/// despacio, con un halo. Como el de la esquina de Personas en Android.
struct SolView: View {
    var radio: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reducirMovimiento)) { linea in
            Canvas { contexto, tamano in
                let t = reducirMovimiento ? 0 : linea.date.timeIntervalSinceReferenceDate
                let centro = CGPoint(x: tamano.width / 2, y: tamano.height / 2)
                let r = min(tamano.width, tamano.height) / 2 / 1.4
                // Halo que respira.
                let halo = r * (1.32 + 0.04 * sin(t * 1.3))
                let gradiente = Gradient(colors: [Paleta.acento.opacity(0.45), Paleta.acento.opacity(0)])
                contexto.fill(
                    Path(ellipseIn: CGRect(x: centro.x - halo, y: centro.y - halo, width: halo * 2, height: halo * 2)),
                    with: .radialGradient(gradiente, center: centro, startRadius: r * 0.8, endRadius: halo)
                )
                // El cuerpo: un circulo con puntas onduladas.
                var cuerpo = Path()
                let pasos = 180
                let giro = t * 0.35
                for i in 0...pasos {
                    let a = Double(i) / Double(pasos) * 2 * .pi
                    let rr = r * (1 + 0.09 * sin(12 * a + giro * 4))
                    let p = CGPoint(x: centro.x + rr * cos(a + giro * 0.5), y: centro.y + rr * sin(a + giro * 0.5))
                    if i == 0 { cuerpo.move(to: p) } else { cuerpo.addLine(to: p) }
                }
                cuerpo.closeSubpath()
                contexto.fill(cuerpo, with: .color(Paleta.acento))
                // Los ojos, con parpadeo.
                let fase = t.truncatingRemainder(dividingBy: 5)
                let parpadeo = fase < 0.16 ? abs(fase / 0.08 - 1) : 1
                let mira = sin(t * 0.6) * r * 0.05
                for lado in [-1.0, 1.0] {
                    let alto = max(r * 0.34 * parpadeo, 1.5)
                    let cx = centro.x + lado * r * 0.24 + mira
                    let ojo = CGRect(x: cx - r * 0.075, y: centro.y - r * 0.05 - alto / 2, width: r * 0.15, height: alto)
                    contexto.fill(Path(ellipseIn: ojo), with: .color(Paleta.ojos))
                }
            }
        }
        .frame(width: radio * 2.8, height: radio * 2.8)
        .accessibilityLabel(Text("tu_el_sol"))
    }
}

/// Foto de la agenda (si la hay) o la inicial, dentro de un circulo.
struct AvatarView: View {
    var contacto: Contacto
    var tamano: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(Paleta.burbuja)
            if let datos = contacto.foto, let imagen = UIImage(data: datos) {
                Image(uiImage: imagen).resizable().scaledToFill()
            } else {
                Text(verbatim: contacto.inicial)
                    .font(.system(size: tamano * 0.45, weight: .semibold, design: .rounded))
                    .foregroundStyle(Paleta.texto)
            }
        }
        .frame(width: tamano, height: tamano)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}
