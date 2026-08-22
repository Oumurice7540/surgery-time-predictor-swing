#!/bin/sh
MVNW_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -f "$MVNW_DIR/.mvn/wrapper/maven-wrapper.jar" ]; then
  echo "Maven Wrapper JAR is missing: $MVNW_DIR/.mvn/wrapper/maven-wrapper.jar" >&2
  exit 1
fi
cd "$MVNW_DIR" || exit 1
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" \
  -classpath "$MVNW_DIR/.mvn/wrapper/maven-wrapper.jar" \
  "-Dmaven.multiModuleProjectDirectory=$MVNW_DIR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
