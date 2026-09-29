package com.example.recuerdallamar.escaparate

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.recuerdallamar.App
import com.example.recuerdallamar.Bateria
import com.example.recuerdallamar.Enlaces
import com.example.recuerdallamar.MainActivity
import com.example.recuerdallamar.R
import com.example.recuerdallamar.datos.AlmacenAjustes
import com.example.recuerdallamar.datos.BaseDatos
import com.example.recuerdallamar.datos.Contacto
import com.example.recuerdallamar.datos.IdiomaElegido
import com.example.recuerdallamar.datos.MedioContacto
import com.example.recuerdallamar.datos.TemaElegido
import com.example.recuerdallamar.datos.VistaPersonas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.MonthDay
import java.util.Locale

/*
 * Las capturas de la ficha de Play: las pantallas de verdad, con gente de
 * ejemplo en cada idioma, enmarcadas con un titulo sobre el marino. No son
 * pruebas: sin la variable ESCAPARATE se saltan. Con ella (la raiz del repo),
 * dejan las imagenes en fastlane/metadata/android/<idioma>/images/phoneScreenshots:
 *
 *   ESCAPARATE=$PWD ./gradlew :app:testDebugUnitTest --tests '*Escaparate*'
 *
 * Con ESCAPARATE_CRUDO=<carpeta>, ademas, las pantallas sin enmarcar.
 * Los titulos estan en TITULOS; la gente, en GENTE. Tambien el icono de la
 * tienda y el grafico destacado (EscaparateGraficosTest).
 */

enum class Lengua(val carpeta: String, val idioma: IdiomaElegido, val locale: Locale) {
    ES("es-ES", IdiomaElegido.ES, Locale("es", "ES")),
    EN("en-US", IdiomaElegido.EN, Locale("en", "US")),
    FR("fr-FR", IdiomaElegido.FR, Locale("fr", "FR")),
    DE("de-DE", IdiomaElegido.DE, Locale("de", "DE")),
    RU("ru-RU", IdiomaElegido.RU, Locale("ru", "RU")),
}

/**
 * Cada captura, en el orden de la ficha, alternando claro y oscuro: orbitas,
 * burbujas (oscuro), ficha, horas (oscuro), orbitas en oscuro.
 */
private val TITULOS = mapOf(
    Lengua.ES to listOf(
        "Tu gente, en órbita a tu alrededor",
        "Cuanto más tiempo sin hablar, más grande la burbuja",
        "Llama, escribe o manda un WhatsApp con un toque",
        "Un aviso al día, solo en tus horas",
        "Órbitas, burbujas o lista; claro u oscuro",
    ),
    Lengua.EN to listOf(
        "Your people, orbiting around you",
        "The longer it's been, the bigger the bubble",
        "Call, text or WhatsApp in one tap",
        "One gentle reminder a day, in your hours",
        "Orbits, bubbles or a list; light or dark",
    ),
    Lengua.FR to listOf(
        "Tes proches, en orbite autour de toi",
        "Plus ça fait longtemps, plus la bulle grossit",
        "Appelle, écris ou envoie un WhatsApp en un geste",
        "Un rappel par jour, à tes heures",
        "Orbites, bulles ou liste ; clair ou sombre",
    ),
    Lengua.DE to listOf(
        "Deine Liebsten in deiner Umlaufbahn",
        "Je länger es her ist, desto größer die Blase",
        "Anrufen, schreiben oder WhatsApp mit einem Tipp",
        "Eine Erinnerung am Tag, zu deinen Zeiten",
        "Umlaufbahnen, Blasen oder Liste; hell oder dunkel",
    ),
    Lengua.RU to listOf(
        "Твои близкие — на твоей орбите",
        "Чем дольше не общались, тем больше пузырь",
        "Звонок, SMS или WhatsApp в одно касание",
        "Одно напоминание в день — в твои часы",
        "Орбиты, пузыри или список; светлая или тёмная тема",
    ),
)

/** Nombres de ejemplo; el primero es el de la ficha. */
private val GENTE = mapOf(
    Lengua.ES to listOf("Mamá", "Lucía", "Javier", "Abuela Carmen", "Papá", "Marta", "Diego", "Elena"),
    Lengua.EN to listOf("Mom", "Emma", "James", "Grandma Rose", "Dad", "Olivia", "Noah", "Sophie"),
    Lengua.FR to listOf("Maman", "Camille", "Lucas", "Mamie Jeanne", "Papa", "Léa", "Hugo", "Chloé"),
    Lengua.DE to listOf("Mama", "Lena", "Jonas", "Oma Helga", "Papa", "Mia", "Felix", "Hannah"),
    Lengua.RU to listOf("Мама", "Аня", "Дима", "Бабушка Валя", "Папа", "Катя", "Миша", "Оля"),
)

