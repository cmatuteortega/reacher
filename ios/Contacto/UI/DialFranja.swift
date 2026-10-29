import SwiftUI

/// Esfera de 24 horas con la franja de avisos pintada como un arco, la de
/// `DialFranja.kt`. Las dos asas (inicio en el color principal, fin en teja) se
/// arrastran alrededor y saltan de hora en hora con un tic, como una rueda con
/// dientes. Cruzar la medianoche es solo seguir girando. El punto de fuera
/// marca la hora de ahora. Sin `conResumen` el centro queda libre.
struct DialFranja: View {
    let desde: Int
    let hasta: Int
    var conResumen = true
    let alCambiar: (_ desde: Int, _ hasta: Int) -> Void

    private enum Asa { case desde, hasta }

    @State private var agarrada: Asa?

    private var todoElDia: Bool { desde == hasta }
    private var horasAvisando: Int { todoElDia ? 24 : (hasta - desde + 24) % 24 }

    var body: some View {
        GeometryReader { geo in
            let m = Medidas(geo.size)
            ZStack(alignment: .topLeading) {
                Canvas { contexto, _ in dibujar(&contexto, m) }
                asa(hora: desde, color: Paleta.principal, tinta: Paleta.sobrePrincipal, viva: agarrada == .desde, m)
                asa(hora: hasta, color: Paleta.acento, tinta: Paleta.ojos, viva: agarrada == .hasta, m)
                if conResumen { resumen }
                // Solo el anillo agarra; el resto de la esfera deja desplazar la pantalla.
                Color.clear
                    .contentShape(Corona(centro: m.centro, desde: m.radio - m.grosor * 1.2, hasta: m.radio + m.grosor * 1.2), eoFill: true)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { v in mover(v, m) }
                            .onEnded { _ in agarrada = nil }
                    )
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .aspectRatio(1, contentMode: .fit)
        .sensoryFeedback(.selection, trigger: desde)
        .sensoryFeedback(.selection, trigger: hasta)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: descripcion))
        .accessibilityAction(named: Text(verbatim: t("empezar_antes"))) { alCambiar((desde + 23) % 24, hasta) }
        .accessibilityAction(named: Text(verbatim: t("empezar_despues"))) { alCambiar((desde + 1) % 24, hasta) }
        .accessibilityAction(named: Text(verbatim: t("terminar_antes"))) { alCambiar(desde, (hasta + 23) % 24) }
        .accessibilityAction(named: Text(verbatim: t("terminar_despues"))) { alCambiar(desde, (hasta + 1) % 24) }
    }

    private var descripcion: String {
        todoElDia ? t("horario_todo_el_dia") : t("horario_de_a", Formato.hora(desde), Formato.hora(hasta))
    }

    // MARK: Gesto

    private func mover(_ v: DragGesture.Value, _ m: Medidas) {
        if agarrada == nil {
            // El asa mas cercana a donde se puso el dedo.
            let tocada = Self.horaFraccion(v.startLocation, m.centro)
            agarrada = Self.distanciaHoras(tocada, Double(desde)) <= Self.distanciaHoras(tocada, Double(hasta)) ? .desde : .hasta
            Haptico.toque()
        }
        let nueva = Int(Self.horaFraccion(v.location, m.centro).rounded()) % 24
        switch agarrada {
        case .some(.desde) where nueva != desde: alCambiar(nueva, hasta)
        case .some(.hasta) where nueva != hasta: alCambiar(desde, nueva)
        default: break
        }
    }

    // MARK: Dibujo

    private func dibujar(_ c: inout GraphicsContext, _ m: Medidas) {
        let pista = Path(ellipseIn: CGRect(x: m.centro.x - m.radio, y: m.centro.y - m.radio, width: m.radio * 2, height: m.radio * 2))
        c.stroke(pista, with: .color(Paleta.borde.opacity(0.35)), lineWidth: m.grosor)
        if todoElDia {
            c.stroke(pista, with: .color(Paleta.principal), lineWidth: m.grosor)
        } else {
            // Se traza a trozos de media hora: evita el sentido de giro de addArc, que va al reves en SwiftUI.
            var arco = Path()
            let pasos = horasAvisando * 2
            for i in 0...pasos {
                let p = Self.punto(m.centro, m.radio, Double(desde) + Double(i) / 2)
                if i == 0 { arco.move(to: p) } else { arco.addLine(to: p) }
            }
            c.stroke(arco, with: .color(Paleta.principal), style: StrokeStyle(lineWidth: m.grosor, lineCap: .round, lineJoin: .round))
        }

        // Una marca por hora por dentro del anillo; numeros cada seis.
        let radioMarcas = m.radio - m.grosor / 2 - 8
        for h in 0..<24 {
            if h % 6 == 0 {
                let p = Self.punto(m.centro, radioMarcas - 10, Double(h))
                c.draw(Text(verbatim: "\(h)").font(.caption2).foregroundColor(Paleta.textoSecundario), at: p)
            } else {
                let p = Self.punto(m.centro, radioMarcas, Double(h))
                c.fill(Path(ellipseIn: CGRect(x: p.x - 1.5, y: p.y - 1.5, width: 3, height: 3)), with: .color(Paleta.borde))
            }
        }

        // La hora de ahora, por fuera.
        let ahora = Fechas.calendario.dateComponents([.hour, .minute], from: Date())
        let horaAhora = Double(ahora.hour ?? 0) + Double(ahora.minute ?? 0) / 60
        let p = Self.punto(m.centro, m.radio + m.grosor / 2 + 8, horaAhora)
        c.fill(Path(ellipseIn: CGRect(x: p.x - 3, y: p.y - 3, width: 6, height: 6)), with: .color(Paleta.acentoLegible))
    }

    private func asa(hora: Int, color: Color, tinta: Color, viva: Bool, _ m: Medidas) -> some View {
        let p = Self.punto(m.centro, m.radio, Double(hora))
        return Text(verbatim: "\(hora)")
            .font(.footnote.weight(.semibold))
            .foregroundStyle(tinta)
            .frame(width: m.radioAsa * 2, height: m.radioAsa * 2)
            .background(color, in: Circle())
            .overlay(Circle().stroke(Paleta.tarjeta, lineWidth: 3))
            .scaleEffect(viva ? 1.25 : 1)
            .position(p)
            .zIndex(viva ? 1 : 0)
            .animation(.spring(response: 0.3, dampingFraction: 0.55), value: viva)
            .animation(.spring(response: 0.25, dampingFraction: 0.75), value: hora)
            .allowsHitTesting(false)
    }

    private var resumen: some View {
        VStack(spacing: 2) {
            Text(verbatim: t("horas_corto", horasAvisando))
                .font(.system(.largeTitle, design: .rounded).weight(.semibold))
                .foregroundStyle(Paleta.texto)
            Text(verbatim: todoElDia ? t("todo_el_dia") : "\(Formato.hora(desde)) – \(Formato.hora(hasta))")
                .font(.subheadline)
                .foregroundStyle(Paleta.textoSecundario)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .allowsHitTesting(false)
    }

    // MARK: Geometria

    /// Medidas del anillo, las mismas para pintar y para saber que se toca.
    private struct Medidas {
        let centro: CGPoint
        let grosor: CGFloat = 28
        let radio: CGFloat
        let radioAsa: CGFloat

        init(_ tamano: CGSize) {
            let lado = min(tamano.width, tamano.height)
            centro = CGPoint(x: tamano.width / 2, y: tamano.height / 2)
            radio = lado / 2 - 18 - 14
            radioAsa = 28 * 0.72
        }
    }

    /// El anillo como zona tocable: un circulo grande con otro quitado por dentro.
    private struct Corona: Shape {
        let centro: CGPoint
        let desde: CGFloat
        let hasta: CGFloat

        func path(in rect: CGRect) -> Path {
            var p = Path()
            p.addEllipse(in: CGRect(x: centro.x - hasta, y: centro.y - hasta, width: hasta * 2, height: hasta * 2))
            let dentro = max(desde, 0)
            p.addEllipse(in: CGRect(x: centro.x - dentro, y: centro.y - dentro, width: dentro * 2, height: dentro * 2))
            return p
        }
    }

    /// Punto del circulo para una hora (0 arriba, en el sentido del reloj).
    private static func punto(_ centro: CGPoint, _ radio: CGFloat, _ hora: Double) -> CGPoint {
        let angulo = (hora * 15 - 90) * .pi / 180
        return CGPoint(x: centro.x + radio * CGFloat(cos(angulo)), y: centro.y + radio * CGFloat(sin(angulo)))
    }

    /// Hora, con decimales, que queda bajo un punto de la esfera.
    private static func horaFraccion(_ p: CGPoint, _ centro: CGPoint) -> Double {
        let grados = atan2(Double(p.y - centro.y), Double(p.x - centro.x)) * 180 / .pi + 90
        return ((grados + 360).truncatingRemainder(dividingBy: 360)) / 15
    }

    private static func distanciaHoras(_ a: Double, _ b: Double) -> Double {
        let d = abs(a - b).truncatingRemainder(dividingBy: 24)
        return min(d, 24 - d)
    }
}
