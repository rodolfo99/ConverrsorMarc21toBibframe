# Resumen de mapeo MARC21 → BIBFRAME

| Grupo MARC21 | Destino principal |
|---|---|
| Leader / 00X | `bf:AdminMetadata`, tipos de `bf:Work`, identificadores locales, idioma y lugar codificados |
| 01X–04X | `bf:identifiedBy`, `bf:language`, datos codificados y administrativos |
| 05X–08X | `bf:classification` y clases `bf:Classification*` |
| 1XX / 7XX | `bf:Contribution` → `bf:Agent`, `bf:Person`, `bf:Organization`, `bf:Meeting` |
| 2XX | `bf:Title`, `bf:VariantTitle`, `bf:editionStatement`, `bf:ProvisionActivity` |
| 3XX | extensión, dimensiones, contenido, medio, portador, características técnicas y musicales |
| 4XX / 8XX | `bf:Hub` y `bf:partOf` para series |
| 5XX | `bf:Note` y propiedades especializadas como `bf:summary`, `bf:intendedAudience`, `bf:systemRequirement` |
| 6XX | `bf:subject` → personas, organizaciones, reuniones, obras, temas, lugares y géneros |
| 76X–78X | relaciones `bf:partOf`, `bf:hasPart`, `bf:precededBy`, `bf:succeededBy`, `bf:translationOf`, etc. |
| 84X–87X | `bf:Item`, ubicación, signatura, código de barras, estado y existencias |
| 880 | nota de escritura alternativa con enlace al campo asociado |
| otros | preservación íntegra como `marc:Field` y `marc:Subfield` |

## Estrategia de preservación

Cuando una etiqueta no tiene una regla dedicada, el conversor genera algo semejante a:

```turtle
catalog:marc-field/...
    a marc:Field ;
    marc:tag "999" ;
    marc:indicator1 " " ;
    marc:indicator2 " " ;
    marc:subfield [
        a marc:Subfield ;
        marc:code "a" ;
        rdf:value "Campo local preservado"
    ] .
```

Así, ampliar el mapeo posteriormente no exige volver a obtener el MARC original para recuperar el contenido no interpretado.
