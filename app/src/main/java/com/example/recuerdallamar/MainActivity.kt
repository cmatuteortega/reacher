package com.example.recuerdallamar

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.example.recuerdallamar.avisos.Notificaciones
import com.example.recuerdallamar.datos.Ajustes
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.VistaPersonas
import com.example.recuerdallamar.ui.AvisoBateria
import com.example.recuerdallamar.ui.PantallaAjustes
import com.example.recuerdallamar.ui.PantallaBienvenida
import com.example.recuerdallamar.ui.PantallaFicha
import com.example.recuerdallamar.ui.PantallaLista
import com.example.recuerdallamar.ui.PreguntaEstadisticas
import com.example.recuerdallamar.ui.Relevo
import com.example.recuerdallamar.ui.TemaApp
import com.example.recuerdallamar.ui.esOscuro
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val vm: ContactosViewModel by viewModels()

    /** El sol que pasa de la bienvenida, o del icono de arranque, a la esquina de Personas. */
    private val relevo = Relevo()

    /** Enlace llegado con la app ya abierta (aviso, widget, atajo), a la espera de la navegacion. */
    private val enlacePedido = MutableStateFlow<Intent?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Idioma.envolver(newBase))
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val arranque = installSplashScreen()
        super.onCreate(savedInstanceState)
        // El sol del arranque se queda hasta que la lista ha cargado: asi no se
        // ve un instante la pantalla vacia antes de que lleguen las burbujas.
        arranque.setKeepOnScreenCondition { vm.contactos.value == null }
        // Al abrir la app desde el icono, con la bienvenida ya hecha, el sol del
        // arranque no se va: el de la esquina sale de donde esta y sube hasta
        // su sitio. Personas se monta debajo mientras tanto, esperandolo.
        val subirSol = savedInstanceState == null &&
            AlmacenAjustes.de(this).ajustes.value.bienvenidaHecha &&
            intent?.action == Intent.ACTION_MAIN &&
            ValueAnimator.areAnimatorsEnabled()
        if (subirSol) relevo.esperarArranque(solDelArranqueEstimado())
        arranque.setOnExitAnimationListener { salida ->
            if (!ValueAnimator.areAnimatorsEnabled()) {
                salida.remove()
                return@setOnExitAnimationListener
            }
            if (subirSol) relevo.arranqueTerminado(solDelIcono(salida.iconViewOrNull()))
            // Fundido corto: el sol de la app ya esta debajo en el mismo sitio y
            // empieza a subir despacio, asi que solo se nota que se va el fondo.
            salida.view.animate()
                .alpha(0f)
                .setDuration(if (subirSol) 180 else 250)
                .withEndAction { salida.remove() }
                .start()
        }
        enableEdgeToEdge()
        // Navigation abre el enlace con el que arranca la actividad al montar el
        // grafo. Sin NEW_TASK, que le haria reiniciar la actividad para
        // rehacer la pila: la rehace en el sitio.
        if (savedInstanceState == null) {
            intent = prepararEnlace(intent)
            if (Enlaces.esNuevo(intent?.data)) vm.pedirAlta()
        }
        val almacen = AlmacenAjustes.de(this)
        val idiomaAlCrear = almacen.ajustes.value.idioma
        val bienvenidaAlCrear = almacen.ajustes.value.bienvenidaHecha
        // Una pregunta por sesion: si toca la de las estadisticas, la de la
        // bateria espera a la proxima vez que se abra la app.
        val preguntarEstadisticas = bienvenidaAlCrear &&
            almacen.ajustes.value.estadisticas == null &&
            BuildConfig.POSTHOG_KEY.isNotBlank()
        setContent {
            val ajustes by almacen.ajustes.collectAsStateWithLifecycle()
            // Otro idioma en Ajustes: se rehace la actividad para que lo coja
            // todo; Navigation deja al usuario en la misma pantalla.
            LaunchedEffect(ajustes.idioma) {
                if (ajustes.idioma != idiomaAlCrear) {
                    Notificaciones.crearCanal(applicationContext)
                    recreate()
                }
            }
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
            val ventana = calculateWindowSizeClass(this)
            TemaApp(modoOscuro = oscuro) {
                AppRecuerda(
                    vm = vm,
                    relevo = relevo,
                    ajustes = ajustes,
                    cambiarAjustes = almacen::cambiar,
                    anchoAmplio = ventana.widthSizeClass == WindowWidthSizeClass.Expanded,
                    enlacePedido = enlacePedido.collectAsStateWithLifecycle().value,
                    onEnlaceAtendido = { enlacePedido.value = null },
                )
                PreguntaEstadisticas(toca = preguntarEstadisticas, onCambiar = almacen::cambiar)
                AvisoBateria(
                    hayGente = vm.contactos.collectAsStateWithLifecycle().value?.isNotEmpty() == true,
                    bienvenidaYaHecha = bienvenidaAlCrear && !preguntarEstadisticas,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val enlace = prepararEnlace(intent) ?: return
        if (Enlaces.esNuevo(enlace.data)) vm.pedirAlta() else enlacePedido.value = enlace
    }

    /**
     * Donde deberia estar el sol del icono de arranque: centrado en la ventana,
     * en un icono de [ICONO_ARRANQUE_DP] dp. Solo para colocar el de la esquina
     * mientras el arranque lo tapa; al irse se corrige con [solDelIcono].
     */
    private fun solDelArranqueEstimado(): Relevo.Sol {
        val metricas = resources.displayMetrics
        val (ancho, alto) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            metricas.widthPixels to metricas.heightPixels
        }
        val lado = ICONO_ARRANQUE_DP * metricas.density
        return Relevo.Sol(Offset(ancho / 2f, alto / 2f), lado * RADIO_SOL_ICONO)
    }

    /** El sol del icono de arranque tal como se ve, en coordenadas de la ventana. */
    private fun solDelIcono(icono: View?): Relevo.Sol? {
        if (icono == null || icono.width == 0) return null
        val donde = IntArray(2)
        icono.getLocationInWindow(donde)
        val centro = Offset(donde[0] + icono.width / 2f, donde[1] + icono.height / 2f)
        return Relevo.Sol(centro, icono.width * icono.scaleX * RADIO_SOL_ICONO)
    }

    /**
     * Quita NEW_TASK a los enlaces propios (ver onCreate) y retira el aviso de
     * esa persona: los botones de un aviso no lo retiran solos.
     */
    private fun prepararEnlace(intent: Intent?): Intent? {
        if (intent?.data?.scheme != Enlaces.ESQUEMA) return intent
        Enlaces.idDeFicha(intent.data)?.let { Notificaciones.quitar(this, it) }
        return Intent(intent).apply { flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv() }
    }
}

