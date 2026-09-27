import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    // Rutas con tipo de Navigation Compose.
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Firma de la version publicada. Se lee de las variables de entorno (en CI,
 * de los secretos del repositorio) o de keystore.properties en la raiz, que
 * no se sube. Sin ellas, assembleRelease/bundleRelease salen sin firmar.
 */
val firma: Map<String, String?> = run {
    val local = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    listOf("CONTACTO_KEYSTORE", "CONTACTO_KEYSTORE_PASSWORD", "CONTACTO_KEY_ALIAS", "CONTACTO_KEY_PASSWORD")
        .associateWith { System.getenv(it) ?: local.getProperty(it) }
}
val hayFirma = firma.values.all { !it.isNullOrBlank() }

android {
    // El namespace (paquete del codigo y de R) se queda; lo que ve Google Play
    // es el applicationId de abajo.
    namespace = "com.example.recuerdallamar"
    compileSdk = 35

    defaultConfig {
        // No puede cambiar nunca despues de la primera subida a Google Play.
        // Va tambien en res/xml/atajos.xml (targetPackage).
        applicationId = "com.cmatuteortega.contacto"
        // 26 para tener java.time sin desugaring y canales de notificacion siempre.
        minSdk = 26
        targetSdk = 35
        // Los pone CI (-Pcontacto.versionCode=<numero de ejecucion>,
        // -Pcontacto.versionName=<etiqueta de git>); en local, 1 y "dev".
        versionCode = (project.findProperty("contacto.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("contacto.versionName") as String?) ?: "dev"

        // Direccion a la que va "Enviar opiniones" (Ajustes). Vacia = el correo
        // se abre sin destinatario. Se pone en gradle.properties o con
        // -Pcontacto.correoOpiniones=... sin tocar el codigo.
        val correo = (project.findProperty("contacto.correoOpiniones") as String?).orEmpty()
        buildConfigField("String", "CORREO_OPINIONES", "\"$correo\"")
    }

    signingConfigs {
        if (hayFirma) {
            create("publicacion") {
                storeFile = rootProject.file(firma.getValue("CONTACTO_KEYSTORE")!!)
                storePassword = firma["CONTACTO_KEYSTORE_PASSWORD"]
                keyAlias = firma["CONTACTO_KEY_ALIAS"]
                keyPassword = firma["CONTACTO_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hayFirma) signingConfig = signingConfigs.getByName("publicacion")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // El idioma se puede elegir dentro de la app (Ajustes > Depuracion): todas
    // las traducciones tienen que venir en el APK, no solo la del telefono.
    bundle {
        language {
            enableSplit = false
        }
    }
}

// Esquemas de Room en el repositorio, para ver cada cambio y probar migraciones.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Clases de tamano de ventana: lista y ficha lado a lado en tableta y plegable.
    implementation("androidx.compose.material3:material3-window-size-class")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Widget de la pantalla de inicio.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Dialogo de valoracion de Google Play dentro de la app.
    implementation("com.google.android.play:review-ktx:2.0.2")

    testImplementation("junit:junit:4.13.2")
}
