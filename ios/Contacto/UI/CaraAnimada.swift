import SwiftUI

/*
 * Caras de solo ojos sobre una esfera, como en Android (`Cara.kt`). Una
 * expresion es la postura de la cabeza y la forma de cada ojo; una animacion,
 * una lista de pasos (transicion y espera) entre expresiones. `EstadoCara` la
 * reproduce con el reloj de fotogramas, parpadea y anade el movimiento
 * ambiental (microsacadas, deriva, temblor).
 */

enum MovimientoOjos { case ninguno, microsacadas, temblor }
enum MovimientoCuerpo { case ninguno, deriva, temblor }
/// Muelle: se pasa un poco y vuelve. Suave: entra y sale despacio. Seca: arranca de golpe.
enum Transicion { case muelle, suave, seca }
enum ModoAnimacion { case bucle, unaVez, idaYVuelta }

/// Un ojo en el plano de la cara, en radios de la esfera. `angulo` en grados.
struct Ojo {
    var ancho = 0.24
    var alto = 0.35
    var x = 0.0
    var y = 0.0
    var angulo = 0.0

    func menor(_ k: Double) -> Ojo { Ojo(ancho: ancho * k, alto: alto * k, x: x * k, y: y * k, angulo: angulo) }
}

struct Expresion {
    /// Grados: mas de 0 mira arriba.
    var cabezaX = 0.0
    /// Grados: mas de 0 mira a la derecha de la pantalla.
    var cabezaY = 0.0
    /// Grados: ladea la cabeza.
    var cabezaZ = 0.0
    var izquierdo = Ojo()
    var derecho = Ojo()
    /// Distancia entre los centros de los ojos, en radios.
    var separacion = 0.54
    /// 0 sin perspectiva; hacia 0.5, muy marcada.
    var perspectiva = 0.3
    var movOjos = MovimientoOjos.microsacadas
    var movCuerpo = MovimientoCuerpo.deriva

    /// Los dos ojos iguales, en espejo: `ojo` es el derecho.
    func conOjos(_ ojo: Ojo) -> Expresion {
        var e = self
        var izq = ojo
        izq.x = -ojo.x
        izq.angulo = -ojo.angulo
        e.izquierdo = izq
        e.derecho = ojo
        return e
    }

    func girada(x: Double? = nil, y: Double? = nil, z: Double? = nil) -> Expresion {
        var e = self
        e.cabezaX = x ?? cabezaX
        e.cabezaY = y ?? cabezaY
        e.cabezaZ = z ?? cabezaZ
        return e
    }

    /// La misma cara con los ojos (y lo que los separa) a `k` de su tamano.
    func conOjosDe(_ k: Double) -> Expresion {
        var e = self
        e.izquierdo = izquierdo.menor(k)
        e.derecho = derecho.menor(k)
        e.separacion = separacion * k
        return e
    }
}

