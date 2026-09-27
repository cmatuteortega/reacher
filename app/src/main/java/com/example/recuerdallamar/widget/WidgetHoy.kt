package com.example.recuerdallamar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.recuerdallamar.Enlaces
import com.example.recuerdallamar.Idioma
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.porUrgencia
import com.example.recuerdallamar.ui.claro
import com.example.recuerdallamar.ui.oscuro
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Los colores de la app, en claro y oscuro segun el sistema. */
private val colores = ColorProviders(light = claro, dark = oscuro)

/**
 * Widget "Hoy toca": a quien le toca hoy (o ya se paso), por urgencia, y quien
 * cumple anos. Tocar a alguien abre su ficha; el titulo, Personas; el +, la
 * agenda para anadir. Se redibuja cada vez que cambia la base de datos (ver
 * App) y una vez al dia desde los trabajos de avisos, que es cuando cambia la fecha.
 */
class WidgetHoy : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hoy = LocalDate.now()
        val gente = BaseDatos.de(context).contactos().lista()
            .filter { (it.tocaLlamar(hoy) && !it.pausado()) || it.esCumpleanos(hoy) }
            .porUrgencia(hoy)
        // Fuera de una actividad: el idioma elegido en Ajustes hay que ponerlo a mano.
        val textos = Idioma.envolver(context)
        provideContent {
            GlanceTheme(colors = colores) {
                Contenido(textos, gente, hoy)
            }
        }
    }

    companion object {
        suspend fun actualizar(context: Context) {
            runCatching { WidgetHoy().updateAll(context) }
        }
    }
}

class ReceptorWidgetHoy : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetHoy()
}

@Composable
private fun Contenido(textos: Context, gente: List<Contacto>, hoy: LocalDate) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                textos.getString(R.string.widget_titulo),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(Enlaces.personas(textos))),
            )
            Image(
                provider = ImageProvider(R.drawable.ic_anadir),
                contentDescription = textos.getString(R.string.anadir_persona),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier
                    .size(48.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity(Enlaces.nuevo(textos))),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        if (gente.isEmpty()) {
            Text(
                textos.getString(R.string.widget_nadie),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(Enlaces.personas(textos))),
            )
        } else {
            LazyColumn {
                items(gente, itemId = { it.id }) { contacto ->
                    Fila(textos, contacto, hoy)
                }
            }
        }
    }
}

@Composable
private fun Fila(textos: Context, contacto: Contacto, hoy: LocalDate) {
    val cumple = contacto.esCumpleanos(hoy)
    val tarde = ChronoUnit.DAYS.between(contacto.proximoAviso(), hoy).toInt()
    val detalle = when {
        cumple -> textos.getString(R.string.cumpleanos_corto)
        tarde > 0 -> textos.resources.getQuantityString(R.plurals.dias_tarde, tarde, tarde)
        else -> textos.getString(R.string.toca_hoy)
    }
    Column(
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(actionStartActivity(Enlaces.ficha(textos, contacto.id)))
            .semantics { contentDescription = "${contacto.nombre}, $detalle" },
    ) {
        Text(
            (if (cumple) "🎂 " else "") + contacto.nombre,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            detalle,
            maxLines = 1,
            style = TextStyle(
                color = if (tarde > 0 && !cumple) GlanceTheme.colors.tertiary else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
            ),
        )
    }
}
