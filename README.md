# Contacto

*Cuida tu sistema.*

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
de depuración queda como artefacto `app-debug` de la ejecución y, con los
secretos de la firma, el `.aab` firmado para Google Play como
`app-release-<versión>`. Cómo publicar: [PUBLICAR.md](PUBLICAR.md).

## Aspecto

* **Icono**: el sol con ojos de la pantalla principal, en naranja sobre marino,
  como icono adaptativo (`mipmap-anydpi-v26`). Tiene capa monocroma para los
  iconos temáticos de Android 13+. La notificación usa la misma silueta
  (`drawable/ic_notificacion.xml`), con acento teja.
* **Paleta** (`ui/Tema.kt`, y en `res/values/colors.xml` para lo que no es
  Compose):

  | color     | hex       | uso                                              |
  |-----------|-----------|--------------------------------------------------|
  | noche     | `#0D2B45` | texto; fondo en modo oscuro                      |
  | marino    | `#203C56` | color principal; fondo del icono                 |
  | ciruela   | `#544E68` | secundario; burbujas en modo oscuro              |
  | malva     | `#8D697A` | bordes                                           |
  | teja      | `#D08159` | acentos en claro: el sol, "toca"                 |
  | naranja   | `#FFAA5E` | acentos en modo oscuro                           |
  | melocotón | `#FFD4A3` | burbujas en claro; principal en modo oscuro      |
  | crema     | `#FFECD6` | tarjetas; texto en modo oscuro; símbolo          |

  Sin color dinámico: en Android 12+ taparía la paleta con los colores del
  fondo de pantalla.

## Flujo

**Bienvenida** (`ui/PantallaBienvenida.kt`) — solo la primera vez, en tres
pasos con puntos de progreso, todos sobre **tu sistema** (`ui/SistemaSolar.kt`):
arriba el sol, que eres tú, y alrededor, en órbita, la gente que añades. El
sol es una esfera con ojos como las burbujas, pero con puntas onduladas que
giran despacio y un halo que brilla; saluda, mira a quien llega, cierra los
ojos al apretarlo y se ríe al tocarlo. Cada persona nueva sale del sol y se
va a su anillo (hasta tres; los de dentro giran más deprisa), con su cara
mirando hacia donde va. Solo cambia el texto de debajo:

1. *Tú eres el sol*: la gente que te importa es tu sistema. *Empezar* pide
   los permisos (avisos y fotos de la agenda), ya explicados; fuera de la
   bienvenida se piden al abrir, como antes.
2. *Crea tu sistema*: *Elegir de la agenda* abre la ficha de siempre y al
   darla de alta se vuelve aquí, con la persona ya en órbita. *Ahora no*
   salta el paso.
3. *¡Ya está!*: tu sistema y el recado de que se puede cerrar la app y
   seguir con la vida; se avisará cuando toque. Un toque en un planeta abre
   su ficha. *Entendido* acaba en la vista de burbujas, sin cortar: el sol
   sube y crece hasta la esquina de la pantalla principal y cada planeta se
   sale de su órbita con su impulso y cae a su sitio entre las burbujas
   (`ui/Relevo.kt` guarda dónde estaba cada cosa al pulsar).

Se recuerda en `Ajustes.bienvenidaHecha`. Quien ya tenía gente guardada al
llegar esta versión no la ve.

**Arranque**: la API SplashScreen (`core-splashscreen`, también en Android
8–11) enseña el sol del icono en el centro, sobre el fondo de la app y con
los colores de su sol en cada tema (`drawable/ic_arranque.xml`), hasta que la
lista ha cargado. Cada vez que se abre la app desde el icono, con la
bienvenida ya hecha, ese sol no desaparece: el de la esquina de Personas sale
de donde estaba y sube y crece hasta su sitio, como al acabar la bienvenida
(`ui/Relevo.kt`). Sin animaciones en el sistema, o si se abre desde un aviso,
el widget o el atajo, aparece ya en la esquina.

**Navegación** (`Navegacion.kt`, Navigation Compose con rutas con tipo):
Bienvenida, Personas, Ajustes, Ficha(id) y Nueva (el alta a medias). La pila
la guarda Navigation, así que girar la pantalla o que Android cierre la app
en segundo plano no saca a nadie de donde estaba; el alta a medias, el filtro
por círculo y la persona elegida en tableta van en el `SavedStateHandle` del
ViewModel. El gesto atrás es predictivo (Android 13+,
`enableOnBackInvokedCallback`): al arrastrar se ve la pantalla de debajo con
la misma animación de deslizar. Enlaces profundos (`Enlaces.kt`), solo para la
propia app y el lanzador:

