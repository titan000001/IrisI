# IrisI reverse-engineering continuation plan

Date: 2026-11-16. This is a research/acceptance plan derived from existing source and saved checkpoints, not new reference-app observations. This tooling worker performs no reference installation, launch, new decompilation or application implementation.

## Inputs and confidence rules

Read [CHECKPOINT-20261116.md](docs/reverse-engineering/CHECKPOINT-20261116.md) and preserve all exports and existing source changes. Independent partial specifications already exist:

| Reference | Specification | Base APK SHA-256 |
|---|---|---|
| A: Mereframe 0.8.8/137 | [APP_A_SPEC.md](reverse_engineering/APP_A_SPEC.md) | `4f9a43b6fdde1dc328e2c6b96bd58b18fb574d478062e4130e083a8bab4c0214` |
| B: Native Camera / RawCam 1.4.5/57 | [APP_B_SPEC.md](reverse_engineering/APP_B_SPEC.md) | `172d062c70b8456f1379459afbdafea17727647d0e6502c79348fe7a487d1795` |
| C: MotionCam 5.0.8-trial/3308 | [APP_C_SPEC.md](reverse_engineering/APP_C_SPEC.md) | `731dd8682540f9eca2b61d3ec886f6fdecbfe2f606095400383b9251c1d011a6` |

All three specs expressly classify static behavioral findings as INFERRED; none establishes CONFIRMED runtime requirements. Preserve UNAVAILABLE/UNKNOWN boundaries. An enum, manifest permission, help string or JNI declaration is not proof of a functioning feature. Match every reused observation to artifact identity and exact Evidence ID.

## Phase 1: recover evidence without repeating discovery

1. Inventory existing `reference-evidence.json`, `checkpoint-evidence-20261116.json` and per-app checkpoint exports under `docs/reverse-engineering/`. Union by exact Evidence ID and artifact digest, not filenames or record counts. Do not reset a shared ledger or overwrite exports.
2. Resolve cited-but-missing records using retained tool outputs first. The coordinator checkpoint reported 23 missing recording citations; app-specific checkpoints describe additional coverage. Counts have different scopes and must not be conflated. Reconcile before claiming portable complete evidence.
3. Use the saved selectors and app-specific next steps for focused missing acquisitions only. Native implementation coverage is incomplete; current Windows provider capability must be probed, not inferred from historical Linux work.

## Phase 2: finish independent specifications

Maintain each app's 20-section structure: identity, screens, navigation, features, workflows, states, I/O, validation, edges, errors, settings, persistence, formats, network, permissions, background, hardware, performance, accessibility and evidence/confidence.

- **A:** trace library/viewer callbacks and editor field/control bindings, image/result/output pairing, storage/settings handlers, stop/drain/recovery and diagnostic entry points. Retain encrypted names as unknown; do not guess transfer-profile branding or proprietary storage schema.
- **B:** map the recovered 61-setting schema to defaults/setters/gates, finish gallery/help/entitlement flows and malformed bind/finalize/save branches. Keep DNG still capture separate from encoded CameraX video; the bounded RAW-video/LOG nonfinding is not proof of global absence.
- **C:** expand editor/library/profile/settings handlers, archive import and queue/deletion constraints, native JNI boundaries and help/onboarding/purchase surfaces. Keep MCRAW, processed LOG, SCREEN and export formats distinct; declarations are not codec support measurements.

This worker does not perform dynamic reference testing. Any later execution requires the coordinating session's authorization and a feasibility check against supplied splits/ABI. Reuse the already running emulator if appropriate, never start a duplicate. Hardware conclusions require a suitable physical device.

## Phase 3: synthesis before implementation

Produce `FEATURE_COMPARISON.md`, `IMPLEMENTATION_DECISIONS.md`, `UNIFIED_PRODUCT_SPEC.md` and `FEATURE_TRACEABILITY_MATRIX.md`. Preserve useful unique features rather than selecting only the intersection. Compare observed correctness, recovery, accessibility, offline behavior, compatibility and resource use; label unmeasured criteria rather than inventing rankings.

Then produce `PRODUCT_REQUIREMENTS.md`, `SCREEN_AND_NAVIGATION_SPEC.md`, `STATE_MACHINE_SPEC.md`, `DATA_AND_STORAGE_SPEC.md`, `API_AND_INTEGRATION_SPEC.md`, `ERROR_HANDLING_SPEC.md` and `ACCEPTANCE_TEST_PLAN.md`. Give each requirement an ID, evidence/confidence, priority, state/input/output/error contract, permission/hardware conditions and executable acceptance criteria. Separate product decisions from reference observations.

## Phase 4: independent IrisI implementation and verification

The coordinating implementation worker should retain the current Kotlin/Compose/CameraX/Camera2/Room structure. Copy no protected code, assets, branding, layouts, strings or reference native libraries. Historical recording priorities are in [RECORDING-IMPLEMENTATION-PLAN.md](docs/reverse-engineering/RECORDING-IMPLEMENTATION-PLAN.md): bounded timestamp pairing, separate capture/writer ownership, timeout, exactly-once completion, draining/shutdown, usable stop and guarded configuration. Recheck current code because concurrent work may already address these.

Run the restored wrapper's host suite and debug assembly after final edits; see [environment audit](ENVIRONMENT_AUDIT.md). Host tests must cover actual lifecycle failure/stop/late-callback cases, not only formatting and enum values. Add device tests for permission denial, lifecycle interruption, storage failure, media publication/deletion and finalization. Inspect produced files for actual codec, dimensions, cadence, bit depth and transfer tags. Do not claim encoded LOG, OpenGate/binning, real-time RAW, losslessness or sustained FPS from settings alone.

## Completion gate

Publish `TEST_RESULTS.md`, `KNOWN_LIMITATIONS.md`, `FINAL_AUDIT.md` and reproducible build/install instructions. Map requirements to PASS/PARTIAL/FAIL/UNVERIFIED/BLOCKED using the exact tested source revision or content fingerprint. A build pass is not runtime parity. Record unsupported hardware/native/container behavior explicitly and retain the original app identity and uncommitted user work.

Current project context: [PROJECT_AUDIT.md](PROJECT_AUDIT.md). This plan authorizes no additional app, device, git-push or deployment actions by the tooling worker.
