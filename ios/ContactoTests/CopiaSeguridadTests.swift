import XCTest
@testable import Contacto

/// El archivo es el mismo que escribe la app de Android: estos JSON son del formato 1 de `CopiaSeguridad.kt`.
final class CopiaSeguridadTests: XCTestCase {
    private func datos(_ texto: String) -> Data { Data(texto.utf8) }

    func testLeeUnaCopiaDeAndroid() throws {
        let json = """
        {
          "app": "contacto", "formato": 1, "exportado": "2025-03-12T10:15:30",
          "ajustes": { "avisos_activos": false, "pausa_hasta": "2025-04-01", "hora_desde": 8, "hora_hasta": 22,
                       "horas_posponer": 3, "medio_por_defecto": "WHATSAPP", "tema": "OSCURO", "vista": "BURBUJAS" },
          "personas": [
            { "nombre": "Ana", "telefono": "+34 600 12 34 56", "frecuencia_dias": 14, "ultimo_contacto": "2025-03-01",
              "medio": "LLAMADA", "notas": "hijos: Leo", "cumpleanos": "--03-12", "cumpleanos_manual": true,
              "circulo": "Familia", "pausado_hasta": "2025-03-20T09:00:00" },
            { "nombre": "", "telefono": "" }
          ]
        }
        """
        let leido = try CopiaSeguridad.leer(datos(json))
        XCTAssertEqual(leido.personas.count, 1, "la fila vacia se descarta")
        let c = CopiaSeguridad.contacto(leido.personas[0])
        XCTAssertEqual(c.nombre, "Ana")
        XCTAssertEqual(c.frecuenciaDias, 14)
        XCTAssertEqual(c.medio, .MARCADOR, "llamar directamente no existe en iOS")
        XCTAssertEqual(c.cumpleanos?.mes, 3)
        XCTAssertEqual(c.cumpleanos?.dia, 12)
        XCTAssertTrue(c.cumpleanosManual)
        XCTAssertEqual(c.circulo, "Familia")
        XCTAssertNotNil(c.pausadoHasta)

        let a = leido.aplicar(a: Ajustes())
        XCTAssertFalse(a.avisosActivos)
        XCTAssertNotNil(a.pausaHasta)
        XCTAssertEqual(a.horaDesde, 8)
        XCTAssertEqual(a.horaHasta, 22)
        XCTAssertEqual(a.horasPosponer, 3)
        XCTAssertEqual(a.medioPorDefecto, .WHATSAPP)
        XCTAssertEqual(a.tema, .OSCURO)
        XCTAssertEqual(a.vista, .BURBUJAS)
    }

    func testLosCamposQueFaltanTomanSuValorPorDefecto() throws {
        let leido = try CopiaSeguridad.leer(datos(#"{"app":"contacto","formato":1,"personas":[{"nombre":"Luis","telefono":"1"}]}"#))
        let c = CopiaSeguridad.contacto(leido.personas[0])
        XCTAssertEqual(c.frecuenciaDias, 7)
        XCTAssertEqual(c.notas, "")
        XCTAssertNil(c.circulo)
        XCTAssertEqual(leido.aplicar(a: Ajustes()), Ajustes(), "sin ajustes en el archivo, los de ahora")
    }

    func testIgnoraLoQueSobra() throws {
        XCTAssertNoThrow(try CopiaSeguridad.leer(datos(#"{"app":"contacto","formato":1,"nuevo":true,"personas":[{"nombre":"A","telefono":"1","raro":3}]}"#)))
    }

    func testRechazaLoQueNoEsUnaCopiaDeLaApp() {
        XCTAssertThrowsError(try CopiaSeguridad.leer(datos("no es json")))
        XCTAssertThrowsError(try CopiaSeguridad.leer(datos(#"{"app":"otra","formato":1}"#)))
        XCTAssertThrowsError(try CopiaSeguridad.leer(datos(#"{"app":"contacto","formato":99}"#)), "de una version mas nueva")
    }

    func testEscribirYLeerConservaLaGente() throws {
        let c = Contacto(nombre: "Eva", telefono: "600 000 000", frecuenciaDias: 30, medio: .TELEGRAM, notas: "hola",
                         cumpleanos: (2, 29), cumpleanosManual: true, circulo: "Trabajo")
        let archivo = try CopiaSeguridad.escribir([c], ajustes: Ajustes())
        let texto = String(decoding: archivo, as: UTF8.self)
        XCTAssertTrue(texto.contains("\"frecuencia_dias\""), "las claves son las de Android")
        XCTAssertTrue(texto.contains("--02-29"))
        let vuelta = CopiaSeguridad.contacto(try CopiaSeguridad.leer(archivo).personas[0])
        XCTAssertEqual(vuelta.nombre, "Eva")
        XCTAssertEqual(vuelta.frecuenciaDias, 30)
        XCTAssertEqual(vuelta.medio, .TELEGRAM)
        XCTAssertEqual(vuelta.cumpleanos?.dia, 29)
        XCTAssertEqual(vuelta.circulo, "Trabajo")
        XCTAssertEqual(Fechas.iso(dia: vuelta.ultimoContacto), Fechas.iso(dia: c.ultimoContacto))
    }

    func testMismoTelefonoIgnoraEspaciosYGuiones() {
        XCTAssertTrue(CopiaSeguridad.mismoTelefono("+34 600-12-34-56", "+34600123456"))
        XCTAssertFalse(CopiaSeguridad.mismoTelefono("+34600123456", "600123456"))
        XCTAssertFalse(CopiaSeguridad.mismoTelefono("", ""))
    }
}
