# IrisI project audit

Audit date: 2026-11-16. Source inspection and build-tool observations only unless explicitly identified as a test result. Baseline revision: `54031a76667e0d9a51248de1843b5d77355866dc`, branch `main`, with existing uncommitted application work. Application work may change concurrently; this is not an immutable source snapshot.

## Identity and structure

- Single Android `:app` module, Kotlin, Jetpack Compose Material 3. Application ID `com.aistudio.cinerig.vxkpmq`; namespace `com.example`; version `1.0` / 1.
- Minimum API 24, target API 36, compile API 36.1 (`app/build.gradle.kts`). Java source/target compatibility is 11; the build itself uses the Android Studio JBR.
- Entry point: `app/src/main/java/com/example/MainActivity.kt`. A `CineRigViewModel` exposes lifecycle-collected state to `ViewfinderScreen`; sheets provide camera configuration, media/development, hardware information, audio, presets and HUD customization.
- Camera implementation: `camera/CameraController.kt`, CameraX 1.5.0 and Camera2 interop. Analysis/color/audio/level helpers are separate classes. `RawSequenceRecorder.kt` and `RawDevelopmentEngine.kt` were pre-existing untracked work, not additions by this audit.
- Persistence: Room 2.7.0, DataStore Preferences 1.1.7, coroutines 1.10.2. Room entities are `camera_presets`, `ui_layouts`, `captured_media_logs`; DataStore `cinerig_prefs` stores `active_layout_id`.

## Storage, permissions and integration

`CineRigRepository.kt` discovers MediaStore outputs under `DCIM/IrisI/` and `Movies/IrisI/`, filtering ownership and publication state. Deletion checks package/folder ownership; private RAW deletion is constrained to canonical paths beneath `filesDir/raw_sequences`. These are inspected safeguards, not a completed deletion security test.

The manifest declares CAMERA, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS and WRITE_EXTERNAL_STORAGE through API 28. Camera/autofocus/microphone hardware is optional. The source manifest has no INTERNET permission, no recording foreground service, and disables backup. The final merged manifest was not independently audited here. Build dependency downloads are unrelated to app runtime permissions.

## Existing functionality and evidence limits

Source and the saved checkpoint describe photo/video modes, manual exposure/focus/WB, sequential RAW capture, image development, scopes, peaking/zebra/false color, horizon, audio monitoring, presets, media logs and editable HUD. Their presence in source does not establish hardware support or successful end-to-end operation.

The historical [recording baseline](docs/reverse-engineering/IRISI-BASELINE.md) identifies RAW lifecycle/stop/timestamp and configuration hazards. It describes pre-continuation code, not necessarily the current implementation. The current recorder already references a separate lifecycle helper and timestamp matching; use fresh tests and source review to determine which historical hazards remain. Do not relabel sequential DNG archives as proprietary RAW video, preview tone treatment as recorded named LOG, or requested OpenGate/codec/FPS settings as measured output.

## Build and test inventory

- AGP 9.1.1; wrapper distribution pinned to Gradle 9.3.1; Compose plugin catalog version 2.2.10; KSP 2.3.5; Compose BOM 2024.09.00.
- Host tests include the arithmetic smoke test and Robolectric API-36 checks for app resources, color matrices, multi-frame merging, focus stacking, WB gains, telemetry formatting, exposure programs and resolution model flags. Concurrent work may add further tests.
- Requested baseline: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache`. Execution outcome and environment details are recorded in [ENVIRONMENT_AUDIT.md](ENVIRONMENT_AUDIT.md).
- Hardware capture, actual encoder/transfer metadata, audio synchronization, DNG fidelity, sustained performance, accessibility and foreground/background recovery are not validated by these host tests.

## Preservation and scope

Seven tracked application files and two untracked camera files were dirty at audit entry, alongside `docs/` and `reverse_engineering/`. This worker preserves concurrent source changes and does not stage unrelated work, reset the tree, install reference applications or start emulators. Wrapper restoration and these audit documents are the tooling/documentation changes. The coordinator subsequently permitted compile-only corrections: this worker replaced one unsupported nullable primitive-array `orEmpty()` call with `?: intArrayOf()` in `RawSequenceRecorder.kt`, retaining the intended fallback. No application behavior changes were made by this worker.

Next research and acceptance gates: [REVERSE_ENGINEERING_PLAN.md](REVERSE_ENGINEERING_PLAN.md).
