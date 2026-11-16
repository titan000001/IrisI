# Data and storage contract

Original target for IR01/IR02/IR08/IR12/IR15. Reference support: B E19/E27 DNG/MediaStore, A R04 timestamp/stride awareness and C S24 locked atomic JSON save. These static facts **do not prove** IrisI durability, DNG conformance or crash recovery. [Evidence](docs/reverse-engineering/SYNTHESIS-EVIDENCE-20261116.md).

## Logical data contracts (not claims of new Room tables)

| Object | Required data / validation | Lifetime |
|---|---|---|
| CaptureConfiguration | Mode, lens ID, requested size/rate/format/depth/audio, active effective values, finite frame/duration/memory limits | Immutable per operation; requested settings persist only through existing validated preferences/presets |
| CaptureOperation | Unique ID/generation, state, start monotonic time, counts, stop/terminal reason | Live authoritative owner; minimal interrupted-work marker only if persistence implemented |
| FramePair | Request/session identity, exact sensor timestamp, camera characteristics, image/metadata handles | Ephemeral bounded join; owned Image closed exactly once after write/discard |
| FrameArtifact | Sequence index, independent relative filename, sensor timestamp, write status, dimensions, optional error | Only committed frames count as saved; do not persist raw platform Image handles |
| MediaRecord | Owned URI/path, media kind, creation time, actual output metadata, completeness/error | Existing media log/index; reconcile index failures independently from valid bytes |
| ExportResult | Valid artifact handle or explicit partial sources, counts, terminal reason | Returned only after stream/finalization boundary |

## Formats and location

- Historical source baseline: owned photos `DCIM/IrisI`, videos `Movies/IrisI`, private sequences/ZIP in `filesDir/raw_sequences`; Room `camera_presets`, `ui_layouts`, `captured_media_logs`; DataStore `cinerig_prefs` active layout. Main agent must verify current schema; this document does not migrate it.
- Sequence payload is a series of standard DNG files generated through a supported Android RAW/DNG writer, optionally ZIP-packaged. ZIP is an archive, not a video container, not MCRAW/Mereframe/CinemaDNG interoperability certification. No synchronized RAW audio is promised.
- Use sensor image dimensions/format/stride and matching capture metadata, never assume packed 16-bit data from a label. Android DngCreator path requires supported RAW_SENSOR input; RAW10/12 packing awareness in A is not permission to pass those buffers to an incompatible writer.
- Independent filenames must avoid collisions/traversal and preserve capture ordering. Timestamp sensor values are nanosecond-domain identifiers, not UTC dates. Use wall clock separately for human-facing creation time; do not equate callback timing to exposure time.
- Processed output format/codec/color claims require a connected writer and file inspection. Preview color state cannot become encoded metadata by naming alone.

## Publication, recovery and deletion

1. Preflight writable destination and conservative free-space estimate; runtime IO can still fail. Do not promise a duration from estimated capacity.
2. Write each frame under temporary ownership, finish/close successfully, then commit/count it. Failed temporary files are not included as valid frames. Use destination-appropriate atomic/pending publication; do not assume rename atomicity on every URI provider.
3. Packaging reads committed frames only, writes a temporary ZIP, closes/validates the archive boundary and commits before returning a valid export. Report partial counts/reason. A failed ZIP preserves valid frame sources and is not indexed as completed export.
4. Index only actual committed media. If indexing fails after valid bytes, report discoverable output plus indexing warning; avoid duplicate publication on retry.
5. On interruption scan only owned work locations; validate known operation/artifact identities. Mark incomplete work, expose recovery/export/delete choices. Do not automatically erase originals because a reference uses crash markers. Do not restore camera acquisition after process death.
6. Scoped deletion requires confirmation and ownership validation; canonicalize paths, restrict URI operations to intended grants/owned media, exclude active work, avoid broad recursive deletion or symlink/path escape. Partial batch deletion reports individual failures.
7. Settings/preset writes validate first and commit atomically where supported; preserve last valid data on failed write/load. Backup/migration/archive import policies remain unimplemented until explicitly tested. Future ZIP import must limit entries/expanded bytes/depth and reject traversal/symlinks, malformed values and unknown executable content.

Privacy: location off unless independently implemented/enabled with permission; no reference private metadata or assets bundled. Media may contain personal content; diagnostics should omit images, coordinates and full user paths by default. Network/backup/reference privacy behavior remains unknown, not inferred safe.
