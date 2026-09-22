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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recuerdallamar.avisos.Notificaciones
import com.example.recuerdallamar.datos.Ajustes
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.ui.PantallaAjustes
import com.example.recuerdallamar.ui.PantallaFicha
import com.example.recuerdallamar.ui.PantallaLista
import com.example.recuerdallamar.ui.TemaApp
import com.example.recuerdallamar.ui.esOscuro

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            TemaApp(modoOscuro = oscuro) { AppRecuerda(ajustes, almacen::cambiar) }
        }
    }
}

// Los mismos velos que pone enableEdgeToEdge() por defecto tras los botones de navegacion.
private val ESTOR_CLARO = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val ESTOR_OSCURO = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private enum class Seccion(val etiqueta: String, val icono: ImageVector) {
    PERSONAS("Personas", Icons.Filled.Person),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

/** Frecuencia que se propone al elegir a alguien nuevo. */
private const val FRECUENCIA_INICIAL = 7

@Composable
private fun AppRecuerda(
    ajustes: Ajustes,
    cambiarAjustes: ((Ajustes) -> Ajustes) -> Unit,
    vm: ContactosViewModel = viewModel(),
) {
    val context = LocalContext.current
    val contactos by vm.contactos.collectAsStateWithLifecycle()
    val ficha by vm.ficha.collectAsStateWithLifecycle()

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

    // Lo que hace falta, en un solo paso al abrir: avisos siempre que falten,
    // fotos solo la primera vez (luego se piden desde la ficha).
    LaunchedEffect(Unit) {
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

    var seccion by rememberSaveable { mutableStateOf(Seccion.PERSONAS) }

    BackHandler(enabled = ficha != null) { vm.cerrarFicha() }
    // Atras desde Ajustes vuelve a Personas antes de salir de la app.
    BackHandler(enabled = ficha == null && seccion != Seccion.PERSONAS) { seccion = Seccion.PERSONAS }

    // contentKey solo distingue lista/ficha: cambiar de ficha a ficha no anima.
    AnimatedContent(
        targetState = ficha,
        contentKey = { it == null },
        transitionSpec = {
            val entra = targetState != null
            slideInHorizontally { if (entra) it else -it } togetherWith
                slideOutHorizontally { if (entra) -it else it }
        },
        label = "pantalla",
    ) { abierta ->
        if (abierta == null) {
            // La barra de secciones solo esta fuera de la ficha, que ocupa la pantalla entera.
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    NavigationBar {
                        Seccion.entries.forEach { opcion ->
                            NavigationBarItem(
                                selected = seccion == opcion,
                                onClick = { seccion = opcion },
                                icon = { Icon(opcion.icono, contentDescription = null) },
                                label = { Text(opcion.etiqueta) },
                            )
                        }
                    }
                },
            ) { relleno ->
                val dentro = Modifier.padding(relleno).consumeWindowInsets(relleno)
                Crossfade(targetState = seccion, label = "seccion") { actual ->
                    when (actual) {
                        Seccion.PERSONAS -> PantallaLista(
                            contactos = contactos,
                            fotosPermitidas = fotosPermitidas,
                            onAnadir = {
                                try {
                                    elegir.launch(Unit)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Toast.makeText(context, "No hay agenda de contactos", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAbrir = vm::abrirFicha,
                            modifier = dentro,
                        )
                        Seccion.AJUSTES -> PantallaAjustes(
                            ajustes = ajustes,
                            onCambiar = cambiarAjustes,
                            modifier = dentro,
                        )
                    }
                }
            }
        } else {
            PantallaFicha(
                borrador = abierta,
                observar = vm::observar,
                fotosPermitidas = fotosPermitidas,
                onPedirFotos = pedirFotos,
                onGuardar = vm::guardar,
                onContactar = { telefono, medio -> Contactar.abrir(context, telefono, medio) },
                onLlamadoHoy = { id ->
                    vm.llamadoHoy(id)
                    Toast.makeText(context, "Anotado: último contacto hoy", Toast.LENGTH_SHORT).show()
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
