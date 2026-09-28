import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    // Rutas con tipo de Navigation Compose.
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.sentry.android.gradle")
    id("androidx.baselineprofile")
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
    compileSdk = 36

    defaultConfig {
        // No puede cambiar nunca despues de la primera subida a Google Play.
        // Va tambien en res/xml/atajos.xml (targetPackage).
        applicationId = "com.cmatuteortega.contacto"
        // 26 para tener java.time sin desugaring y canales de notificacion siempre.
        minSdk = 26
        targetSdk = 36
        // Los pone CI (-Pcontacto.versionCode=<numero de ejecucion>,
        // -Pcontacto.versionName=<etiqueta de git>); en local, 1 y "dev".
        versionCode = (project.findProperty("contacto.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("contacto.versionName") as String?) ?: "dev"

        // Direccion a la que va "Enviar opiniones" (Ajustes). Vacia = el correo
        // se abre sin destinatario. Se pone en gradle.properties o con
        // -Pcontacto.correoOpiniones=... sin tocar el codigo.
        val correo = (project.findProperty("contacto.correoOpiniones") as String?).orEmpty()
        buildConfigField("String", "CORREO_OPINIONES", "\"$correo\"")

        // Claves de Sentry (fallos) y PostHog (estadisticas de uso). Igual que
        // el correo: gradle.properties, -P o, en CI, los secretos. Vacias, la
        // app no envia nada y ni siquiera arranca esos SDK.
        val sentryDsn = (project.findProperty("contacto.sentryDsn") as String?).orEmpty()
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
        val posthogKey = (project.findProperty("contacto.posthogKey") as String?).orEmpty()
        buildConfigField("String", "POSTHOG_KEY", "\"$posthogKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Ninguna cadena sale solo en ingles: si falta una traduccion en alguno de
    // los idiomas, lint (y con el, CI) falla antes de compilar nada.
    lint {
        error += "MissingTranslation"
        abortOnError = true
    }

    // Las pruebas corren en la JVM con Robolectric (base de datos, workers y
    // pantallas incluidos): no hace falta emulador ni en local ni en CI.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Las pruebas de migracion leen los esquemas de Room como assets. Solo en
    // debug: la version publicada no los lleva.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}

/**
 * Sentry: sin auth token (SENTRY_AUTH_TOKEN, SENTRY_ORG y SENTRY_PROJECT en
 * CI) el mapping no se sube, y las trazas de la version publicada llegan
 * ofuscadas, pero la compilacion no falla.
 */
sentry {
    autoInstallation.enabled.set(false)
    tracingInstrumentation.enabled.set(false)
    includeSourceContext.set(false)
    includeDependenciesReport.set(false)
    telemetry.set(false)
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(
        listOf("SENTRY_AUTH_TOKEN", "SENTRY_ORG", "SENTRY_PROJECT").all { !System.getenv(it).isNullOrBlank() },
    )
}

/**
 * El perfil lo genera el modulo :baselineprofile en un emulador (flujo
 * "Rendimiento" de GitHub Actions, a mano) y se guarda en
 * src/release/generated/baselineProfiles. No se genera en cada compilacion:
 * necesita emulador y tarda.
 */
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
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
    // Copia de seguridad en JSON (Ajustes > Copia de seguridad).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Widget de la pantalla de inicio.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Instala el perfil de referencia en los telefonos que no lo reciben de Play.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))

    // Dialogo de valoracion de Google Play dentro de la app.
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Informes de fallos (activados por defecto, se apagan en Ajustes) y
    // estadisticas de uso anonimas (solo si el usuario acepta).
    implementation("io.sentry:sentry-android:8.58.0")
    implementation("com.posthog:posthog-android:3.71.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.work:work-testing:2.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
