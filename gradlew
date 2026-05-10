#!/bin/sh

APP_HOME=$(dirname "$0")
APP_BASE_NAME=$(basename "$0")

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    echo "Gradle wrapper JAR not found at $GRADLE_WRAPPER_JAR" >&2
    exit 1
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$GRADLE_WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