| enlace                  | abre                                        |
|-------------------------|---------------------------------------------|
| `contacto://personas`   | la pantalla principal                       |
| `contacto://ficha/{id}` | la ficha de esa persona (aviso, widget)     |
| `contacto://nuevo`      | Personas y la agenda para añadir (atajo, +) |

**Pantallas anchas**: con ancho *expandido* (tableta, plegable abierto,
ventana grande; clases de tamaño de ventana de Material 3) Personas va a la
izquierda y la ficha de quien se toque a la derecha. Al plegar con alguien
abierto, su ficha pasa a pantalla completa, y al desplegar vuelve al panel.
Ajustes y la ficha ya van en dos columnas desde 600 dp.

**Widget** «Hoy toca» (`widget/WidgetHoy.kt`, Glance): a quién le toca hoy o
ya se pasó, por urgencia, y quién cumple años, con los colores de la app en
claro y oscuro. Tocar a alguien abre su ficha; el título, Personas; el +, la
agenda. Se redibuja con cada cambio de la base de datos y una vez al día.
**Atajo** del icono (mantenerlo pulsado): *Añadir persona*.

La pantalla principal es **Personas** (la lista y sus fichas). El botón de
engranaje, arriba a la derecha junto al de la vista, abre **Ajustes**; *Atrás* o la
flecha vuelven a Personas.

1. **Personas** — vacía al principio. En la esquina de arriba a la
   izquierda asoma **el sol** (`ui/SolEsquina.kt`), como el de los dibujos,
   con sus rayos girando y los ojos sobre la esfera entera, con la cabeza
   girada hacia la esquina, así que se deslizan y se estrechan hacia el borde
   al mirar alrededor; se queda en las
   dos vistas y ni las burbujas ni la lista se le meten debajo. El **+** de
   abajo, en el centro, abre el selector de contactos del sistema filtrado a
   números de teléfono: al apretarlo el sol lo mira, se encoge y se ríe, y
   luego saluda a quien llega. Se ve de dos formas, con los
   mismos datos y el mismo orden por **urgencia** (días desde el último
   contacto ÷ frecuencia de esa persona: 0 recién hablado, 1 toca hoy); el
   icono de la barra superior alterna entre ellas y la elección se recuerda:
   - **Burbujas** (por defecto, `ui/VistaBurbujas.kt`) — cada persona es una
     burbuja con cara, más grande cuanto más cerca está de su
     fecha; a quien ya le toca lleva anillo teja/naranja y un halo que respira. Se
     colocan con un empaquetado circular (las urgentes en el centro, sin
     solaparse) y se mueven con una pequeña simulación de muelles y choques
     (`ui/FisicaBurbujas.kt`): se pueden arrastrar y lanzar, empujan a las
     demás y al soltarlas vuelven solas a su sitio con un rebote suave. Un
     toque abre la ficha. Con más de 18 personas, las que van sobradas se
     atenúan y se recogen en una burbuja «Con calma» que se abre al tocarla;
     si aun así no caben, las grandes encogen y el lienzo se desplaza.
     Al lanzarlas se estiran en la dirección en que corren.
     Cada burbuja es una esfera con ojos (`ui/Cara.kt`): los ojos son elipses
     pegadas a la superficie, así que al girar la cabeza se deslizan, se
     estrechan hacia el borde y se esconden detrás, lo que da el aire de 3D.
     Una *expresión* es la postura de la cabeza y la forma de cada ojo; una
     *animación*, pasos de transición y espera entre expresiones (en bucle, una
     vez o de ida y vuelta) con su parpadeo; `EstadoCara` las reproduce
     (`reproducir`, `pausar`, `poner`, `parar`) y añade microsacadas, deriva y
     temblor. Duermen si van sobradas, miran alrededor en reposo, se ponen
     atentas al acercarse la fecha, asienten contentas cuando toca y niegan
     impacientes si se pasa mucho; al apretarlas cierran los ojos de gusto, al
     cogerlas se asustan y al lanzarlas giran la cabeza hacia donde van. La
     foto de la agenda, si la hay, va en una chapita.
     Se notan en la mano (`ui/Tacto.kt`): un toque al cogerlas, un golpe seco
     al lanzarlas fuerte y un «click» en cada choque, entre ellas o contra el
     borde, más fuerte cuanto más deprisa iban (vibración compuesta en Android
     11+ si el motor la admite; si no, un tic en los golpes que se notan).
     Sigue el ajuste del sistema de vibrar al tocar.
   - **Lista** — cada fila lleva la foto de la agenda o la inicial, el
     círculo encima del nombre y una tarta si hoy es su cumpleaños.

   Si alguien tiene **círculo**, arriba aparece un filtro («Todos»,
   «Familia»...) que vale para las tres vistas.
