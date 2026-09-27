plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    // Rutas con tipo de Navigation Compose.
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.recuerdallamar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.recuerdallamar"
        // 26 para tener java.time sin desugaring y canales de notificacion siempre.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // Direccion a la que va "Enviar opiniones" (Ajustes). Vacia = el correo
        // se abre sin destinatario. Se pone en gradle.properties o con
        // -Pcontacto.correoOpiniones=... sin tocar el codigo.
        val correo = (project.findProperty("contacto.correoOpiniones") as String?).orEmpty()
        buildConfigField("String", "CORREO_OPINIONES", "\"$correo\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
