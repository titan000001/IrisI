# Test results

Date: 2026-11-16. This report distinguishes software test results from reference execution and physical-camera validation. Final command output and device smoke evidence are appended below when available; pending runs are not PASS.

## Branding, theme and recovery continuation

The branded launcher, system Material You light/dark UI and read-only retained-LOG
review/share flow are implemented. Eight targeted wireless Pixel tests and one
live-camera disclosure test passed against the documented pre-remux-fix APK pair.
Light/dark camera UI screenshots were inspected privately; device mode restored.
The repository automation's separate **61 Python unittest tests** also passed.

The app/test APKs built, but lint found an actual extractor/codec flag-domain error
in LOG remuxing (1 error, 73 warnings); explicit mapping and regression coverage
have been added. **54 host tests subsequently passed**, zero failures/errors/skips.
The next batch-share regression exposed a build source-snapshot overlap; final
stable-tree app/test-APK/lint verification is pending. Details:
[`BRANDING-THEME-RECOVERY-20261116.md`](docs/validation/BRANDING-THEME-RECOVERY-20261116.md).

## Encoded LOG/HDR direction

**Latest continuation:** real 8-bit recorded LOG is now wired through the
CameraX GPU effect and AVC finalization. Two repeated five-second LOG recordings
passed stricter wireless Pixel instrumentation, including nonblank decoded
contrast, unspecified SPS transfer, matching grading sidecars, ~23.9936 fps and
audio/video track endpoints within 44 ms. Commit-safety changes passed **43 host
tests, zero failures/errors/skips**, including five failure/success regressions.
The older unconnected-curve statements below describe earlier checkpoints.
Exact APK hashes, results and limitations:
[`LOG-COMMIT-WIRELESS-20261116.md`](docs/validation/LOG-COMMIT-WIRELESS-20261116.md).
No new lint, perceptual AV-sync, colorimetry, 10-bit LOG or thermal pass is claimed.

- Two opt-in HLG recordings on physical Pixel `2B081FDH300H1F` finalized at
  FHD24. Container inspection passed HEVC, BT.2020, HLG, limited range, 1920×1080,
  and repeated stop/finalization. Annex-B SPS inspection passed 10-bit luma and
  chroma depth; audio was AAC 48 kHz.
- The sampled HLG files are **not visually validated**: Android
  `MediaMetadataRetriever` returned near-black display RGB (luma max 2–3) for
  sampled frames. This may be HDR display/decoder interpretation or capture
  exposure, and blocks a reliable-HLG claim until decoded with a suitable HDR
  path and scene/patch checks.
- `IrisLogTransferTest` (4), `HevcSequenceInfoTest` (3), host build, Android test
  APK assembly and lint passed. `IrisLogTransfer` remains an unconnected math
  contract, not a delivered recorded-LOG mode.
- Opt-in synthetic LOG ramp on the Pixel passed for hardware AVC and HEVC: 48
  frames, EOS, and five decoded transfer patches per codec. Effective encoder
  metadata was SDR/Rec.709 despite the custom curve request. Therefore the test
  proves GPU pixel transformation and encoder drain, but fails the honest custom
  LOG signaling gate by design.

## Executed history

1. Initial offline test attempt failed dependency resolution before execution. The project also lacked wrapper launchers/JAR; these were restored with Gradle 9.3.1.
2. Compilation exposed an unsupported nullable primitive-array `orEmpty()` call. It was replaced with `?: intArrayOf()` without changing the intended capability fallback.
3. Two intermediate build/test passes succeeded. The first reported 20 tests, zero failures/errors/skips. Subsequent changes require fresh verification.
4. Sharing-source run: **24 host tests, one failure, no errors/skips; APK assembly succeeded**. All seven recorder and four lifecycle tests passed. `MediaShareUrisTest.completedArchiveUsesRestrictedProvider` failed because Android FileProvider's Linux-path containment ran against Windows paths under Robolectric. The complete three-test sharing contract was moved unchanged in assertions to the Android instrumentation lane, without mocking the provider or weakening its containment rules.
5. Final validation command `:app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest :app:lintDebug --no-configuration-cache`: **BUILD SUCCESSFUL**. Host unit tests, assembly, lint and all 4 Android instrumentation tests passed after moving the FileProvider contract to the device lane. The first device run exposed a test-fixture directory setup issue; the fixture was corrected and rerun successfully.

