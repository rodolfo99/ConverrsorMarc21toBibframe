# Herramientas de la carpeta `tools`

Esta carpeta contiene utilerías auxiliares independientes del flujo principal Java/Maven del proyecto.

Actualmente incluye:

- `reparar_marc_completo.py`: reparación completa de archivos MARC21/ISO2709 dañados o transformados incorrectamente, especialmente archivos guardados como UTF-16 o con delimitadores de subcampo escritos como `^a`, `^b`, etc.

---

## `reparar_marc_completo.py`

`reparar_marc_completo.py` es una herramienta escrita en Python que toma un archivo MARC21 problemático y genera un archivo MARC21 ISO2709 reconstruido en UTF-8.

A diferencia del flujo Java del proyecto, esta utilidad puede ejecutarse directamente con Python y no necesita Maven ni compilar el proyecto.

## Requisitos

Se requiere:

```bash
python3 --version
```

La herramienta utiliza únicamente módulos de la biblioteca estándar de Python:

- `argparse`
- `re`
- `sys`
- `pathlib`

No necesita instalar paquetes mediante `pip`.

## Sintaxis

Desde la raíz del proyecto:

```bash
python3 tools/reparar_marc_completo.py [entrada] [salida] [opciones]
```

También puede ejecutarse desde la carpeta `tools`:

```bash
cd tools
python3 reparar_marc_completo.py [entrada] [salida] [opciones]
```

## Valores por defecto

Si no se indican archivos, utiliza:

```text
entrada = catalogo.marc
salida  = catalogo-final.mrc
```

Por ejemplo:

```bash
python3 tools/reparar_marc_completo.py
```

intenta leer:

```text
catalogo.marc
```

y genera:

```text
catalogo-final.mrc
```

---

## Ejemplo básico

```bash
python3 tools/reparar_marc_completo.py \
  fichas_siabuc.iso \
  salida/fichas_siabuc-reparado.mrc
```

La carpeta de salida se crea automáticamente si no existe.

---

## Opciones

### `--no-caret`

Desactiva la conversión de marcadores textuales de subcampo como:

```text
^a
^b
^0
```

a delimitadores MARC reales:

```text
0x1F + código de subcampo
```

Uso:

```bash
python3 tools/reparar_marc_completo.py \
  catalogo.marc \
  catalogo-final.mrc \
  --no-caret
```

Esta opción es útil cuando el archivo ya contiene delimitadores MARC `0x1F` correctos y no quieres interpretar secuencias `^x` como subcampos.

---

### `--guardar-normalizado ARCHIVO`

Guarda una copia intermedia del flujo después de normalizar la codificación y los delimitadores, pero **antes** de reconstruir el Leader y el Directory.

Ejemplo:

```bash
python3 tools/reparar_marc_completo.py \
  fichas_siabuc.iso \
  salida/fichas_siabuc-reparado.mrc \
  --guardar-normalizado salida/fichas_siabuc-normalizado.mrc
```

Esto genera dos archivos:

```text
salida/fichas_siabuc-normalizado.mrc
salida/fichas_siabuc-reparado.mrc
```

El archivo normalizado es especialmente útil para diagnóstico y comparación de bytes.

---

# Qué repara la herramienta

El proceso se realiza en varias etapas.

## 1. Detección de codificación

La utilidad reconoce:

- UTF-16LE con BOM;
- UTF-16BE con BOM;
- UTF-16LE sin BOM mediante heurística;
- UTF-16BE sin BOM mediante heurística;
- UTF-8 con BOM;
- archivos que ya parecen binarios/UTF-8.

Cuando detecta UTF-16, convierte el contenido a UTF-8.

Si no detecta una transformación necesaria, conserva los bytes originales para evitar corromper un ISO2709 que ya sea válido.

---

## 2. Eliminación de BOM

Si encuentra un BOM UTF-8:

```text
EF BB BF
```

lo elimina antes de continuar.

El archivo MARC final queda en UTF-8 sin BOM.

---

## 3. Conversión de marcadores `^x`

Por defecto convierte secuencias como:

```text
^aTítulo
^bSubtítulo
^0Identificador
```

en delimitadores MARC21 reales:

```text
0x1F a Título
0x1F b Subtítulo
0x1F 0 Identificador
```

La transformación conserva dos bytes por marcador:

```text
^a    ->    0x1F a
```

Puede desactivarse mediante `--no-caret`.

---

## 4. Separación de registros

La utilidad localiza los registros mediante el terminador ISO2709:

```text
0x1D = terminador de registro
```

También tolera saltos de línea `CR` o `LF` introducidos accidentalmente entre registros.

---

## 5. Reconstrucción del Directory

Para cada registro:

1. Lee el Leader de 24 bytes.
2. Localiza físicamente el final del Directory mediante `0x1E`.
3. Recupera las etiquetas MARC del Directory original.
4. Localiza los campos utilizando sus terminadores físicos `0x1E`, sin confiar en las longitudes antiguas.
5. Recalcula la longitud real en bytes de cada campo.
6. Recalcula el offset de cada campo.
7. Construye nuevamente cada entrada de 12 bytes del Directory.

Cada entrada queda formada por:

```text
TAG + longitud de 4 dígitos + offset de 5 dígitos
```

Por ejemplo conceptualmente:

```text
245008800190
```

---

## 6. Reconstrucción del Leader

La herramienta recalcula y actualiza:

### Leader/00-04

Longitud total del registro en bytes.

### Leader/09

Se establece en:

```text
a
```

