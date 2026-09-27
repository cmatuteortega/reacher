# Publicar en Google Play

Lo que hay en el repositorio y lo que hay que hacer a mano, una vez.

Mientras se sigue probando no hace falta nada de esto: la versión de
depuración se compila igual que antes (en local y en CI, artefacto
`app-debug`). Todo lo de abajo es para cuando toque publicar.

## Identificador

`applicationId = "com.cmatuteortega.contacto"` (`app/build.gradle.kts`). **No
puede cambiar nunca** después de la primera subida. El paquete del código
(`namespace`, `com.example.recuerdallamar`) no lo ve Play y se queda como está.
Si algún día cambia el `applicationId`, cambiar también `targetPackage` en
`res/xml/atajos.xml`.

## Firma

Play firma la app con su propia clave (*Play App Signing*); nosotros firmamos
con una **clave de subida**. Crearla una vez y guardarla (y sus contraseñas)
fuera del repositorio, en un gestor de contraseñas:

```sh
keytool -genkeypair -v -keystore subida.jks -alias subida \
  -keyalg RSA -keysize 2048 -validity 10000
```

**En CI**: en *Settings › Secrets and variables › Actions* del repositorio,
cuatro secretos:

| secreto                      | valor                                 |
|------------------------------|---------------------------------------|
| `CONTACTO_KEYSTORE_BASE64`   | `base64 -w0 subida.jks`               |
| `CONTACTO_KEYSTORE_PASSWORD` | contraseña del almacén                |
| `CONTACTO_KEY_ALIAS`         | `subida`                              |
| `CONTACTO_KEY_PASSWORD`      | contraseña de la clave                |

Con ellos, cada push deja el artefacto `app-release-<versión>` con el `.aab`
firmado y el `mapping.txt` de R8. Sin ellos, CI sigue compilando solo el APK
de depuración.

**En local**: un `keystore.properties` en la raíz (lo ignora git) con las
mismas claves, `CONTACTO_KEYSTORE` como ruta al `.jks`:

```properties
CONTACTO_KEYSTORE=/ruta/a/subida.jks
CONTACTO_KEYSTORE_PASSWORD=...
CONTACTO_KEY_ALIAS=subida
CONTACTO_KEY_PASSWORD=...
```

y `./gradlew bundleRelease`.

## Versiones

- `versionCode` = número de ejecución de GitHub Actions (siempre sube).
- `versionName` = `git describe --tags`: etiquetar las versiones como
  `v1.0.0`; entre etiquetas sale `1.0.0-3-gabc1234`.

En local salen `1` y `dev`; se pueden forzar con
`-Pcontacto.versionCode=… -Pcontacto.versionName=…`.

## R8

La versión publicada va minimizada y sin recursos sobrantes
(`app/proguard-rules.pro`). Antes de cada subida, instalar el `.aab` o un
`assembleRelease` en un teléfono y dar una vuelta: bienvenida, añadir a
alguien, ficha, ajustes, widget, y forzar un aviso esperando al día siguiente
(el botón de forzar solo existe en depuración). Subir el `mapping.txt` a Play
Console junto al `.aab` para leer las trazas de errores.

Solo en depuración: el botón «Forzar notificación» de la ficha y *Ajustes ›
Depuración › Idioma de la app* (en Android 13+ el idioma se cambia desde los
ajustes del sistema).

## Política de privacidad

En `docs/` (español e inglés). Activar GitHub Pages: *Settings › Pages ›
Deploy from a branch*, rama `main`, carpeta `/docs`. La URL para Play Console
será `https://cmatuteortega.github.io/reacher/privacy.html`. **Antes de
publicar**, cambiar `CORREO_DE_CONTACTO` en las dos páginas por un correo de
contacto real.

## Seguridad de los datos (Play Console)

Respuestas para el formulario *Data safety*, según lo que hace la app (sin
permiso de Internet; todo en el teléfono):

- **¿Recoge o comparte datos de usuario?** No. Los datos que solo se procesan
  en el dispositivo y no salen de él no cuentan como recogidos.
