# Reglas de R8 para la version publicada. Room, WorkManager, Glance,
# Navigation y kotlinx.serialization traen las suyas en sus bibliotecas; aqui
# solo va lo que depende de este codigo.

# Rutas de Navigation Compose: se buscan por su serializador. Sin esto R8 puede
# quitar el serializer() de los data object y la app cae al navegar.
-keep @kotlinx.serialization.Serializable class com.example.recuerdallamar.Ruta$* {
    *;
}

# Entidades y DAO de Room: su codigo generado ya las nombra, pero el esquema
# guarda los nombres de las columnas. Se dejan sin renombrar por prudencia.
-keep class com.example.recuerdallamar.datos.** { *; }

# Los workers se guardan en la base de datos de WorkManager por su nombre de
# clase: si R8 lo cambiara entre versiones, los avisos ya programados fallarian.
-keepnames class * extends androidx.work.ListenableWorker

# Nombres de fichero y linea en las trazas de error (para leer los informes de
# Play Console con el mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
