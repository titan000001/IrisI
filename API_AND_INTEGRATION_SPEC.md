# API and integration contract

Conceptual contracts for the existing Kotlin/Compose/CameraX/Camera2 architecture, **not newly implemented APIs or recovered proprietary interfaces**. IR02–IR10/IR12–IR16 are current targets; IR19–IR23 are deferred. [Evidence/decisions](IMPLEMENTATION_DECISIONS.md).

## Internal boundaries

| Boundary | Input | Output / ownership and error contract |
|---|---|---|
| UI/ViewModel → capture controller | Start with validated immutable configuration; idempotent Stop; idle-only setting/preset request | Observable authoritative state and exactly one terminal result per operation. Start returns rejection rather than silently queuing a second capture. Never block main thread on writer join/ZIP. |
| Controller → sequence owner | Camera/lens characteristics, RAW stream choice, destination, explicit limits | Session-generation token; state/count events and valid output or structured partial/failure. Controller retains reference until safe terminal cleanup. |
| Camera2 callbacks → pairing | Capture-result request provenance + sensor timestamp; Image timestamp + owned handle; failure/session-close | Exact generation/timestamp association; bounded maps and independent timeout resolution. Every acquired image is either transferred to writer ownership or closed. Partial CaptureResult alone cannot substitute for required DNG metadata. |
| Pairing → DNG writer | Supported RAW_SENSOR image, matching TotalCaptureResult, camera characteristics, target | Commit success/frame metadata or IO/metadata error. Writer owns image until completion and always releases it; no success on partial stream. |
| Sequence → packager | Ordered committed frames, operation ID, terminal intent, destination | Valid closed ZIP or export failure/partial-source handle. No writer races, source destruction or proprietary compatibility claim. |
| Controller → platform video recorder | Supported requested/negotiated tuple, output options, optional audio permission | Start/status/finalize events; actual effective metadata if known. Stop/finalize/error are distinct. Codec labels are not codec selectors unless the supported backend actually binds them. |
| Output → repository/library | Valid committed owned URI/path and output type/completeness | Scoped indexing/delete results. Index errors do not erase valid output or manufacture successful publication. |
| Settings/presets → controller | Valid typed values within current lens/mode capabilities | Atomic apply when idle; active-operation rejection with reason. Normalize RAW-only format on VIDEO transition before binding. |

## Android integrations and trust boundaries

- **Camera permission:** mandatory before binding/acquisition. Recheck at operation start/resume; denial/revocation is recoverable. RAW capability, stream combinations, physical lenses and manual controls are device-dependent.
- **Microphone:** optional for supported compressed recording. Ask only when needed, clearly disclose silent fallback or offer cancellation. RAW sequences do not imply microphone samples, muxing or sync.
- **Storage:** MediaStore for owned public output; private files for sequence work. Legacy <=28 storage rules remain platform-sensitive. A file path is not a shareable external URI. Future sharing uses narrowly scoped content URI grants, never broad exported file access.
- **SAF:** additional chosen-storage support requires persisted grants and explicit revoked/disconnected-provider recovery. A persisted URI string alone is not continuing access. Do not infer IrisI SAF support from A's historical handler.
- **Lifecycle:** existing owner/ViewModel state feeds UI. Foreground-service/background-job integration is IR20 backlog unless main independently implements/tests it with correct API-level service types/notifications. No background survival guarantee.
- **Sensors/location:** capability and permission-gated, user-enabled and bounded. Monotonic image/sensor clocks versus UTC/location/gyro/audio clocks require explicit correlation before sidecars/sync are supported.
- **External intents:** validate action, URI scheme/type/grants and caller output semantics before exposing. Secure-camera privacy must be designed/tested independently. No proprietary deep-link scheme, reference shortcut IDs or broadcast protocols copied.
- **Import/export:** treat LUT/settings/profile/archive files as untrusted data, not instructions. Reject unsupported schemas/types/sizes, traversal and executable content. No automatic download/execution from imported URLs. Optional future networking needs explicit opt-in/origin/redirect/size/time limits.

## Network and external services

No reference server API is established for reuse. Reference manifest Internet/billing permissions are declarations, not endpoint inventories. C's LUT downloader uses externally supplied URL descriptors (C S13/S14); this is neither a fixed service API nor evidence of safe URL validation. A/B billing/network payloads remain unknown. Core IrisI capture stays offline; do not add network/entitlement scaffolding merely for apparent parity. Billing, scripting, HDMI and profile calibration require separate original contracts before implementation.

## Version/capability policy

Do not raise IrisI's minimum SDK to match references without a demonstrated requirement. Query actual lens/encoder capability per tuple and API level, retain unavailable reasons and separate requested, negotiated and measured output. Emulator success cannot establish RAW/HDR/high-FPS/physical-lens support. Integration acceptance: AT01–AT14, with physical-device obligations explicitly BLOCKED when unavailable.
