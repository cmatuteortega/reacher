import SwiftUI

/// Cada persona es una burbuja, mas grande cuanto mas cerca esta de su fecha.
/// Se colocan con un empaquetado circular (las urgentes en el centro, sin
/// solaparse) y flotan; se pueden arrastrar y al soltarlas vuelven con un rebote.
struct VistaBurbujas: View {
    let personas: [Contacto]
    var alAbrir: (Contacto) -> Void
    var alLlamarHoy: (Contacto) -> Void

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento

    var body: some View {
        GeometryReader { geo in
            let orden = personas.porUrgencia()
            let diseno = Empaquetado.colocar(
                radios: orden.map { Empaquetado.radio(urgencia: $0.urgencia(), maximo: min(geo.size.width * 0.27, 90)) },
                ancho: geo.size.width - 24
            )
            let alto = max(geo.size.height, diseno.alto + 120)
            ScrollView(.vertical, showsIndicators: false) {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reducirMovimiento)) { linea in
                    let t = reducirMovimiento ? 0 : linea.date.timeIntervalSinceReferenceDate
                    ZStack {
                        ForEach(Array(orden.enumerated()), id: \.element.uuid) { i, c in
                            if i < diseno.puestos.count {
                                let p = diseno.puestos[i]
                                BurbujaFlotante(contacto: c, radio: p.radio, alAbrir: { alAbrir(c) })
                                    .accessibilityAction(named: Text("he_llamado_hoy")) { alLlamarHoy(c) }
                                    .position(
                                        x: geo.size.width / 2 + p.centro.x + CGFloat(sin(t * 0.6 + Double(i)) * 3),
                                        y: alto / 2 + p.centro.y + CGFloat(cos(t * 0.5 + Double(i) * 1.7) * 3)
                                    )
                            }
                        }
                    }
                    .frame(width: geo.size.width, height: alto)
                }
            }
        }
    }
}

/// Una burbuja que se arrastra y vuelve sola a su sitio.
private struct BurbujaFlotante: View {
    let contacto: Contacto
    let radio: CGFloat
    var alAbrir: () -> Void
    @State private var arrastre: CGSize = .zero
    @State private var cogida = false

    var body: some View {
        BurbujaView(contacto: contacto, radio: radio, mostrarNombre: radio >= 24, urgencia: contacto.urgencia())
            .scaleEffect(cogida ? 1.08 : 1)
            .offset(arrastre)
            .gesture(
                DragGesture(minimumDistance: 6)
                    .onChanged { v in
                        if !cogida { Haptico.agarrar() }
                        cogida = true
                        arrastre = v.translation
                    }
                    .onEnded { v in
                        // Soltarla con impulso (lo que seguiria moviendose) es lanzarla.
                        let impulso = hypot(v.predictedEndTranslation.width - v.translation.width, v.predictedEndTranslation.height - v.translation.height)
                        if impulso > 220 { Haptico.lanzar() }
                        cogida = false
                        withAnimation(.interpolatingSpring(stiffness: 120, damping: 8)) { arrastre = .zero }
                    }
            )
            .onTapGesture(perform: alAbrir)
    }
}
