#!/bin/sh
# Compila e gera dist/MiniServidorChat.jar (roda em Java 17 ou mais novo).
#   ./build.sh
#   java -jar dist/MiniServidorChat.jar servidor
#   java -jar dist/MiniServidorChat.jar cliente
set -e
cd "$(dirname "$0")"
rm -rf out/build
mkdir -p out/build dist
javac --release 17 -encoding UTF-8 -d out/build $(find src -name '*.java')
jar --create --file dist/MiniServidorChat.jar --main-class minichat.Iniciar -C out/build .
echo "Gerado: dist/MiniServidorChat.jar"
