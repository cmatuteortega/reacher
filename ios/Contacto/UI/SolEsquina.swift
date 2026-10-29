import SwiftUI
import UIKit

/// El sol de la pantalla principal, igual que el de Android (`SolEsquina.kt`):
/// metido en la esquina de arriba a la izquierda de la pantalla, con solo un
/// cuarto asomando y sus rayos ondulados girando alrededor. Los ojos van sobre
/// la esfera entera, con la cabeza girada hacia abajo a la derecha: asi miran
/// a las burbujas de al lado y se estrechan hacia el borde.
///
/// Tocarlo lo hace reir. Cuando `cuantos` sube, saluda; mientras se pulsa el
/// + (`masPulsado`) se encoge, cierra los ojos y lo mira; `risas` sube cada
/// vez que hay que reir sin tocar el sol (al abrir la agenda desde el +).
struct SolEsquinaView: View {
    var cuantos: Int
    var masPulsado: Bool
    var risas: Int

    /// Largo de cada onda del borde y cuanto sobresale.
    private static let onda: CGFloat = 44
    static let punta: CGFloat = 16

    /// Grados que gira la cabeza para asomar por la esquina: a la derecha y hacia abajo.
    private static let giroDerecha = 24.0
    private static let giroAbajo = 30.0
    /// Tamano de los ojos y de sus gestos respecto a una burbuja normal.
    private static let ojos = 0.5
    private static let gestos = 0.25
    /// Grados de mas hacia el + cuando se pulsa, y cuanto se encoge el sol al apretarlo.
    private static let miraMas = 14.0
    private static let encogerse: CGFloat = 8
    /// Del centro del + al borde de abajo de la zona segura (64 de alto y 20 de margen).
    private static let huecoMas: CGFloat = 52

