import Foundation
import SwiftData

/// La unica tabla de la app: a quien hay que llamar, cada cuanto y cuando fue
/// la ultima vez, por donde se prefiere contactar y lo que se sabe de la persona.
///
/// Pausar (`pausadoHasta`) no toca `ultimoContacto`: la urgencia sigue creciendo
/// y la burbuja se sigue haciendo grande. Si el cumpleanos se puso (o se quito)
/// a mano, `cumpleanosManual`.
@Model
final class Contacto {
    var uuid: UUID = UUID()
    var nombre: String
    var telefono: String
    var frecuenciaDias: Int
    var ultimoContacto: Date
    var medioNombre: String
    var descartes: Int = 0
    var pospuestoHasta: Date?
    var pausadoHasta: Date?
    var notas: String = ""
    /// 0 = sin cumpleanos.
    var cumpleMes: Int = 0
    var cumpleDia: Int = 0
    var cumpleanosManual: Bool = false
    var circulo: String?
    @Attribute(.externalStorage) var foto: Data?
    var creado: Date = Date()

    init(
        nombre: String,
        telefono: String,
        frecuenciaDias: Int = 7,
        ultimoContacto: Date = Fechas.inicioDia(Date()),
        medio: MedioContacto = .MARCADOR,
        notas: String = "",
        cumpleanos: (mes: Int, dia: Int)? = nil,
        cumpleanosManual: Bool = false,
        circulo: String? = nil,
        pausadoHasta: Date? = nil,
        foto: Data? = nil
    ) {
        self.nombre = nombre
        self.telefono = telefono
        self.frecuenciaDias = max(frecuenciaDias, 1)
        self.ultimoContacto = Fechas.inicioDia(ultimoContacto)
        self.medioNombre = medio.rawValue
        self.notas = notas
        self.cumpleMes = cumpleanos?.mes ?? 0
        self.cumpleDia = cumpleanos?.dia ?? 0
        self.cumpleanosManual = cumpleanosManual
        self.circulo = circulo
        self.pausadoHasta = pausadoHasta
        self.foto = foto
    }

    var medio: MedioContacto {
        get { MedioContacto.desde(medioNombre) }
        set { medioNombre = newValue.rawValue }
    }

    var cumpleanos: (mes: Int, dia: Int)? {
        get { cumpleMes > 0 ? (cumpleMes, cumpleDia) : nil }
        set { cumpleMes = newValue?.mes ?? 0; cumpleDia = newValue?.dia ?? 0 }
    }

    func esCumpleanos(hoy: Date = Date()) -> Bool { Plazos.esCumpleanos(mes: cumpleMes, dia: cumpleDia, hoy: hoy) }
    func diasHastaCumpleanos(hoy: Date = Date()) -> Int? { Plazos.diasHastaCumpleanos(mes: cumpleMes, dia: cumpleDia, hoy: hoy) }
    func proximoAviso() -> Date { Plazos.proximoAviso(ultimo: ultimoContacto, frecuencia: frecuenciaDias) }
    func tocaLlamar(hoy: Date = Date()) -> Bool { Plazos.tocaLlamar(ultimo: ultimoContacto, frecuencia: frecuenciaDias, hoy: hoy) }
    func urgencia(hoy: Date = Date()) -> Double { Plazos.urgencia(ultimo: ultimoContacto, frecuencia: frecuenciaDias, hoy: hoy) }
    func pausado(ahora: Date = Date()) -> Bool { (pausadoHasta ?? .distantPast) > ahora }

    /// La inicial para el avatar sin foto.
    var inicial: String { nombre.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? "?" }

    /// Pone el ultimo contacto a hoy y borra lo que el aviso de antes ya no dice.
    func llamadoHoy() {
        ultimoContacto = Fechas.inicioDia(Date())
        pospuestoHasta = nil
        descartes = 0
    }
}

extension Sequence where Element == Contacto {
    /// El orden de Personas, en lista y en burbujas: primero a quien mas le toca.
    func porUrgencia(hoy: Date = Date()) -> [Contacto] {
        sorted {
            let a = $0.urgencia(hoy: hoy), b = $1.urgencia(hoy: hoy)
            if a != b { return a > b }
            return $0.nombre.lowercased() < $1.nombre.lowercased()
        }
    }

    /// El orden de los anillos de las orbitas: primero quien va mas a menudo. No cambia con los dias.
    func porFrecuencia() -> [Contacto] {
        sorted {
            if $0.frecuenciaDias != $1.frecuenciaDias { return $0.frecuenciaDias < $1.frecuenciaDias }
            let a = $0.nombre.lowercased(), b = $1.nombre.lowercased()
            if a != b { return a < b }
            return $0.creado < $1.creado
        }
    }
}
