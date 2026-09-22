package com.example.recuerdallamar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.datos.Contacto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLista(
    contactos: List<Contacto>?,
    onAnadir: () -> Unit,
    onAbrir: (Contacto) -> Unit,
) {
    val estado = rememberLazyListState()

    // Los nuevos entran arriba; si la lista estaba desplazada, se subiria el
    // alta por encima de lo visible y la animacion no se veria.
    var tamano by remember { mutableIntStateOf(contactos?.size ?: 0) }
    LaunchedEffect(contactos?.size) {
        val nuevo = contactos?.size ?: 0
        if (nuevo > tamano) estado.animateScrollToItem(0)
        tamano = nuevo
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recuerda llamar") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAnadir,
                // El naranja de la paleta, reservado para lo que pide accion.
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Añadir") },
            )
        },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            AnimatedVisibility(
                visible = contactos?.isEmpty() == true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    "Aún no hay nadie.\nPulsa Añadir y elige un contacto de tu agenda.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }

            LazyColumn(
                state = estado,
                contentPadding = PaddingValues(bottom = 96.dp), // que el FAB no tape la ultima fila
                modifier = Modifier.fillMaxSize(),
            ) {
                items(contactos.orEmpty(), key = { it.id }) { contacto ->
                    FilaContacto(
                        contacto = contacto,
                        onClick = { onAbrir(contacto) },
                        // Al entrar un contacto, aparece fundido y el resto baja con muelle.
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaContacto(contacto: Contacto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val toca = contacto.tocaLlamar()
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(contacto.nombre) },
        supportingContent = {
            Text("Cada ${contacto.frecuenciaDias} días · próximo aviso ${contacto.proximoAviso().bonita()}")
        },
        leadingContent = { Inicial(contacto.nombre) },
        trailingContent = {
            // La campana crece desde nada cuando vence el plazo.
            AnimatedVisibility(visible = toca, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = "Toca llamar",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
    )
}

@Composable
private fun Inicial(nombre: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Text(
            nombre.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
