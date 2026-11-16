# Known limitations

## Not full reference parity

The three static specifications are partial feature inventories. Native processing, proprietary formats and many controls remain unresolved. `PRODUCT_REQUIREMENTS.md` IR17–IR23 retains those differences; they are not silently dropped or marked implemented. No reference camera workflow was reproduced: Mereframe installation hit an existing signature conflict, while RawCam/MotionCam reached licensing/Play routing. No bypass or data reset was attempted. See `docs/reverse-engineering/REFERENCE_RUNTIME_RESULTS.md`.

Historical report citations are not all portable: the synthesis supplement records exact missing-payload counts. New retained observations supersede selected facts only. No measured reference reliability, quality, battery, thermal, performance or accessibility winner can be selected from static code.

## IrisI capture boundaries

- Experimental IrisI LOG 8-bit is now connected to recorder input using a GPU
  effect on ISP SDR frames, with documented inverse-Rec.709/LOG processing,
  unspecified AVC transfer signaling and private grading sidecars. Two short
  repeated wireless Pixel recordings passed decoded contrast, signaling, cadence
  and track endpoint checks. This does not restore ISP-clipped highlights or prove
  color accuracy, perceptual AV sync, source dataspace, interruption or sustained
  reliability. The media library can now list/share complete retained originals
  after a failed finalization; this does not repair transfer tags or recover an
  interrupted encoder. Partial backups are never offered. 10-bit LOG and PQ remain unimplemented. Named vendor LOG monitor
  profiles are still not verified recorded vendor LOG. See
  `docs/validation/LOG-COMMIT-WIRELESS-20261116.md`.
- A synthetic 640×360 GPU-to-hardware-encoder ramp test passed on Pixel AVC and
  HEVC: 48 frames, EOS, and five decoded LOG patch checks within 0.2%. The Android
  encoder forced SDR/Rec.709 transfer metadata over the custom-LOG request. This
  was not a camera recorder at that checkpoint; subsequent camera/AVC signaling
  work is recorded above. The HEVC custom LOG and 10-bit routes remain open.
- Two short repeated HLG clips finalized and decoded at 1080p on the target Pixel
  with HEVC Main10 / BT.2020 / HLG tags. This is not colorimetric, highlight-range,
  lifecycle or sustained thermal validation. SPS inspection confirms 10-bit
  luma/chroma and an AAC track, but MediaMetadataRetriever's sampled display RGB
  was nearly black (8-bit luma max 2–3). HLG is therefore not visually validated;
  investigate HDR decode/display and source exposure before shipping it. The
  camera advertises no direct PQ capture; conversion remains separate work.

- RAW is experimental **sequential automatic-exposure DNG still capture**, up to 60 frames / 10 seconds, requested rate capped at 10 fps. Hardware size selection and actual cadence can differ. It has no RAW audio synchronization, proprietary compression/container compatibility, real-time video guarantee or background service.
- DNG archives are private ZIPs with a restricted grantable content URI for sharing. They are not playable videos, although the existing catalog still groups them with video-like entries.
- LOG/tone profiles affect preview monitoring; there is no validated named recorded-LOG transfer/gamut pipeline. Ordinary MP4 codec selection remains CameraX/device-controlled. HLG requires a specifically reported HLG range and successful use-case binding; physical output still needs validation.
- 120 fps is not implemented; its UI choice is disabled. Other rates/resolutions remain requests. OpenGate, binning, stabilization crop, battery and thermal claims are not established sensor behavior.
- Emulator smoke test demonstrated an actual fallback: 4K UHD/24p/100 Mbps requested UI produced a MediaStore-reported 720×1280 MP4. The settings explanation discloses fallback, but the top HUD is still requested configuration, not a measured effective profile. This discrepancy remains open.
- A later Pixel 7 Pro clip also contained 1920×1080 AVC at approximately 24 fps despite a 4K HUD request. The continuation adds a single-quality binding and dimension guard, plus visible failure advice. This early guard does not establish encoded UHD compliance; see `docs/validation/RESUME-20261116.md` for its separate validation status. That retained physical clip contains AAC audio, but its quality/synchronization was not tested.
- Continuation validation did produce 3840×2160 AVC at approximately 24.01 fps on the Pixel, after retrying without live analysis overlays. The warning is visible; scopes/peaking are not available in that configuration. This is one measured successful configuration, not a universal UHD/cadence/bitrate guarantee or a decoded color/audio-quality pass.
- RAW stop waits for an admitted disk write to finish before closing its reader. Stalled storage cannot be safely turned into a forced successful finalization. `.part` files remain unpublished; completed originals are retained after failure.
- No durable recording foreground service, process-death recovery queue, guaranteed continued background capture, timelapse encoder, calibrated LUT/DCP pipeline, external clean display, advanced audio DSP or external secure-capture contract was added by this patch.

## Verification boundaries

Host state-machine and Robolectric tests establish only the tested software paths. Android FileProvider tests belong to the device lane because Android's canonical-path containment assumes Linux separators; Windows Robolectric cannot faithfully exercise that implementation. The initial host failure is retained in the test history, not hidden with a mock.

Physical-device RAW/DNG validity, HDR/LOG metadata, encoder MIME/profile, audio synchronization, long-running capture, low storage, all lifecycle races, rotation/process death, TalkBack/large fonts, manual sensor compliance and complete unique-feature preservation require further implementation or device tests. `TEST_RESULTS.md` separates executed outcomes from these gaps. A successful debug APK is not a production-readiness or all-feature-completion certificate.
