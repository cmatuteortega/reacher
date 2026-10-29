#!/usr/bin/env python3
"""Genera Localizable.xcstrings de iOS a partir de los strings.xml de Android.

Las traducciones viven en un solo sitio (app/src/main/res/values*/strings.xml);
iOS las lee de aqui. Las claves son los nombres de Android; los plurales se
llaman "<nombre> %lld" para que String(localized: "nombre \\(n)") los encuentre.

Uso: python3 ios/tools/generar_cadenas.py [--comprobar]
Con --comprobar no escribe: sale con error si el fichero generado esta viejo.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
RES = RAIZ / "app/src/main/res"
SALIDA = RAIZ / "ios/Contacto/Recursos/Localizable.xcstrings"
IDIOMAS = {"en": "values", "es": "values-es", "fr": "values-fr", "de": "values-de", "ru": "values-ru"}
CANTIDADES = ["zero", "one", "two", "few", "many", "other"]

EXTRAS_IOS = {
    "ios_desde": {"en": "From", "es": "Desde", "fr": "De", "de": "Von", "ru": "С"},
    "ios_hasta": {"en": "To", "es": "Hasta", "fr": "À", "de": "Bis", "ru": "До"},
    "ios_valorar": {"en": "Rate Contacto", "es": "Valorar Contacto", "fr": "Noter Contacto", "de": "Contacto bewerten", "ru": "Оценить Contacto"},
    "ios_salud_titulo": {"en": "Reminder health", "es": "Estado de los avisos", "fr": "État des rappels", "de": "Zustand der Erinnerungen", "ru": "Состояние напоминаний"},
    "ios_salud_ok": {
        "en": "Everything is in order: reminders can reach you.",
        "es": "Todo en orden: los avisos pueden llegarte.",
        "fr": "Tout est en ordre : les rappels peuvent vous parvenir.",
        "de": "Alles in Ordnung: Erinnerungen erreichen dich.",
        "ru": "Всё в порядке: напоминания дойдут до вас.",
    },
    "ios_salud_permiso": {
        "en": "Reminders need your permission to show up.",
        "es": "Los avisos necesitan tu permiso para salir.",
        "fr": "Les rappels ont besoin de votre autorisation pour s'afficher.",
        "de": "Erinnerungen brauchen deine Erlaubnis, um zu erscheinen.",
        "ru": "Для напоминаний нужно ваше разрешение.",
    },
    "ios_salud_sin_alertas": {
        "en": "Alerts are off for Contacto: reminders arrive without a banner or any trace on the lock screen.",
        "es": "Las alertas de Contacto están apagadas: los avisos llegan sin banner ni rastro en la pantalla de bloqueo.",
        "fr": "Les alertes de Contacto sont désactivées : les rappels arrivent sans bannière ni trace sur l'écran verrouillé.",
        "de": "Die Hinweise von Contacto sind aus: Erinnerungen kommen ohne Banner und ohne Spur auf dem Sperrbildschirm.",
        "ru": "Оповещения Contacto выключены: напоминания приходят без баннера и не видны на экране блокировки.",
    },
    "ios_salud_sin_sonido": {
        "en": "Sounds are off for Contacto: reminders arrive silently.",
        "es": "El sonido de Contacto está apagado: los avisos llegan en silencio.",
        "fr": "Le son de Contacto est désactivé : les rappels arrivent en silence.",
        "de": "Der Ton von Contacto ist aus: Erinnerungen kommen lautlos an.",
        "ru": "Звук Contacto выключен: напоминания приходят без звука.",
    },
    "ios_salud_resumen": {
        "en": "Scheduled Summary is on: iOS may hold reminders until the summary time.",
        "es": "El resumen programado está activado: iOS puede retener los avisos hasta la hora del resumen.",
        "fr": "Le résumé programmé est activé : iOS peut retenir les rappels jusqu'à l'heure du résumé.",
        "de": "Die geplante Zusammenfassung ist an: iOS kann Erinnerungen bis zur Zusammenfassung zurückhalten.",
        "ru": "Включена доставка сводкой: iOS может придерживать напоминания до времени сводки.",
    },
    "ios_salud_sin_segundo_plano": {
        "en": "Background App Refresh is off. Reminders only renew when you open Contacto, so they can run out after a week.",
        "es": "La actualización en segundo plano está apagada. Los avisos solo se renuevan al abrir Contacto, así que pueden acabarse a la semana.",
        "fr": "L'actualisation en arrière-plan est désactivée. Les rappels ne se renouvellent qu'à l'ouverture de Contacto : ils peuvent s'arrêter au bout d'une semaine.",
        "de": "Hintergrundaktualisierung ist aus. Erinnerungen werden nur beim Öffnen von Contacto erneuert und können nach einer Woche ausgehen.",
        "ru": "Фоновое обновление выключено. Напоминания продлеваются только при открытии Contacto и могут закончиться через неделю.",
    },
    "ios_salud_ahorro": {
        "en": "Low Power Mode is on: iOS may delay the background refresh that renews your reminders.",
        "es": "El modo de bajo consumo está activado: iOS puede retrasar la actualización en segundo plano que renueva tus avisos.",
        "fr": "Le mode économie d'énergie est activé : iOS peut retarder l'actualisation en arrière-plan qui renouvelle vos rappels.",
        "de": "Der Stromsparmodus ist an: iOS kann die Hintergrundaktualisierung, die deine Erinnerungen erneuert, verzögern.",
        "ru": "Включён режим энергосбережения: iOS может задерживать фоновое обновление, продлевающее напоминания.",
    },
    "ios_pedir_permiso": {"en": "Allow notifications", "es": "Permitir notificaciones", "fr": "Autoriser les notifications", "de": "Mitteilungen erlauben", "ru": "Разрешить уведомления"},
    "ios_abrir_ajustes_avisos": {
        "en": "Notification settings",
        "es": "Ajustes de notificaciones",
        "fr": "Réglages des notifications",
        "de": "Mitteilungseinstellungen",
        "ru": "Настройки уведомлений",
    },
    "ios_sin_correo": {
        "en": "No email app found. We copied the address: %@",
        "es": "No hay ninguna app de correo. Hemos copiado la dirección: %@",
        "fr": "Aucune app de messagerie. Nous avons copié l'adresse : %@",
        "de": "Keine E-Mail-App gefunden. Wir haben die Adresse kopiert: %@",
        "ru": "Почтовое приложение не найдено. Адрес скопирован: %@",
    },
    "ios_aviso_oculto": {
        "en": "Someone is waiting to hear from you",
        "es": "Alguien espera saber de ti",
        "fr": "Quelqu'un attend de vos nouvelles",
        "de": "Jemand wartet auf ein Lebenszeichen von dir",
        "ru": "Кто-то ждёт вашей весточки",
    },
    "ios_resumen_categoria": {
        "en": "%u more from Contacto",
        "es": "%u más de Contacto",
        "fr": "%u de plus de Contacto",
        "de": "%u weitere von Contacto",
        "ru": "Ещё %u от Contacto",
    },
    "ios_copia_explicacion": {
        "en": "Your people and settings go into your iPhone's backup. To move them yourself, export a file and open it on the other phone (it also works with the Android app).",
        "es": "Tu gente y tus ajustes van en la copia de seguridad del iPhone. Para llevarlos tú mismo, exporta un archivo y ábrelo en el otro teléfono (también vale con la app de Android).",
        "fr": "Vos proches et vos réglages sont inclus dans la sauvegarde de l'iPhone. Pour les déplacer vous-même, exportez un fichier et ouvrez-le sur l'autre téléphone (cela marche aussi avec l'app Android).",
        "de": "Deine Leute und Einstellungen sind im Backup des iPhones. Um sie selbst umzuziehen, exportiere eine Datei und öffne sie auf dem anderen Telefon (geht auch mit der Android-App).",
        "ru": "Ваши люди и настройки входят в резервную копию iPhone. Чтобы перенести их самостоятельно, экспортируйте файл и откройте его на другом телефоне (подходит и для приложения на Android).",
    },
    "ios_opiniones_explicacion": {
        "en": "Something not working, or an idea? Write to us.",
        "es": "¿Algo no funciona o tienes una idea? Escríbenos.",
        "fr": "Quelque chose ne marche pas, ou une idée ? Écrivez-nous.",
        "de": "Etwas funktioniert nicht oder du hast eine Idee? Schreib uns.",
        "ru": "Что-то не работает или есть идея? Напишите нам.",
    },
    "ios_privacidad_texto": {
        "en": "Contacto sends nothing off your phone: no accounts, no tracking, no analytics. Your people never leave it.",
        "es": "Contacto no manda nada fuera del teléfono: sin cuentas, sin rastreo, sin estadísticas. Tu gente no sale de él.",
        "fr": "Contacto n'envoie rien hors de votre téléphone : ni compte, ni suivi, ni statistiques. Vos proches n'en sortent jamais.",
        "de": "Contacto sendet nichts vom Telefon: keine Konten, kein Tracking, keine Statistiken. Deine Leute verlassen es nie.",
        "ru": "Contacto ничего не отправляет с телефона: ни аккаунтов, ни слежки, ни статистики. Ваши люди остаются на нём.",
    },
}


def a_ios(texto: str) -> str:
    """Escapes de Android -> texto normal, y marcadores de formato de Android -> de Apple."""
    texto = texto.strip()
    if len(texto) >= 2 and texto[0] == '"' and texto[-1] == '"':
        texto = texto[1:-1]
    texto = texto.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")
    texto = re.sub(r"%(\d+\$)?s", lambda m: f"%{m.group(1) or ''}@", texto)
    texto = re.sub(r"%(\d+\$)?d", lambda m: f"%{m.group(1) or ''}lld", texto)
    return texto


def leer(carpeta: str):
    arbol = ET.parse(RES / carpeta / "strings.xml").getroot()
    simples, plurales = {}, {}
    for e in arbol:
        if e.get("translatable") == "false":
            continue
        if e.tag == "string":
            simples[e.get("name")] = a_ios("".join(e.itertext()))
        elif e.tag == "plurals":
            plurales[e.get("name")] = {i.get("quantity"): a_ios("".join(i.itertext())) for i in e}
    return simples, plurales


def unidad(valor: str):
    return {"stringUnit": {"state": "translated", "value": valor}}


def generar() -> str:
    datos = {idioma: leer(carpeta) for idioma, carpeta in IDIOMAS.items()}
    # Cadenas sin traducir (translatable=false) que la app tambien necesita, iguales en todo.
    fijas = {"app_name": "Contacto", "whatsapp": "WhatsApp", "telegram": "Telegram", "sms": "SMS"}
    cadenas = {}
    for nombre, valor in fijas.items():
        cadenas[nombre] = {"localizations": {i: unidad(valor) for i in IDIOMAS}}
    claves = sorted(datos["en"][0])
    for nombre in claves:
        cadenas[nombre] = {"localizations": {i: unidad(datos[i][0][nombre]) for i in IDIOMAS if nombre in datos[i][0]}}
    for nombre in sorted(datos["en"][1]):
        # Un plural sin cifra ("dia · desliza") no vale para Apple, que lo exige; iOS no lo usa.
        if not all("%lld" in v or "%1$lld" in v for i in IDIOMAS for v in datos[i][1].get(nombre, {}).values()):
            continue
        loc = {}
        for i in IDIOMAS:
            if nombre not in datos[i][1]:
                continue
            q = datos[i][1][nombre]
            loc[i] = {"variations": {"plural": {c: unidad(q[c]) for c in CANTIDADES if c in q}}}
        cadenas[f"{nombre} %lld"] = {"localizations": loc}
    # Textos propios de iOS (claves "ios_..."): lo que en Android se hace con otra pantalla o no existe.
    for nombre, t in EXTRAS_IOS.items():
        cadenas[nombre] = {"localizations": {i: unidad(v) for i, v in t.items()}}
    return json.dumps({"sourceLanguage": "en", "strings": cadenas, "version": "1.0"}, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


if __name__ == "__main__":
    nuevo = generar()
    if "--comprobar" in sys.argv:
        if not SALIDA.exists() or SALIDA.read_text(encoding="utf-8") != nuevo:
            sys.exit("Localizable.xcstrings esta desactualizado: ejecuta python3 ios/tools/generar_cadenas.py")
    else:
        SALIDA.write_text(nuevo, encoding="utf-8")
        print(f"{SALIDA.relative_to(RAIZ)}: {nuevo.count(chr(10))} lineas")
