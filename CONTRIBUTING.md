# Contributing to IrisI

## Before changing code

1. Read [README.md](README.md), [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) and
   [BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md).
2. Keep work small: one user-visible behavior or failure mode per change.
3. Preserve unrelated work. Do not replace signing configuration, delete local
   captures or broaden provider access to make a test pass.

## Verification

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-configuration-cache
```

Use `gradlew.bat` in Windows PowerShell. A fresh clone needs the local development
keystore described in the README. The CI key is ephemeral and must never become a
release key. No Firebase credentials are needed for the offline capture workflow.

Add a regression test for changed behavior. For hardware paths, record the device,
OS, requested settings, **measured output**, build hash and limitations. Device
recording tests use explicit opt-ins and create media; do not run them on someone
else's device without permission. Never delete a user's media to clean up a run.

## Pull requests

- Explain what works now that did not work before.
- Include executed checks; distinguish failures, skips and checks not run.
- Keep capture labels honest: preview looks are not recorded vendor LOG; a DNG
  sequence archive is not a playable RAW video.
- Redact personal media, device identifiers, addresses and secrets from reports.
- Do not commit keystores, tokens, `.env`, SDK paths, build outputs or reference APKs.

The maintainer has not selected a repository license yet. Ask before submitting
third-party code or assets with licensing requirements.
