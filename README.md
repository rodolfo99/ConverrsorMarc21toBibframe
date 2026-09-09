# Conversor MARC21 → BIBFRAME en Java

Proyecto Maven completamente en Java para:

1. generar registros MARC21 ISO2709 de prueba;
2. inspeccionarlos con Marc4J;
3. convertirlos a BIBFRAME RDF con Apache Jena;
4. exportar Turtle, JSON-LD, RDF/XML o N-Triples;
5. producir un reporte CSV de cobertura por etiqueta;
6. reparar automáticamente archivos MARC exportados como UTF-16, con BOM, delimitadores `^a/^b` y Directory/Leader desajustados mediante `--repair`.

> **Importante:** es una implementación independiente para aprendizaje, prototipos e integración. No es el conversor oficial de la Library of Congress ni reproduce al 100 % todas sus reglas contextuales. Los campos sin mapeo semántico específico se preservan en RDF mediante el vocabulario local `https://bibliotecas.ucol.mx/vocab/marc/`, por lo que no se descartan silenciosamente.

## Requisitos

- Java 21
- Maven 3.9 o posterior

Comprueba:

```bash
java -version
mvn -version
```

## Abrir en IntelliJ IDEA

1. Descomprime el ZIP.
2. En IntelliJ selecciona **Open**.
3. Abre la carpeta que contiene `pom.xml`.
4. Espera a que Maven descargue Marc4J y Apache Jena.
5. Verifica que el SDK del proyecto sea Java 21.

## Compilar y probar

```bash
mvn clean test
```

## 1. Generar 1000 registros MARC21 ISO2709

```bash
mvn compile -Dexec.args="catalogo.mrc 1000 12345" exec:java@generar
```

Argumentos:

- `catalogo.mrc`: archivo de salida;
- `1000`: cantidad de registros;
- `12345`: semilla reproducible.

## 2. Inspeccionar registros MARC21

```bash
mvn compile -Dexec.args="catalogo.mrc 5" exec:java@inspeccionar
```

## 3. Convertir a BIBFRAME Turtle

```bash
mvn compile -Dexec.args="catalogo.mrc salida/catalogo-bibframe.ttl --format turtle --report salida/reporte.csv --validate" exec:java@convertir
```

La opción `--validate` relee el RDF generado para comprobar su sintaxis.


## Reparar automáticamente un MARC problemático (`--repair`)

Si el archivo original fue exportado en UTF-16LE/UTF-16BE, contiene BOM, usa marcadores textuales como `^a`/`^b` en lugar de `0x1F`, o sus longitudes ISO2709 quedaron desajustadas al recodificarlo, usa `--repair`:

```bash
mvn clean compile \
  -Dexec.args="catalogo.marc salida/catalogo-bibframe.ttl --repair --format turtle --report salida/reporte.csv --validate" \
  exec:java@convertir
```

`--repair` hace automáticamente, antes de Marc4J:

1. detección de UTF-16LE/UTF-16BE (incluido BOM);
2. conversión a UTF-8 sin BOM;
3. conversión de `^a`, `^b`, `^0`, etc. a delimitador MARC `0x1F`;
4. reconstrucción de las longitudes y offsets del Directory en bytes UTF-8;
5. recálculo de Leader/00-04 (longitud del registro) y Leader/12-16 (Base Address);
6. establecimiento de Leader/09 = `a` y Entry Map `4500`;
7. validación estructural ISO2709;
8. conversión normal a BIBFRAME.

El MARC reparado se crea como archivo temporal y se elimina al terminar. El original no se modifica.

También puedes ejecutar solamente la reparación y conservar el MARC resultante:

```bash
mvn compile -Dexec.args="catalogo.marc catalogo-final.mrc" exec:java@reparar
```

O con los scripts incluidos:

```bash
./scripts/convertir-repair.sh catalogo.marc salida/catalogo-bibframe.ttl salida/reporte.csv
./scripts/reparar.sh catalogo.marc catalogo-final.mrc
```

Para un MARC ISO2709 ya correcto **no necesitas** `--repair`.

## Otros formatos

### JSON-LD

```bash
mvn compile -Dexec.args="catalogo.mrc salida/catalogo.jsonld --format jsonld --report salida/reporte.csv --validate" exec:java@convertir
```

### RDF/XML

```bash
mvn compile -Dexec.args="catalogo.mrc salida/catalogo.rdf --format rdfxml --validate" exec:java@convertir
```

### N-Triples

