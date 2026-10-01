#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$DIR"
APP_NAME="Gradle"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

JAVACMD="java"
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
