package com.example.recuerdallamar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    onHablado: (Contacto) -> Unit,
    onAjustes: () -> Unit,
    circulos: List<String>,
    filtro: String?,
    onFiltrar: (String?) -> Unit,
    modifier: Modifier = Modifier,
    listo: Boolean = false,
    onListoVisto: () -> Unit = {},
    relevo: Relevo? = null,
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

    // El sol de la esquina y el + van por encima de todo; el contenido les deja sitio.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val estadoArriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val radioSol = radioSolEsquina(maxWidth) + estadoArriba + PUNTA_ESQUINA
        val hueco = HUECO_MAS
        Scaffold(
            topBar = {
                TopAppBar(
                    // La esquina de la izquierda es del sol.
                    title = {},
                    actions = {
                        if (circulos.isNotEmpty()) FiltroCirculo(circulos, filtro, onFiltrar)
                        // Un solo boton que va pasando por las tres: orbitas, burbujas, lista.
                        val entradas = VistaPersonas.entries
                        val otra = entradas[(vista.ordinal + 1) % entradas.size]
                        IconButton(onClick = { onCambiarVista(otra) }) {
                            // El icono ensena a donde se va, no donde se esta.
                            Crossfade(targetState = otra, label = "icono vista") { destino ->
                                when (destino) {
                                    VistaPersonas.LISTA ->
                                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.ver_como_lista))
                                    VistaPersonas.BURBUJAS ->
                                        Icon(painterResource(R.drawable.ic_burbujas), contentDescription = stringResource(R.string.ver_como_burbujas))
                                    VistaPersonas.ORBITAS ->
                                        Icon(painterResource(R.drawable.ic_orbitas), contentDescription = stringResource(R.string.ver_como_orbitas))
                                }
                            }
                        }
                        IconButton(onClick = onAjustes) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.ajustes))
                        }
                    },
                )
            },
        ) { relleno ->
            // Lo que baja el sol por debajo de la barra de arriba.
            val arriba = relleno.calculateTopPadding()
            val bajoLaBarra = (radioSol - arriba).coerceAtLeast(0.dp)
            Box(Modifier.fillMaxSize().padding(relleno)) {
                AnimatedVisibility(
                    // Con el recado de la bienvenida delante ya se dice lo mismo.
                    visible = contactos?.isEmpty() == true && !listo,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center).padding(bottom = hueco),
                ) {
                    // Sin mascota: el sol de abajo ya mira y saluda.
                    Text(
                        stringResource(R.string.lista_vacia),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                // Las tres vistas, con los mismos datos y el mismo orden por urgencia.
                Crossfade(targetState = vista, label = "vista") { actual ->
                    when (actual) {
                        VistaPersonas.ORBITAS -> if (!contactos.isNullOrEmpty()) {
                            VistaOrbitas(
                                contactos = contactos,
                                fotosPermitidas = fotosPermitidas,
                                onAbrir = onAbrir,
                                onHablado = onHablado,
                                huecoInferior = hueco,
                                solArriba = arriba,
                                solRadio = radioSol,
                                relevo = relevo,
                            )
                        }
                        VistaPersonas.BURBUJAS -> if (!contactos.isNullOrEmpty()) {
                            VistaBurbujas(
                                contactos = contactos,
                                fotosPermitidas = fotosPermitidas,
                                onAbrir = onAbrir,
                                onHablado = onHablado,
                                huecoInferior = hueco,
                                solArriba = arriba,
                                solRadio = radioSol,
                                relevo = relevo,
                            )
                        }
                        VistaPersonas.LISTA -> LazyColumn(
                            state = estado,
                            // Que ni el sol tape la primera fila ni el + la ultima.
                            contentPadding = PaddingValues(top = bajoLaBarra, bottom = hueco),
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
        SolEsquina(relevo = relevo, cuantos = contactos?.size ?: 0, onAnadir = onAnadir)
        RecadoListo(
            visible = listo,
            hayAlguien = !contactos.isNullOrEmpty(),
            onEntendido = onListoVisto,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = hueco),
        )
    }
}

/** Lo que tarda el recado en salir: que antes llegue el sol a la esquina. */
private const val RECADO_ESPERA_MS = 900

/**
 * Al acabar la bienvenida, abajo y sobre el +: ya esta, se puede cerrar la
 * app, que ya se avisara. Se va con "Entendido" y no vuelve.
 */
@Composable
private fun RecadoListo(visible: Boolean, hayAlguien: Boolean, onEntendido: () -> Unit, modifier: Modifier = Modifier) {
    // Empieza oculto para que tambien entre animado la primera vez.
    val estado = remember { MutableTransitionState(false) }
    estado.targetState = visible
    AnimatedVisibility(
        visibleState = estado,
        enter = fadeIn(tween(400, delayMillis = RECADO_ESPERA_MS)) +
            slideInVertically(tween(400, delayMillis = RECADO_ESPERA_MS)) { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        ElevatedCard(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
                Text(
                    stringResource(R.string.ya_esta),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (hayAlguien) R.string.listo_con_gente else R.string.listo_sin_gente),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                TextButton(onClick = onEntendido, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.entendido))
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
        headlineContent = {
            Text(if (contacto.esCumpleanos()) "🎂 ${contacto.nombre}" else contacto.nombre)
        },
        overlineContent = contacto.circulo?.let { { Text(it) } },
        supportingContent = {
            val recursos = LocalContext.current.resources
            Text(
                recursos.cadaDias(contacto.frecuenciaDias) + " · " +
                    stringResource(R.string.proximo_aviso_el, contacto.proximoAviso().bonita()),
            )
        },
        leadingContent = { Avatar(contacto.nombre, foto, 40.dp, MaterialTheme.typography.titleMedium) },
        trailingContent = {
            // La campana crece desde nada cuando vence el plazo.
            AnimatedVisibility(visible = toca, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.toca_llamar),
                    tint = MaterialTheme.colorScheme.acentoLegible,
                )
            }
        },
    )
}

/**
 * Filtro por circulo, en la barra de arriba: una pastilla con el elegido (o
 * "Todos") que despliega los demas. Solo aparece si alguien tiene circulo.
 */
@Composable
private fun FiltroCirculo(circulos: List<String>, filtro: String?, onFiltrar: (String?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val activo = filtro != null && filtro in circulos
    Box {
        FilterChip(
            selected = activo,
            onClick = { abierto = true },
            label = { Text(if (activo) filtro!! else stringResource(R.string.circulo_todos), maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.padding(end = 4.dp),
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.circulo_todos)) },
                onClick = {
                    abierto = false
                    onFiltrar(null)
                },
            )
            circulos.forEach { circulo ->
                DropdownMenuItem(
                    text = { Text(circulo) },
                    onClick = {
                        abierto = false
                        onFiltrar(circulo)
                    },
                )
            }
        }
    }
}
