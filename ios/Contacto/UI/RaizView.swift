import SwiftData
import SwiftUI

enum Destino: Hashable {
    case ajustes
}

/// La raiz: la bienvenida la primera vez y luego Personas con su pila de
/// navegacion (ficha y ajustes). En pantalla ancha (iPad, iPhone grande de lado) la ficha no
/// se apila: queda en un panel a la derecha, junto a Personas, como en Android.
/// Atiende lo que dejan los avisos: tocar uno abre la forma de contacto; *Mas opciones*
/// abre la ficha.
struct RaizView: View {
    @EnvironmentObject private var almacen: AlmacenAjustes
    @ObservedObject var router: Router
    @Environment(\.scenePhase) private var fase
    @State private var ruta = NavigationPath()
    /// Cada atajo de "Anadir persona" suma uno: Personas abre la agenda al verlo cambiar.
    @State private var peticionesAnadir = 0
    /// Pantalla ancha: la ficha elegida se ve en el panel de la derecha.
    @State private var enAncho = false
    @State private var seleccion: Contacto?

    /// Desde aqui hay sitio para Personas y la ficha lado a lado.
    private static let anchoDividido: CGFloat = 700

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
        GeometryReader { geo in
            Group {
                if geo.size.width >= Self.anchoDividido {
                    HStack(spacing: 0) {
                        pila
                            .frame(width: min(max(geo.size.width * 0.4, 340), 460))
                        Divider()
                        panel
                    }
                } else {
                    pila
                }
            }
            .onChange(of: geo.size.width >= Self.anchoDividido, initial: true) { cambioDeAncho(geo.size.width >= Self.anchoDividido) }
        }
    }

    private var pila: some View {
        NavigationStack(path: $ruta) {
            PantallaPersonas(
                alAbrir: abrir,
                alAjustes: {
                    seleccion = nil
                    ruta.append(Destino.ajustes)
                },
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

    /// La ficha elegida, o una invitacion a elegir.
    @ViewBuilder private var panel: some View {
        if let contacto = seleccion {
            NavigationStack {
                PantallaFicha(
                    contacto: contacto,
                    alEliminar: { eliminar(contacto) },
                    alCerrar: { seleccion = nil }
                )
            }
            .id(contacto.uuid)
        } else {
            ZStack {
                Paleta.fondo.ignoresSafeArea()
                Text("elige_a_alguien")
                    .foregroundStyle(Paleta.textoSecundario)
                    .multilineTextAlignment(.center)
                    .padding(40)
            }
        }
    }

    private func abrir(_ contacto: Contacto) {
        if enAncho {
            seleccion = contacto
        } else {
            ruta.append(contacto)
        }
    }

    /// Al girar o cambiar el tamano de la ventana, la ficha abierta pasa del panel a la pila o al reves.
    private func cambioDeAncho(_ ancho: Bool) {
        guard ancho != enAncho else { return }
        enAncho = ancho
        if ancho {
            ruta = NavigationPath()
        } else if let contacto = seleccion {
            seleccion = nil
            ruta = NavigationPath([contacto])
        }
    }

    /// Se sale de la ficha antes de borrar: una ficha viva de algo borrado no debe leer sus campos.
    private func eliminar(_ contacto: Contacto) {
        if seleccion?.uuid == contacto.uuid {
            seleccion = nil
        } else if !ruta.isEmpty {
            ruta.removeLast()
        }
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
            guard let c = Entorno.shared.contacto(uuid: id) else { return }
            if enAncho {
                ruta = NavigationPath()
                seleccion = c
            } else {
                ruta = NavigationPath([c])
            }
        case .anadir:
            seleccion = nil
            ruta = NavigationPath()
            peticionesAnadir += 1
        case .ajustes:
            seleccion = nil
            ruta = NavigationPath([Destino.ajustes])
        case .contactar(let id):
            guard let c = Entorno.shared.contacto(uuid: id) else { return }
            Task { await Contactar.abrir(telefono: c.telefono, medio: c.medio) }
        }
    }
}
