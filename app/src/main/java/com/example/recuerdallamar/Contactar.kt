package com.example.recuerdallamar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.recuerdallamar.datos.MedioContacto
import java.util.Locale

/**
 * Abre la forma de contacto elegida con el numero puesto. Solo LLAMADA marca
 * sin pasar por el marcador, y necesita CALL_PHONE; sin el permiso se abre el
 * marcador como si nada.
 */
object Contactar {

    fun puedeLlamar(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // ACTION_CALL solo si puedeLlamar, comprobado aqui mismo
    fun abrir(context: Context, telefono: String, medio: MedioContacto) {
        val intent = intencion(telefono, medio, puedeLlamar(context), pais(context))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Normal: no tiene WhatsApp, Telegram o app de SMS. No es un fallo de la app.
            Registro.info("contactar", "sin app para ${medio.name}")
            avisarSinApp(context, medio)
        } catch (e: SecurityException) {
            // Quitaron CALL_PHONE entre la comprobacion y la llamada, o una ROM
            // que no deja marcar: que al menos se abra el marcador.
            Registro.fallo("contactar", e, "medio" to medio.name)
            try {
                context.startActivity(intencion(telefono, MedioContacto.MARCADOR, false, "").also {
                    if (context !is Activity) it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (otro: RuntimeException) {
                avisarSinApp(context, medio)
            }
        }
    }

    private fun avisarSinApp(context: Context, medio: MedioContacto) {
        val textos = Idioma.envolver(context)
        Toast.makeText(context, textos.getString(R.string.sin_app_para, textos.getString(medio.boton)), Toast.LENGTH_SHORT).show()
    }

    /**
     * Lo que se abre para cada forma de contacto. Solo LLAMADA con [puedeLlamar]
     * marca directamente; [pais] (ISO, "ES") completa los numeros sin prefijo.
     */
    fun intencion(telefono: String, medio: MedioContacto, puedeLlamar: Boolean, pais: String): Intent =
        // fromParts codifica '#', '*' y espacios, que en un Uri.parse se perderian.
        when (medio) {
            MedioContacto.MARCADOR -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", telefono, null))
            MedioContacto.LLAMADA ->
                if (puedeLlamar) {
                    Intent(Intent.ACTION_CALL, Uri.fromParts("tel", telefono, null))
                } else {
                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", telefono, null))
                }
            MedioContacto.SMS -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", telefono, null))
            // Los enlaces web los abre la app si esta instalada, y si no el navegador.
            MedioContacto.WHATSAPP ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${internacional(telefono, pais)}"))
            MedioContacto.TELEGRAM ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+${internacional(telefono, pais)}"))
        }

    /** El pais de la SIM, o el del idioma del telefono si no hay SIM. */
    private fun pais(context: Context): String =
        context.getSystemService(TelephonyManager::class.java)
            ?.simCountryIso
            ?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country

    /**
     * WhatsApp y Telegram quieren el numero con prefijo de pais y solo cifras.
     * Los de la agenda suelen venir sin prefijo ("600 12 34 56"): se completa
     * con [pais].
     */
    fun internacional(telefono: String, pais: String): String {
        val e164 = PhoneNumberUtils.formatNumberToE164(telefono, pais.uppercase(Locale.ROOT))
        return (e164 ?: telefono).filter(Char::isDigit).removePrefix("00")
    }
}