    /// Lo que mide el borde de arriba (la barra de estado o la isla), sin la barra de navegacion.
    static var arriba: CGFloat {
        let ventana = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.windows.first(where: \.isKeyWindow) }
            .first
        return ventana?.safeAreaInsets.top ?? 0
    }

    /// Lo que mide el borde de abajo (la barra de inicio).
    static var abajo: CGFloat {
        let ventana = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.windows.first(where: \.isKeyWindow) }
            .first
        return ventana?.safeAreaInsets.bottom ?? 0
    }

    /// Radio del sol sin contar el borde de arriba, segun el ancho de la pantalla.
    static func radioBase(ancho: CGFloat) -> CGFloat { min(max(ancho * 0.3, 100), 140) }

    /// Radio del cuerpo del sol, con lo que entra bajo el borde de arriba.
    static func radio(ancho: CGFloat) -> CGFloat { radioBase(ancho: ancho) + arriba }

    /// Hasta donde llegan las puntas: lo que el resto de la pantalla le deja libre.
    static func radioLibre(ancho: CGFloat) -> CGFloat { radio(ancho: ancho) + punta }

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    @State private var sol = SolEstado()
    @State private var vistos = 0

    var body: some View {
        GeometryReader { geo in
            let radio = SolEsquinaView.radio(ancho: geo.size.width)
            let mas = CGPoint(x: geo.size.width / 2, y: geo.size.height - SolEsquinaView.abajo - SolEsquinaView.huecoMas)
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { linea in
                    Canvas { contexto, _ in
                        sol.avanzar(linea.date, quieta: reducirMovimiento)
                        SolEsquinaView.dibujar(&contexto, radio: radio, mas: mas, sol: sol)
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)

                // El sol recoge los toques del cuarto que asoma.
                Color.clear
                    .frame(width: radio * 0.8, height: radio * 0.8)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { _ in sol.solPulsado = true }
                            .onEnded { v in
                                sol.solPulsado = false
                                if hypot(v.translation.width, v.translation.height) < 20 { sol.reir() }
                            }
                    )
                    .accessibilityElement()
                    .accessibilityLabel(Text("tu_el_sol"))
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { sol.reir() }
            }
        }
        .ignoresSafeArea()
        .onAppear {
            vistos = cuantos
            sol.saludar()
        }
        .onChange(of: cuantos) {
            if cuantos > vistos { sol.saludar() }
            vistos = cuantos
        }
        .onChange(of: masPulsado) { sol.masPulsado = masPulsado }
        .onChange(of: risas) { sol.reir() }
    }

    private static func dibujar(_ contexto: inout GraphicsContext, radio radioSol: CGFloat, mas: CGPoint, sol: SolEstado) {
        let t = sol.cara.reloj
        let centro = CGPoint.zero
        let radio = radioSol - encogerse * CGFloat(sol.encogido.x)
        let hondura = Double(punta / radio) * (1 + 0.12 * sin(t * 1.7))
        // Entero: el corte no se ve ni aunque asome el sol entero.
        let puntas = (2 * CGFloat.pi * radioSol / onda).rounded()

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

        // La cara gira hacia la esquina; si se pulsa el +, un poco mas hacia el.
        let d = CGPoint(x: mas.x - centro.x, y: mas.y - centro.y)
        let largo = max(hypot(d.x, d.y), 1)
        let hacia = sol.haciaMas.x
        let mirada = (x: giroDerecha + Double(d.x / largo) * miraMas * hacia, y: giroAbajo + Double(d.y / largo) * miraMas * hacia)
        let m = sol.cara.muestra()
        let cabeza = m.girada(x: m.cabezaX * gestos, y: m.cabezaY * gestos, z: m.cabezaZ * gestos).conOjosDe(ojos)
        dibujarOjos(&contexto, e: cabeza, parpadeo: sol.cara.parpadeo(), centro: centro, radio: radio, mirada: mirada)
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

    /// Dos elipses pegadas a la esfera de `radio`, vistas con la cabeza de `e`
    /// girada ademas `mirada` grados (x a la derecha, y hacia abajo): se
    /// deslizan por ella, se estrechan al acercarse al borde y se esconden detras.
    private static func dibujarOjos(_ contexto: inout GraphicsContext, e: Expresion, parpadeo: Double, centro: CGPoint, radio: CGFloat, mirada: (x: Double, y: Double)) {
        let rad = Double.pi / 180
        let cabezaY = (e.cabezaY + mirada.x) * rad
        let cabezaX = (e.cabezaX - mirada.y) * rad
        let cabezaZ = e.cabezaZ * rad
        let cy = cos(cabezaY), sy = sin(cabezaY)
        let cx = cos(cabezaX), sx = sin(cabezaX)
        let cz = cos(cabezaZ), sz = sin(cabezaZ)
        let p = min(max(e.perspectiva, 0), 0.6)
        // Con perspectiva la silueta de la esfera se ve mayor: se escala para que ocupe el radio.
        let ajuste = (1 - p * p).squareRoot()
        let puntos = 32

        for lado in [-1.0, 1.0] {
            let ojo = lado < 0 ? e.izquierdo : e.derecho
            let centroU = lado * e.separacion / 2 + ojo.x
            let centroV = ojo.y
            let a = ojo.ancho / 2
            let b = max(ojo.alto / 2 * parpadeo, 0.006)
            let ca = cos(ojo.angulo * rad), sa = sin(ojo.angulo * rad)
            var trazo = Path()
            var profundidad = 0.0
            for i in 0..<puntos {
                let th = 2 * Double.pi * Double(i) / Double(puntos)
                let ex = a * cos(th), ey = b * sin(th)
                var u = centroU + ex * ca - ey * sa
                var v = centroV + ex * sa + ey * ca
                let d2 = u * u + v * v
                if d2 > 0.96 {
                    let f = (0.96 / d2).squareRoot()
                    u *= f
                    v *= f
                }
                // Punto de la esfera que queda debajo, mirando a camara.
                let w = max(0, 1 - u * u - v * v).squareRoot()
                // Giro: primero a los lados, luego arriba y abajo, luego ladear.
                let x1 = u * cy + w * sy
                let z1 = -u * sy + w * cy
                let y2 = v * cx - z1 * sx
                let z2 = v * sx + z1 * cx
                let x3 = x1 * cz - y2 * sz
                let y3 = x1 * sz + y2 * cz
                profundidad += z2
                let px: Double, py: Double
                if z2 < p {
                    // Detras del borde: se queda pegado a la silueta.
                    let l = max((x3 * x3 + y3 * y3).squareRoot(), 1e-4)
                    px = x3 / l
                    py = y3 / l
                } else {
                    let f = ajuste / (1 - p * z2)
                    px = x3 * f
                    py = y3 * f
                }
                let punto = CGPoint(x: centro.x + CGFloat(px) * radio, y: centro.y + CGFloat(py) * radio)
                if i == 0 { trazo.move(to: punto) } else { trazo.addLine(to: punto) }
            }
            if profundidad / Double(puntos) > p * 0.6 {
                trazo.closeSubpath()
                contexto.fill(trazo, with: .color(Paleta.ojos))
            }
        }
    }
}

