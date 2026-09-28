package com.example.recuerdallamar.ui

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los anillos de las orbitas van por frecuencia; la urgencia solo cambia el tamano. */
class OrbitasTest {
    private val densidad = Density(2.75f)

    private fun plano(personas: List<EnOrbita>) =
        planearOrbitas(personas, ancho = 1080f, alto = 2000f, solY = -60f, solRadio = 220f, largoLetrero = 180f, densidad = densidad)

    /** 12 personas: 4 cada semana, 4 cada dos, 4 cada mes. */
    private fun gente(urgencia: (Long) -> Float) = listOf(7, 7, 7, 7, 14, 14, 14, 14, 30, 30, 30, 30)
        .mapIndexed { i, f -> EnOrbita(i.toLong(), f, urgencia(i.toLong())) }

    @Test
    fun deDentroAfueraDeLaMasFrecuenteALaQueMenosYElLetreroNoMiente() {
        val p = plano(gente { 0.5f })
        val porAnillo = p.puestos.entries.groupBy({ it.value.anillo }, { it.key.toInt() })
        val frecuencias = gente { 0f }.map { it.frecuencia }
        var antes = 0
        for (k in p.radios.indices) {
            val suyas = porAnillo[k].orEmpty().map { frecuencias[it] }
            if (suyas.isEmpty()) continue
            assertTrue(suyas.min() >= antes)
            antes = suyas.max()
            assertEquals(suyas.max(), p.letreros[k].dias)
        }
        assertEquals(30, antes)
    }

    @Test
    fun conSitioLasFrecuenciasIgualesVanJuntas() {
        // Una cada semana, tres cada dos, cinco cada mes: 20 %, 35 % y 45 %, mas o menos.
        val gente = listOf(7, 14, 14, 14, 30, 30, 30, 30, 30).mapIndexed { i, f -> EnOrbita(i.toLong(), f, 0.2f) }
        val p = planearOrbitas(gente, ancho = 2400f, alto = 2400f, solY = -60f, solRadio = 220f, largoLetrero = 180f, densidad = densidad)
        assertEquals(listOf(7, 14, 30), p.letreros.map { it.dias })
        val anillos = p.puestos.entries.sortedBy { it.key }.map { it.value.anillo }
        assertEquals(listOf(0, 1, 1, 1, 2, 2, 2, 2, 2), anillos)
    }

    @Test
    fun loQueNoCabeJuntoAlSolPasaAlSiguienteEnOrden() {
        // Cuatro cada semana no caben en el anillo de dentro de un movil.
        val p = plano(gente { 0.5f })
        val deDentro = p.puestos.filterValues { it.anillo == 0 }.keys
        assertTrue(deDentro.isNotEmpty() && deDentro.size < 4)
        assertEquals(7, p.letreros[0].dias)
        assertEquals(14, p.letreros[1].dias)
    }

    @Test
    fun conLosDiasNadieCambiaDeAnillo() {
        val antes = plano(gente { 0.1f })
        // Pasa el tiempo: unos ya tocan, otros van con retraso.
        val despues = plano(gente { if (it % 3 == 0L) 1.4f else 0.6f })
        antes.puestos.forEach { (id, puesto) -> assertEquals(puesto.anillo, despues.puestos.getValue(id).anillo) }
        // Pero crecen.
        assertTrue(despues.puestos.getValue(0L).radio > antes.puestos.getValue(0L).radio)
    }

    @Test
    fun lasBurbujasNoPisanElLetrero() {
        val p = plano(gente { 1f })
        p.puestos.values.forEach { puesto ->
            val r = p.radios[puesto.anillo]
            val inicio = p.letreros[puesto.anillo].angulo * r + 180f
            assertTrue(puesto.angulo * r - puesto.radio >= inicio - 0.5f)
        }
    }
}
