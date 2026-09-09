#!/usr/bin/env bash
set -euo pipefail
INPUT="${1:-catalogo.mrc}"
OUTPUT="${2:-salida/catalogo-bibframe.ttl}"
REPORT="${3:-salida/reporte.csv}"
shift $(( $# >= 3 ? 3 : $# )) || true
EXTRA=("$@")
mvn compile -Dexec.args="$INPUT $OUTPUT --format turtle --report $REPORT --validate ${EXTRA[*]}" exec:java@convertir
