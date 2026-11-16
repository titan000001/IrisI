# Feature traceability matrix

2026-11-16. This matrix connects partial reference research to **proposed** IrisI requirements, not completed implementation. A/B/C aliases resolve to exact Evidence IDs in [A §20](reverse_engineering/APP_A_SPEC.md), [B §20](reverse_engineering/APP_B_SPEC.md), [C §20](reverse_engineering/APP_C_SPEC.md). Historical-only payloads remain explicitly qualified by [retention supplement](docs/reverse-engineering/SYNTHESIS-EVIDENCE-20261116.md). No runtime CONFIRMED reference findings; all IrisI tests UNVERIFIED here.

## Immediate trace spine: retained exact evidence

| Evidence ID / selector | Bounded observation | Original requirement / decision | Tests |
|---|---|---|---|
| `ev_3f93e208f721eb57fa4a6d15403ecad7aadf826b0dc4ef446cc3424eae44cd6a` A E3.n | Timestamp-keyed image ingestion, format/stride flags, ownership bound 16; not proof of full result association | IR01–IR03/IR05; D01–D03 | AT01–AT06 |
| `ev_f69a305919181b334c9cf244aedf15d6cea74bcc8c43fcdff171774a45ddf6c1` B M1.j0.L | RAW still DNG MediaStore dispatch and scheduled 8 s callback, not verified timeout recovery | IR01/IR03/IR08/IR13; D01/D08 | AT01/AT03/AT10/AT14 |
| `ev_ee2a44ae316287d1e3e1475297de5154d5faced0d3eaa000ea51960c5b7ed31a` B M1.X.onCaptureSuccess | Async processed saves and separate pending-save state; malformed branches qualified | IR03/IR05/IR13; D03 | AT03/AT05/AT14 |
| `ev_dab884304382a7908c4f28097f58d4b31d0f3af40b590a819517040036f11777` C lu0.b; `ev_db1dc8b4d638f8287ea77eb8a61197b4484bb3a5cfcf69396dff4d2d929491b3` C gu0.run | Async stop/finalize state, not validated media | IR04/IR05/IR08/IR10; D03/D04/D09 | AT04–AT06/AT12 |
| `ev_9c5a7c8482c12c69a52e6dbe10a11253a2b45c54184c12cd3ebcb1490b7dc4ac` B M1.j0.z | Capability predicate for codec/depth/size/rate | IR07; D06 | AT08/AT09 |
| `ev_0278f520365a41201d2835bc923ba061b73fe49501189b31fe5eef72dbfca1ae` B M1.j0.n2 | MP4 record start and optional mic path | IR09/IR13/IR16 | AT11/AT14 |
| `ev_353623219ee5652926846f46bb3faed649df4042d2c1c2e16ff6cf26286a96e0` B a.a.N | MediaStore/legacy naming and collision route | IR08/IR12 | AT10/AT14 |
| `ev_708e776bbeaf5cbd98cd280ee8572743cdfa817e296f7153f9fc52e0dffc77d0` C SettingsManager.save | Locked UTF-8 JSON AtomicFile intended rollback | IR08/IR15; D08 | AT10 |
| `ev_c99cd1c4a87c1b1862a7e28171eb521907455e0679e8b9f03cd06bffcf8702c1` B onPause | Preview/sensor teardown; recording continuity unestablished | IR10; D09 | AT12 |
| `ev_0e3e4dd857ff062b654bcee26ec62c17acf39dac9dbfa5a90173d4a89f97a5b2` B onKeyDown | Enabled capture click on first volume press only | IR11 optional key policy | AT13 |
| `ev_863a6d3771ea1a233163593a5bd87f1e5055f87dcb7f1ba8abd2742639ed455f` C validateSettings | Bounded buffers/threads/timelapse; unresolved storage-index constant | IR03/IR07; IR20 backlog | AT03/AT08; future timelapse tests |
| `ev_3c5172e4275fbfe0ef83f8541f694ed4873a9cd38b6700cb8470d7f3a69ac8cc` C onTogglePauseRecording | Guarded JNI pause/resume/local flag, not timestamp continuity | IR20 deferred | Future physical continuity/cancellation tests |
| `ev_60c1fa3c212228547b5811009eed1efd65dae1307787c378ad61579ed1af2e93` A Rr.e | Viewer branch returns to library; other delegated semantics unresolved | IR15; UI03 | AT14; other actions research-open |

