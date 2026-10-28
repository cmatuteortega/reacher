import SwiftData
import SwiftUI

/// Todo lo que se ajusta de una persona. Sirve para dar de alta (con `alta`: la
/// persona aun no esta en la base y *Anadir* la guarda) y para editarla (se
/// guarda sola, como en Android; *Hecho* solo cierra).
struct FormularioPersona: View {
    @Bindable var contacto: Contacto
    var alta = false
    var alTerminar: () -> Void = {}
    var alEliminar: () -> Void = {}

    @Environment(\.modelContext) private var contexto
    @Query private var todos: [Contacto]
    @State private var nuevoCirculo = false
    @State private var textoCirculo = ""
    @State private var confirmarEliminar = false

    private static let atajos: [(clave: String.LocalizationValue, dias: Int)] = [
        ("atajo_1_semana", 7), ("atajo_2_semanas", 14), ("atajo_1_mes", 30), ("atajo_2_meses", 60),
        ("atajo_3_meses", 90), ("atajo_6_meses", 180), ("atajo_1_anio", 365),
    ]
    private static let plazosPausa = [1, 3, 7, 14, 30]

    var body: some View {
        Form {
            Section {
                Text(verbatim: contacto.nombre).font(.title3.weight(.semibold))
                Text(verbatim: contacto.telefono).foregroundStyle(Paleta.textoSecundario)
            }
            .listRowBackground(Paleta.tarjeta)

            Section("cada_cuanto") {
                Stepper(value: $contacto.frecuenciaDias, in: 1...730) {
                    Text(verbatim: Plural.cadaDias(contacto.frecuenciaDias)).font(.body.weight(.semibold))
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(Array(Self.atajos.enumerated()), id: \.offset) { _, a in
                            Button { contacto.frecuenciaDias = a.dias } label: {
                                Text(String(localized: a.clave))
                                    .font(.subheadline.weight(.medium))
                                    .padding(.horizontal, 14).padding(.vertical, 8)
                                    .background(contacto.frecuenciaDias == a.dias ? Paleta.principal : Paleta.borde.opacity(0.5), in: Capsule())
                                    .foregroundStyle(contacto.frecuenciaDias == a.dias ? Paleta.sobrePrincipal : Paleta.texto)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .listRowBackground(Paleta.tarjeta)

            Section("al_tocar_aviso") {
                Picker(selection: $contacto.medioNombre) {
                    ForEach(MedioContacto.elegibles, id: \.rawValue) { m in
                        Label(m.etiqueta, systemImage: m.icono).tag(m.rawValue)
                    }
                } label: { Text("al_tocar_aviso") }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            .listRowBackground(Paleta.tarjeta)

            Section("circulo") {
                Picker("circulo", selection: Binding(get: { contacto.circulo ?? "" }, set: { contacto.circulo = $0.nonVacio })) {
                    Text("circulo_ninguno").tag("")
                    ForEach(opcionesCirculo, id: \.self) { Text(verbatim: $0).tag($0) }
                }
                Button { textoCirculo = ""; nuevoCirculo = true } label: { Label("circulo_nuevo", systemImage: "plus") }
            }
            .listRowBackground(Paleta.tarjeta)

            Section("cumpleanos_titulo") { cumpleanos }
                .listRowBackground(Paleta.tarjeta)

            Section("notas") {
                TextField("notas_pista", text: $contacto.notas, axis: .vertical)
                    .lineLimit(3...8)
            }
            .listRowBackground(Paleta.tarjeta)

            if !alta {
                Section { pausa }.listRowBackground(Paleta.tarjeta)
                Section {
                    Button(role: .destructive) { confirmarEliminar = true } label: { Label("eliminar_contacto", systemImage: "trash") }
                }
                .listRowBackground(Paleta.tarjeta)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Paleta.fondo)
        .navigationTitle(Text(alta ? LocalizedStringKey("nuevo_contacto") : LocalizedStringKey("editar")))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(alta ? LocalizedStringKey("anadir") : LocalizedStringKey("guardar")) {
                    if alta { contexto.insert(contacto) }
                    Entorno.shared.cambioGuardado()
                    alTerminar()
                }
                .fontWeight(.semibold)
            }
            if alta {
                ToolbarItem(placement: .cancellationAction) { Button("cancelar") { alTerminar() } }
            }
        }
        .onChange(of: contacto.frecuenciaDias) { guardarSiExiste() }
        .onChange(of: contacto.medioNombre) { guardarSiExiste() }
        .onDisappear { guardarSiExiste() }
        .alert("circulo_nuevo", isPresented: $nuevoCirculo) {
            TextField("circulo_pista", text: $textoCirculo)
            Button("crear") { contacto.circulo = textoCirculo.trimmingCharacters(in: .whitespaces).nonVacio }
            Button("cancelar", role: .cancel) {}
        }
        .confirmationDialog(
            Text(verbatim: t("eliminar_a", contacto.nombre)), isPresented: $confirmarEliminar, titleVisibility: .visible
        ) {
            Button("eliminar", role: .destructive) { alEliminar() }
            Button("cancelar", role: .cancel) {}
        } message: {
            Text("eliminar_texto")
        }
    }

    private func guardarSiExiste() {
        guard !alta else { return }
        Entorno.shared.cambioGuardado()
    }

    /// Familia, amigos y trabajo siempre, junto a los que haya.
    private var opcionesCirculo: [String] {
        var opciones = [t("circulo_familia"), t("circulo_amigos"), t("circulo_trabajo")]
        for c in todos.compactMap(\.circulo) where !opciones.contains(c) { opciones.append(c) }
        if let actual = contacto.circulo, !opciones.contains(actual) { opciones.append(actual) }
        return opciones
    }

    // MARK: Cumpleanos

    @ViewBuilder private var cumpleanos: some View {
        if let dia = contacto.cumpleanos {
            Text(verbatim: Formato.diaYMes(mes: dia.mes, dia: dia.dia))
            Picker("mes", selection: Binding(get: { dia.mes }, set: { ponerCumple(mes: $0, dia: dia.dia) })) {
                ForEach(1...12, id: \.self) { m in Text(verbatim: Calendar.current.monthSymbols[m - 1]).tag(m) }
            }
            Picker("dia", selection: Binding(get: { dia.dia }, set: { ponerCumple(mes: dia.mes, dia: $0) })) {
                ForEach(1...31, id: \.self) { Text(verbatim: "\($0)").tag($0) }
            }
            Button(role: .destructive) {
                contacto.cumpleanos = nil
                contacto.cumpleanosManual = true
            } label: { Label("cumpleanos_quitar", systemImage: "xmark") }
        } else {
            Button {
                ponerCumple(mes: Fechas.calendario.component(.month, from: Date()), dia: Fechas.calendario.component(.day, from: Date()))
            } label: { Label("cumpleanos_poner", systemImage: "birthday.cake") }
        }
    }

    private func ponerCumple(mes: Int, dia: Int) {
        // Febrero con 30 dias no existe: se queda en el 29.
        let tope = mes == 2 ? 29 : ([4, 6, 9, 11].contains(mes) ? 30 : 31)
        contacto.cumpleanos = (mes, min(dia, tope))
        contacto.cumpleanosManual = true
    }

    // MARK: Pausa

    @ViewBuilder private var pausa: some View {
        if contacto.pausado(), let hasta = contacto.pausadoHasta {
            Text(verbatim: t("en_pausa_burbuja", Formato.fecha(hasta)))
            Button { contacto.pausadoHasta = nil; guardarSiExiste() } label: { Label("reanudar_avisos", systemImage: "bell") }
        } else {
            Menu {
                ForEach(Self.plazosPausa, id: \.self) { dias in
                    Button(t("pausar", Plural.dias(dias))) {
                        contacto.pausadoHasta = Fechas.sumar(dias: dias, a: Date())
                        guardarSiExiste()
                    }
                }
            } label: { Label("pausa", systemImage: "bell.slash") }
        }
    }
}
