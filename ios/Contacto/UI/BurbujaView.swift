import SwiftUI

/// Una persona como burbuja: esfera con cara (o su foto de la agenda, que la llena
/// entera), anillo teja/naranja y halo si ya le toca. El nombre debajo si cabe.
struct BurbujaView: View {
    let contacto: Contacto
    let radio: CGFloat
    var mostrarNombre = true
    var urgencia: Double

    private var toca: Bool { urgencia >= 1 }

    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                if toca {
                    Circle()
                        .fill(Paleta.acento.opacity(0.22))
                        .frame(width: radio * 2.5, height: radio * 2.5)
                }
                ZStack {
                    Circle().fill(Paleta.burbuja)
                    if let datos = contacto.foto, let imagen = UIImage(data: datos) {
                        Image(uiImage: imagen).resizable().scaledToFill()
                    } else {
                        CaraView(urgencia: urgencia, semilla: Double(abs(contacto.uuid.hashValue % 97)))
                    }
                }
                .frame(width: radio * 2, height: radio * 2)
                .clipShape(Circle())
                .overlay(Circle().stroke(toca ? Paleta.acento : Paleta.malva.opacity(0.6), lineWidth: toca ? 3 : 2))
            }
            .frame(width: radio * 2, height: radio * 2)
            if mostrarNombre {
                Text(verbatim: contacto.nombre)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Paleta.texto)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .frame(width: max(radio * 2 + 24, 60))
            }
        }
        .contentShape(Circle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(etiquetaAccesible)
        .accessibilityAddTraits(.isButton)
    }

    private var etiquetaAccesible: Text {
        if toca { return Text(verbatim: t("burbuja_toca", contacto.nombre)) }
        let porcentaje = Int((urgencia * 100).rounded())
        return Text(verbatim: String(format: String(localized: "burbuja_plazo"), contacto.nombre, porcentaje))
    }
}