2. **Ficha** — foto de la agenda (o la inicial si no tiene), nombre y número del
   elegido, campo de frecuencia en días, la
   **forma de contacto** preferida (ver abajo), *Guardar*, un botón que
   contacta ya por esa vía (*Llamar*, *WhatsApp*, *Mensaje* o *Telegram*),
   *He llamado hoy* (pone el último contacto a hoy; solo si ya está guardado),
   *Pausar avisos* (N días sin avisos de esa persona; el último contacto no
   cambia, así que la burbuja sigue creciendo), *Eliminar contacto* (con
   confirmación) y un botón de **depuración** que fuerza la notificación (solo en depuración).
   Además, el **círculo** de la persona (uno como mucho: se elige entre los
   que hay o se crea otro), unas **notas** libres (se guardan al dejar de
   escribir) y, si está en la agenda, su **cumpleaños** con los días que
   faltan.
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
| Avisos en segundo plano        | Solo si hace falta (ver *Batería* abajo): quitar la restricción de batería, inicio automático del fabricante y su guía |
| Copia de seguridad             | *Exportar* / *Importar* un archivo JSON (ver *Copia de seguridad* abajo) |
| Depuración › Idioma de la app  | El del teléfono (por defecto) o uno fijo, para probar traducciones (solo en depuración) |

El botón de depuración de la ficha ignora estos ajustes: siempre avisa.

Debajo, **Opiniones**: *Enviar opiniones* abre el correo con la versión y el
teléfono ya escritos (el destinatario sale de la propiedad de Gradle
`contacto.correoOpiniones`; vacía, se elige en la app de correo) y *Valorar en
Google Play* abre la ficha de la tienda. Además, tras cinco contactos hechos
(aviso tocado o *He llamado hoy*) la app pide la valoración de Play dentro de
la propia app, como mucho una vez cada 120 días (`Valoracion.kt`); Google
decide si llega a enseñar el diálogo.

## Idiomas

La app se llama **Contacto** en todos los idiomas; el lema, *Cuida tu
sistema*, sí se traduce. Textos en español (`values-es`), inglés (`values`,
el que ve quien tenga el teléfono en un idioma sin traducir), francés,
alemán y ruso, con plurales de cada gramática (`<plurals>`). Añadir una
cadena es añadirla en los cinco, o lint (`MissingTranslation`) no deja pasar
la compilación de CI.

Sigue el idioma del teléfono. En *Ajustes › Depuración* se puede fijar otro
sin tocar el del sistema: se guarda en `Ajustes.idioma` e `Idioma.kt` lo
pone al crear cada actividad y al montar la notificación (que sale del
worker, sin actividad); al cambiarlo la pantalla se rehace ya traducida. En
Android 13+ los idiomas también salen en los ajustes de idioma por app del
sistema (`res/xml/locales_config.xml`). El App Bundle no parte por idioma,
para que el que se elija dentro de la app esté siempre instalado.

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
| `notas`               | String          | texto libre; vacío por defecto          |
| `cumpleanos`          | MonthDay?       | día y mes, leído de la agenda («--MM-DD») |
| `circulo`             | String?         | familia, amigos...; uno como mucho      |
| `cumpleanosManual`    | Boolean         | puesto o quitado a mano: la agenda ya no lo toca |

La base va por la versión 5 (la 3→4 añade `notas`, `cumpleanos` y
`circulo`, vacíos; la 4→5, `cumpleanosManual`, a falso: los que había vienen
de la agenda). Los esquemas se exportan a `app/schemas` para ver cada
cambio y probar migraciones. Antes, en la versión 3: la migración 1→2 añade `medio` y deja los
contactos existentes en `MARCADOR`, que es lo que hacían antes; la 2→3 añade
`descartes`, `pospuestoHasta` y `pausadoHasta`, vacíos.

## Forma de contacto

Se elige en un desplegable de la ficha, con un icono por opción. Material no
trae logos de marcas, así que WhatsApp y Telegram (y el de SMS, que solo está
en el paquete extendido) son vectores propios en `res/drawable`. El marcador lleva el «123» de
Material (también del paquete extendido) para no confundirlo con *Llamar
directamente*, que lleva el auricular. Qué se abre al tocar la notificación
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

Tres canales, cada uno con su importancia, para que en los ajustes del
sistema se elija cuáles suenan: **Recordatorios** (normal, los que tocan hoy),
**Muy atrasados** (alta: cuando ya va con media frecuencia de retraso) y
**Cumpleaños** (alta). Con dos o más avisos de gente en la bandeja se agrupan
bajo un resumen con los nombres; suena cada aviso, no el resumen.

