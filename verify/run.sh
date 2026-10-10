#!/usr/bin/env bash
# Day Hub verification (feature 30). One command that proves the app still matches the web app and still stays offline.
#
#   verify/run.sh               everything below
#   verify/run.sh --guard-only  just the offline guard and its own test (needs only a JDK)
#
# 1. The offline guard: no network code, WebView, network permission or networking library (the same class the
#    Gradle build runs before every build, see app/build.gradle), and its own test.
# 2. The Android resources and manifest are compiled with aapt2, which also gives the real R.java.
# 3. The whole app is compiled against the Android SDK and converted to dex.
# 4. The Java engine, the stopwatch dial, the focus rules, the widgets and the reminders are compared with the web
#    app's own code: for each, a generator runs the WEB APP's real code on generated data, and a Java check replays the
#    same data through the app's classes and compares everything the person could notice. Any difference fails.
#
# Needs: a JDK (javac, java), and for the full run Node.js, the Android SDK (ANDROID_HOME, or sdk.dir in
# local.properties), an org.json for the JVM (JSON_JAR, found in ~/.m2 or ~/.gradle if not set) and the web app
# (WEB_APP, or a folder called everything-app next to this project or in ~/Downloads).
# Everything is written under build/verify. Each check's exit code is tested; nothing is hidden behind a pipe.

set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_UNIX="$(cd "$HERE/.." && pwd)"

# Windows (Git Bash) wants C:/... paths for Java and Node; elsewhere paths are left alone.
p() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; *) SEP=":";; esac

ROOT="$(p "$ROOT_UNIX")"
OUT="$ROOT/build/verify"
LOGS="$OUT/logs"
GUARD_ONLY=0
[ "${1:-}" = "--guard-only" ] && GUARD_ONLY=1

fail=0
passed=0
say() { printf '%s\n' "$*"; }
bad() { say "FAILED  $*"; fail=$((fail + 1)); }
good() { say "ok      $*"; passed=$((passed + 1)); }

need() { command -v "$1" >/dev/null 2>&1 || { say "Missing: $1 is not on the PATH. $2"; exit 2; }; }
need javac "Install a JDK (17 or newer)."
need java "Install a JDK (17 or newer)."

rm -rf "$OUT"
mkdir -p "$OUT/guard" "$OUT/classes" "$OUT/web" "$OUT/tests" "$OUT/fixtures" "$OUT/gen" "$LOGS"

# run <label> <logname> <command...>: runs a command, keeps its output in a log, reports its exit code.
run() {
  local label="$1" log="$LOGS/$2.txt"
  shift 2
  "$@" > "$log" 2>&1
  local rc=$?
  if [ $rc -eq 0 ]; then good "$label: $(tail -n 1 "$log" | cut -c1-160)"; else bad "$label (exit $rc): see $log"; tail -n 8 "$log" | sed 's/^/        /'; fi
  return $rc
}

# ---------- 1. the offline guard ----------
GUARD_SRC="$ROOT/buildSrc/src/main/java/app/dayhub/guard/NoNetworkGuard.java"
run "offline guard compiles" guard-compile javac --release 17 -encoding UTF-8 -d "$OUT/guard" "$GUARD_SRC" "$ROOT/verify/java/NoNetworkGuardTest.java"
if [ -f "$OUT/guard/NoNetworkGuardTest.class" ]; then
  run "offline guard test" guard-test java -cp "$OUT/guard" NoNetworkGuardTest "$ROOT"
  run "Day Hub is offline" guard java -cp "$OUT/guard" app.dayhub.guard.NoNetworkGuard "$ROOT"
fi
if [ $GUARD_ONLY -eq 1 ]; then
  say ""; say "passed $passed, failed $fail"; [ $fail -eq 0 ] && exit 0 || exit 1
fi

need node "Install Node.js 18 or newer (the web app's own code runs in it)."

