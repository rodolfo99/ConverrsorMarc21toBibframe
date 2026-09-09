#!/usr/bin/env python3
"""
reparar_marc_completo.py

Repara un catálogo MARC21 que fue guardado/transformado como texto UTF-16
y lo devuelve como MARC21 ISO2709 en UTF-8.

Correcciones realizadas:
1. Detecta UTF-16LE / UTF-16BE con BOM (y heurística sin BOM).
2. Convierte a UTF-8 sin BOM.
3. Elimina BOM UTF-8 si existe.
4. Convierte marcadores textuales de subcampo ^a, ^b, ^0, etc. a 0x1F.
5. Reconstruye Leader y Directory de CADA registro:
   - longitud total del registro (Leader 00-04)
   - base address (Leader 12-16)
   - mapa de entradas 4500 (Leader 20-23)
   - longitud real de cada campo
   - offset real de cada campo
6. Marca Leader/09 = 'a' (Unicode/UTF-8).
7. Conserva 0x1E como terminador de campo y 0x1D como terminador de registro.
8. Valida estructuralmente el ISO2709 resultante.

Uso:
    python3 reparar_marc_completo.py catalogo.marc catalogo-final.mrc

Opcional:
    --no-caret
        No convierte ^a, ^b, etc. a delimitadores 0x1F.

    --guardar-normalizado archivo.mrc
        Guarda además el flujo UTF-8 normalizado ANTES de reconstruir Directory.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

SUBFIELD_DELIMITER = b"\x1f"
FIELD_TERMINATOR = b"\x1e"
RECORD_TERMINATOR = b"\x1d"

UTF8_BOM = b"\xef\xbb\xbf"
UTF16LE_BOM = b"\xff\xfe"
UTF16BE_BOM = b"\xfe\xff"

CARET_SUBFIELD_RE = re.compile(rb"\^([A-Za-z0-9])")


def detectar_utf16_sin_bom(data: bytes) -> str | None:
    """
    Heurística sencilla para detectar UTF-16 sin BOM.
    ASCII en UTF-16LE suele verse 30 00 30 00...
    ASCII en UTF-16BE suele verse 00 30 00 30...
    """
    sample = data[:4096]

    if len(sample) < 20:
        return None

    pares = len(sample) // 2
    if pares == 0:
        return None

    even = sample[0:pares * 2:2]
    odd = sample[1:pares * 2:2]

    zero_even = even.count(0) / max(1, len(even))
    zero_odd = odd.count(0) / max(1, len(odd))

    if zero_odd > 0.25 and zero_even < 0.10:
        return "utf-16-le"

    if zero_even > 0.25 and zero_odd < 0.10:
        return "utf-16-be"

    return None


def normalizar_codificacion(data: bytes) -> tuple[bytes, str]:
    """
    Devuelve datos en UTF-8 SIN BOM.

    Si no detecta UTF-16 ni BOM UTF-8, deja los bytes como están.
    Esto evita corromper accidentalmente un ISO2709 ya binario.
    """
    if data.startswith(UTF16LE_BOM):
        texto = data.decode("utf-16")
        return texto.encode("utf-8"), "UTF-16LE con BOM -> UTF-8"

    if data.startswith(UTF16BE_BOM):
        texto = data.decode("utf-16")
        return texto.encode("utf-8"), "UTF-16BE con BOM -> UTF-8"

    if data.startswith(UTF8_BOM):
        return data[len(UTF8_BOM):], "UTF-8 con BOM -> UTF-8 sin BOM"

    detectado = detectar_utf16_sin_bom(data)

    if detectado:
        texto = data.decode(detectado)
        return texto.encode("utf-8"), f"{detectado.upper()} sin BOM -> UTF-8"

    return data, "sin recodificación (ya binario/UTF-8 o codificación no detectada)"

