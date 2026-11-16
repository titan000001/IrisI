# IrisI LOG v1 mathematical contract

This original curve is not S-Log, C-Log, V-Log or a claim of additional sensor
stops. It is a recording-pipeline building block, not currently a recording mode.

For each normalized linear RGB component `x` in `[0,1]`:

```text
y = ln(1 + 31*x) / ln(32)
x = (exp(y*ln(32)) - 1) / 31
```

Use the inverse only after unpacking signal range and reconstructing RGB from
YUV with the declared matrix. The curve does not prescribe a gamut conversion.
Record the actual primaries, white point, range, source and pipeline version.
Reject nonfinite and out-of-domain input; upstream highlight normalization or
clipping policy must be explicit, not hidden in this function.

RGB quantization uses nearest integer with endpoints:

| Precision | Full range | Limited RGB range |
|---|---|---|
| 8-bit | 0–255 | 16–235 |
| 10-bit | 0–1023 | 64–940 |

These are RGB signal bounds, **not a P010 or chroma packing specification**.
Quantization happens after transfer; do not truncate the 10-bit route through
an 8-bit intermediate. 8-bit has larger rounding steps and higher banding risk,
but remains an explicit supported target for future pipeline implementation.

The 64-point experimental ISP curve samples evenly in encoded space and stores
inverse-mapped linear abscissae. Unit tests bound its interpolation error. An ISP
request is not proof of the executed transfer: capture-result curve agreement,
disabled conflicting processing and decoded ramp/patch measurements are required
before enabling a recording option. ISP clipping cannot be undone by this curve.

Do not tag custom-LOG samples as conformant Rec.709, HLG or PQ. An owned encoder
route must support appropriate unspecified/custom transfer signaling and supply
a grading sidecar or LUT. CameraX Recorder does not currently expose that control
in this app, so wiring only an ISP request to it would be premature.
