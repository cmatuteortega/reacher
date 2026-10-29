import CoreGraphics
import Foundation

/// Las cuentas de la vista de burbujas, sin nada de SwiftUI: que tamano tiene
/// cada una, donde descansa y como se mueve. Es `FisicaBurbujas.kt`; todo en puntos.
enum Fisica {
    /// Urgencia a la que se llega al tamano maximo: un poco pasada la fecha.
    static let urgenciaTope = 1.25

    /// Radio segun la parte de la cadencia consumida. La curva es mas plana al
    /// principio: quien va sobrado se queda pequeno y crece deprisa al acercarse.
    static func radioPorUrgencia(_ urgencia: Double, minimo: CGFloat, maximo: CGFloat) -> CGFloat {
        let t = min(max(urgencia / urgenciaTope, 0), 1)
        return minimo + (maximo - minimo) * CGFloat(pow(t, 1.6))
    }
}

struct Hueco {
    let clave: String
    let radio: CGFloat
}

struct Sitio {
    let clave: String
    let x: CGFloat
    let y: CGFloat
    let radio: CGFloat
}

enum Empaquetado {
    /// Empaquetado voraz: cada circulo, en el orden dado, va al punto libre mas
    /// cercano al foco. Asi los primeros (los mas urgentes) quedan en el centro y
    /// el resto se reparte alrededor y hacia abajo. Los candidatos son los puntos
    /// tangentes a lo ya colocado y a las paredes laterales.
    ///
    /// `margen` se suma al radio de cada uno: aire para el nombre y para el dedo.
    /// `fijos` son circulos que ya ocupan sitio (el sol de la esquina): nadie se
    /// coloca encima, pero se puede arrimar a ellos. No hay pared de abajo: si no
    /// caben, el lienzo crece y se desplaza.
    static func empaquetar(
        huecos: [Hueco], ancho: CGFloat, margen: CGFloat, borde: CGFloat,
        focoX: CGFloat, focoY: CGFloat, fijos: [Sitio] = []
    ) -> [Sitio] {
        let angulos = 24
        var puestos: [Sitio] = []
        for hueco in huecos {
            let alcance = hueco.radio + margen
            let minX = alcance + borde
            let maxX = max(minX, ancho - alcance - borde)
            let minY = alcance + borde

            // Pesa mas alejarse en vertical: llena el ancho antes de bajar.
            func coste(_ x: CGFloat, _ y: CGFloat) -> CGFloat {
                let dx = x - focoX
                let dy = (y - focoY) * 1.35
                return dx * dx + dy * dy
            }

            func libre(_ x: CGFloat, _ y: CGFloat) -> Bool {
                if x < minX - 0.5 || x > maxX + 0.5 || y < minY - 0.5 { return false }
                let fuera = fijos.allSatisfy { f in
                    let minima = f.radio + alcance - 0.5
                    let dx = f.x - x, dy = f.y - y
                    return dx * dx + dy * dy >= minima * minima
                }
                return fuera && puestos.allSatisfy { p in
                    let minima = p.radio + margen + alcance - 0.5
                    let dx = p.x - x, dy = p.y - y
                    return dx * dx + dy * dy >= minima * minima
                }
            }

            var mejorX = min(max(focoX, minX), maxX)
            var mejorY = max(focoY, minY)
            var mejor = libre(mejorX, mejorY) ? coste(mejorX, mejorY) : CGFloat.greatestFiniteMagnitude

            func probar(_ x: CGFloat, _ y: CGFloat) {
                let c = coste(x, y)
                if c < mejor && libre(x, y) {
                    mejor = c
                    mejorX = x
                    mejorY = y
                }
            }

            for f in fijos {
                let distancia = f.radio + alcance
                for k in 0..<angulos {
                    let a = 2 * Double.pi * Double(k) / Double(angulos)
                    probar(f.x + distancia * CGFloat(cos(a)), f.y + distancia * CGFloat(sin(a)))
                }
            }
            for p in puestos {
                let distancia = p.radio + margen + alcance
                for k in 0..<angulos {
                    let a = 2 * Double.pi * Double(k) / Double(angulos)
                    probar(p.x + distancia * CGFloat(cos(a)), p.y + distancia * CGFloat(sin(a)))
                }
                // Pegado a una pared y tocando a este.
                for pared in [minX, maxX] {
                    let dx = pared - p.x
                    if abs(dx) <= distancia {
                        let dy = (distancia * distancia - dx * dx).squareRoot()
                        probar(pared, p.y + dy)
                        probar(pared, p.y - dy)
                    }
                }
            }

            if mejor == CGFloat.greatestFiniteMagnitude {
                // Ningun hueco junto a nadie (no deberia pasar): debajo de todo.
                mejorX = min(max(focoX, minX), maxX)
                mejorY = (puestos.map { $0.y + $0.radio + margen }.max() ?? 0) + alcance
            }
            puestos.append(Sitio(clave: hueco.clave, x: mejorX, y: mejorY, radio: hueco.radio))
        }
        return puestos
    }
}

