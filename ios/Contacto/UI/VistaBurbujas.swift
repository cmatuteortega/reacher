import SwiftUI

/// Clave de la burbuja que agrupa a los que van sobrados (las de personas son su UUID).
private let claveGrupo = "grupo"
/// A partir de cuantas personas se agrupan las que van sobradas.
private let muchos = 18
/// Por debajo de esta urgencia alguien va sobrado: se atenua y, si hay muchos, se agrupa.
private let calma = 0.5
/// No merece la pena un grupo para dos o tres.
private let minAgrupar = 4

private let radioMin: CGFloat = 26 // 52 pt de diametro: sigue siendo facil de pulsar
private let radioMax: CGFloat = 84
private let margen: CGFloat = 12 // aire alrededor de cada una, para el nombre y el dedo
private let borde: CGFloat = 8
/// Abajo se deja libre lo que ocupa el +.
private let huecoInferior: CGFloat = 112
/// Parte del lienzo visible que se deja ocupar antes de encoger las grandes.
private let ocupacion: CGFloat = 0.5

/// Cuanto se estira una burbuja lanzada, y a que velocidad (pt/s) llega a ello.
private let estiron: CGFloat = 0.14
private let velocidadEstiron: CGFloat = 1400
/// Choques mas flojos que esto (pt/s) no vibran: el vaiven de reposo y los recolocados.
private let golpeMinimo: CGFloat = 280
/// A partir de aqui (pt/s por encima del minimo) el choque vibra a toda fuerza.
private let golpePleno: CGFloat = 1400
/// Separacion minima entre dos vibraciones de choque, para que una cascada no zumbe.
private let entreGolpes: TimeInterval = 0.07
/// Soltarla mas deprisa que esto (pt/s) es lanzarla: se nota un golpe seco.
private let velocidadLanzar: CGFloat = 700

/// Tamanos y casas de todas.
struct Disposicion {
    var sitios: [Sitio]
    var altoMundo: CGFloat

    static let vacia = Disposicion(sitios: [], altoMundo: 0)

    /// Con mucha gente las grandes encogen para que quepan mas en pantalla, sin bajar nunca del
    /// minimo que se pulsa bien; lo que aun asi no quepa queda mas abajo, desplazando.
    /// `personas` llega ya ordenado por urgencia: es el orden en que se colocan.
    static func calcular(
        personas: [(clave: String, urgencia: Double)], conGrupo: Bool,
        ancho: CGFloat, alto: CGFloat, sol: Sitio
    ) -> Disposicion {
        guard ancho > 0, alto > 0 else { return .vacia }
        var maximo = max(min(ancho * 0.2, radioMax), radioMin * 1.4)

        func huecos(_ maximo: CGFloat) -> [Hueco] {
            var h = personas.map { Hueco(clave: $0.clave, radio: Fisica.radioPorUrgencia($0.urgencia, minimo: radioMin, maximo: maximo)) }
            if conGrupo { h.append(Hueco(clave: claveGrupo, radio: radioMin * 1.3)) }
            return h
        }

        let libre = alto - huecoInferior
        let disponible = ancho * libre * ocupacion
        let ocupado = huecos(maximo).reduce(CGFloat(0)) { $0 + .pi * ($1.radio + margen) * ($1.radio + margen) }
        if ocupado > disponible, disponible > 0 { maximo = max(radioMin * 1.4, maximo * (disponible / ocupado).squareRoot()) }

        let sitios = Empaquetado.empaquetar(
            huecos: huecos(maximo), ancho: ancho, margen: margen, borde: borde,
            focoX: ancho / 2, focoY: libre * 0.46, fijos: [sol]
        )
        let fondo = sitios.map { $0.y + $0.radio + margen }.max() ?? 0
        return Disposicion(sitios: sitios, altoMundo: max(alto, fondo + huecoInferior))
    }
}

/// Cada persona es una burbuja, mas grande cuanto mas cerca esta de su fecha y las mas urgentes en
/// el centro. Flotan, chocan entre si y con el sol de la esquina, y se pueden arrastrar y lanzar:
/// al soltarlas vuelven solas a su sitio (`Fisica.swift`). Con mucha gente, los que van sobrados se
/// recogen en una burbuja «Con calma» que se abre con un toque.
struct VistaBurbujas: View {
    let personas: [Contacto]
    var alAbrir: (Contacto) -> Void
    var alLlamarHoy: (Contacto) -> Void

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    @State private var sim = Simulacion()
    @State private var vibrador = Vibrador()
    @State private var plano = Disposicion.vacia
    @State private var grupoAbierto = false

