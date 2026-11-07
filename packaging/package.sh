#!/usr/bin/env bash
#
# Build a Scatter installer with jpackage (macOS .dmg, or a Linux .deb/.rpm/app folder).
# For Windows use packaging\package-windows.bat on a Windows PC.
#
# Before running: build the jar in IntelliJ
#   File > Project Structure > Artifacts > + > JAR > From modules with dependencies
#     Main class: Scatter, "copy to the output directory and link via manifest"
#   Build > Build Artifacts > Build      (output: out/artifacts/scatterIV_jar)
#
# Usage (from the project folder):
#   packaging/package.sh [version]               e.g. packaging/package.sh 4.0.1
#
# Options, as environment variables:
#   JAR_DIR=path        folder with the built jars          (default out/artifacts/scatterIV_jar)
#   TYPE=dmg|pkg|app-image|deb|rpm                          (default dmg on macOS, app-image on Linux)
#   MAC_SIGN="Developer ID Application: Your Name (TEAMID)" sign the app (needs an Apple Developer ID)
#   FULL_RUNTIME=1      bundle the whole Java runtime instead of only the modules Scatter uses
#
# The installer is written to build/installer/.

set -euo pipefail

VERSION="${1:-4.0.0}"
APP_NAME="Scatter"
MAIN_CLASS="Scatter"
VENDOR="Robert Rambo"
DESCRIPTION="SAXS and SEC-SAXS data analysis"
MAC_ID="com.github.rambor.scatter"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT="$(cd "$HERE/.." && pwd)"
JAR_DIR="${JAR_DIR:-$PROJECT/out/artifacts/scatterIV_jar}"
STAGING="$PROJECT/build/package/input"
DEST="$PROJECT/build/installer"

case "$(uname -s)" in
  Darwin) OS=mac;   TYPE="${TYPE:-dmg}";       ICON="$HERE/Scatter.icns" ;;
  Linux)  OS=linux; TYPE="${TYPE:-app-image}"; ICON="$HERE/Scatter.png" ;;
  *) echo "Use packaging\\package-windows.bat on Windows." >&2; exit 1 ;;
esac

fail() { echo "ERROR: $*" >&2; exit 1; }

# --- checks --------------------------------------------------------------------
command -v jpackage >/dev/null || fail "jpackage not found. It comes with JDK 17 or newer; make sure that JDK's bin folder is on your PATH."

if ! [[ "$VERSION" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]]; then
  fail "version must be numbers like 4.0.0 (got '$VERSION')"
fi
if [[ "$OS" == mac && "${VERSION%%.*}" == "0" ]]; then
  fail "on macOS the version must not start with 0"
fi

[[ -d "$JAR_DIR" ]] || fail "no jar folder at $JAR_DIR. Build the artifact in IntelliJ first (see the top of this script) or set JAR_DIR."

