# Product requirements

Target specification, 2026-11-16. **Not an implementation or test-completion report.** Evidence aliases A/B/C refer to [traceability](FEATURE_TRACEABILITY_MATRIX.md); original design choices are labeled D01–D10 in [decisions](IMPLEMENTATION_DECISIONS.md). All reference behavior is static INFERRED, not reproduced. All IrisI acceptance below remains UNVERIFIED until main-agent results exist.

## P0 — immediate release gates

### Current priority: encoded recording

The user's latest direction supersedes DNG sequencing as the primary recording
deliverable. Preserve DNG capture and existing media. Deliver genuine recorded
LOG with **explicit 8-bit and 10-bit options**, supported SDR and HLG, and verified
PQ conversion where direct PQ capture is absent. Keep codec, precision, transfer,
gamut, range and input provenance independent. Unsupported combinations require
an explanation, not silent fallback. See the
[encoded recording plan](docs/reverse-engineering/ENCODED_VIDEO_PLAN.md).
Recorded LOG and PQ are not delivered by the existing preview controls.

| ID | Requirement and observable acceptance | Preconditions / failure | Source / tests |
|---|---|---|---|
| IR01 | Describe experimental RAW as finite sequential DNG acquisition with duration/frame/rate bounds; never as playable RAW video or proprietary-compatible output. | RAW-capable lens and supported RAW_SENSOR size, camera grant, writable output; otherwise unavailable with reason. | A R04, B E19; D01; AT01/AT08 |
| IR02 | Write a RAW frame only with matching same-operation sensor timestamp metadata. Either callback order works; duplicate/stale/mismatched callbacks cannot corrupt a frame. | Missing timestamp/result/image expires with explicit failure; bounded unmatched ownership. | A R04 motivates, exact join is D02; AT02 |
| IR03 | Bound frame count, elapsed acquisition, pending request, unmatched images and writer work; terminate each accepted request exactly once. | Capture rejection/failure/timeout clears acquisition ownership; save failure is separately recorded. | A R04, B E21, C R03; D03; AT03 |
| IR04 | Second shutter tap stops sequence acquisition; Stop is idempotent, immediate in UI and asynchronous in cleanup. | Stop during starting/capture/writing never starts another sequence; UI remains responsive while draining. | C R03/R10; D04; AT04/AT05 |
| IR05 | Finalize exactly once only after admitted writers finish or fail; release images/readers/session/executors without racing active writers. | Late callbacks close/discard their own resources; no successful writes after terminal publication. | A R04, C R03/R10; D03; AT05/AT06 |
| IR06 | Reject disruptive mode/lens/codec/size/FPS/preset/config changes during starting/acquiring/draining/packaging or compressed recording/finalizing. | Give reason; do not rebind midway or partially apply preset. RAW→VIDEO reconciles format only when idle. | D05; AT07 |
| IR07 | Present requested and effective output honestly. Preview-only tone treatment is labeled as such; unsupported codec, bit depth, HLG/LOG, FPS, OpenGate/binning cannot be asserted by labels. | Capability checks and explicit unavailable/fallback notice; do not silently clamp while retaining requested label. | B E09/E10, C R07/R08; D06; AT08/AT09 |
| IR08 | Commit/index output only after successful finalization; failed/incomplete sequence is explicitly classified and valid originals retained. | Storage/ZIP/index failure must not report complete success or destroy valid frames. | B E19/E27, C S24; D08; AT06/AT10 |
| IR09 | Camera denial/unavailable lens/storage failure restores recoverable idle/error UI; silent-video policy is disclosed when optional mic unavailable. | Recheck grants/capabilities on resume/start; no permission prompt loop. | New manifests, B E11; D09; AT01/AT11 |
| IR10 | Lifecycle loss uses a documented stop/interrupt policy; no recording continuation/resumption guarantee without verified service ownership. | Prevent stale callbacks reopening a destroyed generation; preserve finalized or explicitly partial output. | N-B-PAUSE, C R10; D09; AT12 |
| IR11 | UI communicates starting/capturing/saving/finalizing/error by text and semantics, including reachable labeled Stop. | Capture control >=48 dp design target; errors not colour-only; large text cannot obscure Stop. | B E17, N-B-KEY; independent accessibility policy; AT13 |
| IR12 | Preserve existing owned-media safety and local capture privacy. | Delete only validated owned targets after explicit confirmation; no reference code/assets/service dependency. | B E27, C S12, checkpoint baseline; D07/D08; AT10/AT14 |

## P1 — preserve and complete existing product workflows

| ID | Contract / acceptance gate | Limits and source | Tests |
|---|---|---|---|
| IR13 | Still and compressed-video capture remain distinct from sequence capture; start/stop/save/error paths work without RAW. | B E19/E11; platform/device validation required. JPEG/DNG/PNG/WebP choices only when an actual writer is connected. | AT14 |
| IR14 | Manual exposure/focus/WB and capture assistance expose valid capability-dependent values and correct units; distinguish display analysis from recorded processing. | B E12/E15, C S19/S20 (inventory weaker). Existing controls need main audit. | AT09/AT14 |
| IR15 | Library/development/presets/HUD flows are functional end-to-end, persist only validated state and never overwrite originals on failure. | A E14/E18, C S12/S24; reference editor semantics incomplete. | AT10/AT14 |
| IR16 | Video audio controls/levels reflect implemented routing and permission state, not UI option presence. | B E15 is schema, not DSP proof. No RAW audio synchronization claim. | AT11/AT14 |

## P2 — explicit retained backlog, not current capabilities

| ID | Feature family retained in all-feature goal | Release prerequisite |
|---|---|---|
| IR17 | Burst/bracket/stack alternatives; advanced photo editors/undo and RAW development quality | Trace output/merge semantics, independently implement and verify files/history/cancellation. |
| IR18 | RAW/LOG native rendering, advanced codecs/HDR, high FPS, full sensor acquisition/binning | Independent sensor/processing/codec implementation, explicit color/container specification and physical-device output tests. No proprietary schema adoption. |
| IR19 | Lens discovery/profiles/DCP, LUT management, calibrated color/NR/point WB | Complete control/data mapping, owned compatible formats, calibration/color tests and safe imports. |
| IR20 | Timelapse, pause/resume, gyro/audio sidecars, encode/move/recover queues and background services | Explicit time bases, bounded queues, lifecycle/service policy, cancellation/recovery tests; no silent source deletion. |
| IR21 | Macro/lens policy, advanced audio DSP/routing, stabilization, external clean display | Capability evidence and actual supported hardware; UI schema alone insufficient. |
| IR22 | Settings portability, diagnostic/log export/reset/help/onboarding, scripting/SCREEN, installation recovery | Complete handlers, trust-boundary validation and original accessible workflows; scripting/SCREEN semantics still unknown. |
| IR23 | External capture/secure/QS/shortcut entry; billing/entitlement and optional network LUT retrieval | Explicit privacy/URI/auth contracts, independent service policy, user consent and validation. Do not copy reference protocols or identifiers. |

No release is “all features complete” while IR17–IR23 or underlying discovery is unfinished. Unsupported/deferred differences must remain visible in release limitations. P0 acceptance requires executed tests, not documentation or source review.
