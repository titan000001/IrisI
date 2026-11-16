# Reference runtime results — 2026-11-16

## Outcome

Bounded **direct-ADB observations**, not REA Evidence records. REA's prior device inspection failed because its server could not resolve ADB; that server was not restarted or reconfigured. No reference camera workflow reached a usable state, so no permission-denial, accessible-navigation, capture, recording, or hardware-parity requirement is CONFIRMED by this run.

| Reference | Supplied version | Installation | Runtime status |
|---|---|---|---|
| A: Mereframe `app.unnamed.rawrecording` | 0.8.8 / 137 (static identity) | BLOCKED: signature mismatch with pre-existing package | Not launched: existing launcher is `com.example.mereframeharness.HarnessActivity`, not the supplied reference. Existing package/data preserved. |
| B: Native Camera `com.rawcam.app` | 1.4.5 / 57 (installed metadata) | SUCCESS, all 33 supplied APKs | Launcher accepted; license-check Activity and automatic Google Play redirection observed. Initial screenshot/hierarchy show Play sign-in, not camera UI. Second launch remained in license handling. |
| C: MotionCam `com.motioncam` | 5.0.8-trial / 3308 (installed metadata) | SUCCESS, all 9 supplied APKs | Waited launch timed out at 90 seconds; activity/log evidence shows license-check/Play paywall routing. One non-waiting launch follow-up remained blocked. Screenshot black; hierarchy unavailable. |

No uninstall, package reset, permission grant/revoke, service enabling, purchase, login, external upload, installer spoofing, license bypass, or artifact modification was performed. No IrisI package command was issued. B/C were absent before installation; A was already present. Installed applications and their data remain intact.

## Environment and provenance

