# Recuerda llamar

Prueba de concepto Android (Kotlin + Jetpack Compose + Material 3): una lista de
personas a las que llamar cada cierto número de días, con un recordatorio diario
por WorkManager. Todo es local; no hay cuentas ni servidor.

## Abrir y ejecutar

Abre el proyecto en Android Studio, o desde terminal con
el SDK de Android instalado:

```sh
./gradlew installDebug
```

minSdk 26, target/compileSdk 35, JDK 17+.

Cada push compila en GitHub Actions (`.github/workflows/android.yml`); el APK
de depuración queda como artefacto `app-debug` de la ejecución.

## Flujo

1. **Lista** — vacía al principio. *Añadir* abre el selector de contactos del
   sistema filtrado a números de teléfono.
2. **Ficha** — nombre y número del elegido, campo de frecuencia en días,
   *Guardar*, *Llamar* (`ACTION_DIAL`, abre el marcador con el número puesto),
   *He llamado hoy* (pone el último contacto a hoy; solo si ya está guardado) y
   un botón de **depuración** que fuerza la notificación.
3. Al guardar se vuelve a la lista; el contacto nuevo entra fundido arriba y el
   resto baja con un muelle (`Modifier.animateItem`).

## Datos (Room)

Una tabla, `contactos`:

| campo                 | tipo            | notas                                   |
|-----------------------|-----------------|-----------------------------------------|
| `id`                  | Long (autogen.) |                                         |
| `nombre`              | String          |                                         |
| `telefono`            | String          |                                         |
| `frecuenciaDias`      | Int             | 1 o más                                 |
| `ultimoContacto`      | LocalDate       | hoy al crear el contacto                |
| `notificacionPulsada` | Boolean         | true tras tocar la notificación         |
| `fechaPulsacion`      | LocalDateTime?  | cuándo se tocó por última vez           |

## Recordatorios

* Al guardar o actualizar un contacto se programa un trabajo periódico único
  por contacto (`recordatorio-<id>`, cada 24 h, `CANCEL_AND_REENQUEUE`). La
  primera comprobación corre en cuanto se guarda.
* El worker avisa si `hoy >= ultimoContacto + frecuenciaDias`, con el nombre
  como título y "Toca para llamar a <nombre>".
* Tocar la notificación abre `NotificacionPulsadaActivity`, una actividad sin
  interfaz que abre el marcador y marca en la base de datos
  `notificacionPulsada = true` y la fecha. Tiene que ser una Activity: desde
  Android 12 un receiver no puede lanzar el marcador desde una notificación.
* *Forzar notificación ahora* encola el mismo worker una vez con un indicador
  que se salta la comprobación de fecha.

En Android 13+ la app pide `POST_NOTIFICATIONS` al arrancar; sin ese permiso no
hay avisos. No se pide `READ_CONTACTS`: al elegir con `ACTION_PICK` sobre
`CommonDataKinds.Phone` el sistema da acceso solo a la fila elegida.

Tocar la notificación **no** cambia `ultimoContacto`: el aviso se repite cada
día hasta que en la ficha se pulse **He llamado hoy**. Ese botón pone
`ultimoContacto` a hoy, retira la notificación pendiente y reprograma el
trabajo diario desde ese momento. Se desactiva si la fecha ya es hoy.