    /// Quien va sobrado, quien se agrupa y la urgencia de cada uno, calculado una vez por pasada.
    private struct Reparto {
        let ordenadas: [Contacto]
        let urgencias: [String: Double]
        let tranquilos: Set<String>
        let agrupar: Bool
        let muchas: Bool

        init(_ personas: [Contacto]) {
            let orden = personas.porUrgencia()
            var urgencia: [String: Double] = [:]
            for c in orden { urgencia[c.clave] = c.urgencia() }
            let sobradas = orden.count > muchos
            let calmados = sobradas ? Set(orden.filter { urgencia[$0.clave, default: 0] < calma }.map(\.clave)) : []
            ordenadas = orden
            urgencias = urgencia
            muchas = sobradas
            tranquilos = calmados
            agrupar = calmados.count >= minAgrupar
        }

        var porClave: [String: Contacto] { Dictionary(uniqueKeysWithValues: ordenadas.map { ($0.clave, $0) }) }
    }

    /// Lo que, si cambia, obliga a recolocar todas.
    private struct Entrada: Equatable {
        var claves: [String]
        var urgencias: [Double]
        var ancho: CGFloat
        var alto: CGFloat
        var sol: CGPoint
        var radioSol: CGFloat
        var abierto: Bool
        var calma: Bool
    }

    var body: some View {
        GeometryReader { geo in
            let reparto = Reparto(personas)
            // El sol esta en la esquina de la pantalla, no en la de esta vista.
            let marco = geo.frame(in: .global)
            let entrada = Entrada(
                claves: reparto.ordenadas.map(\.clave),
                urgencias: reparto.ordenadas.map { reparto.urgencias[$0.clave, default: 0] },
                ancho: geo.size.width, alto: geo.size.height,
                sol: CGPoint(x: (-marco.minX).rounded(), y: (-marco.minY).rounded()),
                radioSol: SolEsquinaView.radioLibre(ancho: UIScreen.main.bounds.width),
                abierto: grupoAbierto, calma: reducirMovimiento
            )
            ScrollView(.vertical, showsIndicators: false) {
                lienzo(reparto, ancho: geo.size.width)
                    .frame(width: geo.size.width, height: max(plano.altoMundo, geo.size.height), alignment: .topLeading)
            }
            .onChange(of: entrada, initial: true) { recolocar(entrada, reparto) }
        }
    }

    private func recolocar(_ e: Entrada, _ r: Reparto) {
        let visibles = r.ordenadas.filter { !r.agrupar || grupoAbierto || !r.tranquilos.contains($0.clave) }
        let sol = Sitio(clave: "sol", x: e.sol.x, y: e.sol.y, radio: e.radioSol)
        let nuevo = Disposicion.calcular(
            personas: visibles.map { ($0.clave, r.urgencias[$0.clave, default: 0]) },
            conGrupo: r.agrupar, ancho: e.ancho, alto: e.alto, sol: sol
        )
        sim.calma = e.calma
        sim.ancho = e.ancho
        sim.alto = nuevo.altoMundo
        sim.margen = margen
        sim.borde = borde
        sim.bordeInferior = huecoInferior
        sim.obstaculos = [sol]
        // Al abrir el grupo, los tranquilos salen de el.
        sim.colocar(nuevo.sitios, salenDe: r.agrupar ? Dictionary(uniqueKeysWithValues: r.tranquilos.map { ($0, claveGrupo) }) : [:])
        plano = nuevo
    }

