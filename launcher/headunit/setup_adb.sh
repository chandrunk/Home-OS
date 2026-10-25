#!/usr/bin/env bash
# Build the head unit launcher, install it over ADB, grant what it needs and make it Home.
#
#   HU_HOST=192.168.1.60 headunit/setup_adb.sh     # over Wi-Fi (adb connect <ip>:5555)
#   headunit/setup_adb.sh                          # over USB, the only device attached
#   headunit/setup_adb.sh --restore                # put the previous launcher back as Home
#
# Nothing is disabled or uninstalled: the unit's own launcher stays, it just stops being Home.
set -euo pipefail
cd "$(dirname "$0")/.."

PKG=com.homeprojects.carlauncher
HOME_ACTIVITY=$PKG/.MainActivity
LISTENER=$PKG/$PKG.CarNotificationListener
SAVED=headunit/.previous-home   # gitignored

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || { [ -d /opt/homebrew/opt/openjdk@17 ] && echo /opt/homebrew/opt/openjdk@17; } || true)}"
export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk ~/Android/Sdk /opt/homebrew/share/android-commandlinetools; do [ -d "$d" ] && echo "$d" && break; done)}"
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

if [ -n "${HU_HOST:-}" ]; then
  adb connect "$HU_HOST:5555" >/dev/null
  export ANDROID_SERIAL="$HU_HOST:5555"
fi
hu() { adb shell "$@" | tr -d '\r'; }

state=$(adb get-state 2>&1 || true)
if [ "$state" != device ]; then
  echo "ERROR: no head unit ($state). Turn on USB debugging, accept the prompt on the screen, or set HU_HOST." >&2
  exit 1
fi
sdk=$(hu getprop ro.build.version.sdk)
if [ "$sdk" -lt 26 ]; then
  echo "ERROR: Android API $sdk; this launcher needs Android 8 (API 26) or newer." >&2
  exit 1
fi

current_home() { hu cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1; }

if [ "${1:-}" = --restore ]; then
  prev=$(cat "$SAVED" 2>/dev/null || true)
  [ -n "$prev" ] || { echo "ERROR: no saved launcher in $SAVED. Pick Home in the unit's settings." >&2; exit 1; }
  hu cmd package set-home-activity "$prev"
  hu input keyevent KEYCODE_HOME
  echo "Home is $(current_home) again."
  exit 0
fi

echo "Head unit: $(hu getprop ro.product.manufacturer) $(hu getprop ro.product.model), Android $(hu getprop ro.build.version.release), $(hu wm size | tail -n 1)"

./gradlew :headunit:assembleRelease -q --console=plain
adb install -r headunit/build/outputs/apk/release/headunit-release.apk

# GPS speedometer and trip computer.
hu pm grant $PKG android.permission.ACCESS_FINE_LOCATION
hu pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
# Media sessions and the Google Maps turn-by-turn notification.
if ! hu cmd notification allow_listener "$LISTENER" 2>/dev/null; then
  list=$(hu settings get secure enabled_notification_listeners)
  case "$list" in
    *"$LISTENER"*) ;;
    null|"") hu settings put secure enabled_notification_listeners "$LISTENER" ;;
    *) hu settings put secure enabled_notification_listeners "$list:$LISTENER" ;;
  esac
fi

prev=$(current_home)
case "$prev" in
  $PKG/*|android|"") ;;
  */*) echo "$prev" > "$SAVED"; echo "Previous Home: $prev (headunit/setup_adb.sh --restore puts it back)" ;;
esac
hu am start -W -n "$HOME_ACTIVITY" | grep -E '^(Status|Error)' || true
hu cmd package set-home-activity "$HOME_ACTIVITY"
hu input keyevent KEYCODE_HOME

now=$(current_home)
case "$now" in
  $PKG/*) echo "Home now resolves to $now. Installed." ;;
  *) echo "WARNING: Home resolves to ${now:-nothing}. Press Home on the unit and choose Nothing Car OS, Always." >&2 ;;
esac
