# Changelog

## 1.1.0 (corrección de empaquetado Jena)

- Corregido el fat JAR `-all.jar` para Apache Jena.
- Añadido `ServicesResourceTransformer` al `maven-shade-plugin` para fusionar `META-INF/services`.
- Evita el error `ExceptionInInitializerError` / `TypeMapper.getInstance() == null` al ejecutar con `java -jar`.
- Se conservan todas las funciones existentes, incluida `--repair`.

## 1.1.0

- Añadido `MarcRepairService` en Java.
- Añadida opción `--repair` al conversor MARC21 → BIBFRAME.
- Detección y conversión UTF-16LE/UTF-16BE → UTF-8.
- Eliminación de BOM UTF-8.
- Conversión de marcadores `^a`, `^b`, etc. a delimitador MARC `0x1F`.
- Reconstrucción de Directory, offsets, longitudes, Base Address y record length.
- Validación estructural ISO2709 antes de Marc4J.
- Añadido comando independiente `exec:java@reparar`.
- Añadidos scripts `convertir-repair.sh`, `reparar.sh` y `probar-repair.sh`.
- Añadido ejemplo UTF-16 problemático para probar `--repair`.
- Incluido `tools/reparar_marc_completo.py` como utilidad alternativa/diagnóstica.
