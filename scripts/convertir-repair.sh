#!/usr/bin/env bash
set -euo pipefail
INPUT="${1:-catalogo.marc}"
OUTPUT="${2:-salida/catalogo-bibframe.ttl}"
REPORT="${3:-salida/reporte.csv}"
mvn compile -Dexec.args="$INPUT $OUTPUT --repair --format turtle --report $REPORT --validate" exec:java@convertir