    private func lienzo(_ r: Reparto, ancho: CGFloat) -> some View {
        let porClave = r.porClave
        return TimelineView(.animation(minimumInterval: 1.0 / 60.0)) { linea in
            let _ = avanzar(linea.date)
            ZStack(alignment: .topLeading) {
                ForEach(Array(plano.sitios.enumerated()), id: \.element.clave) { orden, sitio in
                    if let cuerpo = sim.cuerpos[sitio.clave] {
                        if sitio.clave == claveGrupo {
                            BurbujaMovil(
                                cuerpo: cuerpo, sim: sim, orden: orden, atenuada: false,
                                descripcion: Text(verbatim: descripcionGrupo(r.tranquilos.count)),
                                alToque: { withAnimation(.easeInOut) { grupoAbierto.toggle() } },
                                alLlamarHoy: nil
                            ) {
                                BurbujaGrupoView(cuantos: r.tranquilos.count, abierto: grupoAbierto, radio: cuerpo.radioObjetivo)
                            }
                        } else if let c = porClave[sitio.clave] {
                            let urgencia = r.urgencias[sitio.clave, default: 0]
                            BurbujaMovil(
                                cuerpo: cuerpo, sim: sim, orden: orden, atenuada: r.muchas && urgencia < calma,
                                descripcion: nil,
                                alToque: { alAbrir(c) },
                                alLlamarHoy: { alLlamarHoy(c) }
                            ) {
                                BurbujaView(contacto: c, radio: cuerpo.radioObjetivo, mostrarNombre: cuerpo.radioObjetivo >= 24, urgencia: urgencia)
                            }
                        }
                    }
                }
            }
            .frame(width: ancho, height: max(plano.altoMundo, 1), alignment: .topLeading)
            .coordinateSpace(name: "mundo")
        }
    }

    private func descripcionGrupo(_ n: Int) -> String {
        grupoAbierto ? String(localized: "con_calma_recoger \(n)") : String(localized: "con_calma_mostrar \(n)")
    }

    /// Un paso de la simulacion por fotograma; cada choque se siente, con mas fuerza cuanto mas deprisa iban.
    private func avanzar(_ fecha: Date) {
        let ahora = fecha.timeIntervalSinceReferenceDate
        sim.avanzar(hasta: ahora)
        let fuerza = sim.golpe - golpeMinimo
        if fuerza > 0, ahora - vibrador.ultimo > entreGolpes {
            vibrador.ultimo = ahora
            Haptico.choque(Double(fuerza / golpePleno))
        }
    }
}

private final class Vibrador {
    var ultimo: TimeInterval = 0
}

private extension Contacto {
    /// Como se llama a esta persona en la simulacion.
    var clave: String { uuid.uuidString }
}

/// La burbuja que recoge a los que van sobrados: un circulo liso con cuantos son.
private struct BurbujaGrupoView: View {
    let cuantos: Int
    let abierto: Bool
    let radio: CGFloat

    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                Circle().fill(Paleta.tarjeta)
                Text(verbatim: abierto ? "–" : "+\(cuantos)")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Paleta.textoSecundario)
            }
            .frame(width: radio * 2, height: radio * 2)
            .overlay(Circle().stroke(Paleta.borde, lineWidth: 2))
            Text(abierto ? "recoger" : "con_calma")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Paleta.texto)
                .lineLimit(1)
                .frame(width: max(radio * 2 + 24, 60))
        }
        .contentShape(Circle())
    }
}

/// Lo comun a todas: la posicion sale de la simulacion en cada fotograma, entra creciendo con un
/// rebote, se estira hacia donde corre, se levanta al cogerla y sigue al dedo al arrastrarla.
private struct BurbujaMovil<Contenido: View>: View {
    let cuerpo: Cuerpo
    let sim: Simulacion
    let orden: Int
    let atenuada: Bool
    /// Lo que dice VoiceOver cuando no es el texto propio de la burbuja (la del grupo).
    let descripcion: Text?
    let alToque: () -> Void
    let alLlamarHoy: (() -> Void)?
    let contenido: Contenido

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    @State private var aparecida = false
    @State private var agarrada = false
    @GestureState private var dedo = false
    /// Desde el centro de la burbuja hasta el dedo al cogerla, y el ultimo punto para medir su velocidad.
    @State private var agarre = CGSize.zero
    @State private var ultimoPunto = CGPoint.zero
    @State private var ultimoInstante = Date()

