# Error handling contract

Normative original behavior, not proof of reference or IrisI execution. Evidence motivation: A R04 bounded ingestion/error ownership, B E19/E23/E14 capture/finalize errors and C S12/S24 job/save failure paths. Exact reference numeric native-error mappings and malformed decompilation are **not adopted**. [State contract](STATE_MACHINE_SPEC.md).

Every user-visible error carries operation ID/category, useful original explanation and safe remedy. Diagnostics may include exception/code without exposing media, location or sensitive paths. Preserve first/root failure; later cleanup errors may be attached, not replace it. “Saved” requires valid committed output.

| Category | Trigger | Required behavior and user recovery | Test |
|---|---|---|---|
| Permission | Camera denied/revoked; optional audio denied | Abort/reconcile required camera work, clean ownership and offer retry/system settings if appropriate; avoid repeated prompts. Disclose silent audio fallback before recording or offer cancellation. | AT01/AT11 |
| Capability | No lens/RAW_SENSOR/stream tuple/encoder profile | Reject before start with mode-specific reason; retain supported modes. Explicit selection/change instead of silently retaining unsupported labels. | AT01/AT08/AT09 |
| Capture setup | Session/request rejected, camera disconnect | Resolve pending token once, stop admission, drain owned writes and return failure/partial outcome. Late setup callbacks cannot restart stopped generation. | AT03/AT05 |
| Pairing/metadata | Missing sensor timestamp, mismatched pair, duplicate/stale image/result | Never write guessed association. Bound/expire entries; close discarded images exactly once; fail affected capture with counts. | AT02 |
| Acquisition timeout | Missing image/result/completion callback | Resolve acquisition once independently of writer count; stop or proceed only under explicit safe policy. No perpetual busy/pending flag. | AT03 |
| Backpressure | Writer or unmatched-entry cap reached | Stop admission or explicitly count rejection; do not accumulate unbounded images/tasks. Show overload reason, not invented sustained FPS. | AT03/AT06 |
| Write/storage | Full disk, revoked/disconnected output, DNG writer/stream close failure | Mark frame failed, preserve committed frames, drain safely and report partial/error. A partially written file is not saved. | AT06/AT10 |
| Packaging/index | ZIP exception/close/commit/index failure | No completed-export success. Retain valid sources; allow scoped later export. If bytes valid but index fails, report valid location plus library warning. | AT06/AT10 |
| Stop/drain deadline | Writer/callback cannot finish safely in allotted finite policy | UI reports failure/interruption, never waits indefinitely. Quarantine unresolved owner/output rather than force-closing writer-owned memory and claiming success. No reuse until safely released. | AT04–AT06 |
| Platform video finalize | Encoder/no frames/creation/storage/recorder error | Clear active state only through terminal owner, expose code/category, do not claim playable output without validation. Retain/delete only according to explicit scoped policy. | AT14 |
| Lifecycle/process death | Background/teardown/process killed | Apply documented foreground-only stop policy; reconcile persisted incomplete work next launch. No automatic capture restart or destructive crash cleanup. | AT12 |
| Settings/import | Malformed values, incompatible preset, active-state mutation | Validate before atomic apply; preserve last valid state, explain unsupported fields. Future archives reject traversal/oversize/schema abuse. | AT07/AT10 |
| Library/development | Missing/foreign/unsupported source, cancellation/processing failure | Nonfatal accessible error; preserve originals, accurate partial results, no deletion outside owned scope. | AT10/AT14 |

## Cancellation is not an error synonym

User Stop with valid frames yields a stopped-partial result with actual counts and optional valid ZIP; empty Stop yields cancelled/empty, not a saved clip. Acquisition limit reached without failure is completed only against disclosed bounds. Lifecycle interruption and failed export remain distinguishable from deliberate Stop. No automatic retry that duplicates media or silently resumes recording.

## Accessibility and observability

Announce terminal errors/remedies and retain actionable text until dismissed; do not rely on transient colour alone. Separate capture, save, finalization and indexing progress. Tests assert terminal events/resource ownership as well as user messages. Static error branches and declared notifications do not establish accessible recovery or valid files.
