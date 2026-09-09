#!/usr/bin/env bash
set -euo pipefail
mkdir -p salida
mvn clean compile -Dexec.args="examples/catalogo-utf16-caret.marc salida/prueba-repair.ttl --repair --format turtle --validate" exec:java@convertir
