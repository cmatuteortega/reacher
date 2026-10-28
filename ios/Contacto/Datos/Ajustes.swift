import Foundation

enum TemaElegido: String, CaseIterable, Codable {
    case SISTEMA, CLARO, OSCURO

    var etiqueta: String {
        switch self {
        case .SISTEMA: return t("tema_sistema")
        case .CLARO: return t("tema_claro")
        case .OSCURO: return t("tema_oscuro")
        }
    }
}

/// Como se ensena la gente en Personas; el boton de la barra las recorre en este orden.
enum VistaPersonas: String, CaseIterable, Codable {
    case ORBITAS, BURBUJAS, LISTA

    var siguiente: VistaPersonas {
        let todas = VistaPersonas.allCases
        return todas[(todas.firstIndex(of: self)! + 1) % todas.count]
    }
}

/// Preferencias de la app, las mismas que en Android salvo las de depuracion y telemetria.
/// Los avisos se apagan del todo (`avisosActivos` false y sin fecha) o durante
/// unos dias (false hasta `pausaHasta`): pasada esa fecha vuelven solos.
struct Ajustes: Codable, Equatable {
    var avisosActivos = true
    /// Primer dia en que vuelven los avisos; nil = apagados hasta reactivarlos.
    var pausaHasta: Date?
    /// Franja en que se puede avisar: desde la hora `horaDesde` hasta la `horaHasta` (sin incluir).
    var horaDesde = 9
    var horaHasta = 21
    /// Cuanto tarda en volver un aviso al pulsar "Mas tarde".
    var horasPosponer = 2
    var medioPorDefecto: MedioContacto = .MARCADOR
    var tema: TemaElegido = .SISTEMA
    var vista: VistaPersonas = .ORBITAS
    var bienvenidaHecha = false

    func avisosEncendidos(hoy: Date = Date()) -> Bool {
        if avisosActivos { return true }
        if let pausaHasta { return Fechas.inicioDia(hoy) >= Fechas.inicioDia(pausaHasta) }
        return false
    }

    /// Desde == hasta es todo el dia; desde > hasta cruza la medianoche (p. ej. 20 a 8).
    func dentroDeFranja(hora: Int) -> Bool {
        if horaDesde == horaHasta { return true }
        if horaDesde < horaHasta { return hora >= horaDesde && hora < horaHasta }
        return hora >= horaDesde || hora < horaHasta
    }

    /// El primer momento a partir de `fecha` que cae dentro del horario: ella misma, o la hora de inicio siguiente.
    func siguienteEnFranja(desde fecha: Date) -> Date {
        let cal = Fechas.calendario
        if dentroDeFranja(hora: cal.component(.hour, from: fecha)) { return fecha }
        var inicio = cal.date(bySettingHour: horaDesde, minute: 0, second: 0, of: fecha) ?? fecha
        if inicio <= fecha { inicio = Fechas.sumar(dias: 1, a: inicio) }
        return inicio
    }
}
