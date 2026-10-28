import SwiftUI

/// La lista: foto o inicial, nombre, circulo y cuando toca; una tarta si hoy es su cumpleanos.
struct VistaLista: View {
    let personas: [Contacto]
    var alAbrir: (Contacto) -> Void
    var alLlamarHoy: (Contacto) -> Void

    var body: some View {
        List {
            ForEach(personas.porUrgencia(), id: \.uuid) { c in
                Button { alAbrir(c) } label: { fila(c) }
                    .listRowBackground(Paleta.fondo)
                    .listRowSeparatorTint(Paleta.borde)
                    .swipeActions(edge: .trailing) {
                        Button { alLlamarHoy(c) } label: { Label("he_llamado_hoy", systemImage: "checkmark") }
                            .tint(Paleta.teja)
                    }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .animation(.spring(response: 0.5, dampingFraction: 0.85), value: personas.porUrgencia().map(\.uuid))
    }

    private func fila(_ c: Contacto) -> some View {
        let toca = c.tocaLlamar()
        return HStack(spacing: 14) {
            AvatarView(contacto: c, tamano: 48)
                .overlay(Circle().stroke(toca ? Paleta.acento : .clear, lineWidth: 3))
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: c.nombre).font(.body.weight(.semibold)).foregroundStyle(Paleta.texto)
                if let circulo = c.circulo {
                    Text(verbatim: circulo).font(.footnote).foregroundStyle(Paleta.textoSecundario)
                }
                Text(verbatim: Formato.cuandoToca(c))
                    .font(.footnote)
                    .foregroundStyle(toca ? Paleta.acentoLegible : Paleta.textoSecundario)
            }
            Spacer()
            if c.esCumpleanos() { Image(systemName: "birthday.cake.fill").foregroundStyle(Paleta.acentoLegible) }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(c.nombre), \(Formato.cuandoToca(c))"))
        .accessibilityAction(named: Text("he_llamado_hoy")) { alLlamarHoy(c) }
    }
}
