package com.example.recuerdallamar

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Abre el marcador con el numero puesto. ACTION_DIAL no llama: no pide permiso. */
object Marcador {
    fun abrir(context: Context, telefono: String) {
        // fromParts codifica '#', '*' y espacios, que en un Uri.parse se perderian.
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", telefono, null))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay marcador en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }
}
