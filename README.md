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

## Aspecto

* **Icono**: el símbolo `reminder` de Material Symbols Outlined (Apache 2.0)
  como icono adaptativo (`mipmap-anydpi-v26`), en crema sobre verde azulado.
  Tiene capa monocroma para los iconos temáticos de Android 13+. La
  notificación usa el mismo símbolo, con acento naranja.
* **Paleta** (`ui/Tema.kt`, y en `res/values/colors.xml` para lo que no es
  Compose):

  | color     | hex       | uso                                           |
  |-----------|-----------|-----------------------------------------------|
  | tinta     | `#001524` | texto; fondo en modo oscuro                   |
  | petróleo  | `#15616D` | color principal; fondo del icono              |
  | crema     | `#FFECD1` | tarjetas; texto en modo oscuro; símbolo       |
  | naranja   | `#FF7D00` | acentos: botón *Añadir*, campana de "toca"    |

  Sin color dinámico: en Android 12+ taparía la paleta con los colores del
  fondo de pantalla.

## Flujo

La pantalla principal es **Personas** (la lista y sus fichas). El botón de
engranaje de la esquina superior izquierda abre **Ajustes**; *Atrás* o la
flecha vuelven a Personas.

1. **Personas** — vacía al principio. *Añadir* abre el selector de contactos
   del sistema filtrado a números de teléfono. Se ve de dos formas, con los
   mismos datos y el mismo orden por **urgencia** (días desde el último
   contacto ÷ frecuencia de esa persona: 0 recién hablado, 1 toca hoy); el
   icono de la barra superior alterna entre ellas y la elección se recuerda:
   - **Burbujas** (por defecto, `ui/VistaBurbujas.kt`) — cada persona es una
     burbuja con su foto o inicial, más grande cuanto más cerca está de su
     fecha; a quien ya le toca lleva anillo naranja y un halo que respira. Se
     colocan con un empaquetado circular (las urgentes en el centro, sin
     solaparse) y se mueven con una pequeña simulación de muelles y choques
     (`ui/FisicaBurbujas.kt`): se pueden arrastrar y lanzar, empujan a las
     demás y al soltarlas vuelven solas a su sitio con un rebote suave. Un
     toque abre la ficha. Con más de 18 personas, las que van sobradas se
     atenúan y se recogen en una burbuja «Con calma» que se abre al tocarla;
     si aun así no caben, las grandes encogen y el lienzo se desplaza.
   - **Lista** — cada fila lleva la foto de la agenda o la inicial.
2. **Ficha** — foto de la agenda (o la inicial si no tiene), nombre y número del
   elegido, campo de frecuencia en días, la
   **forma de contacto** preferida (ver abajo), *Guardar*, un botón que
   contacta ya por esa vía (*Llamar*, *WhatsApp*, *Mensaje* o *Telegram*),
   *He llamado hoy* (pone el último contacto a hoy; solo si ya está guardado),
   *Pausar avisos* (N días sin avisos de esa persona; el último contacto no
   cambia, así que la burbuja sigue creciendo), *Eliminar contacto* (con
   confirmación) y un botón de **depuración** que fuerza la notificación.
3. Al guardar se vuelve a Personas; el contacto nuevo, sin urgencia, entra al
   final: en la lista aparece fundido y el resto se recoloca con un muelle
   (`Modifier.animateItem`); en burbujas nace pequeña y sube a su sitio.

## Ajustes

Se guardan en `SharedPreferences` (`datos/Ajustes.kt`) y los leen tanto la
interfaz como el worker.

| ajuste                         | qué hace                                                        |
|--------------------------------|-----------------------------------------------------------------|
| Recordatorios                  | Interruptor. Al apagarlo: *hasta que los vuelva a activar* o *durante N días*; pasada la pausa vuelven solos |
| Horario de avisos              | Horas *desde* / *hasta* en que se puede avisar (por defecto 09:00–21:00). Puede cruzar la medianoche; misma hora = todo el día |
| Botón «Más tarde» del aviso    | Cuántas horas tarda en volver el aviso pospuesto (por defecto 2) |
| Forma de contacto por defecto  | La que se propone al añadir a alguien; se cambia en su ficha    |
| Apariencia                     | Sistema, claro u oscuro                                         |

