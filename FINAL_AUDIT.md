# Delivery audit

## Outcome and completion boundary

Latest recording continuation: experimental 8-bit ISP-SDR-fed LOG now passed two
short repeated captures on the wireless Pixel after SHA-256 finalization safety
and stricter device checks. Host totals: 43 tests, no failures/errors/skips.
This supersedes older unconnected-LOG statements, not the release boundary.
See `docs/validation/LOG-COMMIT-WIRELESS-20261116.md` for current hashes, evidence
and the still-open color, lifecycle, 10-bit and sustained-recording gates.

This delivery advances the three-reference investigation into actual IrisI implementation and tests. **It does not complete all-feature parity.** Missing native/runtime evidence, licensing gates and the retained backlog prevent an honest all-features-complete claim. `TEST_RESULTS.md` is the authority for executed verification; `KNOWN_LIMITATIONS.md` and IR17–IR23 retain unfinished work.

## Delivered

- Three independent, evidence-linked 20-section static inventories and detailed RAW/LOG recording traces, with distinct static/inferred/unknown statuses.
- Artifact/hash inventory, portable REA exports, citation-retention caveats, and bounded runtime installation/launch observations for all three references.
- Project/environment/research audits; comparison; decisions; unified requirements; navigation/state/storage/integration/error specifications; traceability; acceptance test plan.
- Integrated independent recorder changes: exact sensor timestamp association, bounded ownership, setup/frame timeouts, stop during pending capture, active-writer drain, exactly-once completion, thread shutdown, late-device resource cleanup and frame telemetry.
- Atomic-name DNG manifest/ZIP finalization, preservation of completed frames and exclusion of `.part` files from the archive.
- Reachable labeled RAW Stop and busy-state configuration/rebind guards, plus RAW→ordinary-video format reconciliation. Compressed recording remains busy through finalization and reports startup exceptions.
- Specific HLG dynamic-range capability checks and visible unsupported failure; monitor-only LOG/cadence/geometry limits disclosed; unsupported 120 fps choice disabled.
- Restricted FileProvider for completed private DNG ZIP viewing/sharing, rather than exposing `file://` paths. Private traversal/outside/incomplete files are rejected.
- Restored Gradle 9.3.1 wrapper and added state/lifecycle and real-provider tests. Build/install instructions in `BUILD_AND_INSTALL.md`.

## Preservation and independence

The existing app identity, Kotlin/Compose Material 3 architecture, persistence and navigation remain. Pre-existing source modifications and untracked RAW engines were not reverted. The recording engine was repaired in place, not replaced with a demonstration. No reference source, private native code, asset, branding, exact layout or licensed service protocol was incorporated. No app-code commit/push/merge was performed; the separately approved routing-only `CLAUDE.md` commit is `54031a7`.

## Reference runtime disposition

- Mereframe: supplied installation blocked by signature conflict with existing harness; original package/data preserved.
- RawCam and MotionCam: complete supplied splits installed; application launches entered licensing/Play routing. No bypass, login, purchase, installer spoofing, reset or uninstallation was attempted.
- Camera workflow, permission-denial, output fidelity and actual reference feature reliability remain unavailable. REA's ADB path was unavailable; direct-ADB observations are explicitly attributed, not relabeled REA Evidence.

## Validation evidence

The final host/device command passed: unit tests, debug assembly, lint and four Android instrumentation tests. The debug APK was installed and launched on the API 37 emulator. Camera denial/retry/grant and microphone denial were observed, a JPEG was captured into `DCIM/IrisI`, and the media library displayed it. MP4 recording worked without microphone permission; audio capture was not validated. The first instrumentation attempt failed only because its test fixture tried to create a path through a missing intermediate directory; the fixture was corrected and the full rerun passed. The direct-ADB script and captures are in `docs/validation/runtime/`.

Final totals: 21 host tests, four device tests, zero failures; lint zero errors/77 warnings. MP4 start/stop/file publication also worked, but actual output was 720×1280 despite requested 4K, an explicit remaining configuration discrepancy. APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `e09480c59d2b80d90df0b5d01eb2c4cf07e92b4a274ea3e8f7368847fb6153aa`.

## Release gate

Do not market this as reference-equivalent RAW/LOG video, all-feature completion or production readiness. Device output inspection, sustained capture, lifecycle/process-death/low-storage/error testing, manual controls/audio/accessibility and the complete retained unique-feature backlog remain open.

## Restart continuation

Later Pixel evidence and newer geometry changes were recovered without repeating
reference analysis. A missing UI binding-error/notice banner was added with two
device tests. Shared Gradle output locking was bypassed with isolated temporary
outputs, without terminating the lock holder or deleting existing outputs.
The isolated run passed 24 host tests, assembly and lint (zero errors/77 warnings).
Six device contract/UI tests and one named-clip track inspection passed.

The selected Pixel configuration now recorded actual 3840×2160 AVC at approximately
24.01 fps with AAC; analysis overlays were explicitly unavailable. Broader output
fidelity and compatibility remain open. Validated APK:
`app/build/outputs/apk/resume/app-debug.apk`. Full history, hash, logs and boundaries:
`docs/validation/RESUME-20261116.md`. Earlier totals/hash above identify the earlier
emulator build, not this continuation.
