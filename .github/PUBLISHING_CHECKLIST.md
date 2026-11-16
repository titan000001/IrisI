# Before publishing

- Select intended source/docs changes explicitly. Do not use `git add .` on this
  working tree: it contains earlier experimental/reference work and local evidence.
- Keep device videos private. One retained runtime clip exceeds GitHub's 100MB
  file limit; `docs/validation/**/*.mp4` is ignored without deleting the capture.
  Publish measured results and checksums, not someone's raw camera media.
- Review validation logs/screenshots for personal media, identifiers and addresses.
  Dated historical evidence is not automatically publication-safe.
- Keep tokens, keystores, local SDK paths and extracted reference binaries out.
  Do not assume third-party artifacts can be redistributed.
- Select a license deliberately before describing the project as licensed open
  source. No license has been selected by this branding pass.
- Run the Android workflow on GitHub and inspect its reports. Workflow creation is
  not the same as an executed CI pass.
- Document tested device/configuration, unresolved capture boundaries and exact
  build identity. Do not publish the debug/CI signing key as a release key.

The first publication is a user-approved progress checkpoint directly to `main`,
not a release. No GitHub metadata update, release signing or repository-wide
staging is part of that checkpoint.
