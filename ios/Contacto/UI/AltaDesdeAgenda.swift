import SwiftUI

/// Elegir a alguien de la agenda y darlo de alta: el selector del sistema y, al
/// elegir, la ficha de alta (frecuencia, forma de contacto, circulo...) con
/// *Anadir*. Se usa en Personas y en el ultimo paso de la bienvenida.
struct AltaDesdeAgenda: ViewModifier {
    @Binding var eligiendo: Bool
    let medioPorDefecto: MedioContacto
    var alAnadir: (Contacto) -> Void = { _ in }

    @State private var elegida: PersonaElegida?
    @State private var borrador: Contacto?

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: $eligiendo, onDismiss: abrirAlta) {
                SelectorAgenda(
                    alElegir: { persona in
                        elegida = persona
                        eligiendo = false
                    },
                    alCancelar: { eligiendo = false }
                )
                .ignoresSafeArea()
            }
            .sheet(item: $borrador) { contacto in
                NavigationStack {
                    FormularioPersona(contacto: contacto, alta: true, alTerminar: {
                        let anadido = contacto.modelContext != nil
                        borrador = nil
                        if anadido { alAnadir(contacto) }
                    })
                }
            }
    }

    /// El selector ya se ha ido: ahora si se puede abrir la ficha de alta (dos hojas a la vez no se llevan bien).
    private func abrirAlta() {
        guard let persona = elegida else { return }
        elegida = nil
        borrador = Contacto(
            nombre: persona.nombre,
            telefono: persona.telefono,
            medio: medioPorDefecto,
            cumpleanos: persona.cumpleanos,
            foto: persona.foto
        )
    }
}

extension View {
    func altaDesdeAgenda(eligiendo: Binding<Bool>, medioPorDefecto: MedioContacto, alAnadir: @escaping (Contacto) -> Void = { _ in }) -> some View {
        modifier(AltaDesdeAgenda(eligiendo: eligiendo, medioPorDefecto: medioPorDefecto, alAnadir: alAnadir))
    }
}
