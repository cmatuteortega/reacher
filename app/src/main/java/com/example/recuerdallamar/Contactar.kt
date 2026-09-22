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

    @SuppressLint("MissingPermission") // ACTION_CALL solo tras comprobar CALL_PHONE aqui mismo
    fun abrir(context: Context, telefono: String, medio: MedioContacto) {
        // fromParts codifica '#', '*' y espacios, que en un Uri.parse se perderian.
        val intent = when (medio) {
            MedioContacto.MARCADOR -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", telefono, null))
            MedioContacto.LLAMADA ->
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    Intent(Intent.ACTION_CALL, Uri.fromParts("tel", telefono, null))
                } else {
                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", telefono, null))
                }
            MedioContacto.SMS -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", telefono, null))
            // Los enlaces web los abre la app si esta instalada, y si no el navegador.
            MedioContacto.WHATSAPP ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${internacional(context, telefono)}"))
            MedioContacto.TELEGRAM ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+${internacional(context, telefono)}"))
        }
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay app para ${medio.boton}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * WhatsApp y Telegram quieren el numero con prefijo de pais y solo cifras.
     * Los de la agenda suelen venir sin prefijo ("600 12 34 56"): se completa
     * con el pais de la SIM, o el del idioma del telefono si no hay SIM.
     */
    private fun internacional(context: Context, telefono: String): String {
        val pais = context.getSystemService(TelephonyManager::class.java)
            ?.simCountryIso
            ?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country
        val e164 = PhoneNumberUtils.formatNumberToE164(telefono, pais.uppercase(Locale.ROOT))
        return (e164 ?: telefono).filter(Char::isDigit).removePrefix("00")
    }
}
