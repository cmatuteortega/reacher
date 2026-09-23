package com.example.recuerdallamar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.FotoContacto
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.VistaPersonas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLista(
    contactos: List<Contacto>?,
    fotosPermitidas: Boolean,
    vista: VistaPersonas,
    onCambiarVista: (VistaPersonas) -> Unit,
    onAnadir: () -> Unit,
    onAbrir: (Contacto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado = rememberLazyListState()

    // Un alta recien hablada no tiene urgencia y entra al final: se baja
    // hasta ella para que se vea llegar.
    var conocidos by remember { mutableStateOf(contactos?.map { it.id }?.toSet()) }
    LaunchedEffect(contactos) {
        val actuales = contactos ?: return@LaunchedEffect
        val antes = conocidos
        conocidos = actuales.map { it.id }.toSet()
        val nuevo = if (antes == null) -1 else actuales.indexOfFirst { it.id !in antes }
        if (nuevo >= 0 && vista == VistaPersonas.LISTA) estado.animateScrollToItem(nuevo)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Recuerda llamar") },
                actions = {
                    val otra = if (vista == VistaPersonas.BURBUJAS) VistaPersonas.LISTA else VistaPersonas.BURBUJAS
                    IconButton(onClick = { onCambiarVista(otra) }) {
                        // El icono ensena a donde se va, no donde se esta.
                        Crossfade(targetState = otra, label = "icono vista") { destino ->
                            when (destino) {
                                VistaPersonas.LISTA ->
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ver como lista")
                                VistaPersonas.BURBUJAS ->
                                    Icon(painterResource(R.drawable.ic_burbujas), contentDescription = "Ver como burbujas")
                            }
                        }
                    }
                },
            )
        },
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

            // Las dos vistas, con los mismos datos y el mismo orden por urgencia.
            Crossfade(targetState = vista, label = "vista") { actual ->
                when (actual) {
                    VistaPersonas.BURBUJAS -> if (!contactos.isNullOrEmpty()) {
                        VistaBurbujas(
                            contactos = contactos,
                            fotosPermitidas = fotosPermitidas,
                            onAbrir = onAbrir,
                        )
                    }
                    VistaPersonas.LISTA -> LazyColumn(
                        state = estado,
                        contentPadding = PaddingValues(bottom = 96.dp), // que el FAB no tape la ultima fila
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(contactos.orEmpty(), key = { it.id }) { contacto ->
                            FilaContacto(
                                contacto = contacto,
                                fotosPermitidas = fotosPermitidas,
                                onClick = { onAbrir(contacto) },
                                // Al entrar un contacto, aparece fundido y el resto se recoloca con muelle.
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
    }
}

@Composable
private fun FilaContacto(
    contacto: Contacto,
    fotosPermitidas: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toca = contacto.tocaLlamar()
    val context = LocalContext.current
    // Miniatura de la agenda; FotoContacto la guarda en cache, asi que volver a
    // la lista o desplazarla no la vuelve a leer.
    var foto by remember(contacto.telefono) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(contacto.telefono, fotosPermitidas) {
        foto = FotoContacto.cargar(context, contacto.telefono, miniatura = true)
    }
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(contacto.nombre) },
        supportingContent = {
            Text("Cada ${contacto.frecuenciaDias} días · próximo aviso ${contacto.proximoAviso().bonita()}")
        },
        leadingContent = { Avatar(contacto.nombre, foto, 40.dp, MaterialTheme.typography.titleMedium) },
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
