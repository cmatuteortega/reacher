import Foundation

/// Lo que el plano necesita de cada persona: cada cuantos dias toca y cuanto le toca ya.
struct EnOrbita {
    let id: UUID
    let frecuencia: Int
    let urgencia: Double
}

/// Donde descansa cada persona: anillo, angulo (0 a la derecha, pi/2 abajo) y radio de su burbuja.
struct PuestoOrbita {
    let anillo: Int
    let angulo: Double
    let radio: CGFloat
}

/// El letrero de un anillo: "<[dias] D", en el angulo donde acaba el anillo.
struct LetreroOrbita {
    let dias: Int
    let anillo: Int
    let angulo: Double
}

struct PlanoOrbitas {
    var radios: [CGFloat] = []
    /// El trozo visible de cada anillo, en radianes.
    var arcos: [ClosedRange<Double>] = []
    var puestos: [UUID: PuestoOrbita] = [:]
    var letreros: [LetreroOrbita] = []
}

/// Personas en anillos alrededor del sol de la esquina, un cuarto de circulo
/// por anillo: el mismo reparto que la vista de Android (`VistaOrbitas.kt`).
/// Quien va en cada anillo depende solo de cuantos son y de su frecuencia (de
/// quien va mas a menudo a quien menos), no de la urgencia, que solo se ve en
/// el tamano de la burbuja. Con los dias nadie cambia de anillo.
enum Orbitas {
    /// Parte de la gente en cada anillo, de dentro afuera, segun cuantos anillos hay.
    static let partes: [[Double]] = [
        [1],
        [0.35, 0.65],
        [0.2, 0.35, 0.45],
        [0.1, 0.25, 0.3, 0.35],
        [0.08, 0.14, 0.2, 0.26, 0.32],
        [0.06, 0.1, 0.15, 0.2, 0.22, 0.27],
    ]

    /// Cuantos anillos para tanta gente: con poca, no merece la pena repartir.
    static func anillosPara(_ cuantos: Int) -> Int {
        switch cuantos {
        case ...2: return 1
        case ...5: return 2
        case ...10: return 3
        case ...18: return 4
        case ...28: return 5
        default: return 6
        }
    }

    /// Hasta donde llega el anillo de fuera, en partes de la distancia a la esquina opuesta.
    static let alcance: CGFloat = 0.8
    static let huecoSol: CGFloat = 16
    static let radioMaxBurbuja: CGFloat = 46
    static let radioSuelo: CGFloat = 16
    static let largoLetrero: CGFloat = 40

    /// Cuantos de `frecuencias` (ordenadas) van a cada anillo, en proporcion a `pesos`; todos al menos uno si hay gente.
    static func cortes(cuantos: Int, pesos: [Double]) -> [Int] {
        var cuentas = pesos.map { Int(($0 * Double(cuantos)).rounded()) }
        // Que sumen justo `cuantos`: lo que sobra o falta, en el ultimo anillo con sitio.
        var diferencia = cuantos - cuentas.reduce(0, +)
        var i = cuentas.count - 1
        while diferencia != 0 && i >= 0 {
            let nuevo = max(cuentas[i] + diferencia, 0)
            diferencia -= nuevo - cuentas[i]
            cuentas[i] = nuevo
            i -= 1
        }
        return cuentas
    }

    /// Anillos y puestos de todos. `personas` van ordenadas por frecuencia, de la que mas a la que menos.
    static func planear(personas: [EnOrbita], tamano: CGSize, sol: CGPoint, radioSol: CGFloat) -> PlanoOrbitas {
        guard !personas.isEmpty, tamano.width > 0, tamano.height > 0 else { return PlanoOrbitas() }
        let minimos = anillosPara(personas.count)
        for anillos in minimos...partes.count {
            for escala in [1.0, 0.85, 0.7, 0.55, 0.45] {
                if let plano = intentar(personas, anillos: anillos, escala: CGFloat(escala), tamano: tamano, sol: sol, radioSol: radioSol) {
                    return plano
                }
            }
        }
        // No cabe ni asi: se reparte igualmente, apretados, antes que dejar a nadie fuera.
        return intentar(personas, anillos: partes.count, escala: 0.4, tamano: tamano, sol: sol, radioSol: radioSol, aunqueNoQuepan: true) ?? PlanoOrbitas()
    }