/// Una burbuja en movimiento. `casaX`/`casaY` es donde descansa.
final class Cuerpo {
    let clave: String
    var x: CGFloat
    var y: CGFloat
    var radio: CGFloat
    var vx: CGFloat = 0
    var vy: CGFloat = 0
    var casaX: CGFloat
    var casaY: CGFloat
    var radioObjetivo: CGFloat
    var agarrado = false
    var soltadoEn: CGFloat = -100

    /// Cada una flota a su aire: fase y ritmo propios, sacados de la clave.
    let fase: CGFloat
    let ritmo: CGFloat

    init(clave: String, x: CGFloat, y: CGFloat, radio: CGFloat) {
        self.clave = clave
        self.x = x
        self.y = y
        self.radio = radio
        casaX = x
        casaY = y
        radioObjetivo = radio
        // Un hash propio (el de Swift cambia en cada arranque): que cada una flote igual siempre.
        var h: UInt32 = 2166136261
        for b in clave.utf8 { h = (h ^ UInt32(b)) &* 16777619 }
        fase = CGFloat(h & 0xFFFF) / 0xFFFF * 2 * .pi
        ritmo = 0.35 + CGFloat((h >> 16) & 0xFF) / 0xFF * 0.25
    }
}

/// Muelles hacia casa, choques entre burbujas y paredes. Quien esta en el
/// dedo no cede: empuja a las demas. Al soltarla, el muelle arranca flojo y
/// se endurece poco a poco, para que primero se vea la inercia del lanzamiento
/// y luego vuelva sola, con un pequeno rebote, a su sitio.
final class Simulacion {
    private(set) var cuerpos: [String: Cuerpo] = [:]
    private var orden: [Cuerpo] = []

    var ancho: CGFloat = 0
    var alto: CGFloat = 0
    var margen: CGFloat = 0
    var borde: CGFloat = 0
    var bordeInferior: CGFloat = 0

    /// Circulos fijos que las burbujas no pueden pisar (el sol de la esquina).
    var obstaculos: [Sitio] = []

    /// Movimiento reducido: el reposo es quieto y las nuevas nacen ya en su sitio.
    var calma = false

    private(set) var tiempo: CGFloat = 0

    /// El choque mas fuerte del ultimo paso: la velocidad (pt/s) con que se
    /// acercaban dos burbujas, o una y el borde. Para la vibracion.
    private(set) var golpe: CGFloat = 0

    private var marca: TimeInterval?

    // Muelle hacia casa (1/s²): vuelve en algo mas de un segundo.
    private let rigidez: CGFloat = 14
    // Menos de 1: un pequeno rebote al llegar, no un frenazo.
    private let amortiguacion: CGFloat = 0.6
    // Freno minimo mientras el muelle aun esta flojo tras soltarla.
    private let friccion: CGFloat = 2.6
    // Segundos que tarda el muelle en recuperar toda su fuerza tras soltarla.
    private let endurecer: CGFloat = 1.4
    private let rebote: CGFloat = 0.3
    private let rebotePared: CGFloat = 0.5
    private let deriva: CGFloat = 3
    private let velocidadMaxima: CGFloat = 1800
    private let nacerDesde: CGFloat = 36

