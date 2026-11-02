plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.test") version "9.4.1" apply false
    // Perfil de referencia: el arranque y las pantallas principales, ya compilados al instalar.
    id("androidx.baselineprofile") version "1.3.4" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    // Informes de fallos: sube el mapping de R8 para leer las trazas.
    id("io.sentry.android.gradle") version "5.12.2" apply false
}
