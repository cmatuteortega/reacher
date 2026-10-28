import XCTest
@testable import Contacto

final class PlanAvisosTests: XCTestCase {
    private let cal = Calendar.current

    private func momento(_ d: Int, _ h: Int = 0, _ m: Int = 0, mes: Int = 3) -> Date {
        cal.date(from: DateComponents(year: 2025, month: mes, day: d, hour: h, minute: m))!
    }

    private func fechas(
        ultimo: Date, frecuencia: Int = 7, pospuesto: Date? = nil, pausado: Date? = nil,
        ajustes: Ajustes = Ajustes(), ahora: Date, cantidad: Int = 3
    ) -> [Date] {
        PlanAvisos.fechas(
            ultimo: ultimo, frecuencia: frecuencia, pospuestoHasta: pospuesto, pausadoHasta: pausado,
            ajustes: ajustes, ahora: ahora, cantidad: cantidad
        )
    }

    func testElPrimerAvisoEsElDiaQueTocaALaHoraDeInicio() {
        let r = fechas(ultimo: momento(1), ahora: momento(2, 15))
        XCTAssertEqual(r, [momento(8, 9), momento(9, 9), momento(10, 9)])
    }

    func testSiYaTocaYHoySePasoLaHoraElPrimeroEsManana() {
        let r = fechas(ultimo: momento(1), ahora: momento(20, 15))
        XCTAssertEqual(r.first, momento(21, 9))
    }

    func testSiYaTocaYHoyAunNoLlegaLaHoraEsHoy() {
        let r = fechas(ultimo: momento(1), ahora: momento(20, 7))
        XCTAssertEqual(r.first, momento(20, 9))
    }

    func testRespetaLaHoraDeInicioElegida() {
        var a = Ajustes()
        a.horaDesde = 18; a.horaHasta = 22
        let r = fechas(ultimo: momento(1), ajustes: a, ahora: momento(2, 12))
        XCTAssertEqual(r.first, momento(8, 18))
    }

    func testSinAvisosNoHayNada() {
        var a = Ajustes()
        a.avisosActivos = false
        XCTAssertTrue(fechas(ultimo: momento(1), ajustes: a, ahora: momento(2)).isEmpty)
    }

    func testPausaGeneralConFechaVuelveEseDia() {
        var a = Ajustes()
        a.avisosActivos = false
        a.pausaHasta = momento(15)
        let r = fechas(ultimo: momento(1), ajustes: a, ahora: momento(10, 12))
        XCTAssertEqual(r.first, momento(15, 9))
    }

    func testPausaDeLaPersonaRetrasaElAviso() {
        let r = fechas(ultimo: momento(1), pausado: momento(12, 10), ahora: momento(9, 12))
        XCTAssertEqual(r.first, momento(13, 9))
    }

    func testMasTardeCaeDentroDelHorarioYNoDuplicaElDia() {
        let r = fechas(ultimo: momento(1), pospuesto: momento(20, 17), ahora: momento(20, 15))
        XCTAssertEqual(r.first, momento(20, 17))
        XCTAssertEqual(r[1], momento(21, 9))
    }

    func testMasTardeFueraDelHorarioEsperaALaHoraDeInicio() {
        let r = fechas(ultimo: momento(1), pospuesto: momento(20, 23), ahora: momento(20, 21, 30))
        XCTAssertEqual(r.first, momento(21, 9))
        // Y no se repite el mismo dia con el aviso diario.
        XCTAssertEqual(r[1], momento(22, 9))
    }

    func testRespetaLaCantidadPedida() {
        XCTAssertEqual(fechas(ultimo: momento(1), ahora: momento(2), cantidad: 1).count, 1)
        XCTAssertEqual(fechas(ultimo: momento(1), ahora: momento(2), cantidad: 7).count, 7)
        XCTAssertTrue(fechas(ultimo: momento(1), ahora: momento(2), cantidad: 0).isEmpty)
    }
}