# find the jar whose manifest names Scatter as the main class
MAIN_JAR=""
for j in "$JAR_DIR"/*.jar; do
  manifest="$(unzip -p "$j" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' || true)"
  if grep -q "^Main-Class: *$MAIN_CLASS$" <<<"$manifest"; then
    MAIN_JAR="$(basename "$j")"; break
  fi
done
[[ -n "$MAIN_JAR" ]] || fail "no jar in $JAR_DIR has 'Main-Class: $MAIN_CLASS' in its manifest"

# --- stage the jars (leave out sources, javadoc, tests and test libraries) ------
rm -rf "$STAGING" && mkdir -p "$STAGING" "$DEST"
skipped=0
for j in "$JAR_DIR"/*.jar; do
  name="$(basename "$j")"
  case "$name" in
    *-sources.jar|*-javadoc.jar|*-tests.jar|*-test-sources.jar|junit*.jar|hamcrest*.jar|jsr305*.jar)
      skipped=$((skipped+1)) ;;
    *) cp "$j" "$STAGING/" ;;
  esac
done
echo "Staged $(ls "$STAGING" | wc -l | tr -d ' ') jars from $JAR_DIR (left out $skipped source/javadoc/test jars)"

has_class() { # has_class path/in/jar.class
  # ask unzip for that one entry (exit 0 if present). Not "unzip -l | grep -q": with pipefail, grep
  # stopping early makes the pipeline fail on large jars even when the class is there.
  for j in "$STAGING"/*.jar; do
    unzip -l "$j" "$1" >/dev/null 2>&1 && return 0
  done
  return 1
}

if ! has_class "com/intellij/uiDesigner/core/GridLayoutManager.class" && [[ "${SKIP_FORMS_CHECK:-0}" != 1 ]]; then
  fail "IntelliJ's form runtime (forms_rt) is not in the jars. Scatter's windows need it.
  In IntelliJ: Project Structure > Artifacts > your jar > Output Layout, make sure 'forms_rt' (or
  'IntelliJ GUI Designer runtime') is included, then rebuild the artifact."
fi
has_class "com/formdev/flatlaf/FlatLaf.class" || echo "WARNING: FlatLaf is not in the jars; the app will use the plain Java look."

# --- runtime: only the Java modules Scatter needs (much smaller download) -------
EXTRA_MODULES="jdk.unsupported,jdk.zipfs,jdk.charsets"
MODULE_ARGS=()
if [[ "${FULL_RUNTIME:-0}" != 1 ]] && command -v jdeps >/dev/null; then
  echo "Finding the Java modules Scatter uses (jdeps) ..."
  if MODULES="$(cd "$STAGING" && jdeps --print-module-deps --ignore-missing-deps --multi-release "$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)" *.jar 2>/dev/null | tail -1)" \
     && [[ "$MODULES" == *java.base* ]]; then
    MODULE_ARGS=(--add-modules "$MODULES,$EXTRA_MODULES")
    echo "  modules: $MODULES,$EXTRA_MODULES"
  else
    echo "  jdeps did not work; bundling the full Java runtime instead"
  fi
fi

# --- jpackage ------------------------------------------------------------------
args=(
  --type "$TYPE"
  --name "$APP_NAME"
  --app-version "$VERSION"
  --vendor "$VENDOR"
  --description "$DESCRIPTION"
  --input "$STAGING"
  --main-jar "$MAIN_JAR"
  --main-class "$MAIN_CLASS"
  --dest "$DEST"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --java-options "-XX:MaxRAMPercentage=60"
)
[[ -f "$ICON" ]] && args+=(--icon "$ICON")
args+=("${MODULE_ARGS[@]+"${MODULE_ARGS[@]}"}")
args+=(--jlink-options "--strip-debug --no-man-pages --no-header-files --compress=zip-6")

if [[ "$OS" == mac ]]; then
  args+=(--mac-package-identifier "$MAC_ID" --mac-package-name "$APP_NAME"
         --java-options "-Dapple.awt.application.name=$APP_NAME")
  if [[ -n "${MAC_SIGN:-}" ]]; then
    args+=(--mac-sign --mac-signing-key-user-name "$MAC_SIGN")
  fi
fi
if [[ "$OS" == linux && ( "$TYPE" == deb || "$TYPE" == rpm ) ]]; then
  args+=(--linux-shortcut --linux-menu-group "Science")
fi

rm -rf "$DEST/$APP_NAME" "$DEST/$APP_NAME.app"
echo "Running jpackage ($TYPE, version $VERSION) ..."
jpackage "${args[@]}"

echo
echo "Done. Output in $DEST:"
ls -1 "$DEST"
if [[ "$OS" == mac && -z "${MAC_SIGN:-}" ]]; then
  echo
  echo "Note: the app is not signed. On first launch macOS will block it; users open it with"
  echo "System Settings > Privacy & Security > Open Anyway. Set MAC_SIGN to sign it."
fi