private func mezcla(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func mezcla(_ a: Ojo, _ b: Ojo, _ t: Double) -> Ojo {
    Ojo(
        ancho: mezcla(a.ancho, b.ancho, t), alto: mezcla(a.alto, b.alto, t),
        x: mezcla(a.x, b.x, t), y: mezcla(a.y, b.y, t), angulo: mezcla(a.angulo, b.angulo, t)
    )
}

private func mezcla(_ a: Expresion, _ b: Expresion, _ t: Double) -> Expresion {
    Expresion(
        cabezaX: mezcla(a.cabezaX, b.cabezaX, t), cabezaY: mezcla(a.cabezaY, b.cabezaY, t),
        cabezaZ: mezcla(a.cabezaZ, b.cabezaZ, t),
        izquierdo: mezcla(a.izquierdo, b.izquierdo, t), derecho: mezcla(a.derecho, b.derecho, t),
        separacion: mezcla(a.separacion, b.separacion, t), perspectiva: mezcla(a.perspectiva, b.perspectiva, t),
        movOjos: t < 0.5 ? a.movOjos : b.movOjos, movCuerpo: t < 0.5 ? a.movCuerpo : b.movCuerpo
    )
}

private func curva(_ transicion: Transicion, _ t: Double) -> Double {
    switch transicion {
    case .suave: return t * t * (3 - 2 * t)
    case .seca: return 1 - (1 - t) * (1 - t) * (1 - t)
    // Muelle amortiguado: se pasa un poco del destino y se asienta.
    case .muelle: return t >= 1 ? 1 : 1 - exp(-6 * t) * cos(9 * t)
    }
}

enum Expresiones {
    static let neutral = Expresion()
    static let contenta = Expresion(cabezaX: 8).conOjos(Ojo(ancho: 0.29, alto: 0.13, y: -0.02))
    static let sorpresa = Expresion(cabezaX: 10, movCuerpo: .temblor).conOjos(Ojo(ancho: 0.33, alto: 0.4))
    /// Al apretarla: ojos cerrados de gusto.
    static let apretada = Expresion(cabezaX: 4, movOjos: .ninguno).conOjos(Ojo(ancho: 0.3, alto: 0.07))
}

/// Cada paso: `transicionMs` para llegar a `expresion` y `esperaMs` quieta en ella.
struct Paso {
    var expresion: Expresion
    var transicionMs = 300
    var esperaMs = 0
    var transicion = Transicion.muelle
}

struct Parpadeo {
    var activo = true
    var primeroMs = 900
    var minMs = 2200
    var maxMs = 5500
    var duracionMs = 170
}

final class Animacion {
    let pasos: [Paso]
    let modo: ModoAnimacion
    let parpadeo = Parpadeo()

    init(_ pasos: [Paso], modo: ModoAnimacion = .bucle) {
        self.pasos = pasos
        self.modo = modo
    }
}

enum Animaciones {
    /// En reposo: mira a un lado y al otro.
    static let mirarAlrededor = Animacion([
        Paso(expresion: Expresiones.neutral, transicionMs: 400, esperaMs: 2400),
        Paso(expresion: Expresiones.neutral.girada(y: -32), transicionMs: 450, esperaMs: 1200),
        Paso(expresion: Expresiones.neutral.girada(x: 10, y: 28), transicionMs: 550, esperaMs: 1200),
        Paso(expresion: Expresiones.neutral, transicionMs: 400, esperaMs: 800),
    ])

    /// Una vez: se sorprende y sonrie.
    static let saludo = Animacion([
        Paso(expresion: Expresiones.sorpresa, transicionMs: 150, esperaMs: 180, transicion: .seca),
        Paso(expresion: Expresiones.contenta.girada(x: 12, z: -12), transicionMs: 380, esperaMs: 300),
        Paso(expresion: Expresiones.contenta, transicionMs: 320),
    ], modo: .unaVez)

    /// Una vez: se rie meneandose.
    static let risa = Animacion([
        Paso(expresion: Expresiones.apretada.girada(z: -12), transicionMs: 110, transicion: .seca),
        Paso(expresion: Expresiones.apretada.girada(z: 12), transicionMs: 130, transicion: .seca),
        Paso(expresion: Expresiones.apretada.girada(z: -10), transicionMs: 130, transicion: .seca),
        Paso(expresion: Expresiones.apretada.girada(z: 8), transicionMs: 130, transicion: .seca),
        Paso(expresion: Expresiones.contenta, transicionMs: 380, esperaMs: 200),
    ], modo: .unaVez)
}

/// Reproduce animaciones en una cara al ritmo de los fotogramas. Es una clase
/// sin estado observable: solo la lee el dibujo, asi que nada se recompone.
final class EstadoCara {
    /// Milisegundos del reloj de fotogramas.
    private(set) var ahora: Int64 = 0
    private(set) var animacion: Animacion?

    private var desde: Expresion
    private var hacia: Expresion
    private var inicioFase: Int64 = 0
    private var duracion: Int64 = 0
    private var transicion = Transicion.suave
    private var enTransicion = false
    private var paso = 0
    private var sentido = 1
    private var alAcabar: () -> Void = {}
    private var proximoParpadeo: Int64 = -1
    private var parpadeoDesde: Int64 = -1
    private var generador: SystemRandomNumberGenerator
    private let semilla: Int64

    /// Movimiento reducido: sin parpadeo ni vaiven, y las animaciones en bucle
    /// se quedan en su primer gesto. Lo que dura una vez (reirse al tocarla) si se hace.
    var quieta = false

    init(inicial: Expresion, semilla: Int64 = 0) {
        desde = inicial
        hacia = inicial
        self.semilla = semilla
        generador = SystemRandomNumberGenerator()
    }

    /// Segundos para lo que se mueve solo alrededor de la cara. Quieta, siempre 0.
    var reloj: Double { quieta ? 0 : Double(ahora) / 1000 }

    /// Empieza `anim`.
    func reproducir(_ anim: Animacion, alAcabar: @escaping () -> Void = {}) {
        paso = 0
        sentido = 1
        let p = anim.pasos[0]
        ir(p.expresion, p.transicionMs, p.transicion)
        animacion = anim
        self.alAcabar = alAcabar
        proximoParpadeo = ahora + Int64(anim.parpadeo.primeroMs)
    }

    /// Pasa a `e` y se queda ahi, sin animacion.
    func poner(_ e: Expresion, ms: Int = 250, transicion: Transicion = .muelle) {
        ir(e, ms, transicion)
        animacion = nil
    }

    private func ir(_ e: Expresion, _ ms: Int, _ t: Transicion) {
        desde = postura(ahora)
        hacia = e
        inicioFase = ahora
        duracion = Int64(ms)
        transicion = t
        enTransicion = true
    }

    /// La expresion de este instante, sin el movimiento ambiental.
    private func postura(_ t: Int64) -> Expresion {
        if !enTransicion || duracion <= 0 { return hacia }
        let progreso = min(max(Double(t - inicioFase) / Double(duracion), 0), 1)
        return mezcla(desde, hacia, curva(transicion, progreso))
    }

    func avanzar(_ t: Int64) {
        ahora = t
        parpadear(t)
        // Tras mucho rato sin fotogramas (app en segundo plano) no se recupera el tiempo.
        if t - inicioFase > 10_000 { inicioFase = t }
        if enTransicion && t >= inicioFase + duracion {
            enTransicion = false
            inicioFase += duracion
        }
        guard let anim = animacion else { return }
        if quieta && anim.modo != .unaVez { return }
        var vueltas = 0
        while !enTransicion && vueltas <= anim.pasos.count * 2 {
            vueltas += 1
            let fin = inicioFase + Int64(anim.pasos[paso].esperaMs)
            if t < fin { break }
            guard let siguiente = siguientePaso(anim) else {
                animacion = nil
                let callback = alAcabar
                alAcabar = {}
                callback()
                return
            }
            desde = hacia
            paso = siguiente
            let p = anim.pasos[paso]
            hacia = p.expresion
            inicioFase = fin
            duracion = Int64(p.transicionMs)
            transicion = p.transicion
            enTransicion = true
            if t >= inicioFase + duracion {
                enTransicion = false
                inicioFase += duracion
            }
        }
    }

    private func siguientePaso(_ anim: Animacion) -> Int? {
        let n = anim.pasos.count
        switch anim.modo {
        case .bucle: return (paso + 1) % n
        case .unaVez: return paso + 1 < n ? paso + 1 : nil
        case .idaYVuelta:
            if n == 1 { return 0 }
            if !(0..<n).contains(paso + sentido) { sentido = -sentido }
            return paso + sentido
        }
    }

    private func parpadear(_ t: Int64) {
        let p = animacion?.parpadeo ?? Parpadeo()
        if !p.activo || quieta {
            parpadeoDesde = -1
            return
        }
        if proximoParpadeo < 0 { proximoParpadeo = t + Int64(p.primeroMs) }
        if parpadeoDesde >= 0 && t > parpadeoDesde + Int64(p.duracionMs) { parpadeoDesde = -1 }
        if parpadeoDesde < 0 && t >= proximoParpadeo {
            parpadeoDesde = t
            proximoParpadeo = t + Int64(p.duracionMs) + Int64.random(in: Int64(p.minMs)..<Int64(max(p.minMs + 1, p.maxMs)), using: &generador)
        }
    }

    /// 1 abiertos, hacia 0 en mitad del parpadeo.
    func parpadeo() -> Double {
        if parpadeoDesde < 0 { return 1 }
        let d = Double(max((animacion?.parpadeo ?? Parpadeo()).duracionMs, 1))
        let progreso = min(max(Double(ahora - parpadeoDesde) / d, 0), 1)
        return max(1 - sin(progreso * .pi), 0.05)
    }

    /// Lo que se ve en este instante: postura mas movimiento ambiental.
    func muestra() -> Expresion {
        let t = ahora
        var e = postura(t)
        if quieta { return e }
        let s = Double(t) / 1000
        let f = Double(semilla) * 0.37
        switch e.movCuerpo {
        case .deriva:
            e.cabezaX += sin(s * 2.4 + f) * 2.5
            e.cabezaY += sin(s * 1.9 + f * 1.7) * 3.5
            e.cabezaZ += sin(s * 1.5 + f * 2.3) * 1.5
        case .temblor:
            e.cabezaY += sin(s * 70) * 4
        case .ninguno:
            break
        }
        var dx = 0.0, dy = 0.0
        switch e.movOjos {
        case .microsacadas:
            // Saltos pequenos y rapidos de la mirada cada poco menos de un segundo.
            let reloj = t + semilla * 331
            let tramo = reloj / 850
            let salto = min(max(Double(reloj % 850) / 70, 0), 1)
            dx = mezcla(ruido(tramo - 1, 1), ruido(tramo, 1), salto) * 0.035
            dy = mezcla(ruido(tramo - 1, 2), ruido(tramo, 2), salto) * 0.025
        case .temblor:
            dx = sin(s * 90) * 0.015
        case .ninguno:
            break
        }
        e.izquierdo.x += dx
        e.izquierdo.y += dy
        e.derecho.x += dx
        e.derecho.y += dy
        return e
    }

    /// De -1 a 1, fijo para cada tramo y cara.
    private func ruido(_ tramo: Int64, _ canal: Int64) -> Double {
        var h = UInt64(bitPattern: tramo &* 6364136223846793005 &+ semilla &* 1442695040888963407 &+ canal &* 2654435761)
        h ^= h >> 33
        h = h &* UInt64(bitPattern: -0xae502812aa7333)
        h ^= h >> 29
        return Double(h >> 40) / Double(1 << 24) * 2 - 1
    }
}
