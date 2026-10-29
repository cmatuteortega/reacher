import SwiftData
import SwiftUI
import UIKit

/// La ficha de alguien ya guardado, para leer y actuar: foto, nombre, numero,
/// cumpleanos, cuando toca, *He llamado hoy* y, pegado abajo, el boton que
/// contacta por la via elegida. El lapiz abre *Editar*.
struct PantallaFicha: View {
    @Bindable var contacto: Contacto
    var alEliminar: () -> Void
    /// En el panel de una pantalla ancha no hay flecha de volver: una X la cierra.
    var alCerrar: (() -> Void)?

    @Environment(\.dismiss) private var cerrar
    @State private var editando = false
    @State private var mensaje: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                cabecera
                if contacto.pausado(), let hasta = contacto.pausadoHasta {
                    Tarjeta {
                        VStack(alignment: .leading, spacing: 10) {
                            Label(t("en_pausa_burbuja", Formato.fecha(hasta)), systemImage: "bell.slash")
                            Button("reanudar_avisos") {
                                contacto.pausadoHasta = nil
                                Entorno.shared.cambioGuardado()
                            }
                            .buttonStyle(EstiloBotonSecundario())
                        }
                    }
                }
                let yaHoy = Fechas.dias(de: contacto.ultimoContacto, a: Date()) == 0
                BotonMantener(
                    texto: yaHoy ? t("ultimo_contacto_hoy") : t("manten_para_anotar"),
                    icono: "checkmark.circle",
                    habilitado: !yaHoy,
                    accesible: t("he_llamado_hoy"),
                    alConfirmar: llamadoHoy
                )
                fechas
                resumen
            }
            .padding(16)
            .padding(.bottom, 80)
        }
        .background(Paleta.fondo)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let alCerrar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: alCerrar) { Image(systemName: "xmark") }
                        .accessibilityLabel(Text("cerrar"))
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Button { editando = true } label: { Image(systemName: "pencil") }
                    .accessibilityLabel(Text("editar"))
            }
        }
        .safeAreaInset(edge: .bottom) {
            Button {
                Task {
                    let abierto = await Contactar.abrir(telefono: contacto.telefono, medio: contacto.medio)
                    if !abierto { mostrar(t("sin_app_para", contacto.medio.boton)) }
                }
            } label: {
                Label(contacto.medio.boton, systemImage: contacto.medio.icono)
            }
            .buttonStyle(EstiloBotonPrincipal())
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(.ultraThinMaterial)
        }
        .overlay(alignment: .top) {
            if let mensaje {
                Text(verbatim: mensaje)
                    .font(.subheadline.weight(.medium))
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Paleta.principal, in: Capsule())
                    .foregroundStyle(Paleta.sobrePrincipal)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .sheet(isPresented: $editando) {
            NavigationStack {
                FormularioPersona(
                    contacto: contacto,
                    alTerminar: { editando = false },
                    alEliminar: {
                        editando = false
                        alEliminar()
                    }
                )
            }
        }
    }

    private var cabecera: some View {
        VStack(spacing: 10) {
            AnilloUrgencia(urgencia: contacto.urgencia()) {
                AvatarView(contacto: contacto, tamano: 124)
            }
            Text(verbatim: contacto.nombre).font(.title.weight(.bold)).multilineTextAlignment(.center)
            Text(verbatim: contacto.telefono).foregroundStyle(Paleta.textoSecundario)
            if contacto.esCumpleanos() {
                Label("cumpleanos_es_hoy", systemImage: "birthday.cake.fill").foregroundStyle(Paleta.acentoLegible)
            } else if let dia = contacto.cumpleanos, let faltan = contacto.diasHastaCumpleanos() {
                Label(
                    t("cumpleanos_el", Formato.diaYMes(mes: dia.mes, dia: dia.dia), Plural.enDias(faltan)),
                    systemImage: "birthday.cake"
                )
                .font(.subheadline).foregroundStyle(Paleta.textoSecundario)
            }
            Text(verbatim: Formato.cuandoToca(contacto))
                .font(.headline)
                .foregroundStyle(contacto.tocaLlamar() ? Paleta.acentoLegible : Paleta.texto)
        }
        .foregroundStyle(Paleta.texto)
        .padding(.top, 8)
    }

    /// Ultimo contacto y proximo aviso en dos casillas, y lo que ha pasado con el aviso debajo.
    private var fechas: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                casilla("ultimo_contacto", Formato.fecha(contacto.ultimoContacto))
                casilla("proximo_aviso", Formato.fecha(contacto.proximoAviso()))
            }
            if contacto.descartes > 0 {
                Text(verbatim: t("aviso_quitado", Plural.veces(contacto.descartes)))
                    .font(.subheadline).foregroundStyle(Paleta.textoSecundario)
                    .padding(.horizontal, 4)
            }
        }
    }

    private func casilla(_ titulo: LocalizedStringKey, _ valor: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(titulo).font(.footnote).foregroundStyle(Paleta.textoSecundario)
            Text(verbatim: valor).font(.headline)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Paleta.tarjeta, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var resumen: some View {
        Tarjeta {
            VStack(alignment: .leading, spacing: 12) {
                fila("frecuencia", Plural.cadaDias(contacto.frecuenciaDias))
                fila("al_tocar_aviso", contacto.medio.etiqueta)
                if let circulo = contacto.circulo { fila("circulo", circulo) }
                if !contacto.notas.isEmpty { fila("notas", contacto.notas) }
            }
        }
        .onTapGesture { editando = true }
    }

    private func fila(_ titulo: LocalizedStringKey, _ valor: String) -> some View {
        HStack {
            Text(titulo).foregroundStyle(Paleta.textoSecundario)
            Spacer()
            Text(verbatim: valor).fontWeight(.medium)
        }
        .font(.subheadline)
    }

    private func llamadoHoy() {
        contacto.llamadoHoy()
        Entorno.shared.cambioGuardado()
        mostrar(t("anotado_hoy"))
    }

    private func mostrar(_ texto: String) {
        withAnimation { mensaje = texto }
        Task {
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            withAnimation { mensaje = nil }
        }
    }
}
