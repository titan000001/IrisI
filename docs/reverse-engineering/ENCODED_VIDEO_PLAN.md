# Encoded video direction — 2026-11-16

## Scope correction

The user redirected recording work away from DNG sequencing toward reliable
encoded LOG, SDR and HDR. **8-bit LOG is explicitly required as an option**,
not discarded when 10-bit is unavailable. Preserve existing DNG work/data, but
do not treat it as RAW-video or LOG delivery. Do not remove working capture modes.

This document is an implementation/verification plan, **not a claim that the
following options already record in IrisI**. The current LOG monitoring matrix
does not modify recorder input.

## Independent recording controls

Keep these independent rather than adding misleading combined codec labels:

- Codec: AVC/H.264, HEVC/H.265, AV1 when a suitable encoder exists. Investigate
  other reference codecs separately; a reference enum does not supply a codec
  implementation or make it practical on this phone.
- Precision: 8-bit and 10-bit. Keep 8-bit LOG selectable with a supported pipeline;
  warn about quantization, banding, and aggressive grading. No AVC 10-bit option
  without an actually supported implementation/profile.
- Transfer: SDR/Rec.709, documented IrisI LOG, HLG, PQ/ST 2084. Vendor LOG names
  require verified equations, gamut, normalization and output tests, not a
  similar-looking flat preview. PQ is not a codec; HLG is not sensor RAW.
- Gamut and range: BT.709/BT.2020 (or a specifically implemented camera-native
  transform), full/limited, with explicit conversion and consistent metadata.
- Source: ISP SDR, ISP HDR, or RAW-fed development. Save the effective source;
  applying LOG after SDR clipping does not restore sensor highlight information.

Unsupported combinations stay visible with reasons where useful. Do not silently
change codec, precision, transfer or resolution. Offer an explicit alternative.
An advanced override cannot invent a camera/encoder capability.

## Pixel declarations, not recording proof

Physical device: Pixel 7 Pro, serial `2B081FDH300H1F`, SDK 37. The read-only probe
passed on 2026-11-16. Original output:
[`encoded-video-capabilities.json`](../validation/runtime-2B081FDH300H1F/encoded-video-capabilities.json),
with [instrumentation log](../validation/runtime-2B081FDH300H1F/encoded-capabilities-instrumentation.txt).

- CameraX cameras 0 and 1 advertise SDR and HLG10. Back camera Recorder qualities
  include UHD/FHD/HD/SD; front includes FHD/HD/SD. These are per-range declarations,
  not proof all frame rates/stabilization/analysis combinations work.
- Neither camera/Recorder advertises HDR10 PQ or HDR10+. **Do not offer direct
  native PQ capture on this result.** A device supporting HDR10 recording can
  use its genuine path; this Pixel needs an independently implemented conversion
  route, e.g. HLG decode/linearize, declared display rendering/luminance policy,
  PQ encoding and matching BT.2020/range metadata. Changing tags is not conversion.
- Hardware `c2.exynos.h264.encoder` and `c2.exynos.hevc.encoder` report the tested
  FHD/UHD 24/30/60 size/rate combinations. HEVC advertises Main/Main10 and HDR
  profiles. This alone does not prove end-to-end HDR camera input.
- `c2.android.av1.encoder` is software-only and reports no support for tested
  FHD/UHD 24/30/60. Do not advertise real-time AV1 at those settings on this phone.
- Both cameras advertise contrast-curve tonemapping and 64 curve points. This
  makes an ISP-fed 8-bit LOG experiment plausible, **not verified LOG support**.
  Inspect actual capture-result curves and decoded output. Camera2 tone curves
  are applied ISP requests, unlike the current preview-only matrix; they still
  do not establish RAW-fed color fidelity or a universal usable dynamic range.

## Reference-grounded routes

1. RawCam establishes hardware encoder screening, separation of requested and
   actual codec, HLG/10-bit reconciliation and output probing. It does not prove
   a LOG pipeline: [RawCam evidence](RAWCAM.md), [follow-up](APP_B_LOGIC_AUDIT.md).
