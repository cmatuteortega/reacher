import SwiftUI

/// Las burbujas en anillos alrededor del sol de la esquina. La urgencia solo se
/// ve en el tamano; el anillo es la frecuencia, y cada anillo lleva escrito "<X D".
struct VistaOrbitas: View {
    let personas: [Contacto]
    var alAbrir: (Contacto) -> Void
    var alLlamarHoy: (Contacto) -> Void

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    private let radioSol: CGFloat = 40
    private let centroSol = CGPoint(x: 26, y: 26)

    var body: some View {
        GeometryReader { geo in
            let ordenadas = personas.porFrecuencia()
            let plano = Orbitas.planear(
                personas: ordenadas.map { EnOrbita(id: $0.uuid, frecuencia: $0.frecuenciaDias, urgencia: $0.urgencia()) },
                tamano: geo.size, sol: centroSol, radioSol: radioSol
            )
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reducirMovimiento)) { linea in
                    let t = reducirMovimiento ? 0 : linea.date.timeIntervalSinceReferenceDate
                    ZStack(alignment: .topLeading) {
                        anillos(plano)
                        letreros(plano)
                        ForEach(ordenadas, id: \.uuid) { c in
                            if let puesto = plano.puestos[c.uuid] {
                                let a = puesto.angulo + vaiven(anillo: puesto.anillo, t: t)
                                let r = plano.radios[puesto.anillo]
                                BurbujaView(
                                    contacto: c, radio: puesto.radio,
                                    mostrarNombre: puesto.radio >= 20, urgencia: c.urgencia()
                                )
                                // El toque va antes que position: position ocupa todo el espacio y se lo comeria.
                                .onTapGesture { alAbrir(c) }
                                .accessibilityAction(named: Text("he_llamado_hoy")) { alLlamarHoy(c) }
                                .position(
                                    x: centroSol.x + r * CGFloat(cos(a)),
                                    y: centroSol.y + r * CGFloat(sin(a)) + (puesto.radio >= 20 ? 8 : 0)
                                )
                            }
                        }
                    }
                }
                SolView(radio: radioSol)
                    .position(centroSol)
                    .allowsHitTesting(false)
            }
            .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
            .animation(.spring(response: 0.6, dampingFraction: 0.8), value: ordenadas.map(\.uuid))
            .clipped()
        }
    }

    /// Cada anillo va y viene un poco, uno en cada sentido.
    private func vaiven(anillo: Int, t: Double) -> Double {
        0.05 * sin(t * 0.5 + Double(anillo) * 1.3) * (anillo % 2 == 0 ? 1 : -1)
    }

    private func anillos(_ plano: PlanoOrbitas) -> some View {
        Canvas { contexto, _ in
            for (k, r) in plano.radios.enumerated() {
                var camino = Path()
                let pasos = 90
                for i in 0...pasos {
                    let a = Double(i) / Double(pasos) * Double.pi / 2
                    let p = CGPoint(x: centroSol.x + r * CGFloat(cos(a)), y: centroSol.y + r * CGFloat(sin(a)))
                    if i == 0 { camino.move(to: p) } else { camino.addLine(to: p) }
                }
                contexto.stroke(
                    camino, with: .color(Paleta.malva.opacity(k == 0 ? 0.55 : 0.4)),
                    style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [3, 7])
                )
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func letreros(_ plano: PlanoOrbitas) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(plano.letreros.enumerated()), id: \.offset) { _, l in
                let r = plano.radios[l.anillo]
                Text(verbatim: String(format: String(localized: "letrero_orbita"), l.dias))
                    .font(.system(size: 11, weight: .heavy))
                    .tracking(1.2)
                    .foregroundStyle(Paleta.acentoLegible)
                    // A lo largo del anillo, siempre legible (de abajo hacia arriba nunca al reves).
                    .rotationEffect(.radians(l.angulo - Double.pi / 2))
                    .position(
                        x: centroSol.x + r * CGFloat(cos(l.angulo)),
                        y: centroSol.y + r * CGFloat(sin(l.angulo))
                    )
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