- **Cifrado en tránsito**: no aplica (no se transmite nada).
- **¿Se pueden pedir borrar los datos?** Sí: se borran desde la app o al
  desinstalarla.
- **Permisos sensibles**: `READ_CONTACTS` (foto y cumpleaños de las personas
  añadidas, en el dispositivo) y `CALL_PHONE` (solo con «Llamar
  directamente»). Explicarlo igual en la declaración de permisos si Play la
  pide.

## Ficha de la tienda

Textos en `fastlane/metadata/android/<idioma>/` (título ≤ 30, descripción
corta ≤ 80, completa ≤ 4000) para es-ES, en-US, fr-FR, de-DE y ru-RU. Se
pueden copiar a mano en Play Console o subir con `fastlane supply`.

Falta, a mano, en cada `images/`:

- `phoneScreenshots/`: de 2 a 8 capturas del teléfono (1080×1920 o similar):
  burbujas, órbitas, ficha, aviso, widget, tema oscuro.
- `featureGraphic.png`: 1024×500, el sol sobre marino.
- `icon.png`: 512×512 (el icono de la app).

## Play Console, paso a paso

1. **Cuenta de desarrollador** en <https://play.google.com/console/signup>:
   pago único de 25 USD y verificación de identidad (puede tardar unos días).
   Las cuentas personales nuevas tienen que pasar una **prueba cerrada con al
   menos 12 personas durante 14 días seguidos** antes de poder publicar en
   producción; conviene empezarla cuanto antes.
2. **Crear la app**: *Crear aplicación* › nombre «Contacto», idioma
   predeterminado, *Aplicación*, *Gratuita* (no se puede pasar de gratuita a
   de pago después).
3. **Contenido de la aplicación** (*Política › Contenido de la aplicación*),
   todo obligatorio antes de la primera versión:
   - *Política de privacidad*: la URL de GitHub Pages de arriba.
   - *Anuncios*: no contiene anuncios.
   - *Acceso a la aplicación*: todas las funciones sin restricciones (no hay
     inicio de sesión).
   - *Clasificación de contenido*: el cuestionario; categoría «Utilidades /
     productividad», sin contenido sensible.
   - *Público objetivo*: mayores de 18 (o 13+); no dirigida a niños.
   - *Seguridad de los datos*: las respuestas de la sección de arriba.
   - *Aplicación gubernamental*, *funciones financieras*, *salud*,
     *noticias*: no.
4. **Ficha de la tienda** (*Crecimiento › Ficha de Play Store*): copiar
   título y descripciones de `fastlane/metadata/android/es-ES/`, añadir las
   traducciones (*Gestionar traducciones*) con las de los demás idiomas,
   icono de 512×512, gráfico destacado y capturas. Categoría:
   *Productividad* (o *Estilo de vida*).
5. **Primera subida** (*Probar y publicar › Pruebas › Pruebas internas*):
   crear versión, aceptar *Firma de aplicaciones de Play* (Google guarda la
   clave definitiva; la nuestra queda como clave de subida), subir el `.aab`
   del artefacto `app-release-<versión>` de CI, notas de la versión y
   publicar. Añadir testers por correo y abrir el enlace de invitación desde
   el teléfono.
6. **Prueba cerrada** (*Pruebas cerradas*): la misma versión (o una nueva),
   con la lista de los 12+ testers. Pasados los 14 días, *Solicitar acceso a
   producción* desde el panel.
7. **Producción**: crear versión en *Producción*, subir el `.aab`, enviar a
   revisión (de horas a varios días la primera vez). Mejor un lanzamiento
   escalonado (p. ej. 20 %) y subirlo si no llegan errores.

Cada versión nueva: etiquetar (`git tag v1.1.0 && git push --tags`), esperar
a CI, descargar el `.aab` y el `mapping.txt` del artefacto y subirlos en la
pista que toque. El `versionCode` sube solo. Los artefactos de GitHub caducan
a los 90 días: guardar el `.aab` de lo que se publique.
