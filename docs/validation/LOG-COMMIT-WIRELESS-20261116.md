# Recorded LOG continuation — wireless Pixel

## Changes

- `LogOutputCommit` keeps a complete private recovery original before opening the
  provider for replacement. SHA-256 read-back replaces the old byte-count-only
  check. The grading sidecar is staged and only published after verification.
  Failed replacement/read-back retains the recovery original and no success sidecar.
- Five host regression tests cover success, same-length corruption, interrupted
  writes, read-back failure and failure before opening the destination.
- Camera smoke assertions accept exact FHD in either orientation. HLG SPS
  dimensions must match the container's encoded dimensions exactly.
- Explicit capture opt-ins now fail on missing permission, locked device or an
  invalid mode instead of reporting an assumption skip as a capture pass.
- The smoke report identifies the run and remains incomplete until both clips
  pass; failures are recorded. Checks include AVC SPS unspecified transfer,
  matching sidecar identity, sampled cadence, audio/video track endpoint timing,
  optional decoded contrast and return to idle between recordings. The test now
  pauses preview audio metering using the same callback as the normal UI.

## Executed host verification

`:app:testDebugUnitTest :app:assembleDebug` passed after commit-safety changes.
XML totals: **43 tests, zero failures/errors/skips**, including all five new tests.
`:app:testDebugUnitTest :app:assembleDebugAndroidTest` subsequently passed after
the stricter device assertions were added (host task reused successful outputs).
Lint was not rerun; earlier lint totals are historical, not this run's result.

## Earlier device observations (before commit-safety changes)

The Pixel 7 Pro was targeted explicitly over wireless ADB, not the emulator.
Two LOG clips passed the original repeated smoke test after fixing its orientation
assertion: `IRISI_20261116_215942.mp4` and `IRISI_20261116_215949.mp4`.
Both had 1080×1920 AVC, no SDR transfer tag reported by MediaExtractor and private
`irisi-log-v1` 8-bit sidecars. Do not infer a rotation tag from swapped dimensions.

Separate named inspection of the latter clip passed the exact geometry/MIME and
three decoded-contrast checks. It contained 125 samples at approximately
23.9936 fps and mono 48 kHz AAC. Sampled display luma ranged from 1 to 27–31:
nonuniform but dark, not a picture-quality or exposure pass.

## Updated wireless validation

The stricter repeated camera run with `recordEncodedSmoke=LOG` and
`requireNonblankFrames=true` **passed**, one instrumentation test in 17.246 s,
explicitly on the wireless Pixel at `192.168.0.104:45923`. The fresh report has
`complete=true`, run ID `1794847165799`, and two inspected clips:

| Clip | Video samples / measured fps | Audio minus video start / end | Sampled decoded luma |
|---|---|---|---|
| `IRISI_20261116_223928.mp4` | 125 / 23.9936 | −22.700 / +38.193 ms | 5–79 |
| `IRISI_20261116_223935.mp4` | 125 / 23.9936 | −43.400 / +17.743 ms | 4–79 |

Both are AVC 1080×1920 with BT.709 primaries, full-range tags, unspecified SPS
transfer and no SDR transfer tag exposed by MediaExtractor. Both have an AAC
track. Sidecar curve, bit precision and clip identity checks passed. Recording
returned to idle before the second capture. Successful finalization exercised
the new SHA-256 provider read-back and sidecar commit on Android.

Evidence: `runtime-2B081FDH300H1F/log-commit-wireless-instrumentation.txt` and
`runtime-2B081FDH300H1F/log-commit-wireless-result.json`.

Validated app APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256
`e474f127c895297bcef6be4c96bf58d02ef1faa99e58af4a1436872b5691c08a`.
Test APK SHA-256:
`125ef92f27c86a1ef804d0951595e3a587a4f2471efbdcca1f24e77b4748ddec`.

## Boundaries

This is an 8-bit ISP-SDR-fed custom LOG experiment, not RAW-fed acquisition,
extra highlight recovery, 10-bit LOG, vendor LOG conformance or production
readiness. Track timestamp alignment does not establish perceptual AV sync or
audible microphone content. Scene/patch colorimetry, source dataspace confirmation,
grading round trips, interruption/cancellation, process death, sustained thermal
capture and safe recovery UI remain open. Provider replacement is not atomic;
failure retains a private original rather than promising automatic restoration.
