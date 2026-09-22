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
2. **Ficha** — foto de la agenda (o la inicial si no tiene), nombre y número del
   elegido, campo de frecuencia en días, la
   **forma de contacto** preferida (ver abajo), *Guardar*, un botón que
   contacta ya por esa vía (*Llamar*, *WhatsApp*, *Mensaje* o *Telegram*),
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
| `medio`               | MedioContacto   | por nombre; `MARCADOR` por defecto      |

La base va por la versión 2: la migración 1→2 añade `medio` y deja los
contactos existentes en `MARCADOR`, que es lo que hacían antes.

## Forma de contacto

Se elige en un desplegable de la ficha, con un icono por opción. Material no
trae logos de marcas, así que WhatsApp y Telegram (y el de SMS, que solo está
en el paquete extendido) son vectores propios en `res/drawable`. Qué se abre al tocar la notificación
(y con el botón de la ficha):

| opción               | qué hace                                                     |
|----------------------|--------------------------------------------------------------|
| Abrir el marcador    | `ACTION_DIAL` con el número puesto; no llama solo            |
| Llamar directamente  | `ACTION_CALL`: marca sin más. Pide `CALL_PHONE` al elegirla  |
| WhatsApp             | `https://wa.me/<número>`                                     |
| Mensaje (SMS)        | `ACTION_SENDTO smsto:` en la app de mensajes                 |
| Telegram             | `https://t.me/+<número>`                                     |

* Si se deniega `CALL_PHONE` la opción vuelve a *Abrir el marcador*; si el
  permiso se retira después, el aviso abre el marcador en vez de fallar.
* WhatsApp y Telegram necesitan el número internacional: si en la agenda está
  sin prefijo se completa con el país de la SIM (o el del idioma del teléfono).
  Si la app no está instalada, el enlace se abre en el navegador.
* Telegram solo encuentra a alguien por teléfono si esa persona lo permite en
  su privacidad; si no, el enlace no lleva al chat.
* El texto del aviso cambia con la opción ("Toca para escribir a Ana por
  WhatsApp"). Un aviso ya mostrado conserva la opción que había al crearse.

## Recordatorios

* Al guardar o actualizar un contacto se programa un trabajo periódico único
  por contacto (`recordatorio-<id>`, cada 24 h, `CANCEL_AND_REENQUEUE`). La
  primera comprobación corre en cuanto se guarda.
* El worker avisa si `hoy >= ultimoContacto + frecuenciaDias`, con el nombre
  como título y "Toca para llamar a <nombre>".
* Tocar la notificación abre `NotificacionPulsadaActivity`, una actividad sin
  interfaz que abre la forma de contacto elegida y marca en la base de datos
  `notificacionPulsada = true` y la fecha. Tiene que ser una Activity: desde
  Android 12 un receiver no puede lanzar otra app desde una notificación.
* *Forzar notificación ahora* encola el mismo worker una vez con un indicador
  que se salta la comprobación de fecha.

En Android 13+ la app pide `POST_NOTIFICATIONS` al arrancar; sin ese permiso no
hay avisos. Elegir un contacto no necesita `READ_CONTACTS`: con `ACTION_PICK`
sobre `CommonDataKinds.Phone` el sistema da acceso solo a la fila elegida.

La foto sí lo necesita, porque está en otra fila del proveedor. La ficha lo
pide con *Mostrar foto de la agenda*; sin él se ve la inicial y todo lo demás
funciona igual. La foto no se copia: se busca por número (`PhoneLookup`) cada
vez que se abre la ficha, así que vale para los contactos ya guardados y sigue
los cambios de la agenda.

Tocar la notificación **no** cambia `ultimoContacto`: el aviso se repite cada
día hasta que en la ficha se pulse **He llamado hoy**. Ese botón pone
`ultimoContacto` a hoy, retira la notificación pendiente y reprograma el
trabajo diario desde ese momento. Se desactiva si la fecha ya es hoy.
