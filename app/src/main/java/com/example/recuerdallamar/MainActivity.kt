package com.example.recuerdallamar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recuerdallamar.avisos.Notificaciones
import com.example.recuerdallamar.datos.Ajustes
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.VistaPersonas
import com.example.recuerdallamar.ui.PantallaAjustes
import com.example.recuerdallamar.ui.PantallaBienvenida
import com.example.recuerdallamar.ui.PantallaFicha
import com.example.recuerdallamar.ui.PantallaLista
import com.example.recuerdallamar.ui.TemaApp
import com.example.recuerdallamar.ui.esOscuro
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Contacto cuya ficha pide abrir el boton "Mas opciones" del aviso. */
    private val fichaPedida = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Al recrear (p. ej. al girar) el intent sigue ahi, pero ya se atendio.
        if (savedInstanceState == null) atender(intent)
        val almacen = AlmacenAjustes.de(this)
        setContent {
            val ajustes by almacen.ajustes.collectAsStateWithLifecycle()
            val oscuro = ajustes.tema.esOscuro()
            // Los iconos de las barras del sistema siguen al tema elegido en la
            // app, no al del sistema: si no, en claro forzado saldrian blancos.
            DisposableEffect(oscuro) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { oscuro },
                    navigationBarStyle = SystemBarStyle.auto(ESTOR_CLARO, ESTOR_OSCURO) { oscuro },
                )
                onDispose { }
            }
            TemaApp(modoOscuro = oscuro) {
                AppRecuerda(
                    ajustes = ajustes,
                    cambiarAjustes = almacen::cambiar,
                    fichaPedida = fichaPedida.collectAsStateWithLifecycle().value,
                    onFichaAtendida = { fichaPedida.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        atender(intent)
    }

    private fun atender(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_FICHA, -1) ?: -1
        if (id < 0) return
        // Los botones de un aviso no lo retiran solos.
        Notificaciones.quitar(this, id)
        fichaPedida.value = id
    }

    companion object {
        const val EXTRA_FICHA = "abrir_ficha"
    }
}

// Los mismos velos que pone enableEdgeToEdge() por defecto tras los botones de navegacion.
private val ESTOR_CLARO = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val ESTOR_OSCURO = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/** Lo que ocupa la pantalla: la lista es la base y las demas se abren encima. */
private sealed interface Pantalla {
    data object Lista : Pantalla
    data object Bienvenida : Pantalla
    data object Ajustes : Pantalla
    data class Ficha(val contacto: Contacto) : Pantalla
}

/** Frecuencia que se propone al elegir a alguien nuevo. */
private const val FRECUENCIA_INICIAL = 7

