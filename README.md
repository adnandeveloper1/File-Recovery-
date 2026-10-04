# File Recovery

Android photo and video discovery, preview and recovery-copy app built with Kotlin, Jetpack Compose, Hilt, coroutines and StateFlow. Existing theme preferences and the single-activity architecture are preserved.

## App flow

Home offers **Photos**, **Videos** and a combined **Quick scan**. Photos query only image collections; Videos query only video collections. Audio, documents and archives are excluded at classification and result boundaries. Quick scan queries MediaStore metadata without recursively walking shared storage or opening every file.

Results appear during scanning. Quick scan has a 20-second work budget; Deep scan has a 120-second budget. Users can stop early and retain partial results. Provider queries have cancellation signals. Completed and partial sessions are cached (latest three) so results survive process recreation while cache/source access remains available.

Free users can scan and inspect standard previews. Saving files, deep folder searches, detailed image previews/video playback, repair copies and cloud export require verified Premium. Every write is gated again in the repository, independently of the UI.

- **Recovery:** original-byte copies to a user-chosen writable folder, unique destination names, cancellation checks and failed-copy cleanup.
- **Deep scan:** accessible selected folders plus permitted shared media locations. Known non-media files are skipped; ambiguous cache names can be checked using small headers.
- **Repair:** re-encode decodable photo pixels into a new PNG up to 2048 px; preserve the original. Partial decoding is attempted where Android supports it.
- **Cloud export:** a ZIP containing original source bytes, saved through Android's document picker to Google Drive or another installed provider.
- **Saved:** persistent history of successful saves, repairs and exports.

## Recovery limits

Android does not expose raw erased sectors or other apps' private caches to a normal app. Results can include existing media, hidden copies and any accessible trash/cache copies. This app does not claim that existing gallery files were deleted, that unavailable originals can be reconstructed, or that re-encoding removes severe blur. File access can be revoked or a source can disappear between scan and recovery.

Deep scans require folder access and Premium. Cloud export requires a compatible installed provider. Large writes should remain in the foreground; leaving the result screen or terminating the app can cancel an operation. Partial files created by a failed operation are cleaned up where the provider permits it.

## Billing

Google Play Billing loads actual product prices and purchases. A server checks purchase state, product and expiry, then confirms acknowledgement. Premium access is held in memory and bounded to 15 minutes between checks; a local preference cannot unlock it.

The default build intentionally disables purchases until an HTTPS verifier is configured. See [billing-server/README.md](billing-server/README.md) for the service, Gradle properties and Play Console release requirements. No real purchase or production backend deployment is implied by a successful local build.

## Build and targeted validation

Use Android Studio's bundled JDK and the installed Android SDK:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1
```

Device tests cover category-specific MediaStore scanning, incremental results, persisted/partial sessions, original-byte ZIP export, PNG repair and the home category flow. Unit tests cover classification, entitlement expiry, permission/category routing and failed-recovery retry. The verifier includes subscription-state and acknowledgement tests:

```text
cd billing-server
node --test --test-isolation=none verify.test.mjs
```

On memory-constrained computers, compile before starting the emulator and use a single Gradle build at a time. Kotlin compilation runs in-process and Gradle workers are limited. The existing AGP/KSP compatibility property `android.disallowKotlinSourceSets=false` remains.

## Release verification still required

Configure active Play subscription products and deploy the HTTPS verifier with the app's Play Console service-account access. Validate real purchases/restores/refunds with licence testers, cloud export with the intended provider, and behavior on representative physical devices and Android versions before a public release. These account/device checks cannot be replaced by local unit tests.

