package com.example.recuerdallamar

import android.Manifest
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
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

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (!concedido) {
            Toast.makeText(context, "Sin permiso no habra recordatorios", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notificaciones.permitidas(context)) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                onGuardar = vm::guardar,
                onLlamar = { Marcador.abrir(context, it) },
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
