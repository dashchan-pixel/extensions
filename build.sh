#!/usr/bin/env bash
# Build the Dashchan extensions (dvach, local).
#
# Usage:
#   ./build.sh            build release APKs for all extensions
#   ./build.sh debug      build debug APKs
#   ./build.sh clean      remove build outputs
set -euo pipefail
cd "$(dirname "$0")"
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
	for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" \
			/opt/homebrew/share/android-commandlinetools /usr/local/share/android-commandlinetools; do
		if [[ -d "$candidate/platforms" ]]; then
			export ANDROID_HOME="$candidate"
			break
		fi
	done
fi
case "${1:-release}" in
	release) ./gradlew assembleRelease ;;
	debug) ./gradlew assembleDebug ;;
	clean) ./gradlew clean ;;
	*) echo "usage: $0 [release|debug|clean]" >&2; exit 1 ;;
esac
if [[ "${1:-release}" != clean ]]; then
	echo
	ls chans/*/build/outputs/apk/*/*.apk 2>/dev/null
fi
