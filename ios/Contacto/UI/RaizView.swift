import SwiftData
import SwiftUI

enum Destino: Hashable {
    case ajustes
}

/// La raiz: la bienvenida la primera vez y luego Personas con su pila de
/// navegacion (ficha y ajustes). Atiende lo que dejan los avisos: tocar uno
/// abre la forma de contacto; *Mas opciones* abre la ficha.
struct RaizView: View {
    @EnvironmentObject private var almacen: AlmacenAjustes
    @ObservedObject var router: Router
    @Environment(\.scenePhase) private var fase
    @State private var ruta = NavigationPath()
    /// Cada atajo de "Anadir persona" suma uno: Personas abre la agenda al verlo cambiar.
    @State private var peticionesAnadir = 0

    var body: some View {
        Group {
            if almacen.valores.bienvenidaHecha {
                principal
            } else {
                PantallaBienvenida()
            }
        }
        .preferredColorScheme(almacen.valores.tema.esquema)
        .tint(Paleta.principal)
        .task {
            Avisos.shared.configurar()
            Avisos.shared.reprogramar()
            atender()
        }
        .onChange(of: fase) {
            if fase == .active {
                // Los dias pasan: lo que toca hoy y los avisos de mañana se rehacen al volver.
                Avisos.shared.reprogramar()
                atender()
            } else if fase == .background {
                // Si no se vuelve a abrir, el sistema renueva los avisos cuando pueda.
                Refresco.programar()
            }
        }
        .onChange(of: router.pendiente) { atender() }
        .onChange(of: almacen.valores.bienvenidaHecha) { atender() }
    }

    private var principal: some View {
        NavigationStack(path: $ruta) {
            PantallaPersonas(
                alAbrir: { ruta.append($0) },
                alAjustes: { ruta.append(Destino.ajustes) },
                peticionAnadir: peticionesAnadir
            )
            .navigationDestination(for: Contacto.self) { contacto in
                PantallaFicha(contacto: contacto, alEliminar: { eliminar(contacto) })
            }
            .navigationDestination(for: Destino.self) { _ in
                PantallaAjustes()
            }
        }
    }

    /// Se sale de la ficha antes de borrar: una ficha viva de algo borrado no debe leer sus campos.
    private func eliminar(_ contacto: Contacto) {
        if !ruta.isEmpty { ruta.removeLast() }
        Task {
            try? await Task.sleep(nanoseconds: 450_000_000)
            Entorno.shared.eliminar(contacto)
        }
    }

    private func atender() {
        guard fase == .active, almacen.valores.bienvenidaHecha, let pendiente = router.pendiente else { return }
        router.pendiente = nil
        switch pendiente {
        case .ficha(let id):
            if let c = Entorno.shared.contacto(uuid: id) { ruta = NavigationPath([c]) }
        case .anadir:
            ruta = NavigationPath()
            peticionesAnadir += 1
        case .ajustes:
            ruta = NavigationPath([Destino.ajustes])
        case .contactar(let id):
            guard let c = Entorno.shared.contacto(uuid: id) else { return }
            Task { await Contactar.abrir(telefono: c.telefono, medio: c.medio) }
        }
    }
}
