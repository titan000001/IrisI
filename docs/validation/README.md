# Validation evidence

Results are dated snapshots of a particular build, device and test path. Start
with [TEST_RESULTS.md](../../TEST_RESULTS.md) and
[KNOWN_LIMITATIONS.md](../../KNOWN_LIMITATIONS.md) for the overall boundaries.

## Camera milestones

- [Branding, system theming and retained LOG recovery](BRANDING-THEME-RECOVERY-20261116.md)
  — implementation details, verification history and recovery boundaries; consult
  the report for final check status.
- [Recorded LOG: commit safety and repeated wireless Pixel capture](LOG-COMMIT-WIRELESS-20261116.md)
  — 43 host tests at that checkpoint, two five-second LOG recordings, custom
  signaling/sidecars, decoded contrast and track timing; not colorimetry or lifecycle proof.
- [Restart continuation](RESUME-20261116.md)
  — isolated-build identity, physical-device observations and effective-output checks.

Raw captures, reference-analysis payloads, and historical device logs remain local
and are deliberately excluded from the GitHub progress checkpoint. Paths to those
artifacts in dated reports describe local evidence, not downloadable repository
files. Authored reports and reproducible app tests are included. Do not infer
that a current local APK has an older report's checksum, or that an emulator pass
establishes real-camera quality.
