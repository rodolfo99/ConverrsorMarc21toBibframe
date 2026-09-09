#!/usr/bin/env bash
set -euo pipefail
INPUT="${1:-catalogo.marc}"
OUTPUT="${2:-catalogo-final.mrc}"
mvn compile -Dexec.args="$INPUT $OUTPUT" exec:java@reparar