para indicar Unicode/UTF-8.

### Leader/12-16

Se recalcula el `Base Address of Data`.

### Leader/20-23

Se normaliza a:

```text
4500
```

que corresponde al mapa estándar utilizado por las entradas del Directory.

---

## 7. Terminadores ISO2709

El archivo reconstruido utiliza:

```text
0x1F = delimitador de subcampo
0x1E = terminador de campo
0x1D = terminador de registro
```

---

## 8. Validación del archivo reconstruido

Después de reparar el catálogo, la herramienta realiza una segunda pasada independiente de validación.

Comprueba, entre otras cosas:

- que la longitud del registro sea numérica;
- que la longitud declarada no exceda el archivo;
- que cada registro termine en `0x1D`;
- que el Base Address sea numérico y válido;
- que el Directory termine en `0x1E`;
- que la longitud del Directory sea múltiplo de 12;
- que `Leader/09` sea `a`;
- que el recorrido de los registros termine exactamente al final del archivo.

El archivo sólo se guarda como resultado final si la reconstrucción y la validación terminan correctamente.

---

# Ejemplo completo con `fichas_siabuc.iso`

```bash
mkdir -p salida

python3 tools/reparar_marc_completo.py \
  fichas_siabuc.iso \
  salida/fichas_siabuc-reparado.mrc \
  --guardar-normalizado salida/fichas_siabuc-normalizado.mrc
```

Una ejecución correcta muestra información similar a:

```text
Entrada : fichas_siabuc.iso
Salida  : salida/fichas_siabuc-reparado.mrc

Tamaño original: ... bytes
Codificación: ...
Marcadores ^x convertidos a 0x1F: ...
Separadores detectados antes de reconstruir: ...

Reconstruyendo Leader y Directory...

Validando ISO2709 reconstruido...

REPARACIÓN COMPLETADA
Registros reconstruidos : ...
Registros validados      : ...
Tamaño final             : ... bytes
Archivo final            : salida/fichas_siabuc-reparado.mrc
```

---

# Probar la herramienta con el ejemplo incluido

El repositorio incluye:

```text
examples/catalogo-utf16-caret.marc
```

Este archivo está preparado para probar el flujo de reparación.

Ejecuta:

```bash
python3 tools/reparar_marc_completo.py \
  examples/catalogo-utf16-caret.marc \
  salida/catalogo-reparado.mrc
```

Después puedes inspeccionar el resultado con la utilidad Java del proyecto:

```bash
./scripts/inspeccionar.sh salida/catalogo-reparado.mrc 5
```

---

# Códigos de salida

La herramienta utiliza diferentes códigos para indicar el punto donde ocurrió un error.

| Código | Significado |
|---:|---|
| `0` | Reparación completada correctamente. |
| `1` | El archivo de entrada no existe o no es un archivo válido. |
| `2` | Error durante la reconstrucción de Leader/Directory. |
| `3` | Error durante la validación del ISO2709 reconstruido. |
| `4` | El número de registros reconstruidos y validados no coincide. |

Esto permite utilizar la herramienta dentro de scripts automatizados.

Ejemplo:

```bash
python3 tools/reparar_marc_completo.py entrada.marc salida.mrc

if [ $? -eq 0 ]; then
    echo "Reparación correcta"
else
    echo "La reparación falló"
fi
```

---

# Diferencia frente a `scripts/reparar.sh`

El proyecto dispone de dos caminos para reparar MARC21.

## `tools/reparar_marc_completo.py`

```bash
python3 tools/reparar_marc_completo.py entrada.marc salida.mrc
```

Características:

- implementación independiente en Python;
- no necesita Java ni Maven;
- puede guardar el flujo intermedio mediante `--guardar-normalizado`;
- permite `--no-caret`;
- incluye reconstrucción y validación ISO2709 propias.

## `scripts/reparar.sh`

```bash
./scripts/reparar.sh entrada.marc salida.mrc
```

Características:

- utiliza la implementación Java `MarcRepairService`;
- ejecuta `RepairCli` mediante Maven;
- forma parte del flujo principal Java del proyecto;
- es la opción recomendada cuando estás trabajando y compilando normalmente el proyecto Java.

Ambas implementaciones siguen el mismo objetivo general: normalizar y reconstruir archivos MARC21 para obtener un ISO2709 utilizable.

---

# Flujo recomendado

Para un archivo MARC desconocido puedes seguir este orden:

### 1. Intentar inspeccionarlo

```bash
./scripts/inspeccionar.sh fichas_siabuc.iso 5
```

### 2. Si Marc4J no puede leerlo, reparar con Python

```bash
python3 tools/reparar_marc_completo.py \
  fichas_siabuc.iso \
  salida/fichas_siabuc-reparado.mrc
```

### 3. Inspeccionar el archivo reparado

```bash
./scripts/inspeccionar.sh salida/fichas_siabuc-reparado.mrc 5
```

### 4. Convertirlo a BIBFRAME

```bash
./scripts/convertir.sh \
  salida/fichas_siabuc-reparado.mrc \
  salida/fichas_siabuc.ttl \
  salida/reporte.csv
```

---

## Documentación relacionada

Consulta también:

- [`README.md`](../README.md) — documentación general del proyecto.
- [`REPAIR.md`](../REPAIR.md) — detalles del mecanismo de reparación.
- [`scripts/README.md`](../scripts/README.md) — documentación de los scripts Bash.
- [`examples/README.md`](../examples/README.md) — archivos de ejemplo incluidos en el proyecto.
