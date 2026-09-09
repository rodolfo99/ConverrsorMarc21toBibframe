#!/usr/bin/env bash
set -euo pipefail
mvn compile -Dexec.args="${1:-catalogo.mrc} ${2:-1000} ${3:-12345}" exec:java@generar