## Added regression coverage

| Test component | Cases | Meaning / boundary |
|---|---:|---|
| `app/src/test/java/com/example/camera/RawCaptureLifecycleTest.kt` | 4 | Single ownership, exact timestamp match, missing-pair stop, writer drain, terminal callback guard |
| `app/src/test/java/com/example/camera/RawSequenceRecorderTest.kt` | 7 | Public recorder setup/permission/capability errors, stop/setup race, repeated stop, start-after-close rejection and worker release under Robolectric |
| `app/src/androidTest/java/com/example/MediaShareUrisTest.kt` | 3 | Real FileProvider URI, retained MediaStore URI, rejection of outside/traversal/incomplete private files on Android |
| Existing tests | retained | Existing arithmetic, resource, color/merge/telemetry/model tests and package-context instrumentation test are not deleted or bypassed |

These tests do not inject a physical RAW image or prove an encoded clip's color/cadence. Pure timestamp state tests do not alone establish every Camera2 callback order.

## Emulator smoke test

- Installed `app-debug.apk` on `emulator-5554` and launched `com.aistudio.cinerig.vxkpmq/com.example.MainActivity`: `am start -W` returned `Status: ok`, cold launch time 5.6 s.
- Permission flow was observed: camera denial, microphone denial, retry prompt, then camera grant with microphone denied again. The app returned to its own Compose viewfinder without a crash; MP4 capture below therefore exercised the optional-audio-denied path, not microphone recording.
- Captured a real emulator JPEG: MediaStore query showed `IRISI_20261116_131228.jpg`, 570,236 bytes, `DCIM/IrisI/`, and the library listed it with Develop/EXIF/Share/Delete actions.
- Captured viewfinder and library UI hierarchies. The shutter exposed `Start capture`; the primary controls were labeled for library, color workbench, settings, and live scope. RAW DNG sequence, physical RAW output, HDR/LOG metadata and sustained recording were not attempted on the emulated camera.
- Compressed-video start displayed `REC` and `Stop capture`; stop returned to `Start capture`. MediaStore reported `IRISI_20261116_131540.mp4`, 1,525,335 bytes, duration 24,737 ms, **720×1280** under `Movies/IrisI/`. The UI requested 4K UHD/24p/100 Mbps; those labels are not effective-output measurements. This observed fallback is a remaining discrepancy, not a 4K acceptance pass.
- Final totals: **21 host tests, zero failures/errors/skips; 4 Android instrumentation tests passed; lint zero errors and 77 warnings**. Gradle reused unchanged successful host outputs on the final rerun. Reports: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/`, `app/build/reports/lint-results-debug.html`.
- Final APK: `app/build/outputs/apk/debug/app-debug.apk`, 22,627,971 bytes; SHA-256 `e09480c59d2b80d90df0b5d01eb2c4cf07e92b4a274ea3e8f7368847fb6153aa`. Test summary retained in `docs/validation/runtime/artifact-summary.json`; direct-ADB commands/hierarchies/screenshots/logs in the same directory.

## Requirement outcomes

Statuses below apply to the **whole requirement**, not just a passing subtest. Acceptance IDs AT01–AT14 are defined in `ACCEPTANCE_TEST_PLAN.md`.

| Requirement | Status | Implementation / test and remaining gap |
|---|---|---|
| IR01 | PARTIAL | RAW bounds and truthful ZIP description implemented; physical acquisition/output unverified |
| IR02 | PARTIAL | `RawCaptureLifecycle.beginWrite` exact equality, recorder sensor association and frame metadata; model tests passed, real callback/DNG fidelity unverified |
| IR03 | PARTIAL | Setup/frame timeout, single capture/writer and finite sequence; host failure paths passed; storage/device boundaries unverified |
| IR04 | PARTIAL | RAW shutter remains enabled, shows stop shape/semantics, repeated stop retains recorder through packaging; UI/device capture flow unverified |
| IR05 | PARTIAL | Writer drains before reader close; terminal callback guard and thread/executor release covered by host tests; all late/vendor callbacks unverified |
| IR06 | PARTIAL | Viewmodel mode/lens/config/preset guards and delayed-rebind guard; RAW→VIDEO codec reconciliation; compressed busy state retained through finalization; full UI regression/reason feedback incomplete |
| IR07 | PARTIAL | Preview-only LOG / requested cadence disclosure, disabled 120 fps, specific HLG query and explicit binding failure; effective encoded output not measured |
| IR08 | PARTIAL | DNG/manifest/ZIP temporary-file publication, partial files excluded, successful originals retained; process-death/power-loss/storage/index errors not fully tested |
| IR09 | PARTIAL | Recorder permission/capability errors tested; full grant/denial/resume and storage/audio flows require device validation |
| IR10 | UNVERIFIED | No service or durable recovery added; complete lifecycle interruption/reopen policy not verified |
| IR11 | PARTIAL | Labeled 70 dp RAW-capable shutter and text status; TalkBack/large-font/rotation/error access not verified |
| IR12 | PARTIAL | Existing deletion ownership retained; local capture manifest and narrowly scoped sharing provider; complete privacy/deletion tests not run |
| IR13 | PARTIAL | Emulator JPEG save/library and MP4 start/stop/file creation passed; physical camera fidelity/decoded output, full format matrix and requested-resolution compliance not verified |
| IR14 | UNVERIFIED | Existing manual/monitor controls retained; sensor effect and units need device tests |
| IR15 | PARTIAL | Existing library/development/presets/HUD preserved; private archive URI sharing fixed; full persistence/development workflow unverified |
| IR16 | UNVERIFIED | Existing optional audio path retained; route/gain/output synchronization unverified |
| IR17–IR23 | BLOCKED / UNVERIFIED | Retained feature families need additional static/native/runtime evidence, independent implementation and physical hardware; not delivered as full parity |

No reference camera behavior is CONFIRMED by IrisI host tests. Static inventories and runtime installation/licensing observations are separate in `docs/reverse-engineering/REFERENCE_RUNTIME_RESULTS.md`.

## Later physical-device evidence and restart continuation

The emulator totals and APK hash above identify the earlier delivery, not the
subsequent geometry/banner changes. Retained Pixel 7 Pro inspection found AVC
1920×1080 at approximately 23.99865 fps and a mono 48 kHz AAC track in
`IRISI_20261116_164127.mp4`, despite the recorded HUD requesting 4K UHD. The
on-device inspection passed once; decoded image quality, audible content and
synchronization were not tested. A read-only query also found the earlier JPEG
and DNG outputs at 4080×3072; DNG fidelity remains unverified.

The interrupted work adds strict ordinary-video geometry validation and separate
low-resolution analysis. Restart inspection added a visible accessible binding
error banner because `cameraError` was not previously rendered. Verification of
these newer changes is tracked in `docs/validation/RESUME-20261116.md`; do not
apply the earlier passing totals to them.

That continuation now passed in an isolated build directory: **24 host tests,
zero failures/errors/skips; lint zero errors/77 warnings; six device contract/UI
tests plus one named-clip inspection passed**. The newly recorded Pixel clip
`IRISI_20261116_170207.mp4` contains 3840×2160 AVC, 490 samples at approximately
24.01164 fps, and mono 48 kHz AAC. Live analysis overlays were explicitly reported
unavailable for this configuration. This resolves the observed resolution downgrade
for this tested configuration, not every device/setting. Picture/audio fidelity,
sync, physical RAW sequence and the remaining acceptance matrix remain qualified.

Validated continuation APK: `app/build/outputs/apk/resume/app-debug.apk`, SHA-256
`768c642a630fbb83b06d5121d48b64806c8fba00e64d768ba88ec2a3e13219b6`.
The old debug APK/hash and earlier test totals above remain historical evidence.