    /// Avanza hasta `ahora` (un instante de reloj, p. ej. el de `TimelineView`),
    /// a lo sumo 1/30 s de una vez: tras un paron no hay saltos.
    func avanzar(hasta ahora: TimeInterval) {
        defer { marca = ahora }
        guard let antes = marca else { return }
        let dt = CGFloat(min(max(ahora - antes, 0), 1.0 / 30.0))
        if dt > 0 { paso(dt) }
    }

    /// Pone las casas nuevas. Las burbujas que ya estaban van nadando a su
    /// sitio; las nuevas nacen un poco mas abajo y suben, o salen de la
    /// burbuja indicada en `salenDe` si esta existe (el grupo al abrirse).
    func colocar(_ sitios: [Sitio], salenDe: [String: String] = [:]) {
        var nuevos: [String: Cuerpo] = [:]
        var nuevoOrden: [Cuerpo] = []
        for sitio in sitios {
            let cuerpo: Cuerpo
            if let existente = cuerpos[sitio.clave] {
                cuerpo = existente
            } else if let origen = salenDe[sitio.clave].flatMap({ cuerpos[$0] }) {
                cuerpo = Cuerpo(clave: sitio.clave, x: origen.x, y: origen.y, radio: sitio.radio)
            } else {
                cuerpo = Cuerpo(clave: sitio.clave, x: sitio.x, y: sitio.y + (calma ? 0 : nacerDesde), radio: sitio.radio)
            }
            cuerpo.casaX = sitio.x
            cuerpo.casaY = sitio.y
            cuerpo.radioObjetivo = sitio.radio
            nuevos[sitio.clave] = cuerpo
            nuevoOrden.append(cuerpo)
        }
        cuerpos = nuevos
        orden = nuevoOrden
    }

    func agarrar(_ cuerpo: Cuerpo) {
        cuerpo.agarrado = true
        cuerpo.vx = 0
        cuerpo.vy = 0
    }

    /// Sigue al dedo.
    func mover(_ cuerpo: Cuerpo, dx: CGFloat, dy: CGFloat) {
        cuerpo.x += dx
        cuerpo.y += dy
        contenerEnParedes(cuerpo)
    }

    /// La velocidad del dedo: con ella empuja a las que choca mientras la lleva.
    func arrastrarA(_ cuerpo: Cuerpo, vx: CGFloat, vy: CGFloat) {
        cuerpo.vx = vx
        cuerpo.vy = vy
    }

    func soltar(_ cuerpo: Cuerpo, vx: CGFloat, vy: CGFloat) {
        let v = (vx * vx + vy * vy).squareRoot()
        let factor = v > velocidadMaxima ? velocidadMaxima / v : 1
        cuerpo.vx = vx * factor
        cuerpo.vy = vy * factor
        cuerpo.agarrado = false
        cuerpo.soltadoEn = tiempo
    }

    func paso(_ dt: CGFloat) {
        tiempo += dt
        golpe = 0
        let deriva = calma ? 0 : self.deriva
        let crecer = 1 - CGFloat(exp(-8 * Double(dt)))
        for c in orden {
            c.radio += (c.radioObjetivo - c.radio) * crecer
            if c.agarrado { continue }
            let rigidez = self.rigidez * min(max((tiempo - c.soltadoEn) / endurecer, 0.06), 1)
            let freno = max(2 * rigidez.squareRoot() * amortiguacion, friccion)
            // El reposo no es quieto: la casa se mece un poco, como algo que flota.
            let objetivoX = c.casaX + deriva * CGFloat(sin(Double(tiempo * c.ritmo + c.fase)))
            let objetivoY = c.casaY + deriva * CGFloat(cos(Double(tiempo * c.ritmo * 0.8 + c.fase * 1.3)))
            c.vx += (rigidez * (objetivoX - c.x) - freno * c.vx) * dt
            c.vy += (rigidez * (objetivoY - c.y) - freno * c.vy) * dt
            c.x += c.vx * dt
            c.y += c.vy * dt
        }
        for _ in 0..<2 { separar() }
        for c in orden {
            apartarDeObstaculos(c)
            contenerEnParedes(c)
        }
    }

