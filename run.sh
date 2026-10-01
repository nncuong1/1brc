#!/bin/bash
# Usage: ./run.sh <JavaClassName>
# Example: ./run.sh SlowForLoop

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <JavaClassName>"
    echo "Example: $0 SlowForLoop"
    exit 1
fi

CLASS_NAME="$1"
SRC_FILE="src/main/java/dev/morling/onebrc/${CLASS_NAME}.java"

if [ ! -f "$SRC_FILE" ]; then
    echo "Error: File not found: $SRC_FILE"
    exit 1
fi

echo "Compiling $SRC_FILE..."
javac "$SRC_FILE"

echo "Running dev.morling.onebrc.${CLASS_NAME}..."
java -cp src/main/java "dev.morling.onebrc.${CLASS_NAME}"