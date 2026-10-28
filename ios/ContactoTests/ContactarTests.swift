import XCTest
@testable import Contacto

final class ContactarTests: XCTestCase {
    func testInternacionalCompletaConElPaisDeLaSim() {
        XCTAssertEqual(Contactar.internacional("600 12 34 56", pais: "ES"), "34600123456")
        XCTAssertEqual(Contactar.internacional("06 12 34 56 78", pais: "FR"), "33612345678")
    }

    func testInternacionalRespetaElPrefijoYaPuesto() {
        XCTAssertEqual(Contactar.internacional("+34 600 12 34 56", pais: "FR"), "34600123456")
        XCTAssertEqual(Contactar.internacional("0034 600 12 34 56", pais: "FR"), "34600123456")
        XCTAssertEqual(Contactar.internacional("34600123456", pais: "ES"), "34600123456")
    }

    func testInternacionalSinPaisConocidoDejaLasCifras() {
        XCTAssertEqual(Contactar.internacional("(600) 123-456", pais: ""), "600123456")
    }

    func testEnlacesPorMedio() {
        XCTAssertEqual(Contactar.url(telefono: "600 12 34 56", medio: .MARCADOR, pais: "ES")?.absoluteString, "tel:600123456")
        XCTAssertEqual(Contactar.url(telefono: "+34 600123456", medio: .LLAMADA, pais: "ES")?.absoluteString, "tel:+34600123456")
        XCTAssertEqual(Contactar.url(telefono: "600123456", medio: .SMS, pais: "ES")?.absoluteString, "sms:600123456")
        XCTAssertEqual(Contactar.url(telefono: "600123456", medio: .WHATSAPP, pais: "ES")?.absoluteString, "https://wa.me/34600123456")
        XCTAssertEqual(Contactar.url(telefono: "600123456", medio: .TELEGRAM, pais: "ES")?.absoluteString, "https://t.me/+34600123456")
    }

    func testAlMarcarSeCodificanAlmohadillaYAsterisco() {
        XCTAssertEqual(Contactar.paraEsquema("*123#"), "*123%23")
    }
}