    init(
        cuerpo: Cuerpo, sim: Simulacion, orden: Int, atenuada: Bool, descripcion: Text?,
        alToque: @escaping () -> Void, alLlamarHoy: (() -> Void)?, @ViewBuilder contenido: () -> Contenido
    ) {
        self.cuerpo = cuerpo
        self.sim = sim
        self.orden = orden
        self.atenuada = atenuada
        self.descripcion = descripcion
        self.alToque = alToque
        self.alLlamarHoy = alLlamarHoy
        self.contenido = contenido()
    }

    var body: some View {
        let referencia = velocidadEstiron
        // Se estira en la direccion en que corre y se aplasta en la otra. Cogida no: el arrastre
        // tiene que seguir al dedo exacto.
        let d = agarrada ? 0 : estiron * (min(abs(cuerpo.vx) / referencia, 1) - min(abs(cuerpo.vy) / referencia, 1))
        let crecimiento = cuerpo.radioObjetivo > 0 ? cuerpo.radio / cuerpo.radioObjetivo : 1
        contenido
            .scaleEffect(x: 1 + d, y: 1 - d)
            .scaleEffect(crecimiento)
            .scaleEffect(agarrada ? 1.1 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.55), value: agarrada)
            .scaleEffect(aparecida ? 1 : 0.001)
            .animation(
                reducirMovimiento ? nil : Animation.spring(response: 0.7, dampingFraction: 0.55).delay(Double(min(orden * 30, 600)) / 1000),
                value: aparecida
            )
            .opacity(atenuada ? 0.55 : 1)
            // El toque y el arrastre van antes que position: position ocupa todo el espacio y se los comeria.
            .gesture(arrastre)
            .onTapGesture(perform: alToque)
            .modifier(AccionLlamadoHoy(accion: alLlamarHoy))
            .modifier(DescripcionOpcional(texto: descripcion))
            .position(x: cuerpo.x, y: cuerpo.y + (cuerpo.radioObjetivo >= 20 ? 8 : 0))
            .zIndex(agarrada ? 1 : 0)
            .onAppear { aparecida = true }
            .onChange(of: dedo) {
                // El sistema cancelo el gesto (p. ej. lo cogio el scroll) sin llegar a soltarlo.
                if !dedo && agarrada { soltar(vx: 0, vy: 0) }
            }
    }

    private var arrastre: some Gesture {
        DragGesture(minimumDistance: 6, coordinateSpace: .named("mundo"))
            .updating($dedo) { _, estado, _ in estado = true }
            .onChanged { v in
                if !agarrada {
                    agarrada = true
                    Haptico.agarrar()
                    sim.agarrar(cuerpo)
                    agarre = CGSize(width: cuerpo.x - v.startLocation.x, height: cuerpo.y - v.startLocation.y)
                    ultimoPunto = v.startLocation
                    ultimoInstante = v.time
                }
                sim.mover(
                    cuerpo,
                    dx: v.location.x + agarre.width - cuerpo.x,
                    dy: v.location.y + agarre.height - cuerpo.y
                )
                // Con su velocidad empuja a las que choca mientras la lleva.
                let dt = v.time.timeIntervalSince(ultimoInstante)
                if dt > 0.004 {
                    sim.arrastrarA(
                        cuerpo,
                        vx: (v.location.x - ultimoPunto.x) / CGFloat(dt),
                        vy: (v.location.y - ultimoPunto.y) / CGFloat(dt)
                    )
                    ultimoPunto = v.location
                    ultimoInstante = v.time
                }
            }
            .onEnded { v in
                if hypot(v.velocity.width, v.velocity.height) > velocidadLanzar { Haptico.lanzar() }
                soltar(vx: v.velocity.width, vy: v.velocity.height)
            }
    }

    private func soltar(vx: CGFloat, vy: CGFloat) {
        agarrada = false
        sim.soltar(cuerpo, vx: vx, vy: vy)
    }
}

private struct AccionLlamadoHoy: ViewModifier {
    let accion: (() -> Void)?

    func body(content: Content) -> some View {
        if let accion {
            content.accessibilityAction(named: Text("he_llamado_hoy"), accion)
        } else {
            content
        }
    }
}

private struct DescripcionOpcional: ViewModifier {
    let texto: Text?

    func body(content: Content) -> some View {
        if let texto {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(texto)
                .accessibilityAddTraits(.isButton)
        } else {
            content
        }
    }
}
