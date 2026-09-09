# Utilerías de la carpeta `scripts`

Esta carpeta contiene scripts Bash para ejecutar las operaciones más comunes del proyecto MARC21 → BIBFRAME sin tener que escribir manualmente los comandos Maven completos.

Los scripts están pensados para ejecutarse desde la **raíz del proyecto**, es decir, desde la carpeta donde se encuentra `pom.xml`.

## Requisitos

Antes de utilizarlos verifica que estén disponibles:

```bash
java -version
mvn -version
```

El proyecto utiliza:

- Java 21
- Maven 3.9 o posterior
- Bash

Si al clonar el repositorio los scripts no tienen permiso de ejecución, puedes asignarlo con:

```bash
chmod +x scripts/*.sh
```

## Resumen rápido

| Script | Función |
|---|---|
| `generar.sh` | Genera registros MARC21 ISO2709 sintéticos para pruebas. |
| `inspeccionar.sh` | Lee e imprime registros MARC21 utilizando Marc4J. |
| `convertir.sh` | Convierte un MARC21 válido a BIBFRAME Turtle y genera reporte CSV. |
| `convertir-repair.sh` | Repara un MARC problemático y después lo convierte a BIBFRAME. |
| `reparar.sh` | Repara un MARC21 y conserva el archivo MARC reparado. |
| `probar-repair.sh` | Ejecuta una prueba preparada del mecanismo de reparación. |

---

## `generar.sh`

Genera un archivo MARC21 ISO2709 sintético mediante `GeneratorCli`.

### Sintaxis

```bash
./scripts/generar.sh [archivo_salida] [cantidad] [semilla]
```

### Valores por defecto

Si no se proporcionan argumentos utiliza:

```text
archivo_salida = catalogo.mrc
cantidad       = 1000
semilla        = 12345
```

Equivale a ejecutar:

```bash
mvn compile -Dexec.args="catalogo.mrc 1000 12345" exec:java@generar
```

### Ejemplos

Generar 1000 registros:

```bash
./scripts/generar.sh
```

Generar 500 registros en otro archivo:

```bash
./scripts/generar.sh ejemplos/prueba.mrc 500 12345
```

La semilla permite obtener datos reproducibles entre ejecuciones.

---

## `inspeccionar.sh`

Permite abrir un MARC21 ISO2709 y mostrar algunos registros para inspección utilizando `InspectCli` y Marc4J.

### Sintaxis

```bash
./scripts/inspeccionar.sh [archivo_marc] [cantidad]
```

### Valores por defecto

```text
archivo_marc = catalogo.mrc
cantidad     = 5
```

Equivale a:

```bash
mvn compile -Dexec.args="catalogo.mrc 5" exec:java@inspeccionar
```

### Ejemplos

Inspeccionar los primeros cinco registros:

```bash
./scripts/inspeccionar.sh catalogo.mrc
```

Inspeccionar los primeros diez registros:

```bash
./scripts/inspeccionar.sh fichas_siabuc.iso 10
```

Este script es útil para comprobar si Marc4J puede leer directamente el archivo antes de intentar convertirlo.

---

## `convertir.sh`

Convierte un archivo MARC21 válido a BIBFRAME utilizando `ConverterCli`.

El script genera por defecto:

- RDF en formato Turtle;
- reporte CSV de etiquetas encontradas y mapeadas;
- validación sintáctica del RDF generado.

### Sintaxis

```bash
./scripts/convertir.sh [entrada] [salida] [reporte] [opciones_adicionales]
```

### Valores por defecto

```text
entrada = catalogo.mrc
salida  = salida/catalogo-bibframe.ttl
reporte = salida/reporte.csv
```

El comando Maven utilizado internamente es equivalente a:

```bash
mvn compile \
  -Dexec.args="catalogo.mrc salida/catalogo-bibframe.ttl --format turtle --report salida/reporte.csv --validate" \
  exec:java@convertir
```

### Ejemplo básico

```bash
./scripts/convertir.sh catalogo.mrc salida/catalogo.ttl salida/reporte.csv
```

### Ejemplo con `fichas_siabuc.iso`

Si el archivo ya es un MARC ISO2709 válido:

```bash
./scripts/convertir.sh \
  fichas_siabuc.iso \
  salida/fichas_siabuc.ttl \
  salida/reporte.csv
```

### Opciones adicionales

`convertir.sh` permite pasar argumentos adicionales después de los tres primeros parámetros.

Por ejemplo, para procesar sólo diez registros:

```bash
./scripts/convertir.sh \
  catalogo.mrc \
  salida/prueba.ttl \
  salida/reporte.csv \
  --limit 10
```

También puedes cambiar la URI base:

```bash
./scripts/convertir.sh \
  catalogo.mrc \
  salida/catalogo.ttl \
  salida/reporte.csv \
  --base https://mi-biblioteca.mx/bibframe/
```

> Este script fija el formato de salida en `turtle`. Para RDF/XML, JSON-LD o N-Triples utiliza directamente `mvn ... exec:java@convertir` o el JAR ejecutable documentado en el README principal.