private val NOTAS = mapOf(
    Lengua.ES to "Preguntarle por el viaje a Lisboa",
    Lengua.EN to "Ask about the Lisbon trip",
    Lengua.FR to "Lui demander pour le voyage à Lisbonne",
    Lengua.DE to "Nach der Lissabon-Reise fragen",
    Lengua.RU to "Спросить про поездку в Лиссабон",
)

private const val ANCHO = 1080
private const val ALTO = 1920

private fun lenguas(): List<Array<Any>> = Lengua.entries.map { arrayOf<Any>(it) }

private fun localizado(app: Context, lengua: Lengua): Context = app.createConfigurationContext(
    Configuration(app.resources.configuration).apply { setLocale(lengua.locale) },
)

/** La ventana entera dibujada en un bitmap (captureToImage espera un redibujado que Robolectric no da). */
private fun capturar(actividad: Activity): Bitmap {
    val vista = actividad.window.decorView
    return Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888).also { vista.draw(Canvas(it)) }
}

private fun guardar(lengua: Lengua, n: Int, nombre: String, pantalla: Bitmap) {
    System.getenv("ESCAPARATE_CRUDO")?.let { crudo ->
        File(crudo).mkdirs()
        File(crudo, "${lengua.carpeta}-${n}_$nombre.png").outputStream().use { pantalla.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    val carpeta = File(System.getenv("ESCAPARATE"), "fastlane/metadata/android/${lengua.carpeta}/images/phoneScreenshots")
    carpeta.mkdirs()
    val imagen = enmarcar(pantalla, TITULOS.getValue(lengua)[n - 1])
    File(carpeta, "${n}_$nombre.png").outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** 1080x1920: titulo arriba, en crema sobre marino con el sol detras; la pantalla debajo, con esquinas redondas. */
private fun enmarcar(pantalla: Bitmap, titulo: String): Bitmap {
    val imagen = Bitmap.createBitmap(ANCHO, ALTO, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(imagen)
    val pintura = Paint(Paint.ANTI_ALIAS_FLAG)

    pintura.shader = LinearGradient(0f, 0f, 0f, ALTO.toFloat(), 0xFF203C56.toInt(), 0xFF0D2B45.toInt(), Shader.TileMode.CLAMP)
    lienzo.drawRect(0f, 0f, ANCHO.toFloat(), ALTO.toFloat(), pintura)
    // El sol, asomando por arriba.
    pintura.shader = RadialGradient(
        ANCHO / 2f, -120f, 620f,
        intArrayOf(0x66FFAA5E, 0x22D08159, 0x00D08159), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
    )
    lienzo.drawRect(0f, 0f, ANCHO.toFloat(), 700f, pintura)
    pintura.shader = null

    val letra = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFECD6.toInt()
        textSize = 66f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val margen = 90
    val texto = StaticLayout.Builder.obtain(titulo, 0, titulo.length, letra, ANCHO - 2 * margen)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, 1.08f)
        .setMaxLines(3)
        .build()
    val cabecera = 360f
    lienzo.save()
    lienzo.translate(margen.toFloat(), (cabecera - texto.height) / 2f + 20f)
    texto.draw(lienzo)
    lienzo.restore()

    // La pantalla, entera, en lo que queda; una sombra debajo.
    val alto = ALTO - cabecera - 70f
    val ancho = alto * pantalla.width / pantalla.height
    val caja = RectF((ANCHO - ancho) / 2f, cabecera, (ANCHO + ancho) / 2f, cabecera + alto)
    val radio = 44f
    pintura.color = 0x55000000
    lienzo.drawRoundRect(RectF(caja).apply { offset(0f, 14f) }, radio, radio, pintura)
    val escala = ancho / pantalla.width
    pintura.color = 0xFF000000.toInt() // opaca: el alpha de la pintura tambien se aplica al shader
    pintura.isFilterBitmap = true
    pintura.shader = BitmapShader(pantalla, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(escala, escala); postTranslate(caja.left, caja.top) })
    }
    lienzo.drawRoundRect(caja, radio, radio, pintura)
    pintura.shader = null
    pintura.style = Paint.Style.STROKE
    pintura.strokeWidth = 3f
    pintura.color = 0x40FFECD6
    lienzo.drawRoundRect(caja, radio, radio, pintura)
    return imagen
}

/**
 * Orbitas, burbujas y ficha, con gente. Con el reloj de Compose a mano: si
 * avanza solo, la regla cancela las animaciones sin fin, y las orbitas
 * colocan a cada uno con ellas (sin fotogramas se quedan vacias).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h786dp-xxhdpi")
class EscaparateTest(private val lengua: Lengua) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parametros(): List<Array<Any>> = lenguas()
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: App = ApplicationProvider.getApplicationContext()
    private val almacen = AlmacenAjustes.de(app)
    private var escenario: ActivityScenario<MainActivity>? = null

    @After
    fun cerrar() {
        escenario?.close()
    }

    @Test
    fun personas() {
        assumeTrue(System.getenv("ESCAPARATE") != null)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Bateria.marcarAvisado(app)
        almacen.cambiar {
            it.copy(
                bienvenidaHecha = true,
                idioma = lengua.idioma,
                tema = TemaElegido.CLARO,
                vista = VistaPersonas.ORBITAS,
                estadisticas = false,
            )
        }
        val ids = sembrar()

        compose.mainClock.autoAdvance = false
        escenario = ActivityScenario.launch(MainActivity::class.java)
        pasar(4_000)
        guardar(lengua, 1, "orbitas", pantalla())

        almacen.cambiar { it.copy(vista = VistaPersonas.BURBUJAS, tema = TemaElegido.OSCURO) }
        pasar(4_000)
        guardar(lengua, 2, "burbujas", pantalla())

        almacen.cambiar { it.copy(vista = VistaPersonas.ORBITAS, tema = TemaElegido.OSCURO) }
        pasar(4_000)
        guardar(lengua, 5, "orbitas-oscuro", pantalla())
        escenario!!.close()

        almacen.cambiar { it.copy(tema = TemaElegido.CLARO) }
        escenario = ActivityScenario.launch(Enlaces.ficha(app, ids.first()))
        pasar(3_000)
        guardar(lengua, 3, "ficha", pantalla())
    }

    private fun pantalla(): Bitmap {
        lateinit var bitmap: Bitmap
        escenario!!.onActivity { bitmap = capturar(it) }
        return bitmap
    }

    /** [ms] de reloj, a fotogramas de 16 ms; entre medias, tiempo de verdad para Room y DataStore. */
    private fun pasar(ms: Long) {
        repeat((ms / 16).toInt()) {
            compose.mainClock.advanceTimeBy(16)
            if (it % 10 == 0) {
                Thread.sleep(5)
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    /** Ocho personas con urgencias variadas: alguien a quien ya toca, alguien recien llamado. */
    private fun sembrar(): List<Long> {
        val hoy = LocalDate.now()
        val textos = localizado(app, lengua)
        val familia = textos.getString(R.string.circulo_familia)
        val amigos = textos.getString(R.string.circulo_amigos)
        val trabajo = textos.getString(R.string.circulo_trabajo)

        data class Dato(val cada: Int, val hace: Int, val circulo: String, val medio: MedioContacto)
        val datos = listOf(
            Dato(7, 9, familia, MedioContacto.WHATSAPP),
            Dato(14, 12, amigos, MedioContacto.WHATSAPP),
            Dato(30, 34, amigos, MedioContacto.TELEGRAM),
            Dato(14, 10, familia, MedioContacto.MARCADOR),
            Dato(7, 2, familia, MedioContacto.MARCADOR),
            Dato(30, 18, trabajo, MedioContacto.SMS),
            Dato(60, 50, amigos, MedioContacto.WHATSAPP),
            Dato(14, 3, trabajo, MedioContacto.WHATSAPP),
        )
        val dao = BaseDatos.de(app).contactos()
        return runBlocking {
            GENTE.getValue(lengua).zip(datos).mapIndexed { i, (nombre, dato) ->
                dao.insertar(
                    Contacto(
                        nombre = nombre,
                        telefono = "+34 600 000 0${10 + i}",
                        frecuenciaDias = dato.cada,
                        ultimoContacto = hoy.minusDays(dato.hace.toLong()),
                        medio = dato.medio,
                        circulo = dato.circulo,
                        notas = if (i == 0) NOTAS.getValue(lengua) else "",
                        cumpleanos = if (i == 0) MonthDay.from(hoy.plusDays(12)) else null,
                    ),
                )
            }
        }
    }
}

/** El paso "Cuando y como" de la bienvenida. Con la regla de Compose: hay que pulsar Empezar. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h786dp-xxhdpi")
class EscaparateBienvenidaTest(private val lengua: Lengua) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parametros(): List<Array<Any>> = lenguas()
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: App = ApplicationProvider.getApplicationContext()
    private var escenario: ActivityScenario<MainActivity>? = null

    @After
    fun cerrar() {
        escenario?.close()
    }

    @Test
    fun horas() {
        assumeTrue(System.getenv("ESCAPARATE") != null)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AlmacenAjustes.de(app).cambiar {
            it.copy(idioma = lengua.idioma, tema = TemaElegido.OSCURO, horaDesde = 10, horaHasta = 21)
        }
        escenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithText(localizado(app, lengua).getString(R.string.empezar)).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        escenario!!.onActivity { bitmap = capturar(it) }
        guardar(lengua, 4, "horas", bitmap)
    }
}

/**
 * El icono de la tienda (512x512, cuadrado entero: Play le pone la mascara) y
 * el grafico destacado (1024x500): el sol del icono sobre marino, con el
 * nombre y el subtitulo de cada idioma.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EscaparateGraficosTest(private val lengua: Lengua) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parametros(): List<Array<Any>> = lenguas()
    }

    private val app: App = ApplicationProvider.getApplicationContext()

    @Test
    fun graficos() {
        assumeTrue(System.getenv("ESCAPARATE") != null)
        val carpeta = File(System.getenv("ESCAPARATE"), "fastlane/metadata/android/${lengua.carpeta}/images").apply { mkdirs() }
        File(carpeta, "icon.png").outputStream().use { icono(512).compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(carpeta, "featureGraphic.png").outputStream().use { destacado().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** El icono adaptativo sin mascara: fondo y primer plano, los 108dp enteros. */
    private fun icono(lado: Int, conFondo: Boolean = true): Bitmap {
        val imagen = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(imagen)
        if (conFondo) lienzo.drawColor(app.getColor(R.color.icono_fondo))
        app.getDrawable(R.drawable.ic_launcher_foreground)!!.apply {
            setBounds(0, 0, lado, lado)
            draw(lienzo)
        }
        return imagen
    }

    private fun destacado(): Bitmap {
        val ancho = 1024
        val alto = 500
        val imagen = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(imagen)
        val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
        pintura.shader = LinearGradient(0f, 0f, ancho.toFloat(), alto.toFloat(), 0xFF203C56.toInt(), 0xFF0D2B45.toInt(), Shader.TileMode.CLAMP)
        lienzo.drawRect(0f, 0f, ancho.toFloat(), alto.toFloat(), pintura)
        val solX = 250f
        val solY = alto / 2f
        pintura.shader = RadialGradient(
            solX, solY, 360f,
            intArrayOf(0x55FFAA5E, 0x18D08159, 0x00D08159), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        lienzo.drawRect(0f, 0f, ancho.toFloat(), alto.toFloat(), pintura)
        pintura.shader = null
        // Los anillos, a trazos como en la app.
        pintura.style = Paint.Style.STROKE
        pintura.strokeWidth = 2.5f
        pintura.color = 0x33FFECD6
        pintura.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
        listOf(250f, 340f).forEach { lienzo.drawCircle(solX, solY, it, pintura) }
        pintura.pathEffect = null
        pintura.style = Paint.Style.FILL
        pintura.color = 0xFF000000.toInt() // opaca: si no, el sol sale con el alpha de los anillos

        val lado = 520
        lienzo.drawBitmap(icono(lado, conFondo = false), solX - lado / 2f, solY - lado / 2f, pintura)

        val textos = localizado(app, lengua)
        val nombre = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFECD6.toInt()
            textSize = 104f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFAA5E.toInt()
            textSize = 46f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val x = 520f
        lienzo.drawText(textos.getString(R.string.app_name), x, alto / 2f - 6f, nombre)
        val subtitulo = StaticLayout.Builder.obtain(
            textos.getString(R.string.subtitulo), 0, textos.getString(R.string.subtitulo).length, sub, (ancho - x - 40).toInt(),
        ).setMaxLines(2).build()
        lienzo.save()
        lienzo.translate(x, alto / 2f + 30f)
        subtitulo.draw(lienzo)
        lienzo.restore()
        return imagen
    }
}
