# Screens and navigation contract

Original logical surfaces for existing Compose/sheet navigation; not copied layouts or a demand for new Activities. This is a **target**, not witnessed IrisI/reference UI. [Requirements](PRODUCT_REQUIREMENTS.md) and [traceability](FEATURE_TRACEABILITY_MATRIX.md) supply scope/evidence. Reference screen/navigation coverage is incomplete.

| ID / surface | Inputs and actions | Outputs / guards | Requirements |
|---|---|---|---|
| UI01 Viewfinder | Mode/lens, valid exposure/WB/focus, settings/library/preset affordances, shutter | Effective configuration, permission/capability notice, elapsed and acquired/saved counts. Shutter Start when idle, Stop when acquiring, Finalizing while draining/packaging. | IR01–IR07, IR09–IR11 |
| UI02 Capture settings | Mode-specific requested values and explanatory text | Actual active tuple, supported ranges and disabled reasons. Bind/preset changes blocked during active capture/finalize. Preview-only color clearly separated from output processing. | IR06/IR07/IR14/IR16 |
| UI03 Media library/review | Scoped owned-item identity, selection, inspect/develop/delete | Supported preview or explicit unsupported format; sequence item is not a playable video. Deletion confirm includes target and cannot delete active work. | IR08/IR12/IR15 |
| UI04 Development | Valid source handle and independent parameters, start/cancel | Progress, completion/failure and destination; preserve original. Do not invent reference slider meanings. | IR15/IR17 |
| UI05 Presets/HUD | Existing preset/layout data, edit/apply/save | Validate before applying; capture-active rejection is atomic. Original labels and accessible reorder/edit alternatives. | IR06/IR15 |
| UI06 Status/recovery | Structured result/error and optional valid partial media | Explanation, retry after terminal cleanup, inspect partial output, choose storage where implemented; no false Saved status. | IR08–IR11 |

## Routing rules

- Launcher → UI01 permission/capability gate. Lack of RAW support still permits normal supported modes; lack of camera permission exposes recovery rather than a frozen black viewfinder.
- Idle UI01 ↔ UI02/UI03/UI05. UI03 → review or UI04 only for supported valid items. Missing media returns a nonfatal error, not crash/navigation loop.
- Opening a sheet is not authorization to mutate active capture. During acquisition/finalization, preserve reachable Stop and reject disruptive changes at the backend as well as in UI. If a modal blocks Stop, dismiss it or include a Stop action.
- Back dismisses top sheet/dialog first; Back leaving active capture triggers explicit stop/leave handling. Lifecycle teardown must not rely on a confirmation dialog being shown.
- Stop immediately changes text/state to Stopping/Finalizing; repeated Stop does nothing additional. Do not let shutter become Start until terminal state is delivered.
- Rotate/recreate: observe authoritative operation state; never duplicate start/finalize. Resumed process with no live owner reports interrupted work, not active recording.
- External/secure/QS/shortcut routes are IR23 backlog until explicit caller-output and lockscreen privacy contracts exist. Do not register proprietary schemes, auto-capture unexpectedly or expose library in secure mode without a deliberate design.

## Accessible behavior and expansion

Independent names for every icon, semantic toggle values, non-colour status, focusable error/remedy and minimum 48 dp capture target. Keep Stop reachable at large fonts and after rotation. Announce terminal result without continuously announcing every frame. Physical volume capture is optional; if added, consume repeat events and apply the same backend guards (N-B-KEY), including Stop semantics.

Advanced UI is **not implemented by this document**: lens/profile/LUT managers, job queue, diagnostic/install/help/purchase and script/SCREEN surfaces remain IR19–IR23. Every future screen needs valid inputs, empty/error states and actual action handlers before exposure; placeholder controls are not feature completion.
