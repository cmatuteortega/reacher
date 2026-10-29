import XCTest
@testable import Contacto

final class FisicaTests: XCTestCase {
    private func huecos(_ radios: [CGFloat]) -> [Hueco] {
        radios.enumerated().map { Hueco(clave: "p\($0.offset)", radio: $0.element) }
    }

    func testRadioCreceConLaUrgenciaYNoPasaDelTope() {
        let a = Fisica.radioPorUrgencia(0, minimo: 26, maximo: 84)
        let b = Fisica.radioPorUrgencia(0.8, minimo: 26, maximo: 84)
        let c = Fisica.radioPorUrgencia(5, minimo: 26, maximo: 84)
        XCTAssertEqual(a, 26, accuracy: 0.001)
        XCTAssertLessThan(a, b)
        XCTAssertEqual(c, 84, accuracy: 0.001)
    }

    func testEmpaquetadoNoSolapaNoPisaElSolYCabeEnElAncho() {
        let sol = Sitio(clave: "sol", x: 0, y: 0, radio: 120)
        let margen: CGFloat = 12, borde: CGFloat = 8, ancho: CGFloat = 360
        let puestos = Empaquetado.empaquetar(
            huecos: huecos([70, 60, 52, 44, 40, 36, 30, 28, 26, 26, 26, 26]),
            ancho: ancho, margen: margen, borde: borde, focoX: ancho / 2, focoY: 300, fijos: [sol]
        )
        XCTAssertEqual(puestos.count, 12)
        for (i, p) in puestos.enumerated() {
            XCTAssertGreaterThanOrEqual(p.x - p.radio - margen, borde - 0.6)
            XCTAssertLessThanOrEqual(p.x + p.radio + margen, ancho - borde + 0.6)
            XCTAssertGreaterThanOrEqual(hypot(p.x - sol.x, p.y - sol.y), p.radio + sol.radio + margen - 0.6)
            for q in puestos[(i + 1)...] {
                XCTAssertGreaterThanOrEqual(hypot(p.x - q.x, p.y - q.y), p.radio + q.radio + margen - 0.6)
            }
        }
    }

    func testLoMasUrgenteQuedaMasCercaDelFoco() {
        let puestos = Empaquetado.empaquetar(
            huecos: huecos([70, 40, 30]), ancho: 360, margen: 12, borde: 8, focoX: 180, focoY: 300
        )
        let d = puestos.map { hypot($0.x - 180, $0.y - 300) }
        XCTAssertLessThan(d[0], d[1])
        XCTAssertLessThan(d[0], d[2])
    }

    func testSoltadaVuelveASuCasa() {
        let sim = Simulacion()
        sim.ancho = 360
        sim.alto = 700
        sim.margen = 12
        sim.borde = 8
        sim.calma = true
        sim.colocar([Sitio(clave: "a", x: 180, y: 300, radio: 40)])
        let a = sim.cuerpos["a"]!
        a.x = 60
        a.y = 500
        sim.soltar(a, vx: 400, vy: -300)
        for _ in 0..<(60 * 8) { sim.paso(1.0 / 60.0) }
        XCTAssertEqual(a.x, 180, accuracy: 1)
        XCTAssertEqual(a.y, 300, accuracy: 1)
    }

    func testDosBurbujasNoSeSolapanAunqueUnaVayaContraLaOtra() {
        let sim = Simulacion()
        sim.ancho = 360
        sim.alto = 700
        sim.margen = 12
        sim.borde = 8
        sim.calma = true
        sim.colocar([Sitio(clave: "a", x: 100, y: 300, radio: 40), Sitio(clave: "b", x: 260, y: 300, radio: 40)])
        let a = sim.cuerpos["a"]!, b = sim.cuerpos["b"]!
        sim.agarrar(a)
        // Hasta que b toca la pared: mas alla, a no tiene a donde empujarla.
        for _ in 0..<25 {
            sim.mover(a, dx: 4, dy: 0)
            sim.arrastrarA(a, vx: 240, vy: 0)
            sim.paso(1.0 / 60.0)
            XCTAssertGreaterThanOrEqual(hypot(a.x - b.x, a.y - b.y), a.radio + b.radio + 24 - 0.5)
        }
        XCTAssertGreaterThan(b.x, 260)
    }

    func testNadieSaleDeLasParedesNiPisaElSol() {
        let sim = Simulacion()
        sim.ancho = 360
        sim.alto = 700
        sim.borde = 8
        sim.calma = true
        let sol = Sitio(clave: "sol", x: 0, y: 0, radio: 100)
        sim.obstaculos = [sol]
        sim.colocar([Sitio(clave: "a", x: 180, y: 300, radio: 30)])
        let a = sim.cuerpos["a"]!
        sim.soltar(a, vx: -1500, vy: -1500)
        for _ in 0..<120 {
            sim.paso(1.0 / 60.0)
            XCTAssertGreaterThanOrEqual(a.x, a.radio + 8 - 0.01)
            XCTAssertGreaterThanOrEqual(a.y, a.radio + 8 - 0.01)
            XCTAssertGreaterThanOrEqual(hypot(a.x - sol.x, a.y - sol.y), a.radio + sol.radio - 0.01)
        }
    }

    func testLaDerivaEsLaMismaSiempreParaLaMismaClave() {
        let a = Cuerpo(clave: "x", x: 0, y: 0, radio: 10)
        let b = Cuerpo(clave: "x", x: 0, y: 0, radio: 10)
        XCTAssertEqual(a.fase, b.fase)
        XCTAssertEqual(a.ritmo, b.ritmo)
    }
}
