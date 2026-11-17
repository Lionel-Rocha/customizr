#!/bin/sh
# Ponte do Customizr (Linux/Mac). Precisa de Java 25 ou mais novo.
# Uso: ./rodar-ponte.sh [porta]   (padrao 55070)
cd "$(dirname "$0")"
exec java -Xmx128m -jar customizr-ponte.jar "${1:-55070}"
