#!/bin/sh
# ---------------------------------------------------------------------------
# Apache Maven Wrapper
# ---------------------------------------------------------------------------

# Set the Maven wrapper properties
MAVEN_HOME=".mvn/wrapper"

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
        exit 1
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || { echo "ERROR: JAVA_HOME not set and no 'java' command found."; exit 1; }
fi

# Resolve symlinks
while [ -h "$0" ] ; do
    ls=`ls -ld "$0"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        SCRIPT="$link"
    else
        SCRIPT=`dirname "$0"`/"$link"
    fi
done
SCRIPT_DIR="`dirname "$SCRIPT"`"
APP_HOME="`cd "$SCRIPT_DIR" >/dev/null 2>&1 && pwd`"

WRAPPER_JAR="$APP_HOME/.mvn/wrapper/maven-wrapper.jar"

# Download wrapper jar if not present
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading maven-wrapper.jar..."
    MW_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
    if command -v curl > /dev/null 2>&1; then
        curl -sS -o "$WRAPPER_JAR" "$MW_URL"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "$WRAPPER_JAR" "$MW_URL"
    else
        echo "ERROR: Neither curl nor wget found. Please download manually:"
        echo "$MW_URL"
        exit 1
    fi
fi

exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -jar "$WRAPPER_JAR" \
  "$@"
