import Contacts
import ContactsUI
import SwiftUI

/// Lo que se saca de la agenda al elegir un numero.
struct PersonaElegida {
    var nombre: String
    var telefono: String
    var foto: Data?
    var cumpleanos: (mes: Int, dia: Int)?
}

/// El selector de contactos del sistema, filtrado a numeros de telefono. No
/// pide permiso: iOS ensena la agenda en otro proceso y solo entrega lo elegido.
struct SelectorAgenda: UIViewControllerRepresentable {
    var alElegir: (PersonaElegida) -> Void
    var alCancelar: () -> Void

    func makeUIViewController(context: Context) -> CNContactPickerViewController {
        let selector = CNContactPickerViewController()
        selector.delegate = context.coordinator
        selector.displayedPropertyKeys = [CNContactPhoneNumbersKey]
        selector.predicateForEnablingContact = NSPredicate(format: "phoneNumbers.@count > 0")
        return selector
    }

    func updateUIViewController(_ uiViewController: CNContactPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinador { Coordinador(self) }

    final class Coordinador: NSObject, CNContactPickerDelegate {
        let padre: SelectorAgenda
        init(_ padre: SelectorAgenda) { self.padre = padre }

        // Solo se implementa la eleccion de una propiedad (un numero): asi iOS deja
        // elegir cual cuando la persona tiene varios.
        func contactPicker(_ picker: CNContactPickerViewController, didSelect contactProperty: CNContactProperty) {
            guard let numero = contactProperty.value as? CNPhoneNumber else { return }
            let contacto = contactProperty.contact
            var nombre = CNContactFormatter.string(from: contacto, style: .fullName) ?? ""
            if nombre.isEmpty, contacto.isKeyAvailable(CNContactOrganizationNameKey) { nombre = contacto.organizationName }
            var cumple: (mes: Int, dia: Int)?
            if contacto.isKeyAvailable(CNContactBirthdayKey), let b = contacto.birthday, let m = b.month, let d = b.day {
                cumple = (m, d)
            }
            let foto = contacto.isKeyAvailable(CNContactThumbnailImageDataKey) ? contacto.thumbnailImageData : nil
            padre.alElegir(PersonaElegida(nombre: nombre, telefono: numero.stringValue, foto: foto, cumpleanos: cumple))
        }

        func contactPickerDidCancel(_ picker: CNContactPickerViewController) {
            padre.alCancelar()
        }
    }
}