El botón de depuración de la ficha ignora estos ajustes: siempre avisa.

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
| `descartes`           | Int             | veces que se quitó el aviso sin contactar; 0 al contactar |
| `pospuestoHasta`      | LocalDateTime?  | hasta cuándo se pospuso con *Más tarde* |
| `pausadoHasta`        | LocalDateTime?  | avisos de esta persona en pausa hasta   |

La base va por la versión 3: la migración 1→2 añade `medio` y deja los
contactos existentes en `MARCADOR`, que es lo que hacían antes; la 2→3 añade
`descartes`, `pospuestoHasta` y `pausadoHasta`, vacíos.

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
* Con los recordatorios apagados o en pausa no avisa (el día siguiente lo
  vuelve a comprobar). Si corre fuera del horario elegido, encola un trabajo
  de una vez (`aplazado-<id>`) para la hora de inicio, que repite todas las
  comprobaciones: si entretanto se pulsó *He llamado hoy*, ya no avisa.
* Tocar la notificación abre `NotificacionPulsadaActivity`, una actividad sin
  interfaz que abre la forma de contacto elegida y marca en la base de datos
  `notificacionPulsada = true` y la fecha. Tiene que ser una Activity: desde
  Android 12 un receiver no puede lanzar otra app desde una notificación.
* El aviso trae botones:
  - **Contactar** — lo mismo que tocarlo.
  - **Más tarde** — lo retira y encola `aplazado-<id>` para dentro de las
    horas elegidas en Ajustes; guarda `pospuestoHasta` para que el trabajo
    diario no lo saque antes. Si al volver cae fuera del horario, espera a la
    hora de inicio.
  - **Cambié de idea** — solo si ya se quitó alguna vez sin contactar
    (`descartes > 0`). Abre la app en la ficha de esa persona, donde se puede
    pausar sus avisos o eliminarla.
* Quitar el aviso de la bandeja (deslizar o *borrar todo*) suma un descarte
  (`AccionesAviso`, por `setDeleteIntent`). No pospone nada: el trabajo diario
  lo vuelve a sacar al día siguiente mientras siga tocando.
* Con la persona en pausa (`pausadoHasta`) no se avisa, pero la urgencia se
  sigue calculando desde `ultimoContacto`.
* *Forzar notificación ahora* encola el mismo worker una vez con un indicador
  que se salta la comprobación de fecha.

En Android 13+ la app pide `POST_NOTIFICATIONS` al arrancar; sin ese permiso no
hay avisos. Elegir un contacto no necesita `READ_CONTACTS`: con `ACTION_PICK`
sobre `CommonDataKinds.Phone` el sistema da acceso solo a la fila elegida.

Las fotos sí lo necesitan, porque están en otra fila del proveedor.
`READ_CONTACTS` es un permiso peligroso de Android: no hay forma de tenerlo sin
que el usuario lo acepte en el diálogo del sistema. Se pide **una vez al abrir
la app por primera vez**, junto al de notificaciones. Si se deniega, la ficha
ofrece *Mostrar foto de la agenda* para volver a pedirlo; tras dos negativas
el sistema ya no muestra el diálogo y ese botón abre los ajustes de la app.
Sin el permiso se ve la inicial y todo lo demás funciona igual.

Las fotos no se copian: se buscan por número (`PhoneLookup`), la miniatura en
la lista y la grande en la ficha, así que valen para los contactos ya guardados.
Se guardan en una caché en memoria mientras la app está abierta.

Tocar la notificación (o *Contactar*) cuenta como contacto: pone
`ultimoContacto` a hoy y deja a cero descartes y aplazamiento. Si solo se
quita, el aviso se repite cada día hasta que se toque o en la ficha se pulse
**He llamado hoy**. Ese botón hace lo mismo, retira la notificación pendiente
y reprograma el trabajo diario desde ese momento. Se desactiva si la fecha ya
es hoy.
