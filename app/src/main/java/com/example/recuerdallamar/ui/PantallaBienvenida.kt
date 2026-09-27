package com.example.recuerdallamar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.datos.Contacto

/** Pasos de la bienvenida: tu eres el sol, crea tu sistema y "ya puedes cerrar". */
const val PASOS_BIENVENIDA = 3

/**
 * Primera vez que se abre la app. Todo pasa sobre tu sistema: arriba el sol
 * (tu), que se queda quieto entre paso y paso, y las personas que anades
 * entran en su orbita. Debajo cambia el texto de cada paso: que el sol eres
 * tu y la gente tu sistema (y de paso se piden los permisos), anadir al
 * menos a una persona, y el final con el recado de que ya se puede cerrar.
 *
 * Anadir abre la ficha de siempre; al darla de alta se vuelve aqui, al
 * mismo paso, con la persona ya girando alrededor del sol.
 */
@Composable
fun PantallaBienvenida(
    paso: Int,
    onPaso: (Int) -> Unit,
    contactos: List<Contacto>,
    fotosPermitidas: Boolean,
    onPedirPermisos: () -> Unit,
    onAnadir: () -> Unit,
    onAbrir: (Contacto) -> Unit,
    onTerminar: () -> Unit,
    modifier: Modifier = Modifier,
    relevo: Relevo? = null,
) {
    BackHandler(enabled = paso > 0) { onPaso(paso - 1) }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(vertical = 16.dp),
        ) {
            Puntos(paso)
            // El sistema ocupa el ancho entero: los anillos de fuera necesitan sitio.
            SistemaSolar(
                contactos = contactos,
                fotosPermitidas = fotosPermitidas,
                solGrande = paso == 0,
                onAbrir = onAbrir,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                relevo = relevo,
            )
            AnimatedContent(
                targetState = paso,
                transitionSpec = {
                    val avanza = targetState > initialState
                    (slideInHorizontally { if (avanza) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (avanza) -it / 3 else it / 3 } + fadeOut())
                },
                label = "paso",
                modifier = Modifier.padding(horizontal = 24.dp),
            ) { actual ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (actual) {
                        0 -> PasoSol(
                            onEmpezar = {
                                onPedirPermisos()
                                onPaso(1)
                            },
                        )
                        1 -> PasoAnadir(
                            cuantos = contactos.size,
                            onAnadir = onAnadir,
                            onSeguir = { onPaso(2) },
                        )
                        else -> PasoListo(
                            hayAlguien = contactos.isNotEmpty(),
                            onTerminar = onTerminar,
                        )
                    }
                }
            }
        }
    }
}

/** Por donde va: un punto por paso, alargado el actual. */
@Composable
private fun Puntos(paso: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        repeat(PASOS_BIENVENIDA) { i ->
            val ancho by animateDpAsState(if (i == paso) 24.dp else 8.dp, label = "punto")
            val color by animateColorAsState(
                if (i <= paso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "color punto",
            )
            Box(
                Modifier
                    .size(width = ancho, height = 8.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun PasoSol(onEmpezar: () -> Unit) {
    Text(
        "ConTacto",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Titulo("Tú eres el sol")
    Spacer(Modifier.height(12.dp))
    Explicacion(
        "A tu alrededor gira la gente que te importa: es tu sistema. " +
            "ConTacto te avisa cuando toca llamar o escribir a cada uno, para que nadie se aleje de tu órbita.",
    )
    Spacer(Modifier.height(24.dp))
    BotonPrincipal("Empezar", onEmpezar)
    Spacer(Modifier.height(48.dp)) // mismo pie que los otros pasos, sin boton secundario
}

@Composable
private fun PasoAnadir(cuantos: Int, onAnadir: () -> Unit, onSeguir: () -> Unit) {
    val hayAlguien = cuantos > 0
    Titulo(
        when (cuantos) {
            0 -> "Crea tu sistema"
            1 -> "¡Ya tienes a alguien en órbita!"
            else -> "Tu sistema crece"
        },
    )
    Spacer(Modifier.height(12.dp))
    Explicacion(
        if (hayAlguien) {
            "Añade a alguien más o sigue: siempre podrás sumar gente a tu sistema después."
        } else {
            "Elige de tu agenda a quien no quieres perder de vista y entrará en tu órbita. " +
                "En su ficha eliges cada cuántos días hablar y cómo: llamada, WhatsApp, SMS o Telegram."
        },
    )
    Spacer(Modifier.height(24.dp))
    if (hayAlguien) {
        BotonPrincipal("Continuar", onSeguir)
        OutlinedButton(onClick = onAnadir, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Añadir a otra persona")
        }
    } else {
        BotonPrincipal("Elegir de la agenda", onAnadir, icono = true)
        TextButton(onClick = onSeguir, modifier = Modifier.height(48.dp)) { Text("Ahora no") }
    }
}

@Composable
private fun PasoListo(hayAlguien: Boolean, onTerminar: () -> Unit) {
    Titulo("¡Ya está!")
    Spacer(Modifier.height(12.dp))
    Explicacion(
        if (hayAlguien) {
            "Este es tu sistema. Ahora puedes cerrar la app y seguir con tu vida: " +
                "te avisaremos cuando toque hablar con cada uno."
        } else {
            "Tu sistema te espera: quien añadas entrará en tu órbita. " +
                "Te avisaremos cuando toque hablar con cada uno."
        },
    )
    Spacer(Modifier.height(24.dp))
    BotonPrincipal("Entendido", onTerminar)
    Spacer(Modifier.height(48.dp))
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Explicacion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BotonPrincipal(texto: String, onClick: () -> Unit, icono: Boolean = false) {
    // El acento de la paleta, como el boton de anadir de la lista.
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        if (icono) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(texto, style = MaterialTheme.typography.titleMedium)
    }
}
