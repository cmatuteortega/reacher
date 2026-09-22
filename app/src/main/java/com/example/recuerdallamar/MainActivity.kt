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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.ui.PantallaFicha
import com.example.recuerdallamar.ui.PantallaLista
import com.example.recuerdallamar.ui.TemaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaApp { AppRecuerda() }
        }
    }
}

/** Frecuencia que se propone al elegir a alguien nuevo. */
private const val FRECUENCIA_INICIAL = 7

@Composable
private fun AppRecuerda(vm: ContactosViewModel = viewModel()) {
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
            val ajustes = Uri.fromParts("package", context.packageName, null)
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ajustes))
        } else {
            pedirPermisos.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }
    }

    val elegir = rememberLauncherForActivityResult(ElegirTelefono()) { uri ->
        val elegido = uri?.let { leerTelefono(context, it) } ?: return@rememberLauncherForActivityResult
        val (nombre, telefono) = elegido
        vm.abrirFicha(Contacto(nombre = nombre, telefono = telefono, frecuenciaDias = FRECUENCIA_INICIAL))
    }

    BackHandler(enabled = ficha != null) { vm.cerrarFicha() }

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
            PantallaLista(
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
            )
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
