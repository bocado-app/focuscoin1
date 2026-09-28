#!/usr/bin/env sh
set -eu

VERSION="8.9"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_PARENT="$GRADLE_USER_HOME/wrapper/dists/focuscoin-gradle-$VERSION"
GRADLE_HOME="$DIST_PARENT/gradle-$VERSION"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    mkdir -p "$DIST_PARENT"
    ARCHIVE="$DIST_PARENT/gradle-$VERSION-bin.zip"
    URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fL "$URL" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ARCHIVE" "$URL"
    else
        echo "curl 또는 wget이 필요합니다." >&2
        exit 1
    fi
    unzip -q -o "$ARCHIVE" -d "$DIST_PARENT"
    rm -f "$ARCHIVE"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
