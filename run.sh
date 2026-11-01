#!/bin/sh
# SPDX-FileCopyrightText: 2026 The Elkslop contributors
# SPDX-License-Identifier: BSD-3-Clause
#
# Build (if needed) and run the Elkslop desktop emulator.
# Options are passed straight through, for example:
#   ./run.sh --scale 2
#   ./run.sh --type "PRINT 6*7\n"
#   ./run.sh --help
# Needs a JDK 17+: $JAVA_HOME, else .tools/jdk, else `java` on the PATH.
set -eu

cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ] && [ -x .tools/jdk/bin/java ]; then
    JAVA_HOME="$PWD/.tools/jdk"
fi
if [ -n "${JAVA_HOME:-}" ]; then
    export JAVA_HOME
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi
# macOS has a /usr/bin/java stub even without a JDK, so check it actually runs.
if ! "$JAVA" -version >/dev/null 2>&1; then
    echo "run.sh: no working Java found. Install a JDK 17+ or set JAVA_HOME." >&2
    exit 1
fi

./gradlew -q :desktop:fatJar

exec "$JAVA" -Dapple.awt.application.name=Elkslop -jar desktop/build/libs/elkslop.jar "$@"
