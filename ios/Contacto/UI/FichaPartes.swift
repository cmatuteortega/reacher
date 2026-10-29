import SwiftUI

/// Foto grande dentro de un anillo que se va llenando segun pasa el tiempo desde el ultimo
/// contacto: lleno toca llamar, y en teja si ya se paso. Sigue a la urgencia mientras cambia.
/// Es `Cabecera` de `PantallaFicha.kt`.
struct AnilloUrgencia<Contenido: View>: View {
    let urgencia: Double
    @ViewBuilder var contenido: Contenido

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    @State private var lleno = 0.0

    private var meta: Double { min(max(urgencia, 0), 1) }

    var body: some View {
        ZStack {
            Circle().stroke(Paleta.borde.opacity(0.4), lineWidth: 8)
            Circle()
                .trim(from: 0, to: lleno)
                .stroke(urgencia >= 1 ? Paleta.acentoLegible : Paleta.principal, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                .rotationEffect(.degrees(-90))
            contenido
        }
        .frame(width: 148, height: 148)
        .onAppear { ir(a: meta) }
        .onChange(of: urgencia) { ir(a: meta) }
        .accessibilityElement(children: .combine)
    }

    private func ir(a valor: Double) {
        if reducirMovimiento {
            lleno = valor
        } else {
            withAnimation(.spring(response: 1.0, dampingFraction: 0.6)) { lleno = valor }
        }
    }
}

/// Boton que hay que mantener: se va llenando con un tic en cada cuarto y al completarse confirma
/// con una vibracion firme. Soltar antes lo vacia. Evita anotar una llamada por un roce; con
/// VoiceOver basta un toque. `BotonMantener` de `PantallaFicha.kt`.
struct BotonMantener: View {
    let texto: String
    let icono: String
    let habilitado: Bool
    let accesible: String
    let alConfirmar: () -> Void

    @GestureState private var dedo = false
    @State private var progreso: CGFloat = 0
    @State private var llenando: Task<Void, Never>?

    private static let pasos = 28 // unos 700 ms

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule().fill(habilitado ? Paleta.principal.opacity(0.14) : Paleta.borde.opacity(0.25))
            GeometryReader { geo in
                Rectangle()
                    .fill(Paleta.principal.opacity(0.35))
                    .frame(width: geo.size.width * progreso)
            }
            .clipShape(Capsule())
            Label(texto, systemImage: icono)
                .font(.body.weight(.semibold))
                .foregroundStyle(habilitado ? Paleta.acentoLegible : Paleta.textoSecundario)
                .frame(maxWidth: .infinity)
        }
        .frame(height: 60)
        .scaleEffect(1 - 0.04 * progreso)
        .contentShape(Capsule())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($dedo) { _, estado, _ in estado = true }
        )
        .onChange(of: dedo) {
            if dedo { empezar() } else { soltar() }
        }
        .onChange(of: habilitado) {
            llenando?.cancel()
            llenando = nil
            progreso = 0
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: habilitado ? accesible : texto))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { if habilitado { alConfirmar() } }
    }

    private func empezar() {
        guard habilitado, llenando == nil else { return }
        Haptico.toque()
        llenando = Task {
            var cuarto = 0
            for paso in 1...Self.pasos {
                try? await Task.sleep(for: .milliseconds(25))
                if Task.isCancelled { return }
                progreso = CGFloat(paso) / CGFloat(Self.pasos)
                let ahora = paso * 4 / Self.pasos
                if ahora > cuarto {
                    cuarto = ahora
                    if ahora < 4 { Haptico.tic() }
                }
            }
            Haptico.confirmar()
            alConfirmar()
            llenando = nil
        }
    }

    private func soltar() {
        guard let tarea = llenando else { return }
        tarea.cancel()
        llenando = nil
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { progreso = 0 }
    }
}

/// «Guardado» un momento, arriba, cuando algo se guarda solo al cambiarlo. Al volver a entrar a
/// editar no se repite el ultimo. `AvisoGuardado` de `PantallaFicha.kt`.
struct AvisoGuardado: View {
    let cambios: Int

    @State private var visible = false
    @State private var alEntrar: Int

    init(cambios: Int) {
        self.cambios = cambios
        _alEntrar = State(initialValue: cambios)
    }

    var body: some View {
        Label("guardado", systemImage: "checkmark")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Paleta.principal)
            .opacity(visible ? 1 : 0)
            .scaleEffect(visible ? 1 : 0.8)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: visible)
            .accessibilityHidden(!visible)
            .task(id: cambios) {
                guard cambios != alEntrar else { return }
                visible = true
                try? await Task.sleep(for: .milliseconds(1500))
                if !Task.isCancelled { visible = false }
            }
    }
}
