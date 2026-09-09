#!/usr/bin/env bash
set -euo pipefail
mvn compile -Dexec.args="${1:-catalogo.mrc} ${2:-5}" exec:java@inspeccionar
