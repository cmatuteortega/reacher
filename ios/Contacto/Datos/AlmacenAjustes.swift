import Foundation
import SwiftUI

/// Guarda los `Ajustes` en UserDefaults y los ofrece a la interfaz.
final class AlmacenAjustes: ObservableObject {
    private let defaults: UserDefaults
    private let clave = "ajustes"

    @Published var valores: Ajustes {
        didSet { guardar() }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let datos = defaults.data(forKey: "ajustes"), let leidos = try? JSONDecoder().decode(Ajustes.self, from: datos) {
            var v = leidos
            v.horaDesde = min(max(v.horaDesde, 0), 23)
            v.horaHasta = min(max(v.horaHasta, 0), 23)
            v.horasPosponer = min(max(v.horasPosponer, 1), 24)
            valores = v
        } else {
            valores = Ajustes()
        }
    }

    private func guardar() {
        if let datos = try? JSONEncoder().encode(valores) { defaults.set(datos, forKey: clave) }
    }
}
