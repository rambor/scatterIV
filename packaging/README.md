# Building Scatter installers

`jpackage` (part of JDK 17+) turns Scatter into a normal installer that includes its own Java
runtime, so users do not need Java installed. Each installer is built on its own platform:
the macOS one on a Mac, the Windows one on a Windows PC.

## 1. Build the jar in IntelliJ (once per release)

1. File → Project Structure → Artifacts → **+** → JAR → *From modules with dependencies*
   - Main class: `Scatter`
   - choose *copy to the output directory and link via manifest*
2. Check the jar's **Output Layout** includes IntelliJ's form runtime (`forms_rt`); Scatter's
   windows need it. The scripts stop with a message if it is missing.
3. Build → Build Artifacts → Build. The jars go to `out/artifacts/scatterIV_jar/`.

## 2. Make the installer

| | Command (from the project folder) | Result in `build/installer/` |
|---|---|---|
| macOS | `packaging/package.sh 4.0.0` | `Scatter-4.0.0.dmg` |
| Windows | `packaging\package-windows.bat 4.0.0` | `Scatter-4.0.0.msi` |
| Linux | `TYPE=deb packaging/package.sh 4.0.0` | `.deb` (or `app-image` folder by default) |

The version must be numbers (`4.0.0`, `4.1`). Windows also needs the free
[WiX Toolset](https://wixtoolset.org) on the PATH.

What the scripts do:
- copy the jars, leaving out `-sources`, `-javadoc`, `-tests` and test libraries (JUnit, Hamcrest)
- check FlatLaf and the form runtime are included
- bundle only the Java modules Scatter uses (found with `jdeps`): about 70 MB instead of 170 MB.
  Set `FULL_RUNTIME=1` to bundle the whole runtime if something is ever missing.
- pass `--enable-native-access=ALL-UNNAMED` (for FlatLaf) and let Java use up to 60% of the
  computer's memory
- Windows: Start-menu entry, desktop shortcut, choice of folder; installs for the current user
  without admin rights (`set ALL_USERS=1` for Program Files). A fixed upgrade ID means a new
  version replaces the old one.

## 3. Signing

Unsigned installers work but show a warning the first time:
- **macOS**: "cannot verify the developer". Users open it via System Settings → Privacy &
  Security → *Open Anyway*. To sign, get an Apple Developer ID and run
  `MAC_SIGN="Developer ID Application: Your Name (TEAMID)" packaging/package.sh 4.0.0`,
  then notarize the `.dmg` with `xcrun notarytool submit ... --wait` and `xcrun stapler staple`.
- **Windows**: SmartScreen "Windows protected your PC" → *More info* → *Run anyway*.
  A code-signing certificate removes this.

## Notes

- **Apple Silicon / Intel**: the macOS app runs on the kind of Mac it was built on. Build on an
  M-series Mac for M-series Macs; for Intel Macs, build with an Intel (x64) JDK.
- **Settings** are kept in the user's folder (`~/Library/Application Support/Scatter`,
  `%APPDATA%\Scatter`), so the installed app can save them.
- **Icon**: `Scatter.icns` / `Scatter.ico` / `Scatter.png` in this folder. Replace them to change
  the icon (the `.icns` and `.ico` were made from the 1024×1024 `.png`).
