#!/bin/sh
#
# An example hook script to verify what is about to be committed.
# This is the Gradle wrapper script.

##############################################################################
# Gradle Wrapper Download
##############################################################################

# Gradle wrapper properties
GRADLE_VERSION=8.2
GRADLE_WRAPPER_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

# Auto-generate proper gradlew if not exists
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    mkdir -p gradle/wrapper
fi
