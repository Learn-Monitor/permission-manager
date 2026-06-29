#!/usr/bin/env bash

./gradlew deployPluginJar classes

DEPS=$(./gradlew -q printRuntimeClasspath)

PLUGIN_JAR=$(find build/libs -name "*.jar" | head -n 1)

CP="$PLUGIN_JAR:$DEPS"

cd build/debug-run || exit 1

java -cp "$CP" de.igslandstuhl.database.Application