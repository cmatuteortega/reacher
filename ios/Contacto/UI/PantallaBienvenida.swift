import SwiftData
import SwiftUI

/// Solo la primera vez, en tres pasos sobre **tu sistema**: el sol eres tu y la
/// gente que anades gira a tu alrededor. 1) *Tu eres el sol* y el permiso de
/// avisos; 2) *Cuando y como*: horario y forma de contacto; 3) *Crea tu sistema*:
/// elegir de la agenda. Al acabar se recuerda en `Ajustes.bienvenidaHecha`.
struct PantallaBienvenida: View {
    @EnvironmentObject private var almacen: AlmacenAjustes
    @Query private var gente: [Contacto]

    @State private var paso = 0
    @State private var eligiendo = false

    var body: some View {
        ZStack {
            Paleta.fondo.ignoresSafeArea()
            VStack(spacing: 0) {
                puntos.padding(.top, 16)
                Spacer(minLength: 8)
                ilustracion
                    .frame(maxHeight: 320)
                    .animation(.spring(response: 0.6, dampingFraction: 0.8), value: paso)
                Spacer(minLength: 8)
                texto
                botones.padding(.vertical, 24)
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: 560)
        }
        .altaDesdeAgenda(eligiendo: $eligiendo, medioPorDefecto: almacen.valores.medioPorDefecto)
    }

    private var puntos: some View {
        HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { i in
                Capsule()
                    .fill(i <= paso ? Paleta.acento : Paleta.borde)
                    .frame(width: i == paso ? 24 : 8, height: 8)
                    .animation(.spring(response: 0.4, dampingFraction: 0.7), value: paso)
            }
        }
        .accessibilityHidden(true)
    }

    @ViewBuilder private var ilustracion: some View {
        switch paso {
        case 0:
            SolView(radio: 80)
        case 1:
            VStack(spacing: 24) {
                SolView(radio: 44)
                horas
            }
        default:
            if gente.isEmpty {
                SolView(radio: 80)
            } else {
                VistaOrbitas(personas: Array(gente), alAbrir: { _ in }, alLlamarHoy: { _ in })
                    .frame(height: 300)
                    .allowsHitTesting(false)
            }
        }
    }

    private var horas: some View {
        Tarjeta {
            VStack(spacing: 12) {
                DialFranja(desde: almacen.valores.horaDesde, hasta: almacen.valores.horaHasta) { desde, hasta in
                    almacen.valores.horaDesde = desde
                    almacen.valores.horaHasta = hasta
                }
                .frame(maxWidth: 280)
                Picker(selection: $almacen.valores.medioPorDefecto) {
                    ForEach(MedioContacto.elegibles, id: \.self) { m in Text(verbatim: m.etiqueta).tag(m) }
                } label: { Text("al_tocar_aviso") }
            }
        }
    }

    private var texto: some View {
        VStack(spacing: 12) {
            switch paso {
            case 0:
                // La marca: el nombre (igual en todos los idiomas) y su lema.
                Text(verbatim: "\(t("app_name")) · \(t("subtitulo"))")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Paleta.acentoLegible)
                Text("bienvenida_sol_titulo").font(.largeTitle.weight(.bold))
                Text("bienvenida_sol_texto").foregroundStyle(Paleta.textoSecundario)
            case 1:
                Text("cuando_y_como").font(.largeTitle.weight(.bold))
                Text(verbatim: resumenHorario).foregroundStyle(Paleta.textoSecundario)
            default:
                Text(gente.isEmpty ? LocalizedStringKey("crea_tu_sistema") : (gente.count == 1 ? LocalizedStringKey("alguien_en_orbita") : LocalizedStringKey("sistema_crece")))
                    .font(.largeTitle.weight(.bold))
                Text(gente.isEmpty ? LocalizedStringKey("elige_agenda_texto") : LocalizedStringKey("anade_mas_texto"))
                    .foregroundStyle(Paleta.textoSecundario)
            }
        }
        .multilineTextAlignment(.center)
        .foregroundStyle(Paleta.texto)
    }

    private var resumenHorario: String {
        let v = almacen.valores
        if v.horaDesde == v.horaHasta { return t("horario_todo_el_dia") }
        return t("horario_de_a", Formato.hora(v.horaDesde), Formato.hora(v.horaHasta))
    }

    @ViewBuilder private var botones: some View {
        VStack(spacing: 12) {
            switch paso {
            case 0:
                Button("empezar") {
                    Task {
                        // Con permiso, a "Cuando y como"; sin el, directo a anadir gente.
                        let permitido = await Avisos.shared.pedirPermiso()
                        withAnimation { paso = permitido ? 1 : 2 }
                    }
                }
                .buttonStyle(EstiloBotonPrincipal())
            case 1:
                Button("continuar") { withAnimation { paso = 2 } }
                    .buttonStyle(EstiloBotonPrincipal())
            default:
                Button { eligiendo = true } label: {
                    Text(gente.isEmpty ? LocalizedStringKey("elegir_de_la_agenda") : LocalizedStringKey("anadir_otra_persona"))
                }
                .buttonStyle(gente.isEmpty ? AnyButtonStyle(EstiloBotonPrincipal()) : AnyButtonStyle(EstiloBotonSecundario()))
                Button { terminar() } label: {
                    Text(gente.isEmpty ? LocalizedStringKey("ahora_no") : LocalizedStringKey("continuar"))
                }
                .buttonStyle(gente.isEmpty ? AnyButtonStyle(EstiloBotonSecundario()) : AnyButtonStyle(EstiloBotonPrincipal()))
            }
        }
    }

    private func terminar() {
        almacen.valores.bienvenidaHecha = true
        Avisos.shared.reprogramar()
    }
}

/// Para elegir entre dos estilos de boton segun el estado.
struct AnyButtonStyle: ButtonStyle {
    private let hacer: (Configuration) -> AnyView

    init<S: ButtonStyle>(_ estilo: S) {
        hacer = { AnyView(estilo.makeBody(configuration: $0)) }
    }

    func makeBody(configuration: Configuration) -> some View { hacer(configuration) }
}
