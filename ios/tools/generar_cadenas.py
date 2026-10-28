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
