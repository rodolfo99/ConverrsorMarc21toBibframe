# Reparación MARC21 integrada

La opción `--repair` está pensada para archivos que conceptualmente contienen registros MARC21/ISO2709, pero fueron exportados o guardados como UTF-16 o como una variante textual que cambió los delimitadores de subcampo.

## Problemas corregidos

- UTF-16LE con BOM `FF FE`.
- UTF-16BE con BOM `FE FF`.
- Detección heurística de UTF-16 sin BOM.
- UTF-8 con BOM `EF BB BF`.
- Marcadores `^a`, `^b`, `^c`, `^0`, etc. convertidos a `0x1F + código`.
- Longitudes de campos del Directory recalculadas en bytes UTF-8.
- Offsets de campos del Directory recalculados.
- Leader/00-04 (record length) recalculado.
- Leader/12-16 (base address) recalculado.
- Leader/09 establecido en `a` (Unicode).
- Leader/20-23 establecido en `4500`.
- Validación de `0x1E` (fin de campo) y `0x1D` (fin de registro).

## Conversión BIBFRAME con reparación automática

```bash
mvn clean compile \
  -Dexec.args="catalogo.marc salida/catalogo-bibframe.ttl --repair --format turtle --report salida/reporte.csv --validate" \
  exec:java@convertir
```

El archivo original NO se modifica. Se crea un MARC temporal reparado, se convierte con Marc4J y luego se elimina el temporal.

## Solo reparar y conservar el ISO2709

```bash
mvn compile \
  -Dexec.args="catalogo.marc catalogo-final.mrc" \
  exec:java@reparar
```

## Ejemplo incluido para probar `--repair`

El proyecto incluye un archivo deliberadamente problemático:

```text
examples/catalogo-utf16-caret.marc
```

Está codificado en UTF-16LE con BOM y usa `^a` como marcador de subcampo.

Prueba:

```bash
mvn clean compile \
  -Dexec.args="examples/catalogo-utf16-caret.marc salida/prueba-repair.ttl --repair --format turtle --validate" \
  exec:java@convertir
```

O solo repara:

```bash
mvn compile \
  -Dexec.args="examples/catalogo-utf16-caret.marc salida/prueba-reparada.mrc" \
  exec:java@reparar
```

## Cuándo NO usar `--repair`

Si el archivo ya es MARC21 ISO2709 correcto, usa la conversión normal sin `--repair`. La reparación está pensada para corregir exportaciones dañadas o recodificadas.

## Limitación importante

La reparación depende de que se hayan conservado los separadores físicos `0x1E` y `0x1D`, así como un Directory reconocible. Si la exportación convirtió también esos separadores a texto visible o eliminó el Directory, se necesitaría un importador específico para ese formato.
