import Foundation
import SwiftData
import SwiftUI

/// Lo que dejan los avisos y los atajos para que la interfaz lo atienda cuando este lista.
@MainActor
final class Router: ObservableObject {
    enum Pendiente: Equatable {
        case ficha(UUID)
        case contactar(UUID)
    }

    @Published var pendiente: Pendiente?
}

/// Lo que comparten la interfaz y los avisos: la base de datos, los ajustes y el enrutado.
@MainActor
final class Entorno {
    static let shared = Entorno()

    let contenedor: ModelContainer
    let ajustes: AlmacenAjustes
    let router = Router()

    /// Las pruebas unitarias arrancan la app como anfitriona: sin tocar los datos ni los avisos de verdad.
    nonisolated static let enPruebas = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    private init() {
        let enMemoria = Entorno.enPruebas
        let configuracion = ModelConfiguration(isStoredInMemoryOnly: enMemoria)
        if let c = try? ModelContainer(for: Contacto.self, configurations: configuracion) {
            contenedor = c
        } else {
            // Un almacen que no abre no debe dejar la app en blanco: en memoria, al menos se ve.
            contenedor = try! ModelContainer(for: Contacto.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        }
        ajustes = AlmacenAjustes(defaults: enMemoria ? UserDefaults(suiteName: "pruebas") ?? .standard : .standard)
    }

    var contexto: ModelContext { contenedor.mainContext }

    func todos() -> [Contacto] {
        (try? contexto.fetch(FetchDescriptor<Contacto>())) ?? []
    }

    func contacto(uuid: UUID) -> Contacto? {
        todos().first { $0.uuid == uuid }
    }

    /// Guarda y rehace los avisos: despues de cualquier cambio en la gente o en los ajustes de aviso.
    func cambioGuardado() {
        try? contexto.save()
        Avisos.shared.reprogramar()
    }

    /// "Mas tarde" del aviso: vuelve a sonar dentro de las horas elegidas en Ajustes.
    func posponer(uuid: UUID) {
        guard let c = contacto(uuid: uuid) else { return }
        let horas = ajustes.valores.horasPosponer
        c.pospuestoHasta = Date().addingTimeInterval(Double(horas) * 3600)
        c.descartes += 1
        cambioGuardado()
    }

    func eliminar(_ c: Contacto) {
        contexto.delete(c)
        cambioGuardado()
    }

    struct ResultadoImportacion { var nuevas: Int; var actualizadas: Int }

    /// Anade la gente del archivo. Quien ya esta aqui con el mismo numero se
    /// actualiza con lo del archivo; el resto se da de alta. Los ajustes del
    /// archivo pisan los de aqui.
    func importar(_ leido: CopiaSeguridad.Leido) -> ResultadoImportacion {
        var presentes = todos()
        var nuevas = 0, actualizadas = 0
        for p in leido.personas {
            let c = CopiaSeguridad.contacto(p)
            if let ya = presentes.first(where: { CopiaSeguridad.mismoTelefono($0.telefono, p.telefono) }) {
                ya.nombre = c.nombre
                ya.frecuenciaDias = c.frecuenciaDias
                ya.ultimoContacto = max(ya.ultimoContacto, c.ultimoContacto)
                ya.medioNombre = c.medioNombre
                ya.notas = c.notas
                ya.cumpleMes = c.cumpleMes
                ya.cumpleDia = c.cumpleDia
                ya.cumpleanosManual = c.cumpleanosManual
                ya.circulo = c.circulo
                ya.pausadoHasta = c.pausadoHasta
                actualizadas += 1
            } else {
                contexto.insert(c)
                // Dos filas del archivo con el mismo numero: la segunda actualiza a la primera.
                presentes.append(c)
                nuevas += 1
            }
        }
        var nuevosAjustes = leido.aplicar(a: ajustes.valores)
        nuevosAjustes.bienvenidaHecha = true
        ajustes.valores = nuevosAjustes
        cambioGuardado()
        return ResultadoImportacion(nuevas: nuevas, actualizadas: actualizadas)
    }
}
