# File Recovery implementation brief

Continue the existing Kotlin/Compose/Hilt app without discarding previous work. Preserve the blue identity and existing architecture. Inspect relevant source only and make focused, reversible changes.

## Accepted product requirements

- Show Photos, Videos and Audio as separate categories. Each category scan must query only its matching media collection and filter extensions/MIME types. The combined quick scan shows photos/videos, publishes incremental results, supports cancellation, and does not traverse the entire device.
- Free: quick discovery and standard previews. Premium: accessible-folder deep scan, unlimited original-byte recovery, detailed previews, photo re-encoding and original-file cloud ZIP export.
- Never promise raw-sector recovery, access to other apps' private storage, reconstruction of erased originals, or removal of severe blur. Describe actual Android capabilities clearly.
- Provide a clearly marked debug-only Premium testing option so the owner can test real recovery tools without paying. Release builds must require verified purchases and must not allow that override.
- Refine Premium and Settings into a consistent, restrained blue interface with clear selection states, readable descriptions, accessible actions and working navigation. Preserve theme preferences.
- Check the emulator before device tests. Preserve its data; do not wipe it or terminate unrelated applications. Use memory-conscious build and emulator settings.

## Acceptance and handover

Run relevant JVM and billing-verifier tests, Android compilation/lint and device tests where the environment permits. Exercise category filtering, deep scan, partial results, recovery/export and Premium gates. Document actual results and any untested cases. Provide instructions for owner testing. Do not describe the app as release-ready until Play products/backend, real purchase/restore and representative-device/provider checks are completed.
