# Build and install IrisI

## Windows prerequisites

- Android Studio JBR and SDK with API 36.1 build support.
- Network access for the initial Gradle/dependency download. The application itself does not need Internet for local capture.
- The restored wrapper pins Gradle 9.3.1. Preserve the existing signing configuration.
- A fresh clone needs a local `debug.keystore`; see the development-key command in
  [README.md](README.md). Do not replace an existing key or commit it.

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

The host's default Java was JRE 11; use the Android Studio JBR rather than that runtime. `--no-configuration-cache` avoids unrelated configuration-cache serialization problems during dependency resolution. Test and build outcomes are recorded in `TEST_RESULTS.md`, not implied by these instructions.

## Install your build

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices -l
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am start -W -n com.aistudio.cinerig.vxkpmq/com.example.MainActivity
```

Replace the device serial for physical hardware. Do not uninstall an existing package to resolve a signature conflict without first preserving its data and obtaining authorization. Allow camera permission for capture; audio is optional for ordinary video. No reference APK or proprietary library is needed by IrisI.

For the repository's existing on-device test:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache
```

An emulator can establish startup/navigation and some simulated camera behavior. It cannot establish physical RAW/HDR/color fidelity, sustained FPS, thermal behavior or proprietary format parity. A DNG sequence is a finite ZIP archive, not a playable movie. Named LOG monitoring does not imply encoded LOG output. See `KNOWN_LIMITATIONS.md` and `docs/reverse-engineering/REMAINING-DIFFERENCES.md` before relying on capture capabilities.

## Dated build snapshots

The earlier restart-continuation APK used `app/build/outputs/apk/resume/app-debug.apk`
and isolated temporary outputs to avoid a Windows file lock. See
`docs/validation/RESUME-20261116.md` for that snapshot's command and checksum.

The subsequent recorded-LOG snapshot used the normal `debug/` APK path. See
`docs/validation/LOG-COMMIT-WIRELESS-20261116.md` for its exact app/test APK hashes
and wireless Pixel results. New builds overwrite that path, so compare checksums
rather than assuming the current APK matches any older validation report.

## GitHub checks

`.github/workflows/android.yml` builds the debug app and test APK, runs host tests
and lint, and retains reports. It creates an ephemeral CI-only signing key. It
does not run physical-device tests, sign releases or publish APKs. A local pass
does not imply the workflow has executed on GitHub.