2. Mereframe exposes a RAW-aware GPU renderer feeding MediaCodec, high-precision
   paths and an 8-bit terminal fallback. Newly followed renderer links are in
   [A renderer findings](ENCODED_LOG_A_RENDER_FINDINGS.md). Exact shader equations
   and profile conformance remain unresolved; do not copy encrypted shaders.
3. MotionCam explicitly separates codec, precision, transfer, gamut and transport.
   The completed [encoded LOG native investigation](ENCODED_LOG_REFERENCE_FINDINGS.md)
   retains 23 portable records connecting RAW-fed development to encoder rendering,
   8/10-bit declarations and HDR transfer operations. Shader quantization and
   effective file signaling remain unresolved. A class called `HEVCLogEncoder`
   does not by itself prove the encoded curve.

## Latest independent implementation and device results

- Two consecutive FHD24 HLG recordings finalized and decoded on the target Pixel.
  Both report HEVC Main10, BT.2020, HLG and limited range. See
  [short HLG report](../validation/runtime-2B081FDH300H1F/encoded-hlg-smoke.json).
  Follow-up SPS inspection confirmed 10-bit luma/chroma, ~24 fps and an AAC track.
  The Android metadata retriever returned nearly-black display RGB for sampled
  frames (8-bit luma max 2–3), so this is **not visual HLG validation**. The
  cause may be decoder/display HDR handling, source exposure, or the capture
  path; it must be isolated before claiming usable HLG footage.
- `IrisLogTransfer` defines an original normalized curve and inverse with separate
  8/10-bit full/limited RGB quantization. It is a mathematical building block,
  **not yet wired to recording or a user-facing recorded-LOG option**.
- A Pixel-only synthetic surface test then rendered 48-frame ramps through the
  original curve into hardware AVC and HEVC. Both reached the file and decoded
  five sampled patches within 0.2%. The platform encoder changed custom transfer
  metadata to SDR/Rec.709, so this proves the pixel transform only. It does not
  yet provide an honest custom-LOG file contract or camera/audio integration.
- `HevcSequenceInfo` reads SPS precision and cropped geometry independently of
  MediaFormat profile labels. The extended HLG test checks actual SPS depth.

## Delivery gates

1. Establish reliable hardware SDR and HLG recordings, with effective settings.
   Query Recorder qualities for the chosen camera **and dynamic range**. Retry
   without analysis if necessary; do not silently downgrade the output.
2. Select the encoder through supported API/owned MediaCodec integration, not
   reflection or a private CameraX factory. CameraX 1.6.1's cached Recorder source
   has a package-private encoder-factory setter, not a supported public codec
   selection API. A direct pipeline must own input, EOS, audio sync and muxing.
3. Implement a real pre-encoder transfer for 8-bit LOG, with documented source,
   curve, inverse/LUT and quantization. Treat ISP-curve and GPU/RAW-fed approaches
   as different capability tiers. No preview-matrix substitute. Confirm the
   effective camera curve when using ISP controls; reject ignored requests.
4. Preserve sufficient precision end-to-end for 10-bit LOG and HDR conversions:
   camera/input transport, GPU targets or P010 planes, encoder profile and mux.
   Encoding an 8-bit intermediate as Main10 is not 10-bit acquisition.
5. Verify generated files: MIME, SPS/bitstream precision, color standard/transfer/
   range, decoded nonblank frames, duration/cadence, A/V sync, stop/EOS, repeat
   recording, interruption, storage failures and sustained thermal behavior.
   Metadata checks alone are not colorimetry or reliable-recording proof.
6. For custom LOG, use a known transfer identity and grading metadata/sidecar;
   never relabel custom-LOG samples as conformant Rec.709/HLG/PQ. Test round trips
   with ramps/patches before device-scene claims; include 8-bit banding tests.

`VideoOutputInspectionTest` now supports opt-in expected MIME/color/profile/size
assertions and records missing color tags as unknown. The capability test does
not open a camera, record or alter media. Both are evidence tools, not new
user-facing recording options.