/** Tamano del icono en la pantalla de arranque sin fondo de icono (el de Android 12+ y core-splashscreen). */
private const val ICONO_ARRANQUE_DP = 288f

/** Radio del cuerpo del sol en ic_launcher_foreground: 25 de los 108 del lienzo. */
private const val RADIO_SOL_ICONO = 25f / 108f

/** En Android 12+ el icono puede no existir (arranque sin icono); ahi se usa lo estimado. */
private fun SplashScreenViewProvider.iconViewOrNull(): View? = runCatching { iconView }.getOrNull()

// Los mismos velos que pone enableEdgeToEdge() por defecto tras los botones de navegacion.
private val ESTOR_CLARO = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val ESTOR_OSCURO = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/** Frecuencia que se propone al elegir a alguien nuevo. */
private const val FRECUENCIA_INICIAL = 7

/** Ancho maximo del panel de la ficha junto a la lista. */
private val ANCHO_PANEL = 520.dp

@Composable
private fun AppRecuerda(
    vm: ContactosViewModel,
    relevo: Relevo,
    ajustes: Ajustes,
    cambiarAjustes: ((Ajustes) -> Ajustes) -> Unit,
    anchoAmplio: Boolean,
    enlacePedido: Intent?,
    onEnlaceAtendido: () -> Unit,
) {
    val context = LocalContext.current
    val navegador = rememberNavController()
    val contactos by vm.contactos.collectAsStateWithLifecycle()
    val circulos by vm.circulos.collectAsStateWithLifecycle()
    val filtro by vm.filtro.collectAsStateWithLifecycle()
    val seleccion by vm.seleccion.collectAsStateWithLifecycle()
    val altaPedida by vm.altaPedida.collectAsStateWithLifecycle()

    // Desde la ficha o, con TalkBack, desde la burbuja. El aviso lo lee TalkBack.
    val anotarHoy = { id: Long ->
        vm.llamadoHoy(id)
        Toast.makeText(context, context.getString(R.string.anotado_hoy), Toast.LENGTH_SHORT).show()
    }

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
            navegador.irAPersonas()
        }
    }

    // Cada pantalla a la que se llega, por su ruta ("Ficha", no "Ficha/12").
    DisposableEffect(navegador) {
        val oyente = NavController.OnDestinationChangedListener { _, destino, _ ->
            destino.route?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('.')?.let(Telemetria::pantalla)
        }
        navegador.addOnDestinationChangedListener(oyente)
        onDispose { navegador.removeOnDestinationChangedListener(oyente) }
    }

    // Enlace con la app abierta: Navigation rehace la pila hasta su pantalla.
    LaunchedEffect(enlacePedido) {
        if (enlacePedido != null) {
            navegador.handleDeepLink(enlacePedido)
            onEnlaceAtendido()
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

    val elegir = rememberLauncherForActivityResult(ElegirTelefono()) { uri ->
        val elegido = uri?.let { leerTelefono(context, it) } ?: return@rememberLauncherForActivityResult
        val (nombre, telefono) = elegido
        vm.empezarAlta(
            Contacto(
                nombre = nombre,
                telefono = telefono,
                frecuenciaDias = FRECUENCIA_INICIAL,
                medio = ajustes.medioPorDefecto,
            ),
        )
        navegador.navigate(Ruta.Nueva) { launchSingleTop = true }
    }

    val anadirDeLaAgenda = {
        try {
            elegir.launch(Unit)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.sin_agenda), Toast.LENGTH_SHORT).show()
        }
    }

    // Al ir a la agenda con el + se pide lo que falte; al contestar se sigue
    // hasta la agenda.
    var permisosPedidos by rememberSaveable { mutableStateOf(false) }
    var elegirTrasPermisos by rememberSaveable { mutableStateOf(false) }

    val pedirPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultado ->
        if (resultado[Manifest.permission.POST_NOTIFICATIONS] == false) {
            Toast.makeText(context, context.getString(R.string.sin_permiso_avisos), Toast.LENGTH_LONG).show()
        }
        resultado[Manifest.permission.READ_CONTACTS]?.let { concedido ->
            fotosPermitidas = concedido
            val puedeVolverAPreguntar = (context as? Activity)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CONTACTS)
            } ?: true
            fotosBloqueadas = !concedido && !puedeVolverAPreguntar
        }
        if (elegirTrasPermisos) {
            elegirTrasPermisos = false
            anadirDeLaAgenda()
        }
    }

    // Lo que hace falta, en un solo paso: avisos siempre que falten, fotos solo
    // la primera vez (luego se piden desde la ficha o al anadir).
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
    // Al abrir, salvo en la bienvenida: alli se piden al ir a la agenda. Si ya
    // se pidieron alli, no se vuelve a preguntar nada mas acabarla. Si se
    // abrio con el atajo de anadir, ya se piden ahi.
    LaunchedEffect(ajustes.bienvenidaHecha) {
        if (ajustes.bienvenidaHecha && !permisosPedidos && !altaPedida) pedirLoQueFalta()
    }

    // Cada vez que se va a anadir a alguien, en la bienvenida o con el + de la
    // lista: mientras falten los avisos o las fotos se vuelven a pedir, que es
    // justo cuando se ve para que sirven.
    val anadirPidiendoPermisos: () -> Unit = {
        val faltan = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notificaciones.permitidas(context)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!FotoContacto.permitida(context)) add(Manifest.permission.READ_CONTACTS)
        }
        permisosPedidos = true
        if (faltan.isEmpty()) {
            anadirDeLaAgenda()
        } else {
            FotoContacto.marcarPreguntado(context)
            elegirTrasPermisos = true
            pedirPermisos.launch(faltan.toTypedArray())
        }
    }

    // Atajo del icono o "+" del widget (contacto://nuevo): a Personas y a la agenda.
    LaunchedEffect(altaPedida) {
        if (!altaPedida) return@LaunchedEffect
        vm.altaAtendida()
        if (ajustes.bienvenidaHecha) navegador.popBackStack(Ruta.Personas, inclusive = false)
        anadirPidiendoPermisos()
    }

    val pedirFotos = {
        if (fotosBloqueadas) {
            val ficheroApp = Uri.fromParts("package", context.packageName, null)
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ficheroApp))
        } else {
            pedirPermisos.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }
    }

    /** Ficha de alguien ya guardado: la misma en pantalla completa y en el panel. */
    @Composable
    fun Ficha(contacto: Contacto, enPanel: Boolean, onVolver: () -> Unit) {
        PantallaFicha(
            borrador = contacto,
            observar = vm::observar,
            circulos = circulos,
            fotosPermitidas = fotosPermitidas,
            onPedirFotos = pedirFotos,
            onAnadir = {},
            onActualizar = vm::actualizar,
            onNotas = vm::cambiarNotas,
            onCirculo = vm::cambiarCirculo,
            onCumpleanos = vm::ponerCumpleanos,
            onContactar = { telefono, medio -> Contactar.abrir(context, telefono, medio) },
            onLlamadoHoy = { id -> anotarHoy(id) },
            onPausar = { id, dias ->
                vm.pausar(id, dias)
                Toast.makeText(context, context.resources.getQuantityString(R.plurals.avisos_en_pausa, dias, dias), Toast.LENGTH_SHORT).show()
            },
            onReanudar = vm::reanudar,
            onEliminar = { id ->
                onVolver()
                vm.eliminar(id)
                Toast.makeText(context, context.getString(R.string.contacto_eliminado), Toast.LENGTH_SHORT).show()
            },
            onForzarNotificacion = { id ->
                if (!Notificaciones.permitidas(context)) {
                    Toast.makeText(context, context.getString(R.string.activa_notificaciones), Toast.LENGTH_LONG).show()
                } else {
                    vm.forzarNotificacion(id)
                    Toast.makeText(context, context.getString(R.string.notificacion_prueba_enviada), Toast.LENGTH_SHORT).show()
                }
            },
            onVolver = onVolver,
            enPanel = enPanel,
        )
    }

    // La pantalla de inicio se fija una vez: si siguiera a bienvenidaHecha,
    // al acabar la bienvenida Navigation veria otro grafo y reharia la pila de
    // golpe, sin transicion, y la lista gastaria el relevo (el sol que sube a
    // la esquina y los planetas que caen) antes de verse. Acabarla es una
    // navegacion normal (irAPersonas).
    val inicio: Any = rememberSaveable { ajustes.bienvenidaHecha }.let { hecha ->
        if (hecha) Ruta.Personas else Ruta.Bienvenida
    }
    NavHost(
        navController = navegador,
        startDestination = inicio,
        // Lo que se abre entra por la derecha y al volver sale por donde vino;
        // el gesto atras predictivo arrastra esta misma animacion.
        enterTransition = { slideInHorizontally { it } },
        exitTransition = { slideOutHorizontally { -it } },
        popEnterTransition = { slideInHorizontally { -it } },
        popExitTransition = { slideOutHorizontally { it } },
    ) {
        composable<Ruta.Bienvenida>(
            // Sin deslizar: el sol y los planetas siguen en su sitio y desde ahi
            // se van al horizonte y a sus burbujas. Solo se funde lo demas.
            exitTransition = { if (targetState.es<Ruta.Personas>()) fadeOut(tween(200)) else slideOutHorizontally { -it } },
        ) {
            // Un enlace puede haber rehecho la pila con la bienvenida debajo
            // cuando ya estaba hecha: entonces no se ensena, se va a Personas.
            LaunchedEffect(ajustes.bienvenidaHecha) {
                if (ajustes.bienvenidaHecha) navegador.irAPersonas()
            }
            PantallaBienvenida(
                paso = pasoBienvenida,
                onPaso = { pasoBienvenida = it },
                contactos = contactos.orEmpty(),
                fotosPermitidas = fotosPermitidas,
                onAnadir = anadirPidiendoPermisos,
                onAbrir = { navegador.navigate(Ruta.Ficha(it.id)) },
                // Se acaba en la vista de burbujas, la que se acaba de ensenar.
                onTerminar = {
                    relevo.sacarFoto()
                    cambiarAjustes { it.copy(bienvenidaHecha = true, vista = VistaPersonas.BURBUJAS) }
                    Telemetria.evento(Telemetria.Evento.BIENVENIDA_TERMINADA, "people" to contactos.orEmpty().size)
                    navegador.irAPersonas()
                },
                relevo = relevo,
            )
        }

        composable<Ruta.Personas>(
            deepLinks = listOf(navDeepLink { uriPattern = Enlaces.PERSONAS }),
            enterTransition = { if (initialState.es<Ruta.Bienvenida>()) fadeIn(tween(300)) else slideInHorizontally { -it } },
        ) {
            // Pedir la valoracion de Play al volver aqui, si ya se ha usado lo bastante.
            LifecycleResumeEffect(Unit) {
                (context as? Activity)?.let(Valoracion::pedirSiToca)
                onPauseOrDispose { }
            }
            // Al plegar la tableta (o estrechar la ventana) con alguien abierto
            // al lado, su ficha pasa a pantalla completa.
            LaunchedEffect(anchoAmplio, seleccion) {
                val elegido = seleccion
                if (!anchoAmplio && elegido != null) {
                    vm.seleccionar(null)
                    navegador.navigate(Ruta.Ficha(elegido))
                }
            }
            val lista = @Composable { modifier: Modifier ->
                PantallaLista(
                    contactos = contactos,
                    fotosPermitidas = fotosPermitidas,
                    vista = ajustes.vista,
                    onCambiarVista = { vista ->
                        cambiarAjustes { it.copy(vista = vista) }
                        Telemetria.evento(Telemetria.Evento.VISTA_CAMBIADA, "view" to vista.name.lowercase())
                    },
                    onAnadir = anadirPidiendoPermisos,
                    onAbrir = { if (anchoAmplio) vm.seleccionar(it.id) else navegador.navigate(Ruta.Ficha(it.id)) },
                    onHablado = { anotarHoy(it.id) },
                    onAjustes = { navegador.navigate(Ruta.Ajustes) },
                    circulos = circulos,
                    filtro = filtro,
                    onFiltrar = vm::filtrar,
                    modifier = modifier,
                    relevo = relevo,
                )
            }
            if (anchoAmplio) {
                BackHandler(enabled = seleccion != null) { vm.seleccionar(null) }
                Row(Modifier.fillMaxSize()) {
                    lista(Modifier.weight(1f))
                    VerticalDivider()
                    Box(
                        Modifier
                            .widthIn(max = ANCHO_PANEL)
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        PanelFicha(vm, seleccion) { contacto -> Ficha(contacto, enPanel = true) { vm.seleccionar(null) } }
                    }
                }
            } else {
                lista(Modifier)
            }
        }

        composable<Ruta.Ajustes> { entrada ->
            PantallaAjustes(
                ajustes = ajustes,
                onCambiar = cambiarAjustes,
                onVolver = { navegador.volverDesde(entrada) },
                onExportar = vm::exportar,
                onImportar = vm::importar,
            )
        }

        composable<Ruta.Ficha>(
            deepLinks = listOf(navDeepLink<Ruta.Ficha>(basePath = "${Enlaces.ESQUEMA}://ficha")),
        ) { entrada ->
            val id = entrada.toRoute<Ruta.Ficha>().id
            val volver = { navegador.volverDesde(entrada) }
            // En pantalla ancha, abierta sobre Personas (un enlace, o al
            // desplegar el plegable): se pasa al panel de al lado.
            LaunchedEffect(anchoAmplio) {
                if (anchoAmplio && navegador.previousBackStackEntry?.destination?.hasRoute<Ruta.Personas>() == true) {
                    vm.seleccionar(id)
                    volver()
                }
            }
            var contacto by remember { mutableStateOf<Contacto?>(null) }
            LaunchedEffect(id) {
                var primera = true
                vm.observar(id).collect { leido ->
                    // Ya no existe (borrado, o un enlace viejo): de vuelta.
                    if (leido == null) {
                        volver()
                    } else {
                        if (primera) vm.refrescarCumpleanos(leido)
                        primera = false
                        contacto = leido
                    }
                }
            }
            contacto?.let { Ficha(it, enPanel = false, onVolver = volver) }
        }

        composable<Ruta.Nueva> { entrada ->
            val borrador by vm.borrador.collectAsStateWithLifecycle()
            val volver = {
                vm.descartarAlta()
                navegador.volverDesde(entrada)
            }
            // Tras guardar, o si se perdio el borrador, no queda nada que ensenar.
            LaunchedEffect(borrador) { if (borrador == null) navegador.volverDesde(entrada) }
            borrador?.let { nuevo ->
                PantallaFicha(
                    borrador = nuevo,
                    observar = vm::observar,
                    circulos = circulos,
                    fotosPermitidas = fotosPermitidas,
                    onPedirFotos = pedirFotos,
                    onAnadir = vm::anadir,
                    onActualizar = { _, _, _ -> },
                    onNotas = { _, _ -> },
                    onCirculo = { _, _ -> },
                    onCumpleanos = { _, _ -> },
                    onContactar = { telefono, medio -> Contactar.abrir(context, telefono, medio) },
                    onLlamadoHoy = {},
                    onPausar = { _, _ -> },
                    onReanudar = {},
                    onEliminar = {},
                    onForzarNotificacion = {},
                    onVolver = volver,
                )
            }
        }
    }
}