# ---------- finding the pieces ----------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$ROOT_UNIX/local.properties" ]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT_UNIX/local.properties" | head -n 1 | sed 's/\\:/:/g; s/\\\\/\//g' | tr -d '\r')"
fi
[ -d "$SDK/platforms" ] || { say "Missing: the Android SDK. Set ANDROID_HOME, or put sdk.dir in local.properties."; exit 2; }
ANDROID_JAR=""
COMPILE_SDK="$(sed -n 's/^[[:space:]]*compileSdk[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$ROOT_UNIX/app/build.gradle" | head -n 1 | tr -d '\r')"
if [ -n "$COMPILE_SDK" ] && [ -f "$SDK/platforms/android-$COMPILE_SDK/android.jar" ]; then
  ANDROID_JAR="$SDK/platforms/android-$COMPILE_SDK/android.jar" # the platform the app is built against
else
  for d in $(ls -d "$SDK"/platforms/android-* 2>/dev/null | sort -V); do [ -f "$d/android.jar" ] && ANDROID_JAR="$d/android.jar"; done
fi
[ -n "$ANDROID_JAR" ] || { say "Missing: no platform with an android.jar in $SDK/platforms."; exit 2; }
BT=""
for d in $(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V); do
  ls "$d"/aapt2* >/dev/null 2>&1 || continue
  BT="$d"
  [ -n "$COMPILE_SDK" ] && [ "$(basename "$d")" = "$COMPILE_SDK.0.0" ] && break # the build-tools that match the platform
done
[ -n "$BT" ] || { say "Missing: Android build-tools (aapt2) in $SDK/build-tools."; exit 2; }
AAPT2="$(ls "$BT"/aapt2.exe "$BT"/aapt2 2>/dev/null | head -n 1)"
D8="$(ls "$BT"/d8.bat "$BT"/d8 2>/dev/null | head -n 1)"

JSON="${JSON_JAR:-}"
if [ -z "$JSON" ]; then
  JSON="$(find "$HOME/.m2" "$HOME/.gradle" -name 'android-json*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | head -n 1)"
  [ -n "$JSON" ] || JSON="$(find "$HOME/.m2" "$HOME/.gradle" -name 'json-2*.jar' -path '*org/json*' ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | head -n 1)"
fi
[ -n "$JSON" ] && [ -f "$JSON" ] || { say "Missing: an org.json jar for the JVM. Set JSON_JAR (for example com.vaadin.external.google:android-json)."; exit 2; }
JSON="$(p "$JSON")"

WEB="${WEB_APP:-}"
if [ -z "$WEB" ]; then
  for c in "$ROOT_UNIX/../everything-app" "$HOME/Downloads/everything-app"; do [ -f "$c/public/js/core/backend.js" ] && WEB="$c" && break; done
fi
[ -n "$WEB" ] && [ -f "$WEB/public/js/core/backend.js" ] || { say "Missing: the web app. Set WEB_APP to its folder (the one with public/ and android/)."; exit 2; }
WEB="$(p "$WEB")"
export WEB_APP="$WEB"
say "web app:   $WEB"
say "SDK:       $(p "$SDK") ($(basename "$(dirname "$ANDROID_JAR")"), $(basename "$BT"))"
say "org.json:  $JSON"
say ""

ANDROID_JAR="$(p "$ANDROID_JAR")"
AAPT2="$(p "$AAPT2")"

# ---------- 2. resources and manifest ----------
mkdir -p "$OUT/res-compiled" "$OUT/gen-r"
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.dayhub">|' \
  "$ROOT/app/src/main/AndroidManifest.xml" > "$OUT/Manifest-with-package.xml"
if run "resources compile (aapt2)" aapt2-compile "$AAPT2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/res-compiled/res.zip"; then
  run "resources and manifest link (aapt2)" aapt2-link "$AAPT2" link -I "$ANDROID_JAR" --manifest "$OUT/Manifest-with-package.xml" \
    --min-sdk-version 26 --target-sdk-version 35 --java "$OUT/gen-r" -o "$OUT/res.apk" "$OUT/res-compiled/res.zip"
