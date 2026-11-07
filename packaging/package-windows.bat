@echo off
rem ============================================================================
rem Build a Scatter installer for Windows (.msi) with jpackage.
rem
rem Needs, on the Windows PC:
rem   - JDK 17 or newer (jpackage and jdeps come with it), on the PATH
rem   - WiX Toolset (free, https://wixtoolset.org), which jpackage uses to make .msi files
rem
rem Before running: build the jar in IntelliJ
rem   File > Project Structure > Artifacts > + > JAR > From modules with dependencies
rem     Main class: Scatter, "copy to the output directory and link via manifest"
rem   Build > Build Artifacts > Build      (output: out\artifacts\scatterIV_jar)
rem
rem Usage, from the project folder:
rem   packaging\package-windows.bat [version]        e.g. packaging\package-windows.bat 4.0.1
rem
rem Options, set before running:
rem   set JAR_DIR=path       folder with the built jars   (default out\artifacts\scatterIV_jar)
rem   set TYPE=exe           make an .exe installer instead of .msi
rem   set FULL_RUNTIME=1     bundle the whole Java runtime instead of only the modules Scatter uses
rem   set ALL_USERS=1        install into Program Files for all users (needs admin rights);
rem                          by default Scatter installs for the current user only, no admin needed
rem
rem The installer is written to build\installer\.
rem ============================================================================

setlocal EnableDelayedExpansion

set "VERSION=%~1"
if "%VERSION%"=="" set "VERSION=4.0.0"
set "APP_NAME=Scatter"
set "MAIN_CLASS=Scatter"
set "VENDOR=Robert Rambo"
set "DESCRIPTION=SAXS and SEC-SAXS data analysis"
rem Keep this UUID the same for every release, so a new version replaces the old one.
set "UPGRADE_UUID=7272e024-d2dd-46fd-94eb-96134c037322"

set "HERE=%~dp0"
for %%I in ("%HERE%..") do set "PROJECT=%%~fI"
if "%JAR_DIR%"=="" set "JAR_DIR=%PROJECT%\out\artifacts\scatterIV_jar"
if "%TYPE%"=="" set "TYPE=msi"
set "STAGING=%PROJECT%\build\package\input"
set "DEST=%PROJECT%\build\installer"

rem --- checks -------------------------------------------------------------------
where jpackage >nul 2>nul || (echo ERROR: jpackage not found. Install JDK 17 or newer and put its bin folder on the PATH. & exit /b 1)

echo %VERSION%| findstr /r "^[0-9][0-9]*$ ^[0-9][0-9]*\.[0-9][0-9]*$ ^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul || (
  echo ERROR: version must be numbers like 4.0.0 ^(got "%VERSION%"^)
  exit /b 1
)

where candle >nul 2>nul
if errorlevel 1 (
  where wix >nul 2>nul
  if errorlevel 1 (
    echo ERROR: WiX Toolset not found. Install it from https://wixtoolset.org and make sure it is on the PATH.
    exit /b 1
  )
)

if not exist "%JAR_DIR%\" (
  echo ERROR: no jar folder at %JAR_DIR%
  echo Build the artifact in IntelliJ first ^(see the top of this file^) or set JAR_DIR.
  exit /b 1
)

rem find the jar whose manifest names Scatter as the main class
set "MAIN_JAR="
for %%J in ("%JAR_DIR%\*.jar") do (
  if not defined MAIN_JAR (
    tar -xOf "%%~fJ" META-INF/MANIFEST.MF 2>nul | findstr /r /c:"^Main-Class: *%MAIN_CLASS%" >nul && set "MAIN_JAR=%%~nxJ"
  )
)
if not defined MAIN_JAR (
  echo ERROR: no jar in %JAR_DIR% has "Main-Class: %MAIN_CLASS%" in its manifest
  exit /b 1
)