- Device: `emulator-5554`, AVD `Medium_Phone_API_37.0`, Android 17 / API 37, build `CE2A.260420.019` / 15611780.
- Fingerprint: `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:user/release-keys`.
- ABI list: `x86_64,arm64-v8a`; native bridge `libndk_translation.so`; 1080×2400, density 420. ARM64 installation acceptance does not establish native processing compatibility.
- ADB: 1.0.41, platform-tools `37.0.1-15733141`; absolute executable `C:/Users/tawhi/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
- Host helper: Python at `C:/Python315`; `runtime/reference_runtime.py` records direct commands, timestamps, stdout, stderr and exit statuses in `runtime/commands.jsonl`.
- Input identity came from `bundle-inventory.json` and `reverse_engineering/APP_{A,B,C}_SPEC.md`. Every extracted APK was checked against inventory SHA-256 before installation. Missing companion splits were extracted byte-for-byte from the supplied archives, without overwriting mismatching files.

## Exact commands and artifacts

`runtime/commands.jsonl` contains full expanded commands, including every split pathname. The helper's `install` action installed **every inventory-listed split** via `install-multiple -r`; B includes all language/density APKs, C all density APKs, and A attempted base/ARM64/MDPI together. No APK was re-signed or edited.

Executed entry commands:

```powershell
python docs/reverse-engineering/runtime/reference_runtime.py install
python docs/reverse-engineering/runtime/reference_runtime.py environment
python docs/reverse-engineering/runtime/reference_runtime.py launch b 1
python docs/reverse-engineering/runtime/reference_runtime.py capture b initial
python docs/reverse-engineering/runtime/reference_runtime.py logs b initial
python docs/reverse-engineering/runtime/reference_runtime.py launch c 1
python docs/reverse-engineering/runtime/reference_runtime.py capture c initial
python docs/reverse-engineering/runtime/reference_runtime.py logs c initial
python docs/reverse-engineering/runtime/reference_runtime.py capture c repeat
```

Follow-ups invoked the same helper's `run`/`capture` functions through `runpy`; exact underlying device commands are in the JSONL:

```text
ADB -s emulator-5554 shell am start -n com.motioncam/.pro.LauncherActivity
ADB -s emulator-5554 logcat -d -v threadtime -e com.motioncam -t 2500
ADB -s emulator-5554 shell am start -n com.rawcam.app/.ui.MainActivity
ADB -s emulator-5554 logcat -d -v threadtime -e com.rawcam.app -t 2500
```

Here `ADB` means the absolute executable above. Initial launches used `shell am start -W -n` after resolving MAIN/LAUNCHER within the named package. **The initial C launch timed out in Python before its completion could be appended to JSONL**; exact command was `ADB -s emulator-5554 shell am start -W -n com.motioncam/.pro.LauncherActivity`. It is independently visible in `c-repeat-focused-logcat.txt`. The helper was subsequently hardened to record future timeouts; no further waited launch was attempted.

Captures used `shell uiautomator dump`, `shell cat`, binary-safe `exec-out screencap -p`, `shell dumpsys activity activities`, and process-scoped logcat when a PID existed. Final B/C `dumpsys package` snapshots preserve installed state. Initial PID-missing fallback crash-buffer files contain unrelated historical system crashes and **are not evidence of reference crashes**. Use the package-filtered/PID-scoped logcat files for reference attribution.

## Observations and limitations

1. **A installation blocker — observed:** `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package app.unnamed.rawrecording signatures do not match newer version; ignoring!`. `a-package.txt` identifies the pre-existing harness launcher. `a-install.txt` is empty because ADB reported the failure on stderr; the complete error is preserved in JSONL. No attempt was made to remove the conflicting package.
2. **B routing — observed:** `b-initial-focused-logcat.txt` shows `com.pairip.licensecheck.LicenseActivity`. `b-initial-activities.txt`, `b-initial.xml` and `b-initial.png` show Google Play's unauthenticated page. `b-repeat-activities.txt` again shows LicenseActivity. `b-repeat-logcat.txt` reports `Local install check failed due to wrong installer` and connection/request to the licensing service. These are automatic application effects of launch, not an agent purchase/login or manually sent payload. No packet capture was taken: remote transmission destinations/payloads are unknown.
3. **C routing — observed:** `c-repeat-focused-logcat.txt` shows the initial launcher and LicenseActivity transitions. `c-initial-activities.txt` / `c-repeat-activities.txt` show a Play intent with `playPassPaywall:/com.motioncam`. This establishes a blocked route, not purchase eligibility or a hardware fault.
4. **UI capture failures — observed:** C initial/repeat and B repeat dumps returned `ERROR: null root node returned by UiTestAutomationBridge.` despite exit code 0. **`c-initial.xml` is stale B/Play XML**, read from the shared device path after C's dump failed; it must not be used as C hierarchy evidence. Later captures use unique paths: `c-repeat.xml` and `b-repeat.xml` are empty because no dump file was produced. JSONL preserves these failures. PNGs are actual screen captures; black frames do not establish camera output.
5. **Permission denial/navigation — UNAVAILABLE:** no usable reference permission prompt or app navigation control was reached. No synthetic denial is represented as a user denial, and no accessibility service was enabled. XML availability is not TalkBack verification.
6. **Repeat boundary:** B and C each received at most two launcher invocations. No successful basic camera flow existed to repeat. Further retries, account setup, bypasses and internal-activity forcing were deliberately excluded.
7. **Hardware/output parity — UNAVAILABLE:** no capture or recording was attempted; RAW, LOG, sensor controls, codecs, native library operation, GPU processing, media correctness and physical-device behavior remain unverified.

## Hashes and retention

- Full source archive/APK hashes: `bundle-inventory.json`.
- Exact extracted installation-input SHA-256 list: `runtime/installed-artifact-hashes.json` (45 APKs, including A's rejected install inputs; filename does not imply all 45 installed).
- Source archive SHA-256: A `1599297dac8f9ed0886da024c69726752c6188bba11d25bb65df16d0372d2850`; B `3f963de66d86b9d10ba45ff88cc7d8837779f96a1d2da9614165833a5f8e09ab`; C `c07dc70511d819fa171e516fbbe3aaddf2388363874f140d550f56c703d4d79b`.
- Runtime evidence-file SHA-256: `runtime/runtime-evidence-hashes.json`, generated after the bounded run (excludes itself and Python cache files).
- Shared REA ledger/session was not opened, closed, reset or otherwise altered. Existing static specs remain unchanged; this report supplements their prior static-only coverage.