fi
[ -f "$OUT/gen-r/app/dayhub/R.java" ] || { say "No R.java, so the app cannot be compiled."; exit 1; }

# ---------- 3. the app ----------
find "$ROOT/app/src/main/java" -name '*.java' > "$OUT/sources.txt"
echo "$OUT/gen-r/app/dayhub/R.java" >> "$OUT/sources.txt"
sed -i 's/.*/"&"/' "$OUT/sources.txt"
run "the app compiles" app-compile javac --release 17 -encoding UTF-8 -Xlint:-options -cp "$ANDROID_JAR$SEP$JSON" -d "$OUT/classes" @"$OUT/sources.txt"
[ -d "$OUT/classes/app/dayhub" ] || { say "The app did not compile."; exit 1; }

# the web app's own Android classes: the oracle for the focus rules, the widget rows and the widget actions
WEBCORE="$WEB/android/app/src/main/java/app/dayhub/core"
run "the web app's Android logic compiles" web-compile javac --release 17 -encoding UTF-8 -d "$OUT/web" \
  "$WEBCORE/FocusSession.java" "$WEBCORE/FocusPolicy.java" "$WEBCORE/MiniJson.java" "$WEBCORE/SnapshotData.java" \
  "$WEBCORE/WidgetRows.java" "$WEBCORE/ActionFactory.java"

CP="$OUT/classes$SEP$OUT/web$SEP$OUT/tests$SEP$OUT/guard$SEP$JSON$SEP$ANDROID_JAR"
TESTS=()
for f in "$ROOT"/verify/java/*Parity.java "$ROOT"/verify/java/*Test.java "$ROOT"/verify/java/TapPlan.java; do TESTS+=("$f"); done
run "the checks compile" tests-compile javac --release 17 -encoding UTF-8 -Xlint:-options -cp "$CP" -d "$OUT/tests" "${TESTS[@]}"

# the app must also become Android bytecode
if [ -n "$D8" ]; then
  javac --release 17 -d "$OUT/gen" "$ROOT/verify/java/MkJar.java" > "$LOGS/mkjar-compile.txt" 2>&1
  mkdir -p "$OUT/dex"
  if java -cp "$OUT/gen" MkJar "$OUT/classes" "$OUT/app-classes.jar" > "$LOGS/mkjar.txt" 2>&1; then
    run "the app converts to dex (d8)" d8 "$(p "$D8")" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/app-classes.jar"
  else
    bad "could not pack the compiled app for d8: see $LOGS/mkjar.txt"
  fi
else
  say "skip    dex conversion (no d8 in the build-tools)"
fi

# the Gradle scripts must at least parse (Gradle itself cannot run everywhere)
GRADLE_VERSION="$(sed -n 's|.*gradle-\([0-9.]*\)-bin.*|\1|p' "$ROOT_UNIX/gradle/wrapper/gradle-wrapper.properties" | head -n 1)"
GLIB="$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-"$GRADLE_VERSION"-bin/*/gradle-"$GRADLE_VERSION"/lib 2>/dev/null | head -n 1)"
if [ -n "$GLIB" ]; then
  GCP="$(ls "$GLIB"/groovy-3*.jar "$GLIB"/asm*.jar "$GLIB"/antlr4-runtime*.jar 2>/dev/null | while read -r j; do p "$j"; done | tr '\n' "$SEP")"
  if javac -cp "$GCP" -d "$OUT/gen" "$ROOT/verify/java/ParseGroovy.java" > "$LOGS/groovy-compile.txt" 2>&1; then
    run "Gradle scripts parse" gradle-syntax java -cp "$OUT/gen$SEP$GCP" ParseGroovy "$ROOT/build.gradle" "$ROOT/app/build.gradle" "$ROOT/settings.gradle"
  fi
else
  say "skip    Gradle script syntax (no Gradle $GRADLE_VERSION distribution in ~/.gradle)"
fi

