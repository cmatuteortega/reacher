package com.example.recuerdallamar

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.recuerdallamar.avisos.Notificaciones
import com.example.recuerdallamar.avisos.RecordatorioWorker
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.CopiaSeguridad
import com.example.recuerdallamar.datos.MedioContacto
import com.example.recuerdallamar.datos.porUrgencia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.MonthDay

/** Pausa sin cambios en la ficha tras la que se reprograma el aviso. */
private const val REPROGRAMAR_TRAS_MS = 800L

/** Pausa al escribir las notas tras la que se guardan. */
private const val GUARDAR_NOTAS_TRAS_MS = 500L

/**
 * Lo que no es de una sola pantalla: la gente, el alta a medias, el filtro por
 * circulo y la persona elegida en tableta. Lo que tiene que sobrevivir a que
 * Android cierre el proceso en segundo plano va en [estado] (SavedStateHandle).
 */
class ContactosViewModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val base = BaseDatos.de(app)
    private val dao = base.contactos()

    /** Circulo por el que se filtra Personas; null = todos. */
    val filtro: StateFlow<String?> = estado.getStateFlow(FILTRO, null)

    fun filtrar(circulo: String?) {
        estado[FILTRO] = circulo
    }

    val circulos: StateFlow<List<String>> = dao.circulos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Ordenados por urgencia, no por fecha de alta, y solo los del circulo
     * elegido. null mientras carga, para no ensenar "lista vacia" un instante al abrir.
     */
    val contactos: StateFlow<List<Contacto>?> = combine(dao.todos(), filtro) { todos, circulo ->
        // Un filtro de un circulo que ya no existe (se vacio) no deja la pantalla en blanco.
        val enCirculo = if (circulo == null || todos.none { it.circulo == circulo }) todos else todos.filter { it.circulo == circulo }
        enCirculo.porUrgencia()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Alguien recien elegido de la agenda y aun sin guardar. Se guarda campo a
     * campo en [estado]: si Android cierra la app mientras se mira la agenda o
     * se escribe, al volver la ficha sigue ahi.
     */
    private val _borrador = MutableStateFlow(
        estado.get<String>(BORRADOR_NOMBRE)?.let { nombre ->
            Contacto(
                nombre = nombre,
                telefono = estado[BORRADOR_TELEFONO] ?: "",
                frecuenciaDias = estado[BORRADOR_FRECUENCIA] ?: 7,
                medio = MedioContacto.desde(estado[BORRADOR_MEDIO]),
            )
        },
    )
    val borrador: StateFlow<Contacto?> = _borrador.asStateFlow()

    /** Se ve al momento: la pantalla de alta se abre justo despues. */
    fun empezarAlta(contacto: Contacto) {
        estado[BORRADOR_NOMBRE] = contacto.nombre
        estado[BORRADOR_TELEFONO] = contacto.telefono
        estado[BORRADOR_FRECUENCIA] = contacto.frecuenciaDias
        estado[BORRADOR_MEDIO] = contacto.medio.name
        _borrador.value = contacto
    }

    fun descartarAlta() {
        estado.remove<String>(BORRADOR_NOMBRE)
        _borrador.value = null
    }

    private val _altaPedida = MutableStateFlow(false)

    /** El atajo del icono o el + del widget piden abrir la agenda para anadir a alguien. */
    val altaPedida: StateFlow<Boolean> = _altaPedida.asStateFlow()

    fun pedirAlta() {
        _altaPedida.value = true
    }

    fun altaAtendida() {
        _altaPedida.value = false
    }

    /** Persona abierta al lado de la lista en pantallas anchas; null = ninguna. */
    val seleccion: StateFlow<Long?> = estado.getStateFlow(SELECCION, null)

    fun seleccionar(id: Long?) {
        estado[SELECCION] = id
    }

    fun observar(id: Long): Flow<Contacto?> = dao.observar(id)

    /**
     * Da de alta a alguien nuevo. El cumpleanos, el puesto a mano en la ficha;
     * si no, el de la agenda si se puede leer.
     */
    fun anadir(contacto: Contacto) {
        descartarAlta()
        viewModelScope.launch {
            val cumpleanos = if (contacto.cumpleanosManual) {
                contacto.cumpleanos
            } else {
                withContext(Dispatchers.IO) { CumpleanosAgenda.leer(getApplication(), contacto.telefono) }
            }
            val id = dao.insertar(contacto.copy(ultimoContacto = LocalDate.now(), cumpleanos = cumpleanos))
            RecordatorioWorker.programar(getApplication(), id)
        }
    }

    /**
     * Al abrir una ficha: si en la agenda ha aparecido (o cambiado) el
     * cumpleanos, se apunta. Sin permiso no se toca lo que hubiera.
     */
    fun refrescarCumpleanos(contacto: Contacto) {
        if (contacto.id == 0L || contacto.cumpleanosManual) return
        viewModelScope.launch {
            val leido = withContext(Dispatchers.IO) { CumpleanosAgenda.leer(getApplication(), contacto.telefono) }
            if (leido != null && leido != contacto.cumpleanos) dao.actualizarCumpleanos(contacto.id, leido)
        }
    }

    private val reprogramaciones = mutableMapOf<Long, Job>()

    /**
     * Guardado automatico de la ficha de alguien que ya existe. La fila se
     * escribe al momento; el trabajo se reprograma cuando se deja de tocar,
     * para no reencolarlo en cada dia que pasa al arrastrar la frecuencia.
     */
    fun actualizar(id: Long, dias: Int, medio: MedioContacto) {
        viewModelScope.launch { dao.actualizarFicha(id, dias, medio) }
        reprogramaciones.remove(id)?.cancel()
        reprogramaciones[id] = viewModelScope.launch {
            delay(REPROGRAMAR_TRAS_MS)
            RecordatorioWorker.programar(getApplication(), id)
            reprogramaciones.remove(id)
        }
    }

    private val escrituraNotas = mutableMapOf<Long, Job>()

    /** Las notas se guardan al dejar de escribir, no con cada letra. */
    fun cambiarNotas(id: Long, notas: String) {
        escrituraNotas.remove(id)?.cancel()
        escrituraNotas[id] = viewModelScope.launch {
            delay(GUARDAR_NOTAS_TRAS_MS)
            dao.actualizarNotas(id, notas.trim())
            escrituraNotas.remove(id)
        }
    }

    /** Cumpleanos puesto (o quitado, con null) a mano: desde ahora manda sobre la agenda. */
    fun ponerCumpleanos(id: Long, dia: MonthDay?) {
        viewModelScope.launch { dao.ponerCumpleanos(id, dia) }
    }

    fun cambiarCirculo(id: Long, circulo: String?) {
        viewModelScope.launch { dao.actualizarCirculo(id, circulo?.trim()?.takeIf { it.isNotEmpty() }) }
    }

    /**
     * Pone el ultimo contacto a hoy. Reprograma el trabajo como al guardar, para
     * que el ciclo diario arranque desde ahora, y retira el aviso pendiente:
     * ya no dice nada cierto.
     */
    fun llamadoHoy(id: Long) {
        Valoracion.contactoHecho(getApplication())
        viewModelScope.launch {
            dao.actualizarUltimoContacto(id, LocalDate.now())
            Notificaciones.quitar(getApplication(), id)
            RecordatorioWorker.programar(getApplication(), id)
        }
    }

    /**
     * Sin avisos de esta persona durante [dias]. El ultimo contacto no se toca:
     * la burbuja sigue creciendo mientras tanto.
     */
    fun pausar(id: Long, dias: Int) {
        viewModelScope.launch {
            dao.pausar(id, LocalDate.now().plusDays(dias.toLong()).atStartOfDay())
            Notificaciones.quitar(getApplication(), id)
        }
    }

    /** Quita la pausa; si ya tocaba, el trabajo diario lo comprueba al momento. */
    fun reanudar(id: Long) {
        viewModelScope.launch {
            dao.pausar(id, null)
            RecordatorioWorker.programar(getApplication(), id)
        }
    }

    fun eliminar(id: Long) {
        if (seleccion.value == id) seleccionar(null)
        viewModelScope.launch {
            RecordatorioWorker.cancelar(getApplication(), id)
            Notificaciones.quitar(getApplication(), id)
            dao.borrar(id)
        }
    }

    /** Como acabo una exportacion o importacion, para decirlo en pantalla. */
    sealed interface ResultadoCopia {
        data class Exportada(val personas: Int) : ResultadoCopia
        data class Importada(val nuevas: Int, val actualizadas: Int) : ResultadoCopia
        data object NoValida : ResultadoCopia
        data object Fallo : ResultadoCopia
    }

    /** Escribe la copia en el archivo elegido con el selector del sistema. */
    fun exportar(uri: Uri, alAcabar: (ResultadoCopia) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                try {
                    val gente = dao.lista()
                    val texto = CopiaSeguridad.escribir(gente, AlmacenAjustes.de(app).ajustes.value)
                    val salida = app.contentResolver.openOutputStream(uri) ?: return@withContext ResultadoCopia.Fallo
                    salida.use { it.write(texto.toByteArray()) }
                    ResultadoCopia.Exportada(gente.size)
                } catch (e: Exception) {
                    ResultadoCopia.Fallo
                }
            }
            alAcabar(resultado)
        }
    }

    /**
     * Anade la gente del archivo. Quien ya esta aqui con el mismo numero se
     * actualiza con lo del archivo (sin perder su id, ni lo de su aviso en
     * curso); el resto se da de alta. Los ajustes del archivo pisan los de
     * aqui. Despues, cada uno con su trabajo diario, como al darlo de alta.
     */
    fun importar(uri: Uri, alAcabar: (ResultadoCopia) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                val leido = try {
                    val entrada = app.contentResolver.openInputStream(uri) ?: return@withContext ResultadoCopia.Fallo
                    CopiaSeguridad.leer(entrada.use { it.readBytes().decodeToString() })
                } catch (e: CopiaSeguridad.ArchivoNoValido) {
                    return@withContext ResultadoCopia.NoValida
                } catch (e: Exception) {
                    return@withContext ResultadoCopia.Fallo
                }
                var nuevas = 0
                var actualizadas = 0
                val tocados = mutableListOf<Long>()
                base.withTransaction {
                    val presentes = dao.lista().toMutableList()
                    leido.personas.forEach { persona ->
                        val ya = presentes.firstOrNull { CopiaSeguridad.mismoTelefono(it.telefono, persona.telefono) }
                        if (ya != null) {
                            dao.reemplazar(
                                ya.copy(
                                    nombre = persona.nombre,
                                    frecuenciaDias = persona.frecuenciaDias,
                                    ultimoContacto = maxOf(ya.ultimoContacto, persona.ultimoContacto),
                                    medio = persona.medio,
                                    notas = persona.notas,
                                    cumpleanos = persona.cumpleanos,
                                    cumpleanosManual = persona.cumpleanosManual,
                                    circulo = persona.circulo,
                                    pausadoHasta = persona.pausadoHasta,
                                ),
                            )
                            tocados += ya.id
                            actualizadas++
                        } else {
                            val id = dao.insertar(persona)
                            // Dos filas del archivo con el mismo numero: la segunda actualiza a la primera.
                            presentes += persona.copy(id = id)
                            tocados += id
                            nuevas++
                        }
                    }
                }
                AlmacenAjustes.de(app).cambiar { leido.aplicarA(it).copy(bienvenidaHecha = true) }
                tocados.distinct().forEach { RecordatorioWorker.programar(app, it) }
                ResultadoCopia.Importada(nuevas, actualizadas)
            }
            alAcabar(resultado)
        }
    }

    fun forzarNotificacion(id: Long) {
        RecordatorioWorker.forzar(getApplication(), id)
    }

    private companion object {
        const val FILTRO = "filtro"
        const val SELECCION = "seleccion"
        const val BORRADOR_NOMBRE = "borrador_nombre"
        const val BORRADOR_TELEFONO = "borrador_telefono"
        const val BORRADOR_FRECUENCIA = "borrador_frecuencia"
        const val BORRADOR_MEDIO = "borrador_medio"
    }
}
