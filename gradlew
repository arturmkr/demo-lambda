#!/bin/sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
  echo "Missing gradle/wrapper/gradle-wrapper.jar" >&2
  echo "Run 'gradle wrapper' locally once or let CI generate the wrapper before executing ./gradlew." >&2
  exit 1
fi

JAVA_CMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
exec "$JAVA_CMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