rem --- stage the jars (leave out sources, javadoc, tests and test libraries) -----
if exist "%STAGING%" rmdir /s /q "%STAGING%"
mkdir "%STAGING%"
if not exist "%DEST%" mkdir "%DEST%"
robocopy "%JAR_DIR%" "%STAGING%" *.jar /NFL /NDL /NJH /NJS /NP /XF *-sources.jar *-javadoc.jar *-tests.jar *-test-sources.jar junit*.jar hamcrest*.jar jsr305*.jar >nul
if errorlevel 8 (echo ERROR: could not copy the jars & exit /b 1)

set "HAS_FORMS="
set "HAS_FLATLAF="
for %%J in ("%STAGING%\*.jar") do (
  tar -tf "%%~fJ" 2>nul | findstr /c:"com/intellij/uiDesigner/core/GridLayoutManager.class" >nul && set "HAS_FORMS=1"
  tar -tf "%%~fJ" 2>nul | findstr /c:"com/formdev/flatlaf/FlatLaf.class" >nul && set "HAS_FLATLAF=1"
)
if not defined HAS_FORMS if not "%SKIP_FORMS_CHECK%"=="1" (
  echo ERROR: IntelliJ's form runtime ^(forms_rt^) is not in the jars. Scatter's windows need it.
  echo In IntelliJ: Project Structure ^> Artifacts ^> your jar ^> Output Layout, include 'forms_rt',
  echo then rebuild the artifact.
  exit /b 1
)
if not defined HAS_FLATLAF echo WARNING: FlatLaf is not in the jars; the app will use the plain Java look.

rem --- runtime: only the Java modules Scatter needs ------------------------------
set "MODULE_ARGS="
if not "%FULL_RUNTIME%"=="1" (
  echo Finding the Java modules Scatter uses ^(jdeps^) ...
  pushd "%STAGING%"
  set "MODULES="
  for /f "delims=" %%M in ('jdeps --print-module-deps --ignore-missing-deps --multi-release base *.jar 2^>nul') do set "MODULES=%%M"
  popd
  echo !MODULES! | findstr /c:"java.base" >nul && (
    set "MODULE_ARGS=--add-modules !MODULES!,jdk.unsupported,jdk.zipfs,jdk.charsets"
    echo   modules: !MODULES!,jdk.unsupported,jdk.zipfs,jdk.charsets
  ) || echo   jdeps did not work; bundling the full Java runtime instead
)

rem --- jpackage ------------------------------------------------------------------
set "ICON_ARG="
if exist "%HERE%Scatter.ico" set "ICON_ARG=--icon "%HERE%Scatter.ico""

set "SCOPE_ARG=--win-per-user-install"
if "%ALL_USERS%"=="1" set "SCOPE_ARG="

echo Running jpackage (%TYPE%, version %VERSION%) ...
jpackage --type %TYPE% ^
  --name "%APP_NAME%" ^
  --app-version %VERSION% ^
  --vendor "%VENDOR%" ^
  --description "%DESCRIPTION%" ^
  --input "%STAGING%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class %MAIN_CLASS% ^
  --dest "%DEST%" ^
  --java-options "--enable-native-access=ALL-UNNAMED" ^
  --java-options "-XX:MaxRAMPercentage=60" ^
  --jlink-options "--strip-debug --no-man-pages --no-header-files --compress=zip-6" ^
  %MODULE_ARGS% ^
  %ICON_ARG% ^
  --win-menu --win-menu-group "%APP_NAME%" --win-shortcut --win-dir-chooser ^
  --win-upgrade-uuid %UPGRADE_UUID% ^
  %SCOPE_ARG%
if errorlevel 1 (echo ERROR: jpackage failed & exit /b 1)

echo.
echo Done. Output in %DEST%:
dir /b "%DEST%"
echo.
echo Note: the installer is not code-signed, so Windows SmartScreen may warn on first run
echo ("More info" ^> "Run anyway").
endlocal
