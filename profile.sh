#!/bin/bash
# Usage: ./profile.sh <JavaClassName> [event]
# Example: ./profile.sh EasySolution
#          ./profile.sh EasySolution alloc
#
# Runs the class through jbang with async-profiler (via ap-loader) attached
# and writes a flamegraph to <timestamp>-profile-<JavaClassName>-<event>.html.

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <JavaClassName> [event]"
    echo "Example: $0 EasySolution"
    echo "Events: cpu (default), alloc, lock, wall"
    exit 1
fi

CLASS_NAME="$1"
EVENT="${2:-cpu}"
SRC_FILE="src/main/java/dev/morling/onebrc/${CLASS_NAME}.java"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="profile-${CLASS_NAME}-${EVENT}-${TIMESTAMP}.html"

if [ ! -f "$SRC_FILE" ]; then
    echo "Error: File not found: $SRC_FILE"
    exit 1
fi

echo "Profiling $SRC_FILE (event=$EVENT)..."
jbang --javaagent=ap-loader@jvm-profiling-tools/ap-loader=start,event=${EVENT},file=${OUT_FILE} "$SRC_FILE"

echo "Flamegraph written to $OUT_FILE"