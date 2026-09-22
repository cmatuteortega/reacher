package com.example.recuerdallamar.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material 3 con color dinamico (Android 12+) y el esquema por defecto antes. */
@Composable
fun TemaApp(content: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    val context = LocalContext.current
    val esquema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (oscuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        oscuro -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = esquema, content = content)
}