    private static func intentar(
        _ personas: [EnOrbita], anillos pedidos: Int, escala: CGFloat, tamano: CGSize, sol: CGPoint, radioSol: CGFloat,
        aunqueNoQuepan: Bool = false
    ) -> PlanoOrbitas? {
        let anillos = max(1, min(pedidos, personas.count))
        let w = tamano.width, h = tamano.height
        // Anillos de junto al sol hasta cerca de la esquina opuesta; las burbujas mas grandes caben justas entre dos.
        let alcanza = hypot(max(w - sol.x, 0), max(h - sol.y, 0)) * alcance
        let maxBurbuja = max(min(radioMaxBurbuja * escala, 60), radioSuelo * 0.5)
        let primero = radioSol + huecoSol + maxBurbuja
        let ultimo = max(alcanza, primero + 1)
        let separacion = anillos > 1 ? min((ultimo - primero) / CGFloat(anillos - 1), 170) : 0
        if anillos > 1, separacion < maxBurbuja * 1.9, !aunqueNoQuepan { return nil }
        let radios: [CGFloat] = (0..<anillos).map { k in
            anillos == 1 ? min((primero + ultimo) / 2, primero + 120) : primero + separacion * CGFloat(k)
        }

        /// El trozo visible de un anillo para burbujas de hasta `b` de radio, sin el sitio del letrero.
        func visible(_ r: CGFloat, _ b: CGFloat) -> ClosedRange<Double>? {
            var desde: Double?
            var hasta: Double?
            let pasos = 180
            for i in 0...pasos {
                let a = Double(i) / Double(pasos) * Double.pi / 2
                let x = sol.x + r * CGFloat(cos(a)), y = sol.y + r * CGFloat(sin(a))
                if x >= b && x <= w - b && y >= b && y <= h - b {
                    if desde == nil { desde = a }
                    hasta = a
                }
            }
            guard let d = desde, let hh = hasta, hh > d else { return nil }
            return d...hh
        }

        var arcos: [ClosedRange<Double>] = []
        var capacidades: [Int] = []
        for r in radios {
            guard let arco = visible(r, maxBurbuja) else {
                arcos.append(0...0)
                capacidades.append(0)
                continue
            }
            // El letrero, al final del anillo, con su aire.
            let angLetrero = Double((largoLetrero + 6) / r)
            let util = arco.upperBound - angLetrero > arco.lowerBound ? arco.lowerBound...(arco.upperBound - angLetrero) : arco
            arcos.append(util)
            let largo = r * CGFloat(util.upperBound - util.lowerBound)
            capacidades.append(max(Int((largo / (2 * maxBurbuja * 0.9)).rounded(.down)) + 1, 1))
        }

        // Cada anillo con los suyos; lo que no cabe pasa, en orden, al siguiente.
        let objetivo = cortes(cuantos: personas.count, pesos: partes[anillos - 1])
        var reparto = Array(repeating: 0, count: anillos)
        var pendientes = 0
        for k in 0..<anillos {
            let quiere = objetivo[k] + pendientes
            let cabe = aunqueNoQuepan ? quiere : min(quiere, capacidades[k])
            reparto[k] = cabe
            pendientes = quiere - cabe
        }
        if pendientes > 0 {
            if aunqueNoQuepan { reparto[anillos - 1] += pendientes } else { return nil }
        }

        var puestos: [UUID: PuestoOrbita] = [:]
        var letreros: [LetreroOrbita] = []
        var indice = 0
        for k in 0..<anillos {
            let cuantos = reparto[k]
            guard cuantos > 0 else { continue }
            let arco = arcos[k]
            let grupo = personas[indice..<(indice + cuantos)]
            indice += cuantos
            let minimo = max(maxBurbuja * 0.55, radioSuelo * 0.6)
            for (j, p) in grupo.enumerated() {
                let u = CGFloat(min(max(p.urgencia, 0), 1.2) / 1.2)
                let radio = minimo + (maxBurbuja - minimo) * u
                let angulo = cuantos == 1
                    ? (arco.lowerBound + arco.upperBound) / 2
                    : arco.lowerBound + (arco.upperBound - arco.lowerBound) * (Double(j) + 0.5) / Double(cuantos)
                puestos[p.id] = PuestoOrbita(anillo: k, angulo: angulo, radio: radio)
            }
            let masLarga = grupo.map(\.frecuencia).max() ?? 0
            letreros.append(LetreroOrbita(dias: masLarga, anillo: k, angulo: arco.upperBound + Double((largoLetrero / 2 + 3) / radios[k])))
        }
        return PlanoOrbitas(radios: radios, arcos: arcos, puestos: puestos, letreros: letreros)
    }
}
