package com.example.recuerdallamar.ui

import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.recuerdallamar.FotoContacto
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.MedioContacto
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Tope de dias de frecuencia: el mismo de antes con el campo de texto. */
private const val FRECUENCIA_MAXIMA = 9999

/** A partir de este ancho la ficha va en dos columnas, como los ajustes. */
private val ANCHO_DOS_COLUMNAS = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFicha(
    borrador: Contacto,
    observar: (Long) -> Flow<Contacto?>,
    circulos: List<String>,
    fotosPermitidas: Boolean,
    onPedirFotos: () -> Unit,
    /** Alta: el contacto ya lleva frecuencia, medio, notas y circulo. */
    onAnadir: (Contacto) -> Unit,
    onActualizar: (Long, Int, MedioContacto) -> Unit,
    onNotas: (Long, String) -> Unit,
    onCirculo: (Long, String?) -> Unit,
    onContactar: (String, MedioContacto) -> Unit,
    onLlamadoHoy: (Long) -> Unit,
    onPausar: (Long, Int) -> Unit,
    onReanudar: (Long) -> Unit,
    onEliminar: (Long) -> Unit,
    onForzarNotificacion: (Long) -> Unit,
    onVolver: () -> Unit,
    /** Junto a la lista en pantalla ancha: la flecha de volver pasa a ser una X que la cierra. */
    enPanel: Boolean = false,
) {
    val guardado = borrador.id != 0L

    // Un contacto ya guardado se lee en vivo: si se pulsa su notificacion con
    // la ficha abierta, la marca aparece sin salir y volver a entrar.
    val flujo = remember(borrador.id) { if (guardado) observar(borrador.id) else flowOf(borrador) }
    val actual = flujo.collectAsState(initial = borrador).value ?: borrador

    var frecuencia by rememberSaveable(borrador.id) { mutableIntStateOf(borrador.frecuenciaDias.coerceIn(1, FRECUENCIA_MAXIMA)) }
    var medio by rememberSaveable(borrador.id) { mutableStateOf(borrador.medio) }
    var notas by rememberSaveable(borrador.id) { mutableStateOf(borrador.notas) }
    var circulo by rememberSaveable(borrador.id) { mutableStateOf(borrador.circulo) }

    // Lo que se esta editando, para que el anillo y las fechas respondan al momento.
    val vistaPrevia = actual.copy(frecuenciaDias = frecuencia)

    // Alguien que ya existe se guarda solo al cambiar algo; el aviso de
    // "Guardado" aparece un momento arriba para que se note.
    var guardadoHace by remember { mutableIntStateOf(0) }
    LaunchedEffect(frecuencia, medio) {
        if (guardado && (frecuencia != actual.frecuenciaDias || medio != actual.medio)) {
            onActualizar(actual.id, frecuencia, medio)
            guardadoHace++
        }
    }
    // Las notas se guardan al dejar de escribir (lo espera el ViewModel), sin "Guardado".
    LaunchedEffect(notas) {
        if (guardado && notas.trim() != actual.notas) onNotas(actual.id, notas)
    }
    LaunchedEffect(circulo) {
        if (guardado && circulo != actual.circulo) {
            onCirculo(actual.id, circulo)
            guardadoHace++
        }
    }

    val context = LocalContext.current

    // Sin permiso de contactos no hay foto: se ve la inicial y un boton para pedirlo.
    var foto by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(actual.telefono, fotosPermitidas) {
        foto = FotoContacto.cargar(context, actual.telefono)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (guardado) "" else stringResource(R.string.nuevo_contacto)) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        if (enPanel) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cerrar))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.volver))
                        }
                    }
                },
                actions = { if (guardado) AvisoGuardado(guardadoHace) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            BarraAcciones(
                medio = medio,
                // Solo al dar de alta: lo que ya existe se guarda solo.
                onAnadir = if (guardado) {
                    null
                } else {
                    { onAnadir(actual.copy(frecuenciaDias = frecuencia, medio = medio, notas = notas.trim(), circulo = circulo)) }
                },
                // Prueba lo elegido aunque aun no se haya guardado.
                onContactar = { onContactar(actual.telefono, medio) },
            )
        },
    ) { relleno ->
        BoxWithConstraints(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            val dosColumnas = maxWidth >= ANCHO_DOS_COLUMNAS
            val cabecera: @Composable ColumnScope.() -> Unit = {
                Cabecera(vistaPrevia, guardado, foto, fotosPermitidas, onPedirFotos)
                if (guardado) {
                    val yaHoy = actual.ultimoContacto == LocalDate.now()
                    BotonMantener(
                        texto = stringResource(if (yaHoy) R.string.ultimo_contacto_hoy else R.string.manten_para_anotar),
                        habilitado = !yaHoy,
                        onConfirmar = { onLlamadoHoy(actual.id) },
                    )
                    Fechas(vistaPrevia)
                }
            }
            val ajustes: @Composable ColumnScope.() -> Unit = {
                TarjetaFicha(stringResource(R.string.cada_cuanto)) {
                    SelectorFrecuencia(frecuencia, onCambio = { frecuencia = it })
                }
                TarjetaFicha(stringResource(R.string.al_tocar_aviso)) {
                    SelectorMedio(medio = medio, onCambio = { medio = it })
                    Text(
                        stringResource(medio.etiqueta),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                TarjetaFicha(stringResource(R.string.circulo)) {
                    SelectorCirculo(circulo, circulos, onCambio = { circulo = it })
                }
                TarjetaFicha(stringResource(R.string.notas)) {
                    OutlinedTextField(
                        value = notas,
                        onValueChange = { notas = it },
                        placeholder = { Text(stringResource(R.string.notas_pista)) },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (guardado) {
                    PausaYBorrado(
                        contacto = actual,
                        onPausar = { dias -> onPausar(actual.id, dias) },
                        onReanudar = { onReanudar(actual.id) },
                        onEliminar = { onEliminar(actual.id) },
                    )
                } else {
                    Text(
                        stringResource(R.string.se_guardara_hoy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                Depuracion(guardado) { onForzarNotificacion(actual.id) }
            }

            Column(
                Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (dosColumnas) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = cabecera)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = ajustes)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        cabecera()
                        ajustes()
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Foto grande dentro de un anillo que se va llenando segun pasa el tiempo
 * desde el ultimo contacto: lleno toca llamar, y en teja si ya se paso.
 * Sigue a la frecuencia mientras se cambia.
 */
@Composable
private fun Cabecera(
    contacto: Contacto,
    guardado: Boolean,
    foto: ImageBitmap?,
    fotosPermitidas: Boolean,
    onPedirFotos: () -> Unit,
) {
    val hoy = LocalDate.now()
    val colores = MaterialTheme.colorScheme
    val urgencia = contacto.urgencia(hoy)
    val lleno by animateFloatAsState(
        if (guardado) urgencia.coerceIn(0f, 1f) else 0f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "anilloFicha",
    )
    val colorAnillo by animateColorAsState(if (urgencia >= 1f) colores.tertiary else colores.primary, label = "colorAnillo")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val grosor = 8.dp.toPx()
                val radio = size.minDimension / 2f - grosor / 2f
                drawCircle(colores.surfaceContainerHighest, radio, style = Stroke(grosor))
                if (lleno > 0f) {
                    drawArc(
                        color = colorAnillo,
                        startAngle = -90f,
                        sweepAngle = 360f * lleno,
                        useCenter = false,
                        topLeft = Offset(center.x - radio, center.y - radio),
                        size = Size(radio * 2, radio * 2),
                        style = Stroke(grosor, cap = StrokeCap.Round),
                    )
                }
            }
            Avatar(contacto.nombre, foto, 124.dp, MaterialTheme.typography.displayMedium)
        }
        Text(contacto.nombre, style = MaterialTheme.typography.headlineMedium)
        Text(contacto.telefono, style = MaterialTheme.typography.bodyLarge, color = colores.onSurfaceVariant)
        contacto.cumpleanos?.let { dia ->
            val faltan = contacto.diasHastaCumpleanos(hoy) ?: 0L
            Text(
                "🎂 " + when (faltan) {
                    0L -> stringResource(R.string.cumpleanos_es_hoy)
                    else -> stringResource(
                        R.string.cumpleanos_el,
                        dia.bonito(),
                        pluralStringResource(R.plurals.en_dias, faltan.toInt(), faltan.toInt()),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (faltan == 0L) colores.tertiary else colores.onSurfaceVariant,
            )
        }
        if (guardado) {
            val faltan = ChronoUnit.DAYS.between(hoy, contacto.proximoAviso()).toInt()
            AnimatedContent(
                targetState = faltan,
                transitionSpec = {
                    val sube = targetState > initialState
                    (slideInVertically { if (sube) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (sube) -it else it } + fadeOut())
                },
                label = "faltan",
            ) { n ->
                Surface(
                    shape = CircleShape,
                    color = if (n <= 0) colores.tertiaryContainer else colores.primaryContainer,
                ) {
                    Text(
                        when {
                            n > 1 -> pluralStringResource(R.plurals.toca_en_dias, n, n)
                            n == 1 -> stringResource(R.string.toca_manana)
                            n == 0 -> stringResource(R.string.toca_hoy)
                            else -> pluralStringResource(R.plurals.dias_tarde, -n, -n)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (!fotosPermitidas) {
            TextButton(onClick = onPedirFotos) { Text(stringResource(R.string.mostrar_foto)) }
        }
    }
}

/**
 * Boton que hay que mantener: se va llenando con un tic en cada cuarto y al
 * completarse confirma con una vibracion firme. Soltar antes lo vacia. Evita
 * anotar una llamada por un roce; con lector de pantalla basta un toque.
 */
@Composable
private fun BotonMantener(texto: String, habilitado: Boolean, onConfirmar: () -> Unit) {
    val vista = LocalView.current
    val alcance = rememberCoroutineScope()
    val progreso = remember { Animatable(0f) }
    val confirmar by rememberUpdatedState(onConfirmar)
    LaunchedEffect(habilitado) { progreso.snapTo(0f) }

    val colores = MaterialTheme.colorScheme
    val fondo by animateColorAsState(
        if (habilitado) colores.secondaryContainer else colores.surfaceContainerHigh,
        label = "fondoMantener",
    )
    val escala = 1f - 0.04f * progreso.value
    val heLlamadoHoy = stringResource(R.string.he_llamado_hoy)
    val anotarLlamada = stringResource(R.string.anotar_llamada_hoy)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }
            .clip(RoundedCornerShape(30.dp))
            .background(fondo)
            .semantics {
                role = Role.Button
                contentDescription = if (habilitado) heLlamadoHoy else texto
                if (habilitado) {
                    onClick(anotarLlamada) {
                        confirmar()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(habilitado) {
                if (!habilitado) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    vista.toque()
                    var cuarto = 0
                    val llenar = alcance.launch {
                        progreso.animateTo(1f, tween(durationMillis = 700, easing = LinearEasing)) {
                            val ahora = (value * 4).toInt()
                            if (ahora > cuarto) {
                                cuarto = ahora
                                if (ahora < 4) vista.tic()
                            }
                        }
                        vista.confirmar()
                        confirmar()
                    }
                    waitForUpOrCancellation()
                    if (progreso.value < 1f) {
                        llenar.cancel()
                        alcance.launch { progreso.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progreso.value)
                .background(colores.primary.copy(alpha = 0.35f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colores.onSecondaryContainer)
            Text(texto, style = MaterialTheme.typography.labelLarge, color = colores.onSecondaryContainer)
        }
    }
}

/** Ultimo contacto y proximo aviso en dos casillas, y lo que ha pasado con el aviso debajo. */
@Composable
private fun Fechas(contacto: Contacto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dato(stringResource(R.string.ultimo_contacto), contacto.ultimoContacto.bonita(), Modifier.weight(1f))
            Dato(stringResource(R.string.proximo_aviso), contacto.proximoAviso().bonita(), Modifier.weight(1f))
        }
        val pulsada = contacto.fechaPulsacion
        Text(
            if (contacto.notificacionPulsada && pulsada != null) {
                stringResource(R.string.notificacion_pulsada, pulsada.bonito())
            } else {
                stringResource(R.string.notificacion_no_pulsada)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (contacto.descartes > 0) {
            Text(
                stringResource(
                    R.string.aviso_quitado,
                    pluralStringResource(R.plurals.veces, contacto.descartes, contacto.descartes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Frecuencia en dias: numero grande que se arrastra de lado (un tic por dia),
 * botones - y + que repiten y aceleran si se mantienen, y atajos comunes debajo.
 */
@Composable
private fun SelectorFrecuencia(valor: Int, onCambio: (Int) -> Unit) {
    val vista = LocalView.current
    val actual by rememberUpdatedState(valor)
    val cambio by rememberUpdatedState(onCambio)
    val recursos = LocalContext.current.resources
    val frecuencia = stringResource(R.string.frecuencia)
    val cadaTanto = recursos.cadaDias(valor)
    val poner = { n: Int ->
        val nuevo = n.coerceIn(1, FRECUENCIA_MAXIMA)
        if (nuevo != actual) {
            vista.tic()
            cambio(nuevo)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        BotonRepetir("−", stringResource(R.string.un_dia_menos), habilitado = valor > 1) { poner(actual - 1) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = frecuencia
                    stateDescription = cadaTanto
                    setProgress { valor ->
                        poner(valor.roundToInt())
                        true
                    }
                }
                .pointerInput(Unit) {
                    val paso = 14.dp.toPx()
                    var acumulado = 0f
                    detectHorizontalDragGestures(onDragStart = { acumulado = 0f }) { cambioPuntero, dx ->
                        cambioPuntero.consume()
                        acumulado += dx
                        val pasos = (acumulado / paso).toInt()
                        if (pasos != 0) {
                            acumulado -= pasos * paso
                            poner(actual + pasos)
                        }
                    }
                },
        ) {
            Text(stringResource(R.string.cada), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(
                targetState = valor,
                transitionSpec = {
                    val sube = targetState > initialState
                    (slideInVertically { if (sube) it / 2 else -it / 2 } + fadeIn(tween(120))) togetherWith
                        (slideOutVertically { if (sube) -it / 2 else it / 2 } + fadeOut(tween(120)))
                },
                label = "frecuencia",
            ) { n ->
                Text(n.toString(), style = MaterialTheme.typography.displayMedium)
            }
            Text(
                pluralStringResource(R.plurals.unidad_dias_desliza, valor),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BotonRepetir("+", stringResource(R.string.un_dia_mas), habilitado = valor < FRECUENCIA_MAXIMA) { poner(actual + 1) }
    }
    Deslizador(
        opciones = ATAJOS_FRECUENCIA,
        elegida = valor,
        texto = { recursos.atajo(it) },
        descripcion = { recursos.cadaDias(it) },
        onElegir = { cambio(it) },
    )
}

/** Boton redondo que da un paso al tocarlo y, si se mantiene, repite cada vez mas deprisa. */
@Composable
private fun BotonRepetir(simbolo: String, descripcion: String, habilitado: Boolean, onPaso: () -> Unit) {
    val paso by rememberUpdatedState(onPaso)
    val alcance = rememberCoroutineScope()
    val interaccion = remember { MutableInteractionSource() }
    val escala = escalaAlPulsar(interaccion, hundido = 0.88f)
    val colores = MaterialTheme.colorScheme

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
                alpha = if (habilitado) 1f else 0.38f
            }
            .clip(CircleShape)
            .background(colores.secondaryContainer)
            .semantics {
                role = Role.Button
                contentDescription = descripcion
                if (habilitado) onClick { paso(); true } else disabled()
            }
            .pointerInput(habilitado) {
                if (!habilitado) return@pointerInput
                awaitEachGesture {
                    val abajo = awaitFirstDown()
                    val pulsacion = PressInteraction.Press(abajo.position)
                    interaccion.tryEmit(pulsacion)
                    paso()
                    val repetir = alcance.launch {
                        delay(400)
                        var espera = 140L
                        while (true) {
                            paso()
                            delay(espera)
                            espera = max(25L, (espera * 0.85f).toLong())
                        }
                    }
                    waitForUpOrCancellation()
                    repetir.cancel()
                    interaccion.tryEmit(PressInteraction.Release(pulsacion))
                }
            },
    ) {
        Text(simbolo, style = MaterialTheme.typography.headlineMedium, color = colores.onSecondaryContainer)
    }
}

/**
 * "Guardado" con una marca, que entra al guardarse un cambio y se va solo.
 * [cambios] cuenta los guardados: cada uno nuevo lo vuelve a ensenar.
 */
@Composable
private fun AvisoGuardado(cambios: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(cambios) {
        if (cambios == 0) return@LaunchedEffect
        visible = true
        delay(1500)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(end = 16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.guardado), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Contactar siempre a mano, pegado abajo. Al dar de alta, al lado va "Añadir";
 * con [onAnadir] null (alguien que ya existe) Contactar ocupa toda la barra.
 */
@Composable
private fun BarraAcciones(medio: MedioContacto, onAnadir: (() -> Unit)?, onContactar: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Sin nada que anadir, Contactar es la accion principal y va en el color fuerte.
            val colores = if (onAnadir == null) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
            Button(
                onClick = onContactar,
                colors = colores,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(medio.icono(), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(medio.boton))
            }
            if (onAnadir != null) {
                Button(
                    onClick = onAnadir,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.anadir))
                }
            }
        }
    }
}

@Composable
private fun TarjetaFicha(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
            contenido()
        }
    }
}

/**
 * Pausar los avisos de esta persona (sin tocar su ultimo contacto) o
 * eliminarla. Junto a "He llamado hoy", es adonde lleva "Mas opciones" del aviso.
 */
@Composable
private fun PausaYBorrado(
    contacto: Contacto,
    onPausar: (Int) -> Unit,
    onReanudar: () -> Unit,
    onEliminar: () -> Unit,
) {
    var diasPausa by rememberSaveable { mutableIntStateOf(PAUSA_INICIAL) }
    var confirmandoBorrado by rememberSaveable { mutableStateOf(false) }
    val recursos = LocalContext.current.resources

    TarjetaFicha(stringResource(R.string.avisos_de, contacto.nombre)) {
        val pausa = contacto.pausadoHasta?.takeIf { contacto.pausado() }
        if (pausa != null) {
            Text(
                stringResource(R.string.en_pausa_burbuja, pausa.toLocalDate().bonita()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            OutlinedButton(onClick = onReanudar, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.reanudar_avisos))
            }
        } else {
            Deslizador(
                opciones = PLAZOS_PAUSA,
                elegida = diasPausa,
                texto = { recursos.getString(R.string.dias_corto, it) },
                descripcion = { recursos.dias(it) },
                onElegir = { diasPausa = it },
            )
            OutlinedButton(onClick = { onPausar(diasPausa) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.pausar, recursos.dias(diasPausa)))
            }
        }
        TextButton(
            onClick = { confirmandoBorrado = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.eliminar_contacto))
        }
    }

    if (confirmandoBorrado) {
        AlertDialog(
            onDismissRequest = { confirmandoBorrado = false },
            title = { Text(stringResource(R.string.eliminar_a, contacto.nombre)) },
            text = { Text(stringResource(R.string.eliminar_texto)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmandoBorrado = false
                        onEliminar()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.eliminar)) }
            },
            dismissButton = { TextButton(onClick = { confirmandoBorrado = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
private fun Depuracion(guardado: Boolean, onForzar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(stringResource(R.string.depuracion), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onForzar, enabled = guardado, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.forzar_notificacion))
        }
        Text(
            stringResource(if (guardado) R.string.forzar_explicacion else R.string.guarda_primero),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dias de pausa que se proponen al pausar a alguien. */
private const val PAUSA_INICIAL = 7

private val PLAZOS_PAUSA = listOf(1, 3, 7, 14, 30)

private val ATAJOS_FRECUENCIA = listOf(7, 14, 30, 60, 90, 180, 365)

private fun Resources.atajo(d: Int): String = when (d) {
    7 -> getString(R.string.atajo_1_semana)
    14 -> getString(R.string.atajo_2_semanas)
    30 -> getString(R.string.atajo_1_mes)
    60 -> getString(R.string.atajo_2_meses)
    90 -> getString(R.string.atajo_3_meses)
    180 -> getString(R.string.atajo_6_meses)
    365 -> getString(R.string.atajo_1_anio)
    else -> getString(R.string.dias_corto, d)
}

/**
 * Circulo de la persona (familia, amigos, trabajo...): uno como mucho. Se
 * elige entre los que ya hay o se crea uno nuevo con su nombre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorCirculo(circulo: String?, circulos: List<String>, onCambio: (String?) -> Unit) {
    val vista = LocalView.current
    var creando by rememberSaveable { mutableStateOf(false) }
    val opciones = (circulos + listOfNotNull(circulo)).distinct()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = circulo == null,
            onClick = {
                vista.toque()
                onCambio(null)
            },
            label = { Text(stringResource(R.string.circulo_ninguno)) },
        )
        opciones.forEach { nombre ->
            FilterChip(
                selected = circulo == nombre,
                onClick = {
                    vista.toque()
                    onCambio(nombre)
                },
                label = { Text(nombre) },
            )
        }
        FilterChip(
            selected = false,
            onClick = { creando = true },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text(stringResource(R.string.circulo_nuevo)) },
        )
    }
    if (creando) {
        var nombre by rememberSaveable { mutableStateOf("") }
        val listo = nombre.isNotBlank()
        AlertDialog(
            onDismissRequest = { creando = false },
            title = { Text(stringResource(R.string.circulo_nuevo)) },
            text = {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it.take(30) },
                    placeholder = { Text(stringResource(R.string.circulo_pista)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = listo,
                    onClick = {
                        creando = false
                        onCambio(nombre.trim())
                    },
                ) { Text(stringResource(R.string.crear)) }
            },
            dismissButton = { TextButton(onClick = { creando = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}
