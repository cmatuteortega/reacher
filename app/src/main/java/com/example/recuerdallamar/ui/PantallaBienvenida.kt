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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.FotoContacto
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Contacto

/** Pasos de la bienvenida: que es, a quien anadir y "ya puedes cerrar". */
const val PASOS_BIENVENIDA = 3

/** Caras que caben en la fila del paso de anadir antes de resumir con "+N". */
private const val CARAS_MAX = 5

/**
 * Primera vez que se abre la app. Tres pasos cortos: que hace ConTacto
 * (y de paso se piden los permisos), anadir al menos a una persona, y el
 * final con sus burbujas y el recado de que ya se puede cerrar la app.
 *
 * Anadir abre la ficha de siempre; al darla de alta se vuelve aqui, al
 * mismo paso, con la persona ya en la fila.
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
) {
    BackHandler(enabled = paso > 0) { onPaso(paso - 1) }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Puntos(paso)
            AnimatedContent(
                targetState = paso,
                transitionSpec = {
                    val avanza = targetState > initialState
                    (slideInHorizontally { if (avanza) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (avanza) -it / 3 else it / 3 } + fadeOut())
                },
                label = "paso",
                modifier = Modifier.weight(1f),
            ) { actual ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (actual) {
                        0 -> PasoQueEs(
                            onEmpezar = {
                                onPedirPermisos()
                                onPaso(1)
                            },
                        )
                        1 -> PasoAnadir(
                            contactos = contactos,
                            fotosPermitidas = fotosPermitidas,
                            onAnadir = onAnadir,
                            onSeguir = { onPaso(2) },
                        )
                        else -> PasoListo(
                            contactos = contactos,
                            fotosPermitidas = fotosPermitidas,
                            onAbrir = onAbrir,
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
private fun ColumnScope.PasoQueEs(onEmpezar: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                painterResource(R.drawable.ic_notificacion),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Titulo("ConTacto")
        Spacer(Modifier.height(12.dp))
        Explicacion(
            "Para no perder el contacto con la gente que te importa.\n\n" +
                "Eliges a quién y cada cuántos días quieres hablar. " +
                "ConTacto te avisa cuando toca llamar o escribir.",
        )
    }
    BotonPrincipal("Empezar", onEmpezar)
    Spacer(Modifier.height(48.dp)) // mismo pie que los otros pasos, sin boton secundario
}

@Composable
private fun ColumnScope.PasoAnadir(
    contactos: List<Contacto>,
    fotosPermitidas: Boolean,
    onAnadir: () -> Unit,
    onSeguir: () -> Unit,
) {
    val hayAlguien = contactos.isNotEmpty()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Titulo(if (hayAlguien) "¡Bien!" else "¿Con quién quieres\nmantener el contacto?")
        Spacer(Modifier.height(12.dp))
        Explicacion(
            if (hayAlguien) {
                "Añade a alguien más o sigue: siempre podrás añadir más gente después."
            } else {
                "Añade al menos a una persona de tu agenda. En su ficha eliges cada " +
                    "cuántos días quieres hablar con ella y cómo: llamada, WhatsApp, SMS o Telegram."
            },
        )
        Spacer(Modifier.height(32.dp))
        if (hayAlguien) {
            Caras(contactos, fotosPermitidas)
        }
    }
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
private fun ColumnScope.PasoListo(
    contactos: List<Contacto>,
    fotosPermitidas: Boolean,
    onAbrir: (Contacto) -> Unit,
    onTerminar: () -> Unit,
) {
    val hayAlguien = contactos.isNotEmpty()
    Titulo("¡Ya está!")
    Spacer(Modifier.height(12.dp))
    Explicacion(
        if (hayAlguien) {
            "Estas son tus burbujas: cuanto más grande, antes toca hablar con esa persona."
        } else {
            "Cuando añadas a alguien aparecerá aquí como una burbuja: cuanto más grande, antes toca hablar con esa persona."
        },
    )
    // Las mismas burbujas de la pantalla principal, ya vivas: se pueden arrastrar.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        if (hayAlguien) {
            VistaBurbujas(contactos = contactos, fotosPermitidas = fotosPermitidas, onAbrir = onAbrir)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Ahora puedes cerrar la app y seguir con tu vida. " +
                "Te avisaremos cuando llegue el momento de hablar con cada uno.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    BotonPrincipal("Entendido", onTerminar)
    Spacer(Modifier.height(48.dp))
}

/** Quienes se han anadido ya, en fila; si son muchos, los ultimos se resumen. */
@Composable
private fun Caras(contactos: List<Contacto>, fotosPermitidas: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        contactos.take(CARAS_MAX).forEach { contacto ->
            Cara(contacto, fotosPermitidas)
        }
        val resto = contactos.size - CARAS_MAX
        if (resto > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Text("+$resto", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Cara(contacto: Contacto, fotosPermitidas: Boolean) {
    val context = LocalContext.current
    var foto by remember(contacto.telefono) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(contacto.telefono, fotosPermitidas) {
        foto = FotoContacto.cargar(context, contacto.telefono, miniatura = true)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        Avatar(contacto.nombre, foto, 52.dp, MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            contacto.nombre.substringBefore(' '),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
    // El naranja de la paleta, como el boton de anadir de la lista.
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
