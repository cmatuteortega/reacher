package com.example.recuerdallamar

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.recuerdallamar.datos.MedioContacto
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric: Uri y PhoneNumberUtils son del framework. */
@RunWith(AndroidJUnit4::class)
class ContactarTest {

    @Test
    fun marcadorConNumeroTalCual() {
        val intent = Contactar.intencion("600 12 34 56", MedioContacto.MARCADOR, puedeLlamar = true, pais = "ES")
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data?.scheme)
        assertEquals("600 12 34 56", intent.data?.schemeSpecificPart)
    }

    @Test
    fun caracteresEspecialesNoSePierden() {
        // '#' en un Uri.parse se tomaria por fragmento.
        val intent = Contactar.intencion("*123#", MedioContacto.MARCADOR, puedeLlamar = false, pais = "ES")
        assertEquals("*123#", intent.data?.schemeSpecificPart)
    }

    @Test
    fun llamadaDirectaSoloConPermiso() {
        assertEquals(Intent.ACTION_CALL, Contactar.intencion("600", MedioContacto.LLAMADA, puedeLlamar = true, pais = "ES").action)
        assertEquals(Intent.ACTION_DIAL, Contactar.intencion("600", MedioContacto.LLAMADA, puedeLlamar = false, pais = "ES").action)
    }

    @Test
    fun sms() {
        val intent = Contactar.intencion("600123456", MedioContacto.SMS, puedeLlamar = false, pais = "ES")
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:600123456", intent.dataString)
    }

    @Test
    fun whatsappYTelegramConPrefijoDelPais() {
        assertEquals(
            "https://wa.me/34600123456",
            Contactar.intencion("600 12 34 56", MedioContacto.WHATSAPP, puedeLlamar = false, pais = "es").dataString,
        )
        assertEquals(
            "https://t.me/+34600123456",
            Contactar.intencion("600 12 34 56", MedioContacto.TELEGRAM, puedeLlamar = false, pais = "ES").dataString,
        )
    }

    @Test
    fun internacionalRespetaElPrefijoQueYaTiene() {
        assertEquals("447911123456", Contactar.internacional("+44 7911 123456", "ES"))
        assertEquals("447911123456", Contactar.internacional("0044 7911 123456", "ES"))
    }

    @Test
    fun internacionalSinPaisConocidoDejaLasCifras() {
        assertEquals("600123456", Contactar.internacional("600-12-34-56", ""))
    }
}