/** Panel derecho en pantalla ancha: la ficha elegida o una invitacion a elegir. */
@Composable
private fun PanelFicha(vm: ContactosViewModel, seleccion: Long?, ficha: @Composable (Contacto) -> Unit) {
    var contacto by remember(seleccion) { mutableStateOf<Contacto?>(null) }
    LaunchedEffect(seleccion) {
        val id = seleccion ?: return@LaunchedEffect
        var primera = true
        vm.observar(id).collect { leido ->
            if (leido == null) {
                vm.seleccionar(null)
            } else {
                if (primera) vm.refrescarCumpleanos(leido)
                primera = false
                contacto = leido
            }
        }
    }
    val actual = contacto
    if (seleccion != null && actual != null) {
        ficha(actual)
    } else {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.elige_a_alguien),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
    }
}

/**
 * De la bienvenida a Personas, sin dejarla debajo. singleTop: la piden a la
 * vez el boton de acabar y la propia bienvenida al verse ya hecha, y solo
 * debe quedar una.
 */
private fun NavHostController.irAPersonas() {
    navigate(Ruta.Personas) {
        popUpTo(Ruta.Bienvenida) { inclusive = true }
        launchSingleTop = true
    }
}

private inline fun <reified T : Any> NavBackStackEntry.es(): Boolean = destination.hasRoute<T>()

/**
 * Atras desde [entrada], solo si sigue siendo la de arriba: un boton pulsado
 * dos veces, o borrar y a la vez ver que la fila ya no existe, no deben
 * sacar tambien la pantalla de debajo.
 */
private fun NavHostController.volverDesde(entrada: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entrada.id) popBackStack()
}