/// Un valor que va hacia su destino con un muelle (`zeta` 1: sin rebote; 0.5: rebota).
struct Muelle {
    var x = 0.0
    var v = 0.0

    mutating func mover(hacia destino: Double, dt: Double, rigidez: Double = 1500, zeta: Double) {
        let w = rigidez.squareRoot()
        // Pasos cortos para que el muelle no se desmande con un fotograma largo.
        var resto = min(dt, 0.1)
        while resto > 0 {
            let h = min(resto, 1.0 / 240)
            v += (-w * w * (x - destino) - 2 * zeta * w * v) * h
            x += v * h
            resto -= h
        }
    }
}

/// Lo que el sol recuerda entre fotogramas: la cara, el apreton y la mirada al +.
final class SolEstado {
    let cara = EstadoCara(inicial: Expresiones.contenta)
    var encogido = Muelle()
    var haciaMas = Muelle()

    private var ultimo: Date?
    private let inicio = Date()
    private var _solPulsado = false
    private var _masPulsado = false

    var solPulsado: Bool {
        get { _solPulsado }
        set {
            guard newValue != _solPulsado else { return }
            _solPulsado = newValue
            cambioPulsada()
        }
    }

    var masPulsado: Bool {
        get { _masPulsado }
        set {
            guard newValue != _masPulsado else { return }
            _masPulsado = newValue
            cambioPulsada()
        }
    }

    private var pulsada: Bool { _solPulsado || _masPulsado }

    /// Se aprieta el sol o el +: en los dos casos el sol cierra los ojos y se encoge.
    private func cambioPulsada() {
        if pulsada {
            cara.poner(Expresiones.apretada, ms: 100, transicion: .seca)
        } else if cara.animacion == nil {
            cara.reproducir(Animaciones.mirarAlrededor)
        }
    }

    func saludar() {
        cara.reproducir(Animaciones.saludo) { [cara] in cara.reproducir(Animaciones.mirarAlrededor) }
    }

    func reir() {
        cara.reproducir(Animaciones.risa) { [cara] in cara.reproducir(Animaciones.mirarAlrededor) }
    }

    func avanzar(_ ahora: Date, quieta: Bool) {
        let dt = ahora.timeIntervalSince(ultimo ?? ahora)
        ultimo = ahora
        cara.quieta = quieta
        cara.avanzar(Int64(ahora.timeIntervalSince(inicio) * 1000))
        encogido.mover(hacia: pulsada ? 1 : 0, dt: dt, zeta: 0.5)
        haciaMas.mover(hacia: _masPulsado ? 1 : 0, dt: dt, zeta: 1)
    }
}