**Cumpleaños** (`CumpleanosAgenda.kt`, `avisos/CumpleanosWorker.kt`): con
`READ_CONTACTS` se lee la fecha de la agenda al dar de alta, al abrir la ficha
y una vez al día para todos. Ese mismo trabajo diario felicita a quien cumple
hoy (el 29 de febrero, el 28 los años que no son bisiestos), con los mismos
ajustes que el resto: apagados o en pausa no avisa y fuera del horario espera
a la hora de inicio. Tocarlo abre la forma de contacto de esa persona y cuenta
como contacto.

En la ficha también se pone a mano (día y mes, sin año), se cambia o se
quita, incluso sin permiso de contactos. Lo hecho a mano manda: desde
entonces la agenda ya no lo rellena ni lo cambia, y quitarlo lo quita del
todo. Si viene de la agenda, la ficha lo dice.

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
  - **Más opciones** — abre la app en la ficha de esa persona: ahí está
    *He llamado hoy* (si ya se habló por otro lado), *Pausar avisos* y
    *Eliminar contacto*. Android solo deja tres botones por aviso, así que
    este reúne el resto.
* Quitar el aviso de la bandeja (deslizar o *borrar todo*) suma un descarte
  (`AccionesAviso`, por `setDeleteIntent`). No pospone nada: el trabajo diario
  lo vuelve a sacar al día siguiente mientras siga tocando.
* Con la persona en pausa (`pausadoHasta`) no se avisa, pero la urgencia se
  sigue calculando desde `ultimoContacto`.
* *Forzar notificación ahora* encola el mismo worker una vez con un indicador
  que se salta la comprobación de fecha.

* Al arrancar la app se programa el trabajo diario de quien no lo tenga
  (`ExistingPeriodicWorkPolicy.KEEP`, no toca los que ya hay): así vuelven los
  avisos tras restaurar una copia o importar un archivo, porque los trabajos
  de WorkManager no viajan en la copia.

**Batería** (`Bateria.kt`): el ahorro de batería es lo que más avisos se
come. Android retrasa los trabajos de las apps *optimizadas* y varios
fabricantes (Xiaomi, Huawei, Samsung, Oppo, Vivo…) además cierran la app y
no la dejan volver a arrancar sola. La app mira si está *restringida* en
segundo plano (`ActivityManager.isBackgroundRestricted`, Android 9+) o
*optimizada* (`PowerManager.isIgnoringBatteryOptimizations`) y quién es el
fabricante. Si hay algo que decir (restringida, u optimizada en un fabricante
estricto):

* **Una vez**, al abrir la app ya con gente y nunca en la misma sesión que la
  bienvenida, un diálogo lo explica y ofrece *Ajustar*. Se recuerda en unas
  preferencias aparte (`bateria.xml`) que no van en la copia: en otro teléfono
  hay que volver a mirarlo.
* En **Ajustes › Avisos en segundo plano**, el estado (se vuelve a mirar al
  volver de los ajustes del sistema), *Permitir en segundo plano* (la
  pantalla de la app, con *Batería › Sin restricciones*, en Android 12+; antes,
  la lista de optimización de batería), la pantalla de **inicio automático**
  del fabricante si se conoce y la guía de <https://dontkillmyapp.com> para esa
  marca.

No se usa `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (el diálogo directo): Google
Play solo lo permite a unos pocos tipos de app.

**Copia de seguridad**:

* La **copia de Android** (Auto Backup, a la cuenta de Google y al pasar a un
  teléfono nuevo) lleva solo la base de datos y los ajustes
  (`res/xml/reglas_copia.xml` desde Android 12, `copia_completa.xml` antes).
  Ni la base de datos de WorkManager ni el aviso de batería.
* En **Ajustes › Copia de seguridad**, *Exportar* escribe un JSON
  (`datos/CopiaSeguridad.kt`) donde se elija con el selector del sistema (sin
  permiso de almacenamiento) e *Importar* lo lee. Lleva la gente (nombre,
  número, frecuencia, último contacto, forma de contacto, notas, cumpleaños,
  círculo y pausa) y los ajustes que valen en otro teléfono; no lo de un aviso
  en curso (descartes, *Más tarde*) ni el idioma de depuración. Al importar,
  quien ya está con el mismo número (solo las cifras y el `+`) se actualiza con
  lo del archivo, quedándose con el último contacto más reciente de los dos;
  el resto se da de alta. Los campos que falten toman su valor por defecto y
  los que sobren se ignoran; `formato` solo sube si cambia el significado de
  algo.

Detectar solas las llamadas hechas fuera de la app necesitaría leer el
registro de llamadas (`READ_CALL_LOG`), que Google Play solo concede a las apps
de teléfono o SMS por defecto y a unas pocas excepciones: por ahora no se hace.

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
