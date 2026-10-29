import XCTest
@testable import Contacto

final class OrbitasTests: XCTestCase {
    private func gente(_ n: Int) -> [EnOrbita] {
        (0..<n).map { EnOrbita(id: UUID(), frecuencia: 3 + $0 * 2, urgencia: Double($0 % 5) / 3) }
    }

    private let pantalla = CGSize(width: 390, height: 700)
    private let sol = CGPoint(x: 26, y: 26)

    func testTodosTienenPuestoSeaCualSeaElNumero() {
        for n in [1, 2, 3, 5, 8, 12, 20, 35, 60] {
            let personas = gente(n)
            let plano = Orbitas.planear(personas: personas, tamano: pantalla, sol: sol, radioSol: 40)
            XCTAssertEqual(plano.puestos.count, n, "con \(n) personas")
        }
    }

    func testNadieSeSaleDeLaPantalla() {
        let personas = gente(14)
        let plano = Orbitas.planear(personas: personas, tamano: pantalla, sol: sol, radioSol: 40)
        for (_, p) in plano.puestos {
            let r = plano.radios[p.anillo]
            let x = sol.x + r * CGFloat(cos(p.angulo)), y = sol.y + r * CGFloat(sin(p.angulo))
            XCTAssertGreaterThanOrEqual(x - p.radio, -0.5)
            XCTAssertLessThanOrEqual(x + p.radio, pantalla.width + 0.5)
            XCTAssertGreaterThanOrEqual(y - p.radio, -0.5)
            XCTAssertLessThanOrEqual(y + p.radio, pantalla.height + 0.5)
        }
    }

    func testLosAnillosVanDeDentroAfuera() {
        let plano = Orbitas.planear(personas: gente(10), tamano: pantalla, sol: sol, radioSol: 40)
        XCTAssertEqual(plano.radios, plano.radios.sorted())
        XCTAssertGreaterThan(plano.radios.first ?? 0, 40)
    }

    func testQuienVaMasAMenudoVaMasDentro() {
        let personas = gente(10)  // ya ordenadas por frecuencia
        let plano = Orbitas.planear(personas: personas, tamano: pantalla, sol: sol, radioSol: 40)
        var anilloAnterior = 0
        for p in personas {
            let anillo = plano.puestos[p.id]!.anillo
            XCTAssertGreaterThanOrEqual(anillo, anilloAnterior)
            anilloAnterior = anillo
        }
    }

    func testLaUrgenciaSoloCambiaElTamano() {
        let a = EnOrbita(id: UUID(), frecuencia: 7, urgencia: 0.1)
        let b = EnOrbita(id: UUID(), frecuencia: 7, urgencia: 1.2)
        let plano = Orbitas.planear(personas: [a, b], tamano: pantalla, sol: sol, radioSol: 40)
        XCTAssertLessThan(plano.puestos[a.id]!.radio, plano.puestos[b.id]!.radio)
        XCTAssertEqual(plano.puestos[a.id]!.anillo, plano.puestos[b.id]!.anillo)
    }

    func testSinGenteNoHayAnillos() {
        XCTAssertTrue(Orbitas.planear(personas: [], tamano: pantalla, sol: sol, radioSol: 40).radios.isEmpty)
    }

    func testLosLetrerosLlevanLaFrecuenciaMasLargaDeCadaAnillo() {
        let personas = gente(6)
        let plano = Orbitas.planear(personas: personas, tamano: pantalla, sol: sol, radioSol: 40)
        for l in plano.letreros {
            let delAnillo = personas.filter { plano.puestos[$0.id]!.anillo == l.anillo }
            XCTAssertEqual(l.dias, delAnillo.map(\.frecuencia).max())
        }
    }
}
