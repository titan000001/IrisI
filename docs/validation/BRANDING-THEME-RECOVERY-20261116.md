# Branding, Material You and LOG recovery

## Implemented

- Organic optical-bloom identity, editable SVGs, adaptive vector foreground,
  transparent monochrome pupil and all five legacy launcher densities.
- Repository README, documented feature boundaries, contributor guide, issue/PR
  templates and a debug-only Android CI workflow. No license was invented.
- Dynamic Material palettes enabled by default on API31+, system light/dark
  mode and fallback light/dark schemes. The whole dynamic palette is used rather
  than mixing wallpaper text colors with unrelated fixed surfaces. Only the
  optical-preview subtree stays dark. Shutter contrast adapts to the chrome mode.
- A read-only media-library recovery panel for complete retained LOG originals.
  It explains unfinalized transfer tags and shares only validated, nonempty
  originals through a narrowly granted FileProvider URI. It offers no deletion,
  silent overwrite, repair or claimed encoder/process-death recovery.

## Verification history

- Initial icon lane: 45 host tests ran; 44 passed and one failed. The API24
  legacy-image test incorrectly used native Canvas rendering (supported from
  API26). It now checks the actual decoded PNG pixels instead. The API33
  adaptive/monochrome render test passed in that initial lane.
- A subsequent compile caught an overlapping source edit: Gradle had captured
  its source list before new recovery files were added. The dependent edits were
  visible but their new classes were not. A fresh, stable-source build was started;
  this failed compile is not counted as a successful milestone.
- Generic gstack detection could not map the Android lane and reported `pytest`
  absent for unrelated Python automation. It did not pass. Direct, repository-
  declared Gradle checks are the Android verification lane.
- The automation README's actual standard-library command,
  `python -m unittest discover -s automation/tests -v`, passed **61 tests** with
  no dependency installation. These are separate from Android host tests.
- Ten generated legacy PNGs passed dimensions, PNG chunk CRCs and corner/center
  alpha checks. Generated SVG/VectorDrawable XML parsed successfully; sampled
  geometry fits within the centered 66dp adaptive safe region.
- All **54 Android host tests passed**, zero failures/errors/skips, after the
  remux flag conversion, recovery inventory, theme and icon corrections. The app
  APK also built. Android-test compilation then encountered a second overlapping
  source-list snapshot when the next sharing helper/test was added during that
  build. Final validation now uses a stable source tree; no source edits while
  Gradle is running.
- Local README/contributor/brand/validation Markdown links passed an existence
  check. `git diff --check` passed (line-ending warnings only).

- The stable-source app and Android test APKs built. Lint then reported **one error,
  73 warnings**: extractor flags passed directly to `MediaCodec.BufferInfo`. The
  flag domains overlap numerically but mean different things for encrypted/partial
  samples. Explicit mapping now preserves only sync/keyframe semantics and rejects
  encrypted, partial and unknown flags before muxing. Two new regression tests cover
  this contract. The failed lint task prevented the later host-test task in that run.
- The UI/recovery artifact was copied before the remux-only fix for an immutable
  device-test snapshot: app SHA-256
  `b665e682fed65a78d45eed46cf5785734e50cfc7db3bd10a400168e220b3a220`,
  test APK `4c865f96e3b23f676dda67c47dc7d2269b24128e680578edcf37619dd079188e`.
  Its device log is `branding-theme-recovery-device-pre-remux.txt`. This is not
  the remux-fix build.

## Executed Pixel checks (pre-remux-fix snapshot)

All **eight targeted tests passed** on the wireless Pixel 7 Pro, API37, in 3.099s:
theme recomposition, two recovery UI tests, and five real-provider tests, including
the simulated interrupted replacement and exact retained-byte comparison. Log:
`branding-theme-recovery-device-pre-remux.txt`.

The live-activity `CameraConsoleDisclosureTest` also **passed**, one test in 3.13s,
against the same APK pair. Log: `branding-live-console-pre-remux.txt`. Capture tools
can be opened and dismissed through manual-control disclosure in the real app.

Dark and light live-camera screenshots were visually inspected in private local
temporary storage; wallpaper-derived green chrome, mode/lens controls, shutter
contrast and dark preview overlay surfaces were visible. An initial screenshot
was entirely black, so it was not accepted as a visual pass. Explicit wake and a
resumed/focused-app check yielded the useful captures. The device's original
`auto` night-mode setting was restored in `finally`. Screenshots were not added
to the repository because they contain private device-status and camera content.

Final remux-fix Gradle results passed locally: 54 host tests, app/test APK
assembly and lint with zero errors (73 warnings). The matching-APK Pixel rerun
also passed 11 tests in 5.764s; exact hashes and the local log hash are in
`TEST_RESULTS.md`. Hosted CI run
[#43925680456](https://github.com/titan000001/IrisI/actions/runs/43925680456)
failed in `android-actions/setup-android@v3` before Gradle, so hosted compile,
tests and lint were skipped.

## Publication checkpoint — 2026-11-17

The user explicitly chose a progress commit/push directly to `main`, without the
full release/PR workflow. This is not a release or a claim that all gates passed.
The stable-tree run passed the 54 host tests, assembled both the app and test APK,
and passed lint; the matching-APK Pixel rerun passed 11 tests. No
interruption-state feature has been added yet.
Private captures, raw reference artifacts, local configuration and unrelated
automation are intentionally not part of the published checkpoint.

## Next functional slice: safe single and batch sharing

Found and corrected the inconsistent batch path: single DNG archives were resolved
to restricted FileProvider URIs, but batch selection still passed `file://` URIs.
The shared intent builder now resolves every selected output before opening a
chooser, includes every stream in `ClipData`, grants read only, deduplicates
streams and chooses ZIP/video/image/mixed MIME types. Invalid or unavailable
private files fail closed with a visible error, rather than a partial batch.
Two real-provider device tests cover a mixed archive/video batch and duplicate/
invalid selections. They passed in the matching-APK Pixel rerun.

## Repeatable checks

Host: `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest`
with `--no-configuration-cache --no-daemon --max-workers=1` using Android Studio JBR.

Targeted Android classes (no camera media created):

- `com.example.MediaShareIntentsTest`: mixed batch read grants, deduplication and
  fail-closed invalid selections.
- `com.example.CameraConsoleDisclosureTest`: live-activity capture-tool disclosure.
- `com.example.CinemaThemeModeTest`: system-mode recomposition; monitor remains dark.
- `com.example.LogRecoverySectionTest`: empty queue, disclosure, status warning,
  chosen-original callback.
- `com.example.MediaShareUrisTest`: real Android canonical paths/FileProvider,
  recovery content readability and retained bytes after simulated replacement failure.

Install matching app/test APKs first, select an explicit serial, then pass these
comma-separated classes to `am instrument -w -r -e class` with runner
`com.aistudio.cinerig.vxkpmq.test/androidx.test.runner.AndroidJUnitRunner`.

## Boundaries

The recovery inventory can find completed backups after reopening the app, but
does not promote `.part` files, prove a playable bitstream, fix original LOG
signaling, or provide a durable recording service. File/path and simulated-provider
tests are software-contract tests, not a real process-kill or storage-power-loss
experiment. No all-device theme, TalkBack, font-scale or camera-quality pass is
implied by the added UI tests.
