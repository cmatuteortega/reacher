import XCTest
@testable import Contacto

final class AjustesTests: XCTestCase {
    private let cal = Calendar.current

    private func momento(_ d: Int, _ h: Int, _ m: Int = 0) -> Date {
        cal.date(from: DateComponents(year: 2025, month: 3, day: d, hour: h, minute: m))!
    }

    func testFranjaNormal() {
        var a = Ajustes()
        a.horaDesde = 9; a.horaHasta = 21
        XCTAssertFalse(a.dentroDeFranja(hora: 8))
        XCTAssertTrue(a.dentroDeFranja(hora: 9))
        XCTAssertTrue(a.dentroDeFranja(hora: 20))
        XCTAssertFalse(a.dentroDeFranja(hora: 21))
    }

    func testFranjaQueCruzaLaMedianoche() {
        var a = Ajustes()
        a.horaDesde = 20; a.horaHasta = 8
        XCTAssertTrue(a.dentroDeFranja(hora: 23))
        XCTAssertTrue(a.dentroDeFranja(hora: 3))
        XCTAssertFalse(a.dentroDeFranja(hora: 8))
        XCTAssertFalse(a.dentroDeFranja(hora: 12))
        XCTAssertTrue(a.dentroDeFranja(hora: 20))
    }

    func testMismaHoraEsTodoElDia() {
        var a = Ajustes()
        a.horaDesde = 7; a.horaHasta = 7
        for h in 0..<24 { XCTAssertTrue(a.dentroDeFranja(hora: h)) }
    }

    func testSiguienteEnFranja() {
        var a = Ajustes()
        a.horaDesde = 9; a.horaHasta = 21
        XCTAssertEqual(a.siguienteEnFranja(desde: momento(10, 12, 30)), momento(10, 12, 30))
        // Antes de abrir: hoy a las 9. Despues de cerrar: manana a las 9.
        XCTAssertEqual(a.siguienteEnFranja(desde: momento(10, 6)), momento(10, 9))
        XCTAssertEqual(a.siguienteEnFranja(desde: momento(10, 22)), momento(11, 9))
    }

    func testAvisosApagadosVuelvenSolosPasadaLaPausa() {
        var a = Ajustes()
        XCTAssertTrue(a.avisosEncendidos())
        a.avisosActivos = false
        XCTAssertFalse(a.avisosEncendidos())
        a.pausaHasta = momento(12, 0)
        XCTAssertFalse(a.avisosEncendidos(hoy: momento(11, 23)))
        XCTAssertTrue(a.avisosEncendidos(hoy: momento(12, 0)))
        XCTAssertTrue(a.avisosEncendidos(hoy: momento(20, 8)))
    }

    func testMedioDesconocidoVuelveAlMarcadorYLlamadaTambien() {
        XCTAssertEqual(MedioContacto.desde(nil), .MARCADOR)
        XCTAssertEqual(MedioContacto.desde("OTRO"), .MARCADOR)
        XCTAssertEqual(MedioContacto.desde("LLAMADA"), .MARCADOR)
        XCTAssertEqual(MedioContacto.desde("WHATSAPP"), .WHATSAPP)
    }

    func testLosAjustesSobreviven() throws {
        var a = Ajustes()
        a.horaDesde = 8; a.vista = .LISTA; a.tema = .OSCURO; a.medioPorDefecto = .TELEGRAM
        let vuelta = try JSONDecoder().decode(Ajustes.self, from: JSONEncoder().encode(a))
        XCTAssertEqual(a, vuelta)
    }
}