---

## `convertir-repair.sh`

Repara primero un MARC problemático y después lo convierte automáticamente a BIBFRAME Turtle.

Está pensado para archivos que, por ejemplo:

- fueron exportados como UTF-16LE o UTF-16BE;
- contienen BOM;
- usan marcadores textuales como `^a`, `^b`, etc.;
- tienen longitudes u offsets ISO2709 desajustados;
- requieren reconstrucción de Directory, Leader o Base Address.

### Sintaxis

```bash
./scripts/convertir-repair.sh [entrada] [salida] [reporte]
```

### Valores por defecto

```text
entrada = catalogo.marc
salida  = salida/catalogo-bibframe.ttl
reporte = salida/reporte.csv
```

Internamente ejecuta:

```bash
mvn compile \
  -Dexec.args="catalogo.marc salida/catalogo-bibframe.ttl --repair --format turtle --report salida/reporte.csv --validate" \
  exec:java@convertir
```

### Ejemplo con `fichas_siabuc.iso`

```bash
./scripts/convertir-repair.sh \
  fichas_siabuc.iso \
  salida/fichas_siabuc.ttl \
  salida/reporte.csv
```

Durante este proceso el conversor crea un MARC reparado temporal, lo utiliza para convertir a BIBFRAME y después elimina el archivo temporal. El archivo de entrada original no se modifica.

### Cuándo usarlo

Usa `convertir-repair.sh` cuando `convertir.sh` falle al leer el MARC o cuando conozcas que el archivo requiere normalización.

Si el MARC ya es ISO2709 válido, normalmente es preferible `convertir.sh`.

---

## `reparar.sh`

Ejecuta solamente el proceso de reparación mediante `RepairCli` y conserva el MARC reparado como archivo independiente.

A diferencia de `convertir-repair.sh`, este script **no convierte a BIBFRAME**.

### Sintaxis

```bash
./scripts/reparar.sh [entrada] [salida]
```

### Valores por defecto

```text
entrada = catalogo.marc
salida  = catalogo-final.mrc
```

Internamente equivale a:

```bash
mvn compile -Dexec.args="catalogo.marc catalogo-final.mrc" exec:java@reparar
```

### Ejemplo

```bash
./scripts/reparar.sh fichas_siabuc.iso salida/fichas_siabuc-reparado.mrc
```

Después puedes inspeccionar el archivo reparado:

```bash
./scripts/inspeccionar.sh salida/fichas_siabuc-reparado.mrc 5
```

Y posteriormente convertirlo normalmente:

```bash
./scripts/convertir.sh \
  salida/fichas_siabuc-reparado.mrc \
  salida/fichas_siabuc.ttl \
  salida/reporte.csv
```

El archivo de entrada y el archivo de salida deben ser rutas distintas.

---

## `probar-repair.sh`

Ejecuta una prueba preparada del sistema de reparación utilizando el archivo incluido:

```text
examples/catalogo-utf16-caret.marc
```

Este ejemplo contiene deliberadamente condiciones que requieren reparación.

### Ejecución

```bash
./scripts/probar-repair.sh
```

El script crea la carpeta `salida` si no existe y ejecuta:

```bash
mvn clean compile \
  -Dexec.args="examples/catalogo-utf16-caret.marc salida/prueba-repair.ttl --repair --format turtle --validate" \
  exec:java@convertir
```

Si termina correctamente, debe generarse:

```text
salida/prueba-repair.ttl
```

Este script es especialmente útil después de modificar `MarcRepairService`, `ConverterCli` o el `pom.xml` para comprobar rápidamente que el flujo de reparación sigue funcionando.

---

## Flujo recomendado para un archivo MARC desconocido

### 1. Intentar inspeccionarlo

```bash
./scripts/inspeccionar.sh fichas_siabuc.iso 5
```

### 2. Si es válido, convertirlo directamente

```bash
./scripts/convertir.sh \
  fichas_siabuc.iso \
  salida/catalogo.ttl \
  salida/reporte.csv
```

### 3. Si presenta problemas, reparar y convertir

```bash
./scripts/convertir-repair.sh \
  fichas_siabuc.iso \
  salida/catalogo.ttl \
  salida/reporte.csv
```

### 4. Si necesitas conservar el MARC reparado

```bash
./scripts/reparar.sh \
  fichas_siabuc.iso \
  salida/fichas_siabuc-reparado.mrc
```

## Notas

- Los scripts utilizan `set -euo pipefail`, por lo que terminan inmediatamente si ocurre un error, se utiliza una variable no definida o falla una parte de una tubería Bash.
- Deben ejecutarse desde la raíz del repositorio para que Maven encuentre `pom.xml` y para que las rutas relativas funcionen como están documentadas.
- Las carpetas de salida deben existir cuando corresponda, excepto en `probar-repair.sh`, que crea automáticamente `salida`.
- Para opciones avanzadas de conversión, formatos RDF distintos de Turtle o uso directo del JAR ejecutable, consulta el [`README.md`](../README.md) principal del proyecto.
