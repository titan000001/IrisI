# Independent implementation decisions

These are original design recommendations, **not descriptions of already implemented/tested IrisI behavior**. Baseline issues come from [the checkpoint](docs/reverse-engineering/CHECKPOINT-20261116.md), pending main-agent audit. Reference facts are static INFERRED; evidence and portability caveats are in [the supplement](docs/reverse-engineering/SYNTHESIS-EVIDENCE-20261116.md).

| Decision | Rationale / alternatives rejected | Consequence and validation |
|---|---|---|
| D01 — Preserve bounded DNG; prioritize encoded LOG | Latest user direction rejects DNG sequencing as the desired RAW-video substitute. Retain completed safety work and media, but prioritize genuine 8/10-bit recorded LOG, SDR and HDR. | Keep sequence limits explicit. Independently implement and verify a pre-encoder transfer; preview styling and metadata relabeling do not deliver LOG. See the encoded recording plan. |
| D02 — Pair by sensor timestamp, not arrival | A R04 carries image timestamps but does not prove complete pairing. Original safety requirement: equality with CaptureResult SENSOR_TIMESTAMP. | Track operation generation, request token and timestamp. Bound unmatched buffers/time; reject mismatches. No “closest” silent association. AT02. |
| D03 — Separate acquisition, save, and terminal ownership | B E21 distinguishes busy/pending saves; C R03/R10 asynchronous finalization. Existing conflated pending flag is unsafe. | Exactly-once request resolution, writer drain and session terminal result; release Image only after writer ends. AT03–AT06. |
| D04 — Stop is always reachable while acquiring | C R03 demonstrates intent to stop asynchronously; B toggle-stop route. | Shutter becomes Stop during capture, busy during finalization; backend guards independent of UI. Stop is idempotent and bounded. AT04/AT05. |
| D05 — Freeze disruptive configuration while active | C explicit states; A encrypted busy guard only historical. Original consistency policy. | Reject/defer mode/lens/format/preset/rebind changes until terminal; do not silently apply half a preset. AT07. |
| D06 — Truthful settings over aspirational controls | B E09 capability checks, C hardware option validation, A precision fallbacks (partly historical). | Requested/effective distinction. Preview curve is not recorded LOG. Do not promise explicit HEVC/120 fps/OpenGate/binning unless backend and output establish it. AT08/AT09. |
| D07 — Preserve current architecture/identity | Existing Kotlin/Compose, CameraX/Camera2, Room/DataStore source baseline; no evidence requires rewrite. | Minimal coherent lifecycle fixes, preserve user work. No native libraries/assets/source/branding from references. |
| D08 — Publish only completed artifacts | B MediaStore paths and C S24 AtomicFile motivate explicit commit boundary, not proven reference atomicity. | Temp output → finalize → index success. Preserve valid partial frames with incomplete status; failed ZIP is not success. AT06/AT10. |
| D09 — Conservative lifecycle and recovery | A services and C workers require additional implementation; background success is unproven. | Until verified foreground-service ownership exists, stop on lifecycle loss and report interrupted state. No background-recording promise, automatic restart or destructive source cleanup. AT11/AT12. |
| D10 — Local, capability-gated expansion | All three have unverified hardware/processing differences. User's all-feature goal remains visible rather than fictitiously complete. | P1/P2 backlog includes every recovered family; no fake purchase, LUT download, HDMI, scripting, profile calibration or proprietary import implementation. Unsupported features carry reasons. |

## Conflict resolution

1. RAW still, bounded DNG sequence, custom RAW container and processed/LOG video are different products; keep separate types and labels.
2. Do not adopt destructive reference crash-marker cleanup: preserve originals and require explicit scoped deletion.
3. Do not copy reference caps, commercial gates, retry predicates or unresolved enum meanings. Independently select/test limits and document them.
4. No automatic downgrade that still displays the requested quality as effective. Explain and obtain a clear new selection when the platform cannot guarantee the choice.
5. Runtime verification, file parsing, hardware tests and assistive-technology checks determine release status; this document does not grant PASS.
