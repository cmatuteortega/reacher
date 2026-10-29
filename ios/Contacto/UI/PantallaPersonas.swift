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
    /// Sube cuando el atajo del icono pide dar de alta a alguien.
    var peticionAnadir = 0

    @State private var filtro: String?
    @State private var eligiendo = false
    /// El + esta apretado (el sol lo mira) y cuantas veces ha de reir por el.
    @State private var masPulsado = false
    @State private var risas = 0

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
        // El sol va por encima de todo, metido en la esquina de la pantalla.
        .overlay(alignment: .topLeading) { SolEsquinaView(cuantos: todos.count, masPulsado: masPulsado, risas: risas) }
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
        .onChange(of: peticionAnadir) { eligiendo = true }
    }

    @ViewBuilder private var contenido: some View {
        if todos.isEmpty {
            Text("lista_vacia")
                .multilineTextAlignment(.center)
                .foregroundStyle(Paleta.textoSecundario)
                .padding(40)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            switch vista {
            case .ORBITAS:
                VistaOrbitas(personas: visibles, alAbrir: alAbrir, alLlamarHoy: llamadoHoy)
            case .BURBUJAS:
                DejandoSitioAlSol { arriba in
                    VistaBurbujas(personas: visibles, alAbrir: alAbrir, alLlamarHoy: llamadoHoy)
                        .padding(.top, arriba)
                }
            case .LISTA:
                DejandoSitioAlSol { arriba in
                    VistaLista(personas: visibles, alAbrir: alAbrir, alLlamarHoy: llamadoHoy)
                        .safeAreaInset(edge: .top) { Color.clear.frame(height: arriba) }
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
            .padding(.leading, SolEsquinaView.radioBase(ancho: UIScreen.main.bounds.width) * 0.6)
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
        Button {
            // Que se le vea reir antes de que se abra la agenda.
            risas += 1
            Task {
                try? await Task.sleep(for: .milliseconds(220))
                eligiendo = true
            }
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(Paleta.sobrePrincipal)
                .frame(width: 64, height: 64)
                .background(Paleta.principal, in: Circle())
                .shadow(color: .black.opacity(0.25), radius: 8, y: 4)
        }
        .buttonStyle(BotonMas(alPulsar: { masPulsado = $0 }))
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

/// Da a su contenido lo que el sol de la esquina baja por debajo de su borde de arriba.
private struct DejandoSitioAlSol<Contenido: View>: View {
    @ViewBuilder var contenido: (CGFloat) -> Contenido

    var body: some View {
        GeometryReader { geo in
            let radio = SolEsquinaView.radioLibre(ancho: geo.size.width)
            contenido(max(radio - geo.frame(in: .global).minY, 0))
        }
    }
}

/// El +: se encoge un poco al apretarlo y avisa al sol, que lo mira.
private struct BotonMas: ButtonStyle {
    var alPulsar: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.82 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.5), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { alPulsar(configuration.isPressed) }
    }
}
