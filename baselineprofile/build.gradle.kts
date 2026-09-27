plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

/**
 * Genera el perfil de referencia de :app y mide su arranque. Corre en un
 * emulador gestionado por Gradle (pixel6Api34), no en el telefono de nadie:
 *
 *   ./gradlew :app:generateReleaseBaselineProfile
 *   ./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
 *
 * En CI, el flujo "Rendimiento" (a mano, desde la pestana Actions).
 */
android {
    namespace = "com.example.recuerdallamar.rendimiento"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.devices {
        create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            // Con Google Play no se pueden generar perfiles: hace falta root.
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
}
