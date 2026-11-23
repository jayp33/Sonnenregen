#!/usr/bin/env bash
# Build the current debug APK and attach it to a GitHub release.
#
# Usage: scripts/upload-debug-apk.sh [tag]
#   without a tag, uses the current local timestamp: YYYYMMDD-HHMMSS
#   e.g. scripts/upload-debug-apk.sh v0.1.0-debug
#
# The tag is the GitHub release name. Reusing an existing tag replaces the
# uploaded APK; the generated timestamp tag normally creates a new release.
set -euo pipefail

tag="${1:-$(date +%Y%m%d-%H%M%S)}"
echo "Note: this publishes a debug-signed, debuggable APK for testing only." >&2
echo "Release tag: $tag"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Gradle needs a JDK (javac), not just a JRE. Prefer an explicitly valid
# JAVA_HOME, then Android Studio's bundled JBR, then javac on PATH.
if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME}/bin/javac" ]]; then
    if [[ -x /opt/android-studio/jbr/bin/javac ]]; then
        export JAVA_HOME=/opt/android-studio/jbr
    elif command -v javac >/dev/null 2>&1; then
        export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
    else
        echo "No JDK with javac found. Set JAVA_HOME to a JDK and retry." >&2
        exit 1
    fi
fi
echo "Using JAVA_HOME=$JAVA_HOME"

if ! command -v gh >/dev/null 2>&1; then
    echo "GitHub CLI (gh) is required: https://cli.github.com" >&2
    exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
    echo "Not logged in to GitHub. Run 'gh auth login' first." >&2
    exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
    echo "Warning: working tree is dirty; the APK may not match the release commit." >&2
fi
if git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' >/dev/null 2>&1; then
    ahead="$(git rev-list --count '@{upstream}..HEAD')"
    if (( ahead > 0 )); then
        echo "Warning: $ahead local commit(s) are not on GitHub; the release targets the remote default branch." >&2
    fi
fi

./gradlew :app:assembleDebug

apk="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$apk" ]]; then
    echo "APK not found after build: $apk" >&2
    exit 1
fi

if ! gh release view "$tag" >/dev/null 2>&1; then
    # Let GitHub create the tag on its default branch: a local-only HEAD SHA
    # would be rejected as an invalid target_commitish.
    gh release create "$tag" \
        --title "$tag" \
        --notes "Debug build of Sonnenregen." \
        --prerelease
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
asset="$tmp/sonnenregen-${tag}.apk"
cp "$apk" "$asset"

gh release upload "$tag" "$asset" --clobber
echo "Uploaded $asset to release $tag"
gh release view "$tag" --json url --jq .url
