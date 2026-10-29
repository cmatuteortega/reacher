# Contacto para iOS

La versión nativa de iOS de Contacto (*Cuida tu sistema*): SwiftUI + SwiftData,
iOS 17 o superior, iPhone y iPad. Misma idea, misma paleta y mismas pantallas
que la de Android (`../app`): una lista de personas a las que hablar cada cierto
número de días, con un recordatorio. Todo se queda en el teléfono; esta versión
no manda nada fuera (ni informes de fallos ni estadísticas).

## Abrir y ejecutar

El proyecto de Xcode **no se guarda**: sale de `project.yml` con
[XcodeGen](https://github.com/yonaskolb/XcodeGen). Hace falta un Mac con Xcode 16.

```sh
brew install xcodegen
cd ios
xcodegen generate
open Contacto.xcodeproj
```

Pruebas (simulador, sin firma):

```sh
xcodebuild test -project Contacto.xcodeproj -scheme Contacto \
  -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO
```

Cada push que toque `ios/` (o los `strings.xml` de Android) corre
`.github/workflows/ios.yml`: pruebas en simulador, la app de simulador como
artefacto (`ios-simulador`) y, con los secretos de firma, el `.ipa`. Una
etiqueta `ios-v1.0.0` lo sube a TestFlight. Cómo dejarlo todo listo:
[PUBLICAR.md](PUBLICAR.md).

## Cómo está hecha

```
ios/
  project.yml                 el proyecto (XcodeGen)
  tools/generar_cadenas.py    Localizable.xcstrings desde los strings.xml de Android
  tools/generar_icono.py      el icono de 1024 px
  Contacto/
    ContactoApp.swift         punto de entrada
    Datos/                    Contacto (SwiftData), Ajustes (UserDefaults), copia de seguridad
    Logica/                   lo que no es pantalla: plazos, plan de avisos, orbitas, enlaces
    UI/                       Personas (orbitas, burbujas, lista), ficha, formulario, ajustes, bienvenida
    Recursos/                 iconos, colores y textos
  ContactoTests/              XCTest: la cuenta de plazos, avisos, orbitas, copias...
```

* **Textos**: viven en un solo sitio, los `strings.xml` de Android (5 idiomas,
  con plurales). `tools/generar_cadenas.py` los convierte al catálogo de iOS
  (`Recursos/Localizable.xcstrings`, que se guarda); CI falla si está viejo.
  Tras tocar un texto: `python3 ios/tools/generar_cadenas.py`. Los pocos textos
  que solo existen en iOS llevan la clave `ios_...` y están en ese script.
* **Datos**: una entidad, `Contacto`, con los mismos campos que la tabla de
  Android. La copia de seguridad es **el mismo JSON** (`CopiaSeguridad.swift`):
  se exporta en un teléfono y se abre en el otro, sea Android o iPhone.
* **Lógica pura** (`Fechas`, `Plazos`, `PlanAvisos`, `PlanoOrbitas`,
  `Empaquetado`, `Contactar`): sin SwiftUI ni SwiftData, para probarla sin
  simulador. Es la misma cuenta que `Contacto.kt`: urgencia = días desde el
  último contacto ÷ frecuencia; el anillo de las órbitas es la frecuencia.
* **Contactar**: *Llamar* abre `tel:` (iOS siempre pide confirmar, así que no
  existe «llamar directamente»), *Mensaje* `sms:`, *WhatsApp* `wa.me` y
  *Telegram* `t.me`, completando el prefijo del país con la región del teléfono.
* **Agenda**: el selector de contactos del sistema (`CNContactPickerViewController`),
  que no pide permiso. De ahí salen nombre, número, foto y cumpleaños al dar de
  alta a alguien; la foto y el cumpleaños no se refrescan después.

## Avisos

Android tiene un trabajo diario por persona (WorkManager). iOS no deja ejecutar
nada a una hora fija con la app cerrada, así que aquí son **notificaciones
locales programadas por adelantado** (`Logica/Avisos.swift`, `PlanAvisos`):

* Por persona, una a la hora de inicio del horario el día que toca y luego una
  por día mientras no se haga caso, hasta 7 por persona; el sistema admite 64
  pendientes en total y se reparten entre la gente.
* Se rehacen al abrir la app, al volver a ella y con cualquier cambio (gente,
  horario, pausa, «Más tarde»). Si la app no se abre en una semana, los avisos
  de esa semana se acaban salvo que iOS la despierte en segundo plano (ver
  *Renovación*); abrirla (o tocar un aviso) los renueva siempre.
* Botones del aviso: *Contactar* (abre la forma de contacto), *Más tarde*
  (vuelve a sonar tras las horas de Ajustes, dentro del horario) y *Más
  opciones* (la ficha). Tocar el aviso hace lo mismo que *Contactar*.
* Respeta recordatorios apagados o en pausa, el horario (incluido el que cruza
  la medianoche), la pausa por persona y los cumpleaños (anuales).

## Diferencias con Android

Lo que **no** está en la versión de iOS (todavía): el widget (necesita un App
Group y otro perfil de firma), la física de las burbujas (arrastrar y soltar con
impulso sí, con su háptico; chocar entre ellas no), la burbuja «Con calma», las
actualizaciones dentro de la app (las da la App Store), el aviso de batería
(no aplica: lo sustituye *Estado de los avisos*) y las estadísticas e informes
de fallos (Sentry/PostHog): la de iOS no manda nada fuera del teléfono. Las
caras y el sol son versiones más sencillas de `Cara.kt` y `SistemaSolar.kt`.

Lo que sí tiene, a la manera de iOS:

* **Rueda de horas** (`UI/DialFranja.swift`) y **pastilla deslizante** del tema
  (`Deslizador`, en `UI/Tacto.swift`), con tics hápticos y acciones de
  accesibilidad (VoiceOver ajusta las horas de una en una).
* **Hápticos** (`Haptico`, `.sensoryFeedback`): agarrar y lanzar burbujas, ruedas,
  elecciones, botones que se hunden al pulsarlos y confirmación al anotar.
* **Estado de los avisos** (Ajustes): permiso, alertas y sonido apagados,
  resumen programado, ahorro de energía y actualización en segundo plano, con
  un botón directo a los ajustes de notificaciones de la app.
* **Avisos**: *Contactar*, *Más tarde* y *Más opciones* con icono; descartar un
  aviso cuenta como en Android; el hilo agrupa por nombre («y 3 más de
  Contacto»); la vista previa oculta del bloqueo no muestra nombres; los muy
  atrasados pesan más en el resumen del sistema; los ya atendidos se quitan de
  la bandeja; Ajustes del sistema ofrece «Ajustes de notificaciones de
  Contacto». Sin el permiso *Time Sensitive* (habría que añadirlo al perfil de
  firma) todos salen con el nivel normal.
* **Renovación en segundo plano** (`Logica/Refresco.swift`, `BGAppRefreshTask`):
  cuando el sistema quiere, rehace los avisos aunque no se abra la app.
* **Atajo del icono** «Añadir persona» (`Logica/Atajos.swift`).
* **Valorar** con la petición del sistema, y correo de opiniones que copia la
  dirección si no hay app de correo.
