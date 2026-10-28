import Foundation

/// La cuenta de cuando toca hablar con alguien, sin base de datos ni pantalla:
/// la misma de `Contacto.kt` y lo que prueban los tests.
enum Plazos {
    /// Dia a partir del cual toca avisar.
    static func proximoAviso(ultimo: Date, frecuencia: Int) -> Date {
        Fechas.sumar(dias: max(frecuencia, 1), a: Fechas.inicioDia(ultimo))
    }

    /// "Hoy es igual o posterior a ultimo contacto + frecuencia".
    static func tocaLlamar(ultimo: Date, frecuencia: Int, hoy: Date = Date()) -> Bool {
        Fechas.inicioDia(hoy) >= proximoAviso(ultimo: ultimo, frecuencia: frecuencia)
    }

    /// Parte de su cadencia ya consumida: 0 recien hablado, 1 toca hoy, mas de 1 va con retraso.
    static func urgencia(ultimo: Date, frecuencia: Int, hoy: Date = Date()) -> Double {
        Double(max(Fechas.dias(de: ultimo, a: hoy), 0)) / Double(max(frecuencia, 1))
    }

    static func esCumpleanos(mes: Int, dia: Int, hoy: Date = Date()) -> Bool {
        guard mes > 0, let fecha = Fechas.fecha(mes: mes, dia: dia, anio: Fechas.calendario.component(.year, from: hoy)) else { return false }
        return Fechas.inicioDia(fecha) == Fechas.inicioDia(hoy)
    }

    /// Dias hasta el proximo cumpleanos (0 hoy); nil si no se sabe.
    static func diasHastaCumpleanos(mes: Int, dia: Int, hoy: Date = Date()) -> Int? {
        guard mes > 0 else { return nil }
        let anio = Fechas.calendario.component(.year, from: hoy)
        guard var siguiente = Fechas.fecha(mes: mes, dia: dia, anio: anio) else { return nil }
        if Fechas.inicioDia(siguiente) < Fechas.inicioDia(hoy) {
            guard let del = Fechas.fecha(mes: mes, dia: dia, anio: anio + 1) else { return nil }
            siguiente = del
        }
        return Fechas.dias(de: hoy, a: siguiente)
    }

    /// Dias de retraso respecto a su fecha (0 si aun no toca).
    static func diasDeRetraso(ultimo: Date, frecuencia: Int, hoy: Date = Date()) -> Int {
        max(Fechas.dias(de: proximoAviso(ultimo: ultimo, frecuencia: frecuencia), a: hoy), 0)
    }
}

/// Cuando suenan los avisos. iOS no deja ejecutar un trabajo cada dia como
/// WorkManager: se programan por adelantado notificaciones locales (como mucho
/// 64 pendientes en total) y se rehacen al abrir la app y con cada cambio.
enum PlanAvisos {
    /// Las proximas `cantidad` horas de aviso de una persona: la primera a la
    /// hora de inicio del horario del dia que le toca (o del siguiente, si hoy
    /// ya pasó) y luego una por dia mientras no se haga caso, como el trabajo
    /// diario de Android. Respeta las pausas (la de la persona y la general)
    /// y "Mas tarde" (que cae dentro del horario).
    static func fechas(
        ultimo: Date,
        frecuencia: Int,
        pospuestoHasta: Date?,
        pausadoHasta: Date?,
        ajustes: Ajustes,
        ahora: Date = Date(),
        cantidad: Int
    ) -> [Date] {
        guard cantidad > 0 else { return [] }
        var minimo = ahora
        if let p = pausadoHasta, p > minimo { minimo = p }
        if !ajustes.avisosActivos {
            // Apagados del todo, o en pausa hasta una fecha: vuelven solos.
            guard let reanuda = ajustes.pausaHasta else { return [] }
            let inicio = Fechas.inicioDia(reanuda)
            if inicio > minimo { minimo = inicio }
        }

        let cal = Fechas.calendario
        func hacia(_ dia: Date) -> Date {
            cal.date(bySettingHour: ajustes.horaDesde, minute: 0, second: 0, of: dia) ?? dia
        }

        var resultado: [Date] = []
        // "Mas tarde": un aviso extra a la hora pedida (ajustada al horario).
        if let p = pospuestoHasta, p > ahora, p >= minimo {
            let a = ajustes.siguienteEnFranja(desde: p)
            resultado.append(a)
        }

        var dia = Fechas.inicioDia(Plazos.proximoAviso(ultimo: ultimo, frecuencia: frecuencia))
        // Los avisos diarios: el primero que sea posterior a ahora y a las pausas.
        var guardia = 0
        while hacia(dia) <= minimo && guardia < 4000 {
            dia = Fechas.sumar(dias: 1, a: dia)
            guardia += 1
        }
        // "Mas tarde" ya cubre su dia: no repetir el mismo dia.
        while resultado.count < cantidad {
            let aviso = hacia(dia)
            if let ultimoAviso = resultado.last, cal.isDate(ultimoAviso, inSameDayAs: aviso) {
                dia = Fechas.sumar(dias: 1, a: dia)
                continue
            }
            resultado.append(aviso)
            dia = Fechas.sumar(dias: 1, a: dia)
        }
        return Array(resultado.prefix(cantidad))
    }
}
