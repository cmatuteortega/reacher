import SwiftData
import SwiftUI

/// La pantalla principal: la gente en orbitas, burbujas o lista (el boton de la
/// barra las recorre y se recuerda la ultima), con el sol en la esquina, el
/// filtro por circulo si alguien lo tiene y el + que abre la agenda.
struct PantallaPersonas: View {
    @Query private var todos: [Contacto]
    @EnvironmentObject private var almacen: AlmacenAjustes

    var alAbrir: (Contacto) -> Void
    var alAjustes: () -> Void

    @State private var filtro: String?
    @State private var eligiendo = false

    private var circulos: [String] {
        Array(Set(todos.compactMap(\.circulo))).sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    /// Los de un circulo; un filtro de un circulo que ya no existe (se vacio) no deja la pantalla en blanco.
    private var visibles: [Contacto] {
        guard let filtro, circulos.contains(filtro) else { return todos.map { $0 } }
        return todos.filter { $0.circulo == filtro }
    }

    private var vista: VistaPersonas { almacen.valores.vista }

    var body: some View {
        ZStack(alignment: .bottom) {
            Paleta.fondo.ignoresSafeArea()
            VStack(spacing: 0) {
                if !circulos.isEmpty { filtroCirculos }
                contenido
            }
            botonAnadir
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    withAnimation(.easeInOut) { almacen.valores.vista = vista.siguiente }
                } label: {
                    Image(systemName: icono(de: vista.siguiente))
                }
                .accessibilityLabel(Text(etiqueta(de: vista.siguiente)))
                Button(action: alAjustes) { Image(systemName: "gearshape") }
                    .accessibilityLabel(Text("ajustes"))
            }
        }
        .altaDesdeAgenda(eligiendo: $eligiendo, medioPorDefecto: almacen.valores.medioPorDefecto)
    }

    @ViewBuilder private var contenido: some View {
        if todos.isEmpty {
            ZStack(alignment: .topLeading) {
                Text("lista_vacia")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Paleta.textoSecundario)
                    .padding(40)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                SolView(radio: 40).position(x: 26, y: 26).allowsHitTesting(false)
            }
        } else {
            switch vista {
            case .ORBITAS:
                VistaOrbitas(personas: visibles, alAbrir: alAbrir, alLlamarHoy: llamadoHoy)
            case .BURBUJAS:
                ZStack(alignment: .topLeading) {
                    VistaBurbujas(personas: visibles, alAbrir: alAbrir, alLlamarHoy: llamadoHoy)
                        .padding(.top, 70)
                    SolView(radio: 40).position(x: 26, y: 26).allowsHitTesting(false)
                }
            case .LISTA:
                ZStack(alignment: .topLeading) {
                    VistaLista(personas: visibles, alAbrir: alAbrir, alLlamarHoy: llamadoHoy)
                        .safeAreaInset(edge: .top) { Color.clear.frame(height: 56) }
                    SolView(radio: 40).position(x: 26, y: 26).allowsHitTesting(false)
                }
            }
        }
    }

    private var filtroCirculos: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(titulo: t("circulo_todos"), activo: filtro == nil) { filtro = nil }
                ForEach(circulos, id: \.self) { c in
                    chip(titulo: c, activo: filtro == c) { filtro = c }
                }
            }
            .padding(.horizontal, 16)
            // A la derecha del sol, que ocupa la esquina.
            .padding(.leading, 70)
        }
        .padding(.vertical, 8)
    }

    private func chip(titulo: String, activo: Bool, accion: @escaping () -> Void) -> some View {
        Button(action: accion) {
            Text(verbatim: titulo)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(activo ? Paleta.principal : Paleta.tarjeta, in: Capsule())
                .foregroundStyle(activo ? Paleta.sobrePrincipal : Paleta.texto)
        }
        .buttonStyle(.plain)
    }

    private var botonAnadir: some View {
        Button { eligiendo = true } label: {
            Image(systemName: "plus")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(Paleta.sobrePrincipal)
                .frame(width: 64, height: 64)
                .background(Paleta.principal, in: Circle())
                .shadow(color: .black.opacity(0.25), radius: 8, y: 4)
        }
        .padding(.bottom, 20)
        .accessibilityLabel(Text("anadir_persona"))
    }

    private func llamadoHoy(_ c: Contacto) {
        c.llamadoHoy()
        Entorno.shared.cambioGuardado()
    }

    private func icono(de v: VistaPersonas) -> String {
        switch v {
        case .ORBITAS: return "atom"
        case .BURBUJAS: return "bubbles.and.sparkles"
        case .LISTA: return "list.bullet"
        }
    }

    private func etiqueta(de v: VistaPersonas) -> LocalizedStringKey {
        switch v {
        case .ORBITAS: return "ver_como_orbitas"
        case .BURBUJAS: return "ver_como_burbujas"
        case .LISTA: return "ver_como_lista"
        }
    }
}
