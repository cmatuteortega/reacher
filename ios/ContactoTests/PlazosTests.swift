import XCTest
@testable import Contacto

final class PlazosTests: XCTestCase {
    private let cal = Calendar.current

    private func dia(_ a: Int, _ m: Int, _ d: Int, hora: Int = 0) -> Date {
        cal.date(from: DateComponents(year: a, month: m, day: d, hour: hora))!
    }

    func testUrgenciaEsParteDeLaCadencia() {
        let ultimo = dia(2025, 3, 1)
        XCTAssertEqual(Plazos.urgencia(ultimo: ultimo, frecuencia: 10, hoy: dia(2025, 3, 1)), 0, accuracy: 1e-9)
        XCTAssertEqual(Plazos.urgencia(ultimo: ultimo, frecuencia: 10, hoy: dia(2025, 3, 6)), 0.5, accuracy: 1e-9)
        XCTAssertEqual(Plazos.urgencia(ultimo: ultimo, frecuencia: 10, hoy: dia(2025, 3, 11)), 1, accuracy: 1e-9)
        XCTAssertEqual(Plazos.urgencia(ultimo: ultimo, frecuencia: 10, hoy: dia(2025, 3, 21)), 2, accuracy: 1e-9)
    }

    func testUrgenciaNoEsNegativaNiDividePorCero() {
        let ultimo = dia(2025, 3, 10)
        XCTAssertEqual(Plazos.urgencia(ultimo: ultimo, frecuencia: 7, hoy: dia(2025, 3, 1)), 0)
        XCTAssertTrue(Plazos.urgencia(ultimo: ultimo, frecuencia: 0, hoy: dia(2025, 3, 12)).isFinite)
    }

    func testTocaLlamarElDiaExactoYDespues() {
        let ultimo = dia(2025, 3, 1)
        XCTAssertFalse(Plazos.tocaLlamar(ultimo: ultimo, frecuencia: 7, hoy: dia(2025, 3, 7, hora: 23)))
        XCTAssertTrue(Plazos.tocaLlamar(ultimo: ultimo, frecuencia: 7, hoy: dia(2025, 3, 8, hora: 0)))
        XCTAssertTrue(Plazos.tocaLlamar(ultimo: ultimo, frecuencia: 7, hoy: dia(2025, 4, 1)))
    }

    func testProximoAvisoEsUltimoMasFrecuencia() {
        XCTAssertEqual(Plazos.proximoAviso(ultimo: dia(2025, 2, 25, hora: 15), frecuencia: 7), dia(2025, 3, 4))
    }

    func testDiasDeRetraso() {
        let ultimo = dia(2025, 3, 1)
        XCTAssertEqual(Plazos.diasDeRetraso(ultimo: ultimo, frecuencia: 7, hoy: dia(2025, 3, 5)), 0)
        XCTAssertEqual(Plazos.diasDeRetraso(ultimo: ultimo, frecuencia: 7, hoy: dia(2025, 3, 10)), 2)
    }

    func testCumpleanosHoy() {
        XCTAssertTrue(Plazos.esCumpleanos(mes: 3, dia: 12, hoy: dia(2025, 3, 12, hora: 18)))
        XCTAssertFalse(Plazos.esCumpleanos(mes: 3, dia: 12, hoy: dia(2025, 3, 13)))
        XCTAssertFalse(Plazos.esCumpleanos(mes: 0, dia: 0, hoy: dia(2025, 3, 12)))
    }

    func test29DeFebreroSeCelebraElDia28LosAnosNoBisiestos() {
        XCTAssertTrue(Plazos.esCumpleanos(mes: 2, dia: 29, hoy: dia(2025, 2, 28)))
        XCTAssertFalse(Plazos.esCumpleanos(mes: 2, dia: 29, hoy: dia(2024, 2, 28)))
        XCTAssertTrue(Plazos.esCumpleanos(mes: 2, dia: 29, hoy: dia(2024, 2, 29)))
    }

    func testDiasHastaCumpleanos() {
        XCTAssertEqual(Plazos.diasHastaCumpleanos(mes: 3, dia: 12, hoy: dia(2025, 3, 12)), 0)
        XCTAssertEqual(Plazos.diasHastaCumpleanos(mes: 3, dia: 12, hoy: dia(2025, 3, 2)), 10)
        // Ya paso este ano: el del que viene.
        XCTAssertEqual(Plazos.diasHastaCumpleanos(mes: 3, dia: 12, hoy: dia(2025, 3, 13)), 364)
        XCTAssertNil(Plazos.diasHastaCumpleanos(mes: 0, dia: 0, hoy: dia(2025, 3, 13)))
    }
}
