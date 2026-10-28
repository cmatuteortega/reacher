# Publicar la versión de iOS

Lo que hay en el repositorio y lo que hay que hacer a mano, una vez. Mientras
solo se prueba no hace falta nada de esto: `.github/workflows/ios.yml` compila,
pasa las pruebas y deja la app de simulador como artefacto.

## Identificador

`com.cmatuteortega.contacto` (`project.yml`). Como en Android, no puede cambiar
después de la primera subida.

## Una vez, en Apple

Hace falta una cuenta del [Apple Developer Program](https://developer.apple.com/programs/) (de pago, anual).

1. **Identificador**: en *Certificates, Identifiers & Profiles › Identifiers*,
   un App ID con el identificador de arriba. No necesita capacidades.
2. **La app en App Store Connect**: *Apps › +  › Nueva app*, con ese identificador.
3. **Certificado de distribución**: *Certificates › +  › Apple Distribution*
   (se crea con una petición de firma desde *Acceso a Llaveros*). Exportarlo con
   su clave privada como `.p12`, con contraseña.
4. **Perfil de aprovisionamiento**: *Profiles › +  › App Store Connect*, con el
   App ID y el certificado. Descargar el `.mobileprovision`.
5. **Clave de la API de App Store Connect**: *Usuarios y acceso › Integraciones
   › Claves › +*, con rol *App Manager* (o *Developer*). Se descarga **una sola
   vez** el `.p8`; anotar *Key ID* y *Issuer ID*.

## Secretos de GitHub

En *Settings › Secrets and variables › Actions*:

| secreto                | valor                                         |
|------------------------|-----------------------------------------------|
| `IOS_CERT_P12_BASE64`  | `base64 -i certificado.p12`                   |
| `IOS_CERT_PASSWORD`    | contraseña del `.p12`                         |
| `IOS_PROFILE_BASE64`   | `base64 -i perfil.mobileprovision`            |
| `IOS_TEAM_ID`          | el *Team ID* (10 caracteres)                  |
| `APPSTORE_KEY_ID`      | *Key ID* de la clave de la API                |
| `APPSTORE_ISSUER_ID`   | *Issuer ID*                                   |
| `APPSTORE_KEY_P8`      | el contenido del `.p8`, tal cual              |

Con los cuatro primeros, cada ejecución deja el artefacto `ios-ipa-<versión>-<n>`
con el `.ipa` firmado. Sin ellos, CI sigue compilando y probando (solo se salta
la firma).

## Versiones y TestFlight

* El **número de compilación** es el número de ejecución de GitHub Actions
  (siempre sube, como `versionCode`).
* La **versión** es la de la última etiqueta `ios-v*` (`ios-v1.0.0` → `1.0.0`),
  separada de las `v*` de Android para que cada tienda lleve su ritmo.
* **Subir a TestFlight**: etiquetar y empujar la etiqueta.

  ```sh
  git tag ios-v1.0.0 && git push origin ios-v1.0.0
  ```

  La ejecución firma, exporta y sube con `altool` (hacen falta los tres
  `APPSTORE_*`). A los pocos minutos aparece en TestFlight; la primera vez Apple
  la revisa antes de dejarla a los probadores externos.

## Antes de la primera versión pública

* **Ficha de la tienda**: nombre, subtítulo, descripción y palabras clave en los
  cinco idiomas, capturas (6,9" y 6,5" para iPhone, y iPad si se ofrece) y
  categoría. No están en el repositorio todavía (Android tiene su
  `fastlane/metadata/android`).
* **Privacidad de la app** (*App Privacy*): *Data Not Collected*; la versión de
  iOS no manda nada. `Contacto/PrivacyInfo.xcprivacy` declara lo que Apple pide
  (sin rastreo, solo `UserDefaults` para los ajustes). La política es la misma
  de Android (`docs/privacy.html`).
* **Revisión**: la app pide permiso de notificaciones (en la bienvenida) y no
  pide agenda (usa el selector del sistema). Sin cuentas ni compras.

## A mano, antes de cada subida

Lo que las pruebas no cubren, en un iPhone de verdad:

- [ ] Bienvenida entera: permiso de avisos, horario, elegir de la agenda.
- [ ] Un aviso llega a la hora (poner la hora de inicio dos minutos más
      adelante), y *Contactar*, *Más tarde* y *Más opciones* hacen lo suyo.
- [ ] *Llamar*, *Mensaje*, *WhatsApp* y *Telegram* abren lo que deben.
- [ ] Las tres vistas, claro y oscuro, texto grande, y girar el iPad.
- [ ] Exportar una copia, borrar la gente e importarla; y abrir una copia hecha
      en Android.