New manifest IDs N-A-MAN/N-B-MAN/N-C-MAN in the supplement support permissions/entry/service declarations for IR09/IR10/IR23, **not runtime behavior**. Exact timestamp equality, Stop reachability, cleanup deadlines, mutation guards and publication safety are independent design obligations motivated by evidence/baseline defects, not claims copied from references.

## All recovered feature families and honest differences

Rows preserve candidate families, including weak inventories and unresolved leads. “P1 target” means retain/verify existing implementation, not that code is certified. “Deferred” means **not supported by this delivery's documentation work**; main may update status only with its own implementation/test evidence.

| Family / reference requirements | Source anchors | IrisI mapping and disposition | Verification gap |
|---|---|---|---|
| A/B/C identity, manifests, SDK/splits/hardware declarations | A ID/PRM/HW; B ID/PRM; C ID/PERM/HW; new manifests | IR09/IR12, retain own identity/API compatibility; do not copy requirements wholesale | Signatures, full installs and actual hardware availability unknown |
| Photo single/RAW/processed, burst/alternate requests, flash delays, temporal jobs | A FEA01–02/WFL01–03; B WF01–04/FMT01; C FEAT01–02/WF01–02 | IR13 P1; IR17 advanced burst/merge deferred | A alternate-request semantics, B 10-bit writer, C native photo math/output unresolved |
| RAW ingestion/chunks/journals/packing/calibration | A FEA03/IO02/PER03/FMT01–02 | IR01–IR05 P0 independent DNG sequence; IR18 proprietary/native pipeline unsupported | No custom format/compression compatibility or continuous RAW video |
| Encoded video/depth/HLG/transfer/gamut/NR/codec/container options | A FEA04/SET02/VAL03; B FEA01–04/VAL01–04/FMT02; C FEAT03/10/SET08–09/FMT01–02 | IR07/IR13 P0/P1 honesty; IR18 advanced path deferred | Effective codec/color/precision/achieved rates not measured |
| Recording states, pause, watchdogs, stop/error/fallback | A STA03/EDG01–02/ERR04; B WF05–07/STA01–05/ERR03–06; C FEAT04/STATE01/EDGE03–04/ERR01/04–05 | IR03–IR10 P0; IR20 pause/watchdog expansion deferred | Stop races/output validity/hardware thresholds need execution |
| Exposure/focus/WB, metering/locks, boost/flicker, scopes/grids/peaking/horizon | A unresolved camera controls; B FEA06–07/SET01/E12; C FEAT13–14/SET01 | IR14 P1 capability-bound; boosted/advanced processing IR21 deferred | Full control wiring, sensor/ISP compliance and precision unknown |
| Audio sources/channel/DSP/gain/filters, mirrors/stabilization/macro/HDMI | B FEA05/08–09/SET01; C SET03/HW04; A audio permission/service only | IR16 P1 existing audio policy; IR21 deferred routing/DSP/macro/display | Exposed settings do not prove active routes, DSP or clean display |
| Library/viewer/selection/editor/history/state JSON/accessibility | A SCR01/FEA05–07/STA01–04/VAL02/FMT04/ACC01; B SCR07 unavailable; C SCR01/S04–05 | IR15 P1 existing flows; IR17 richer editor deferred | A encrypted field meanings/history payload gaps, C editor contents and B gallery unestablished |
| LUT import/download, lens search/profiles/calibration/DCP, mode settings | C FEAT06–08/10/VAL04–06/SET02–04/08; B profiles E12/SET01 | IR19 deferred; retain existing independent presets under IR15 | Candidate repository fields != working calibration; download security/format mapping incomplete |
| Timelapse/resolution/scene/storage mode options | C FEAT03/SET06–07/VAL01; A high-speed/OpenGate leads | IR01 truthful sequence limits now; IR18/IR20 deferred | Exact sensor readout/high-speed/continuous timing unproved |
| Gyro/GPS metadata/sidecars | A PRM01; B IO05/VAL05/E28/E32; C FEAT11/IO03/FMT03/HW04 | IR20 deferred; IR12 privacy policy now | Clock correlation/tag precision/runtime grants/native sidecars untested |
| Storage location/URI persistence/naming/collisions/metadata and safe publication | A WFL05/PER01–04; B IO02–05/PER04/FMT04–05; C IO05/PER01–06 | IR08/IR12 P0; IR15 persistence P1; SAF/portable imports deferred IR22 | URI grant recovery, migration/backup, full bytes/publication unknown |
| Developing/export/encode/move/recover/delete workers, crash markers/cancel/service lifecycle | A BKG01–04/ERR03; B BG01–05; C FEAT05/WF04/STATE03/BG01–05/S12/S21/S33 | IR10/IR12 P0 conservative policy; IR15 P1 local development; IR20 deferred durable queues | Background survival, retry/ordering/deletion native behavior unresolved; destructive cleanup rejected |
| Settings schema/basic/advanced/help/reset/restore/debug/log/version | A SET01–04; B SCR02–06/SET01–04/PER01–07; C SET01–09/S22/S24 | IR06/IR07 P0 truthfulness; IR15 existing persist; IR22 deferred diagnostics/portability | A encrypted labels; B many setters/defaults/gates; C inventories UI reachability incomplete |
| Settings ZIP selective import/export, scripting console, SCREEN | C FEAT09/12/15/WF06/S15–18 | IR22 deferred, not represented as fake functional controls | Parser safety, language/API/SCREEN acquisition semantics unknown |
| Onboarding/help/about/purchase/license/install/device diagnostics | A SCR01/manifest; B SCR04–06/WF09–10; C SCR01/NAV01 | IR22/IR23 deferred original flows; no entitlement bypass/emulation | Exact policies/copy/screens/diagnostic algorithms mostly incomplete |
| External capture/secure/QS/shortcut/deep link/navigation/back | A NAV01–04; B NAV01–05; C NAV01–06; N-A-NAV | UI01–UI06 current original navigation; IR23 external integration deferred | Secure privacy/caller URIs/back-stack/full controls unverified |
| Network/billing/referrer/transport, external LUT fetch | A NET01–02; B NET01–03/E24–25; C NET01–04/S13–14 | IR12 offline local capture; IR23 optional services deferred | Complete endpoint/traffic/auth/telemetry/offline policy unknown; no absence claim |
| Performance/resources/device compatibility | A PRF01–04/HW01–03; B PERF01–05/HW01–06; C PERF01–04/HW01–05 | IR03/IR07 bounds/honesty; AT03/AT09 physical obligations | No benchmarks, thermal/power/quality/device winner or sustained cadence claims |
| Accessibility/orientation/RTL/input | A ACC01; B A11Y01–04/IO01; C A11Y01–03; N-B-KEY | IR11 P0 independently accessible design; AT13 | No reference/runtime conformance; large fonts/TalkBack/contrast untested |

## Coverage policy

The three APP_* documents remain the detailed per-reference requirement inventory; this matrix groups related IDs rather than claiming every callback is understood. Missing historical payloads: A 30/51, B 5/51, C 23/55 exact cited IDs (some facts now supported by replacement observations). Preserve historical aliases and new IDs together. Inventory-only features stay candidate/deferred; untraced behavior stays unknown. Main implementation/test report must map IR01–IR23 to actual source/test paths and PASS/PARTIAL/FAIL/UNVERIFIED/BLOCKED; no such status is earned here.