@Composable
private fun AppRecuerda(
    ajustes: Ajustes,
    cambiarAjustes: ((Ajustes) -> Ajustes) -> Unit,
    fichaPedida: Long?,
    onFichaAtendida: () -> Unit,
    vm: ContactosViewModel = viewModel(),
) {
    val context = LocalContext.current
    val contactos by vm.contactos.collectAsStateWithLifecycle()
    val ficha by vm.ficha.collectAsStateWithLifecycle()

    var ajustesAbiertos by rememberSaveable { mutableStateOf(false) }
    var pasoBienvenida by rememberSaveable { mutableIntStateOf(0) }

    // Quien ya tenia gente guardada de antes de que hubiera bienvenida no la
    // necesita. Solo se mira al cargar la primera vez: durante la bienvenida
    // la lista deja de estar vacia en cuanto se anade a alguien.
    var bienvenidaDecidida by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(contactos) {
        val cargados = contactos ?: return@LaunchedEffect
        if (bienvenidaDecidida) return@LaunchedEffect
        bienvenidaDecidida = true
        if (cargados.isNotEmpty() && !ajustes.bienvenidaHecha) {
            cambiarAjustes { it.copy(bienvenidaHecha = true) }
        }
    }

    LaunchedEffect(fichaPedida) {
        if (fichaPedida != null) {
            vm.abrirFichaDe(fichaPedida)
            onFichaAtendida()
        }
    }

    // Fotos de la agenda: se relee al volver a la app, por si se concedio el
    // permiso desde los ajustes del sistema.
    var fotosPermitidas by remember { mutableStateOf(FotoContacto.permitida(context)) }
    LifecycleResumeEffect(Unit) {
        fotosPermitidas = FotoContacto.permitida(context)
        onPauseOrDispose { }
    }
    // Tras dos "no" el sistema ya no muestra el dialogo: entonces el boton de
    // la ficha lleva a los ajustes de la app, que es el unico sitio donde darlo.
    var fotosBloqueadas by rememberSaveable { mutableStateOf(false) }

    val pedirPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultado ->
        if (resultado[Manifest.permission.POST_NOTIFICATIONS] == false) {
            Toast.makeText(context, "Sin permiso no habra recordatorios", Toast.LENGTH_LONG).show()
        }
        resultado[Manifest.permission.READ_CONTACTS]?.let { concedido ->
            fotosPermitidas = concedido
            val puedeVolverAPreguntar = (context as? Activity)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CONTACTS)
            } ?: true
            fotosBloqueadas = !concedido && !puedeVolverAPreguntar
        }
    }

    // Lo que hace falta, en un solo paso: avisos siempre que falten, fotos solo
    // la primera vez (luego se piden desde la ficha).
    val pedirLoQueFalta = {
        val faltan = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notificaciones.permitidas(context)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (FotoContacto.preguntarAlArrancar(context)) {
                add(Manifest.permission.READ_CONTACTS)
                FotoContacto.marcarPreguntado(context)
            }
        }
        if (faltan.isNotEmpty()) pedirPermisos.launch(faltan.toTypedArray())
    }
    // Al abrir, salvo en la bienvenida: alli se piden al pulsar "Empezar",
    // cuando ya se ha explicado para que sirven.
    LaunchedEffect(ajustes.bienvenidaHecha) {
        if (ajustes.bienvenidaHecha) pedirLoQueFalta()
    }

    val pedirFotos = {
        if (fotosBloqueadas) {
            val ficheroApp = Uri.fromParts("package", context.packageName, null)
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ficheroApp))
        } else {
            pedirPermisos.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }
    }

    val elegir = rememberLauncherForActivityResult(ElegirTelefono()) { uri ->
        val elegido = uri?.let { leerTelefono(context, it) } ?: return@rememberLauncherForActivityResult
        val (nombre, telefono) = elegido
        vm.abrirFicha(
            Contacto(
                nombre = nombre,
                telefono = telefono,
                frecuenciaDias = FRECUENCIA_INICIAL,
                medio = ajustes.medioPorDefecto,
            ),
        )
    }

    val anadirDeLaAgenda = {
        try {
            elegir.launch(Unit)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "No hay agenda de contactos", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = ficha != null) { vm.cerrarFicha() }
    BackHandler(enabled = ficha == null && ajustesAbiertos) { ajustesAbiertos = false }

    val pantalla = ficha?.let { Pantalla.Ficha(it) }
        ?: when {
            !ajustes.bienvenidaHecha -> Pantalla.Bienvenida
            ajustesAbiertos -> Pantalla.Ajustes
            else -> Pantalla.Lista
        }

    // contentKey solo distingue el tipo de pantalla: cambiar de ficha a ficha no anima.
    AnimatedContent(
        targetState = pantalla,
        contentKey = { it::class },
        transitionSpec = {
            val entra = targetState != Pantalla.Lista
            slideInHorizontally { if (entra) it else -it } togetherWith
                slideOutHorizontally { if (entra) -it else it }
        },
        label = "pantalla",
    ) { actual ->
        when (actual) {
            Pantalla.Lista -> PantallaLista(
                contactos = contactos,
                fotosPermitidas = fotosPermitidas,
                vista = ajustes.vista,
                onCambiarVista = { vista -> cambiarAjustes { it.copy(vista = vista) } },
                onAnadir = anadirDeLaAgenda,
                onAbrir = vm::abrirFicha,
                onAjustes = { ajustesAbiertos = true },
            )
            Pantalla.Ajustes -> PantallaAjustes(
                ajustes = ajustes,
                onCambiar = cambiarAjustes,
                onVolver = { ajustesAbiertos = false },
            )
            Pantalla.Bienvenida -> PantallaBienvenida(
                paso = pasoBienvenida,
                onPaso = { pasoBienvenida = it },
                contactos = contactos.orEmpty(),
                fotosPermitidas = fotosPermitidas,
                onPedirPermisos = pedirLoQueFalta,
                onAnadir = anadirDeLaAgenda,
                onAbrir = vm::abrirFicha,
                // Se acaba en la vista de burbujas, la que se acaba de ensenar.
                onTerminar = { cambiarAjustes { it.copy(bienvenidaHecha = true, vista = VistaPersonas.BURBUJAS) } },
            )
            is Pantalla.Ficha -> PantallaFicha(
                borrador = actual.contacto,
                observar = vm::observar,
                fotosPermitidas = fotosPermitidas,
                onPedirFotos = pedirFotos,
                onAnadir = vm::anadir,
                onActualizar = vm::actualizar,
                onContactar = { telefono, medio -> Contactar.abrir(context, telefono, medio) },
                onLlamadoHoy = { id ->
                    vm.llamadoHoy(id)
                    Toast.makeText(context, "Anotado: último contacto hoy", Toast.LENGTH_SHORT).show()
                },
                onPausar = { id, dias ->
                    vm.pausar(id, dias)
                    Toast.makeText(context, "Avisos en pausa $dias días", Toast.LENGTH_SHORT).show()
                },
                onReanudar = vm::reanudar,
                onEliminar = { id ->
                    vm.eliminar(id)
                    Toast.makeText(context, "Contacto eliminado", Toast.LENGTH_SHORT).show()
                },
                onForzarNotificacion = { id ->
                    if (!Notificaciones.permitidas(context)) {
                        Toast.makeText(context, "Activa las notificaciones de la app", Toast.LENGTH_LONG).show()
                    } else {
                        vm.forzarNotificacion(id)
                        Toast.makeText(context, "Notificacion de prueba enviada", Toast.LENGTH_SHORT).show()
                    }
                },
                onVolver = vm::cerrarFicha,
            )
        }
    }
}
