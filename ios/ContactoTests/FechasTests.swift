import XCTest
@testable import Contacto

final class FechasTests: XCTestCase {
    func testDiaIdaYVuelta() {
        let d = Fechas.dia(iso: "2025-03-12")!
        XCTAssertEqual(Fechas.iso(dia: d), "2025-03-12")
    }

    func testMomentoAdmiteConYSinSegundos() {
        XCTAssertNotNil(Fechas.momento(iso: "2025-03-12T10:15:30"))
        XCTAssertNotNil(Fechas.momento(iso: "2025-03-12T10:15"))
        XCTAssertNotNil(Fechas.momento(iso: "2025-03-12T10:15:30.123456"))
        XCTAssertNil(Fechas.momento(iso: "ayer"))
        XCTAssertNil(Fechas.momento(iso: nil))
        let m = Fechas.momento(iso: "2025-03-12T10:15:30")!
        XCTAssertEqual(Fechas.iso(momento: m), "2025-03-12T10:15:30")
    }

    func testMesDiaComoMonthDay() {
        XCTAssertEqual(Fechas.iso(mes: 3, dia: 5), "--03-05")
        let r = Fechas.mesDia(iso: "--03-05")
        XCTAssertEqual(r?.mes, 3)
        XCTAssertEqual(r?.dia, 5)
        XCTAssertNil(Fechas.mesDia(iso: "--13-05"))
        XCTAssertNil(Fechas.mesDia(iso: "1990-03-05"))
        XCTAssertNil(Fechas.mesDia(iso: nil))
    }

    func testDiasEntreFechasIgnoraLaHora() {
        let a = Calendar.current.date(from: DateComponents(year: 2025, month: 3, day: 1, hour: 23, minute: 59))!
        let b = Calendar.current.date(from: DateComponents(year: 2025, month: 3, day: 2, hour: 0, minute: 1))!
        XCTAssertEqual(Fechas.dias(de: a, a: b), 1)
        XCTAssertEqual(Fechas.dias(de: b, a: a), -1)
    }
}
