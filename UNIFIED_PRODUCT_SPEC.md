# IrisI unified product specification

## Product and evidence boundary

An independent, local-first Android camera with reliable photo/video capture, explicitly experimental bounded sensor-RAW DNG sequences, media review/development, presets and capture assistance. Preserve IrisI's current identity and architecture. **No claim of proprietary parity, continuous RAW-video performance, recorded LOG fidelity or complete discovery.** [Comparison](FEATURE_COMPARISON.md), [decisions](IMPLEMENTATION_DECISIONS.md), [reference evidence](docs/reverse-engineering/SYNTHESIS-EVIDENCE-20261116.md).

This is a target contract. Source presence, implementation completion and tested behavior are different axes. The main session owns code/build/runtime audit; all acceptance outcomes are UNVERIFIED here. Reference labels INFERRED/UNAVAILABLE must not be confused with IrisI test results.

## Release boundary

**P0:** correct bounded RAW lifecycle, timestamp pairing, usable stop, guarded mode/configuration changes, truthful settings, safe output/error handling, existing capture/library behavior protected from regression. **P1:** complete existing assistance/manual/preset/development workflows after end-to-end tests. **P2/deferred:** recovered advanced feature families requiring additional research/platform implementation. P2 is not removed from the all-feature goal and is not advertised as complete.

## Core journeys

1. Open camera → request required camera permission → choose supported photo/video/sequence mode → show requested/effective capture settings.
2. Sequence preflight → disclose bounds and storage/RAW requirements → start → show acquired/saved counts → tap Stop or reach cap → drain → package → show valid output or explicit partial/failure.
3. Normal video → show actual recording state → stop → wait finalize → review valid output. No success toast merely on accepted start/stop.
4. Open library → select owned item → inspect supported media/develop → export independent output or explicitly fail; delete only confirmed, validated targets.
5. Apply presets/configuration only when idle; settings unsupported in the active path are disabled with reasons, never silently cosmetic.

## Shared invariants

- At most one active capture owner; one immutable settings snapshot/generation per operation.
- RAW image/result association uses exact sensor timestamp and session identity, never callback arrival order.
- Bounded pending acquisition and writers, finite timeouts, idempotent Stop, exactly-once terminal result and eventual resource shutdown.
- A partial ZIP or interrupted sequence is distinguishable from a complete export; preserve valid source frames.
- Separate requested/negotiated/measured values. Do not equate preview treatment, codec selection labels or ten-bit capability with verified output.
- Optional microphone/location denial does not block unrelated silent/local capture; no hidden location collection or reference-service dependence.
- Accessibility: text/semantic labels, non-colour capture states, focusable Stop and errors. No accessibility conformance until tested.

## Linked contracts

| Contract | Authority |
|---|---|
| [Requirements](PRODUCT_REQUIREMENTS.md) | Stable IR-* IDs, priorities and release gates |
| [Screens/navigation](SCREEN_AND_NAVIGATION_SPEC.md) | Original logical surfaces and route guards |
| [State machine](STATE_MACHINE_SPEC.md) | Ownership, transitions, stop/drain/finalization |
| [Data/storage](DATA_AND_STORAGE_SPEC.md) | Formats, persistence, publication, deletion |
| [API/integrations](API_AND_INTEGRATION_SPEC.md) | Proposed internal/platform contracts, trust boundaries |
| [Errors](ERROR_HANDLING_SPEC.md) | Failure/recovery semantics |
| [Acceptance](ACCEPTANCE_TEST_PLAN.md) | Required tests; no execution claims |
| [Traceability](FEATURE_TRACEABILITY_MATRIX.md) | Evidence-to-requirement and deferred differences |

## Explicit gaps

Unfinished reference discovery: complete controls/screens, native algorithms and proprietary schemas, exact encrypted labels, malformed branches, settings setters/defaults, secure entry, traffic, outputs, device matrices, performance and accessibility. Unfinished IrisI verification: main agent must supply current implementation and test reports; historical checkpoint source findings are not current runtime facts. No finite static pass can certify “all features” across unknown native/dynamic behavior.
