import SwiftData
import XCTest
@testable import Contacto

/// El modelo y las reglas de Personas, contra una base en memoria.
@MainActor
final class ContactoModeloTests: XCTestCase {
    private func contexto() throws -> ModelContext {
        let c = try ModelContainer(for: Contacto.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        return ModelContext(c)
    }

    private func hace(_ dias: Int) -> Date { Fechas.sumar(dias: -dias, a: Fechas.inicioDia(Date())) }

    func testSeGuardaYSeLee() throws {
        let ctx = try contexto()
        ctx.insert(Contacto(nombre: "Ana", telefono: "1", frecuenciaDias: 10, medio: .WHATSAPP, cumpleanos: (3, 12)))
        try ctx.save()
        let todos = try ctx.fetch(FetchDescriptor<Contacto>())
        XCTAssertEqual(todos.count, 1)
        XCTAssertEqual(todos[0].medio, .WHATSAPP)
        XCTAssertEqual(todos[0].cumpleanos?.mes, 3)
    }

    func testOrdenPorUrgenciaYDespuesPorNombre() {
        let a = Contacto(nombre: "Zoe", telefono: "1", frecuenciaDias: 10, ultimoContacto: hace(5))   // 0.5
        let b = Contacto(nombre: "Ana", telefono: "2", frecuenciaDias: 10, ultimoContacto: hace(5))   // 0.5
        let c = Contacto(nombre: "Leo", telefono: "3", frecuenciaDias: 10, ultimoContacto: hace(20))  // 2
        XCTAssertEqual([a, b, c].porUrgencia().map(\.nombre), ["Leo", "Ana", "Zoe"])
    }

    func testOrdenPorFrecuenciaNoDependeDeLosDias() {
        let a = Contacto(nombre: "A", telefono: "1", frecuenciaDias: 30, ultimoContacto: hace(40))
        let b = Contacto(nombre: "B", telefono: "2", frecuenciaDias: 7, ultimoContacto: hace(0))
        XCTAssertEqual([a, b].porFrecuencia().map(\.nombre), ["B", "A"])
    }

    func testLlamadoHoyReiniciaLaCuenta() {
        let c = Contacto(nombre: "Ana", telefono: "1", frecuenciaDias: 7, ultimoContacto: hace(20))
        c.pospuestoHasta = Date().addingTimeInterval(3600)
        c.descartes = 3
        XCTAssertTrue(c.tocaLlamar())
        c.llamadoHoy()
        XCTAssertFalse(c.tocaLlamar())
        XCTAssertEqual(c.urgencia(), 0, accuracy: 1e-9)
        XCTAssertNil(c.pospuestoHasta)
        XCTAssertEqual(c.descartes, 0)
    }

    func testPausarNoTocaElUltimoContacto() {
        let c = Contacto(nombre: "Ana", telefono: "1", frecuenciaDias: 7, ultimoContacto: hace(10))
        let antes = c.urgencia()
        c.pausadoHasta = Date().addingTimeInterval(86400)
        XCTAssertTrue(c.pausado())
        XCTAssertEqual(c.urgencia(), antes)
    }

    func testInicialDelAvatar() {
        XCTAssertEqual(Contacto(nombre: " ángel", telefono: "1").inicial, "Á")
        XCTAssertEqual(Contacto(nombre: "", telefono: "1").inicial, "?")
    }
}