    private func separar() {
        for i in orden.indices {
            let a = orden[i]
            for j in (i + 1)..<max(orden.count, i + 1) {
                let b = orden[j]
                if a.agarrado && b.agarrado { continue }
                let minima = a.radio + b.radio + 2 * margen
                let dx = b.x - a.x, dy = b.y - a.y
                let d2 = dx * dx + dy * dy
                if d2 >= minima * minima { continue }
                let d = d2.squareRoot()
                let nx: CGFloat, ny: CGFloat
                if d > 0.01 {
                    nx = dx / d
                    ny = dy / d
                } else {
                    nx = CGFloat(cos(Double(a.fase)))
                    ny = CGFloat(sin(Double(a.fase)))
                }
                let solape = minima - d
                let pa: CGFloat = a.agarrado ? 0 : (b.agarrado ? 1 : 0.5)
                let pb = 1 - pa
                a.x -= nx * solape * pa
                a.y -= ny * solape * pa
                b.x += nx * solape * pb
                b.y += ny * solape * pb
                // Choque blando: se quita la velocidad con que se acercan y un poco mas.
                let acercamiento = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if acercamiento < 0 {
                    golpe = max(golpe, -acercamiento)
                    let impulso = -(1 + rebote) * acercamiento
                    a.vx -= impulso * nx * pa
                    a.vy -= impulso * ny * pa
                    b.vx += impulso * nx * pb
                    b.vy += impulso * ny * pb
                }
            }
        }
    }

    /// Si pisa un obstaculo, sale hacia fuera y pierde la velocidad con que entraba.
    private func apartarDeObstaculos(_ c: Cuerpo) {
        if c.agarrado { return }
        for o in obstaculos {
            let minima = o.radio + c.radio
            let dx = c.x - o.x, dy = c.y - o.y
            let d2 = dx * dx + dy * dy
            if d2 >= minima * minima { continue }
            let d = max(d2.squareRoot(), 0.01)
            let nx = dx / d, ny = dy / d
            c.x = o.x + nx * minima
            c.y = o.y + ny * minima
            let entrando = c.vx * nx + c.vy * ny
            if entrando < 0 {
                c.vx -= (1 + rebotePared) * entrando * nx
                c.vy -= (1 + rebotePared) * entrando * ny
            }
        }
    }

    private func contenerEnParedes(_ c: Cuerpo) {
        let minX = c.radio + borde
        let maxX = max(minX, ancho - c.radio - borde)
        let minY = c.radio + borde
        let maxY = max(minY, alto - bordeInferior - c.radio)
        // Solo cuenta como golpe si no la lleva el dedo: arrastrarla contra el borde no vibra.
        let suelto = !c.agarrado
        if c.x < minX {
            c.x = minX
            if c.vx < 0 {
                if suelto { golpe = max(golpe, -c.vx) }
                c.vx = -c.vx * rebotePared
            }
        } else if c.x > maxX {
            c.x = maxX
            if c.vx > 0 {
                if suelto { golpe = max(golpe, c.vx) }
                c.vx = -c.vx * rebotePared
            }
        }
        if c.y < minY {
            c.y = minY
            if c.vy < 0 {
                if suelto { golpe = max(golpe, -c.vy) }
                c.vy = -c.vy * rebotePared
            }
        } else if c.y > maxY {
            c.y = maxY
            if c.vy > 0 {
                if suelto { golpe = max(golpe, c.vy) }
                c.vy = -c.vy * rebotePared
            }
        }
    }
}
