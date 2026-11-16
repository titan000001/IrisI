# Capture state machine contract

Original normative design for IR02–IR10; not a recovered proprietary algorithm. Inspiration: retained A timestamp-keyed ingestion/bounds, B busy/save separation and C asynchronous stop/finalization. [Decisions D02–D05](IMPLEMENTATION_DECISIONS.md). No execution confirmation.

## Sequence operation

| State | Event / guard | Next state and required effect |
|---|---|---|
| Idle | Start with valid camera, RAW size, limits/storage/grant | Starting: allocate fresh generation, snapshot settings and operation ID; reject other starts/config mutations. |
| Starting | Session ready, not stopped | Acquiring: admit at most configured pending capture/writer limits. |
| Starting | Stop/failure/teardown | Draining: latch stop reason; no new admission even if session-ready arrives late. |
| Acquiring | Matching image/result for live request | Dispatch bounded write with transferred image ownership; resolve acquisition token once. Next capture only within bounds and not stopped. |
| Acquiring | Failure/timeout/cap/duration/Stop/lifecycle loss | Draining: cancel new submissions, resolve pending capture safely, retain admitted writes. |
| Draining | No live acquisitions/unmatched images/admitted writers | Packaging if at least one valid frame and policy permits partial packaging; otherwise Terminal with empty/failure/interruption reason. |
| Packaging | ZIP close/commit and optional index succeed | Terminal: complete or stopped-partial with accurate counts/path; never claim planned frame count was saved. |
| Packaging | Failure/deadline/cancel | Terminal: explicit export failure; retain committed source frames, no valid-export success. |
| Terminal | Result delivered once, owner released | Idle for new operation; cleanup complete or quarantined failed resources cannot accept work. |

Terminal result includes reason (`completed`, `userStopped`, `interrupted`, `failed`), planned/acquired/saved/failed counts, optional valid output/partial-source handles and structured error. User Stop after valid frames can be successful partial capture, never silent full completion. Stop during packaging does not close an actively owned stream from another thread; request cooperative cancellation or finish under the documented deadline.

## Ownership and pairing invariants

1. A capture token and writer count are separate. A single `pending` flag cannot represent both. State mutation is serialized or protected by one consistent synchronization strategy.
2. Join image.timestamp to `TotalCaptureResult[SENSOR_TIMESTAMP]` using operation generation/request provenance. Wall-clock time and callback arrival order are not substitutes. Metadata may arrive before or after image.
3. Unmatched maps have explicit finite counts and expiration; on duplicate, mismatch or expiry close only resources owned by that entry. Missing timestamps fail safely; no guessed nearest-frame join.
4. Every accepted capture resolves exactly once on match/failure/rejection/timeout/cancel; stale timers/callbacks cannot decrement twice or schedule a new capture. Per-frame, pairing, drain and packaging deadlines are separate finite policies, chosen/tested by implementation rather than copied from reference constants.
5. Image/reader lifetime extends through its admitted write. Close writer-owned Image in writer `finally`; do not close it early on Stop. Once writes are drained, close session/reader and stop executors. All exit paths, including normal completion, release worker threads.
6. Terminal notification is guarded independently of request callbacks. Do not publish output while a writer may still mutate it. Ignore stale-generation state changes and close late images.
7. A drain deadline cannot make unsafe force-close correct: if a blocked writer cannot be safely cancelled, surface failure, quarantine its output/owner and prevent its reuse; do not publish success or start on the same resources.

## Compressed video, still and settings

- Video: Idle → Starting → Recording → Stopping/Finalizing → Terminal → Idle. Platform finalize event determines completion; stop request alone is not success. Duplicate callbacks/Stop are idempotent. Optional pause is deferred until platform semantics/output timestamps are tested (N-C-PAUSE proves JNI calls, not continuity).
- Still: Idle → Capturing → Saving → Terminal. Distinguish rapid capture from outstanding writes and reject beyond bounded capacity. Do not permit scene/preset rebind against in-flight ownership.
- The global disruptive-change guard covers every non-idle stage, not only `isRecording`. It protects mode, camera/lens, capture session, codec/depth/quality/FPS, presets and other rebind triggers. Preview-only non-disruptive changes require an explicit whitelist.
- RAW→VIDEO at Idle chooses a supported processed-video format; never retain a RAW-only codec enum. Unsupported saved preferences normalize visibly before start.
- Process death does not restore a live camera/worker owner. Reconcile persisted incomplete records to interrupted/recoverable states; no automatic recapture or deletion.
