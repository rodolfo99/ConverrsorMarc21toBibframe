# Ejemplos

- `catalogo-ejemplo.mrc`: MARC21 ISO2709 válido para probar la conversión normal.
- `catalogo-utf16-caret.marc`: archivo deliberadamente problemático en UTF-16LE con BOM y marcador `^a`, incluido para probar `--repair`.

Conversión normal:

```bash
mvn compile -Dexec.args="examples/catalogo-ejemplo.mrc salida/ejemplo.ttl --format turtle --validate" exec:java@convertir
```

Prueba de reparación automática:

```bash
mvn clean compile -Dexec.args="examples/catalogo-utf16-caret.marc salida/prueba-repair.ttl --repair --format turtle --validate" exec:java@convertir
```