```bash
mvn compile -Dexec.args="catalogo.mrc salida/catalogo.nt --format ntriples --validate" exec:java@convertir
```

## Cambiar la URI base

```bash
mvn compile -Dexec.args="catalogo.mrc salida/catalogo.ttl --base https://mi-biblioteca.mx/bibframe/ --report salida/reporte.csv" exec:java@convertir
```

## Convertir solo los primeros registros

```bash
mvn compile -Dexec.args="catalogo.mrc salida/prueba.ttl --limit 10 --validate" exec:java@convertir
```

## Crear un JAR con dependencias

```bash
mvn clean package
```

Se genera:

```text
target/marc21-bibframe-java-1.1.0-all.jar
```

Ejecuta el conversor:

```bash
java -jar target/marc21-bibframe-java-1.1.0-all.jar catalogo.mrc salida/catalogo.ttl --report salida/reporte.csv --validate
```

## Cobertura implementada

La conversión crea recursos `bf:Work`, `bf:Instance` y, cuando hay información local, `bf:Item`.

- Leader, `001`, `003`, `005`, `006`, `007`, `008`.
- Administración: `040`, `042`, `883`, `884`, `885`.
- Identificadores: `010`, `013`, `015`–`018`, `020`, `022`–`028`, `030`, `032`, `035`–`037`, `074`, `088`.
- Idiomas y códigos: `041`, `043`–`048`, `377`.
- Clasificaciones: `050`–`052`, `055`, `060`–`061`, `070`–`072`, `080`, `082`–`086`.
- Agentes y contribuciones: `100`, `110`, `111`, `700`, `710`, `711` con `$e`, `$4`, `$0` y `$1`.
- Títulos: `130`, `210`, `222`, `240`, `242`, `243`, `245`, `246`, `247`, `730`, `740`.
- Edición y publicación: `250`, `251`, `254`–`258`, `260`, `263`, `264`, `270`.
- Descripción física y RDA: `300`, `306`, `307`, `310`, `321`, `336`–`348`, `351`, `352`, `355`, `357`, `362`, `363`, `365`, `366`, `370`, `380`–`384`.
- Series: `440`, `490`, `800`, `810`, `811`, `830`.
- Notas: campos `5XX`, con tratamiento específico para tesis, acceso, resumen, audiencia, sistema, derechos, procedencia, encuadernación, premios, etc.
- Materias: `600`, `610`, `611`, `630`, `647`, `648`, `650`, `651`, `653`–`658`, `662`, `688`.
- Relaciones: `760`–`788` principales.
- Existencias e ítems: `841`–`878` principales, incluidos `850`, `852`, `856`, `863`–`868`, `876`–`878`.
- Escrituras alternativas: `880`.
- Campos no reconocidos: se conservan como nodos `marc:Field` y `marc:Subfield`.

Consulta [MAPPING.md](MAPPING.md) para más detalle.

## Estructura del proyecto

```text
src/main/java/mx/ucol/marc2bf/
├── bibframe/
│   ├── ConversionContext.java
│   ├── MappingReport.java
│   ├── MarcToBibframeConverter.java
│   └── Vocab.java
├── cli/
│   ├── ConverterCli.java
│   ├── GeneratorCli.java
│   ├── InspectCli.java
│   └── RepairCli.java
├── generator/
│   └── SyntheticMarcGenerator.java
└── util/
    ├── MarcRepairService.java
    ├── MarcUtil.java
    └── UriMinter.java
```

## Limitaciones conocidas

- Los campos fijos `006`, `007` y posiciones específicas de `008` se preservan y se interpreta su núcleo común; una implementación bibliográfica de producción debe aplicar tablas distintas según Leader/06 y Leader/07.
- Algunas propiedades se expresan como notas o recursos locales cuando BIBFRAME no tiene una equivalencia directa o la regla depende del contexto catalográfico.
- La desambiguación de autoridades requiere servicios externos o registros de autoridad; este proyecto reutiliza URI en `$1`/`$0` cuando son URI HTTP y genera URI deterministas en los demás casos.
- Para millones de registros conviene una salida RDF por streaming o TDB2. La versión incluida acumula el catálogo en memoria y es adecuada para pruebas y lotes moderados.

## Referencias normativas

- MARC 21 Format for Bibliographic Data, Library of Congress.
- MARC 21 to BIBFRAME Conversion Specifications 3.1, Library of Congress.
- BIBFRAME 2.0 vocabulary.
