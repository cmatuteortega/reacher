#!/usr/bin/env python3
"""Icono de iOS (1024x1024, sin transparencia): el sol de Contacto en naranja sobre marino."""
import math
from pathlib import Path
from PIL import Image, ImageDraw

N = 1024 * 2  # se dibuja al doble y se reduce, para bordes suaves
img = Image.new("RGB", (N, N), (0x20, 0x3C, 0x56))
d = ImageDraw.Draw(img)
c = N / 2
puntas = 12
pts = []
for i in range(puntas * 2 * 8):
    t = i / (puntas * 2 * 8) * 2 * math.pi
    r = N * (0.30 + 0.045 * math.sin(t * puntas))
    pts.append((c + r * math.cos(t), c + r * math.sin(t)))
halo = Image.new("RGB", (N, N), (0x20, 0x3C, 0x56))
hd = ImageDraw.Draw(halo)
hd.ellipse([c - N * 0.41, c - N * 0.41, c + N * 0.41, c + N * 0.41], fill=(0x3A, 0x4A, 0x62))
img = Image.blend(img, halo, 0.9)
d = ImageDraw.Draw(img)
d.polygon(pts, fill=(0xFF, 0xAA, 0x5E))
# Ojos
for dx in (-0.075, 0.075):
    ex, ey = c + N * dx, c - N * 0.01
    d.ellipse([ex - N * 0.028, ey - N * 0.05, ex + N * 0.028, ey + N * 0.05], fill=(0x0D, 0x2B, 0x45))
img = img.resize((1024, 1024), Image.LANCZOS)
destino = Path(__file__).resolve().parents[1] / "Contacto/Recursos/Assets.xcassets/AppIcon.appiconset/icono.png"
img.save(destino)
print(destino)