# ---------- 4. the comparisons with the web app ----------
F="$OUT/fixtures"
G="$ROOT/verify/gen"

# generate <label> <script> <args...>: the web app's own code produces the expected answers
generate() {
  local label="$1" script="$2"
  shift 2
  run "web app: $label" "gen-$(basename "$script" .mjs)" node "$G/$script" "$@"
}
check() { # <class> <args...>
  local cls="$1"
  shift
  run "$cls" "$cls" java -cp "$CP" "$cls" "$@"
}

generate "timeline data sets" gentimeline.mjs "$F/timeline-cases.json"
generate "stats and badges" genstats.mjs "$F/stats-cases.json"
generate "expense detection" gendetect.mjs "$F/detect-cases.json"
generate "insights" geninsights.mjs "$F/insights-cases.json"
generate "dashboard" gendash.mjs "$F/dash-cases.json"
generate "setup" gensetup.mjs "$F/setup-cases.json"
generate "tasks" gentasks.mjs "$F/tasks-cases.json"
generate "habits" genhabits.mjs "$F/habits-cases.json"
generate "journal" genjournal.mjs "$F/journal-cases.json"
generate "spending" genspend.mjs "$F/spend-cases.json"
generate "music" genmusic.mjs "$F/music-cases.json"
generate "settings" gensettings.mjs "$F/settings-cases.json"
generate "stopwatch dial" genwatch.mjs "$F/watch-cases.json"
generate "focus clock" genclock.mjs "$F/clock-cases.json"
generate "hold to end" genhold.mjs "$F/hold-web.json"
generate "exports" genexport.mjs "$F/timeline-cases.json" "$F/export-cases.json"
generate "blank data" genblank.mjs "$F/timeline-cases.json" "$F/blank-cases.json"
generate "home" genhome.mjs "$F/timeline-cases.json" "$F/home-cases.json"
generate "widgets" genwidgets.mjs "$F/timeline-cases.json" "$F/widget-cases.json"
generate "reminders" genreminders.mjs "$F/timeline-cases.json" "$F/reminder-cases.json"

check StatsParity "$F/stats-cases.json"
check DetectParity "$F/detect-cases.json"
check InsightsParity "$F/insights-cases.json"
check DashParity "$F/dash-cases.json"
check SetupParity "$F/setup-cases.json"
check TasksParity "$F/tasks-cases.json"
check HabitsParity "$F/habits-cases.json"
check JournalParity "$F/journal-cases.json"
check SpendParity "$F/spend-cases.json"
check MusicParity "$F/music-cases.json"
check SettingsParity "$F/settings-cases.json"
check WatchParity "$F/watch-cases.json"
check FocusParity "$F/clock-cases.json"
check PolicyParity
check HoldTest "$F/hold-web.json"
check ExportParity "$F/export-cases.json"
check BlankParity "$F/blank-cases.json"
check HomeParity "$F/home-cases.json"
check WidgetParity "$F/widget-cases.json"
check ReminderParity "$F/reminder-cases.json"
check TimelineParity "$F/timeline-cases.json" "$F/timeline-java.json"
run "timeline compare" timeline-compare node "$G/cmptimeline.mjs" "$F/timeline-cases.json" "$F/timeline-java.json"

# widget taps: Java picks taps, the web app's own applyWidgetAction applies them, Java replays them and compares
check TapPlan "$F/widget-cases.json" "$F/tap-plan.json"
run "web app: applies the widget taps" gen-applytaps node "$G/applytaps.mjs" "$F/tap-plan.json" "$F/tap-expected.json"
check TapParity "$F/tap-plan.json" "$F/tap-expected.json"

# the engine and its save, restore and backup behaviour (self-contained)
check EngineTest
check RestoreCommitTest
check WizardFlowTest
check VerifyTest
check AutoBackupTest

say ""
if [ $fail -eq 0 ]; then
  say "ALL GOOD: $passed checks passed, none failed."
  exit 0
fi
say "$fail FAILED, $passed passed. Logs are in $LOGS"
exit 1
