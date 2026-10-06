# File Recovery

Android photo, video and audio discovery, preview and recovery-copy app built with Kotlin, Jetpack Compose, Hilt, coroutines and StateFlow. Existing theme preferences and the single-activity architecture are preserved.

## App flow

Home offers separate **Photos**, **Videos** and **Audio** scans plus a combined **Quick scan**. Each category queries only its matching MediaStore collection and file types. Documents and archives are excluded. The combined quick scan checks photos and videos. Scans query MediaStore metadata without recursively walking shared storage or opening every file.

Results appear during scanning. Quick scan has a 20-second work budget; Deep scan has a 120-second budget. Users can stop early and retain partial results. Provider queries have cancellation signals. Completed and partial sessions are cached (latest three) so results survive process recreation while cache/source access remains available.

Free users can scan and inspect standard photo/video previews and play audio previews. Saving files, deep folder searches, detailed image previews/video playback, repair copies and cloud export require verified Premium. Every write is gated again in the repository, independently of the UI.

- **Recovery:** original-byte copies to a user-chosen writable folder, unique destination names, cancellation checks and failed-copy cleanup.
- **Deep scan:** accessible selected folders plus permitted shared media locations. Known non-media files are skipped; ambiguous cache names can be checked using small headers.
- **Repair:** re-encode decodable photo pixels into a new PNG up to 2048 px; preserve the original. Partial decoding is attempted where Android supports it.
- **Cloud export:** a ZIP containing original source bytes, saved through Android's document picker to Google Drive or another installed provider.
- **Saved:** persistent history of successful saves, repairs and exports.

Header-detected cache images retain their actual MIME type, and recovered copies and ZIP entries receive a usable image extension. Completed folder copies are recorded even if a later copy is interrupted. Repair/export reject the source document as a destination. Saved export access is retained when the chosen provider supports persistent permissions. Android backups include preferences only, excluding recovery history and file destinations.

## Recovery limits

Android does not expose raw erased sectors or other apps' private caches to a normal app. Results can include existing media, hidden copies and any accessible trash/cache copies. This app does not claim that existing gallery files were deleted, that unavailable originals can be reconstructed, or that re-encoding removes severe blur. File access can be revoked or a source can disappear between scan and recovery.

Deep scans require Premium and either permission for the selected media category or access to a chosen folder. Choose a folder to include its hidden/cache copies. Denied media permissions show an app-settings fallback; cancelling the folder picker shows how to grant access. Cloud export requires a compatible installed provider. Large writes should remain in the foreground; leaving the result screen or terminating the app can cancel an operation. Partial files created by a failed operation are cleaned up where the provider permits it.

## Billing

Google Play Billing loads actual product prices and purchases. A server checks purchase state, product and expiry, then confirms acknowledgement. Purchased Premium access is held in memory and bounded to 15 minutes between checks; a local preference cannot unlock it.

Debug builds have a clearly labelled **Test Premium features** switch on the Premium screen. It enables the real recovery tools without creating a purchase, remains in memory only, and resets on process restart. Turning it off restores the actual purchase entitlement. Both the switch and the entitlement override are guarded by `BuildConfig.DEBUG`; release builds cannot enable test access.

## Test Premium yourself

1. Install the debug APK and open **Get Plus** (or **Settings → Recovery Plus**).
2. Turn on **Test Premium features**, then use Back to return home.
3. Open **Deep scan**, select **Photos**, **Videos** or **Audio**, then grant matching media access or **Choose a folder** containing your test files. In Android's picker confirm **Use this folder > Allow**, return and tap **Start deep scan**. Folder scanning works without media-library permission. Private app directories and erased sectors are unavailable.
4. Select results and use **Recover**, **Cloud export**, or **Repair copy** (one photo selected). Pick a separate destination folder for recovery. Cloud export needs an installed provider such as Drive.
5. Tap a thumbnail for a detailed photo preview or video playback. Check **Saved** for completed operations.
6. Turn test access off to check Premium locks. A force-stop/restart also resets test access. This test mode does not validate Google Play payment, renewal or restore.

For deep-search QA, `Download/RecoveryQA_20261004` on the test emulator contains generated media fixtures, including a PNG with a `.cache` filename and an unrelated text file. The folder's `.nomedia` file keeps the fixtures out of ordinary gallery indexing; folder scanning should find the readable media while skipping the text file.

The default build intentionally disables purchases until an HTTPS verifier is configured. See [billing-server/README.md](billing-server/README.md) for the service, Gradle properties and Play Console release requirements. No real purchase or production backend deployment is implied by a successful local build.

## Build and targeted validation

Use Android Studio's bundled JDK and the installed Android SDK:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease :app:assembleRelease :app:bundleRelease --no-daemon --max-workers=1
```

Device tests cover category-specific MediaStore scanning, incremental results, persisted/partial sessions, original-byte ZIP export, PNG repair and the home category flow. Unit tests cover classification, entitlement expiry, permission/category routing and failed-recovery retry. The verifier includes subscription-state and acknowledgement tests:

The deep-folder integration test needs **read and write** access to `Download/RecoveryQA_20261004`: select that QA folder once as a recovery destination through the app. The test creates and cleans up only files with its unique test prefix. The test is skipped if the grant is missing. Run connected tests after compilation with `:app:connectedDebugAndroidTest --no-daemon --max-workers=1`.

```text
cd billing-server
node --test --test-isolation=none verify.test.mjs
```

On memory-constrained computers, compile before starting the emulator and use a single Gradle build at a time. Kotlin compilation runs in-process and Gradle workers are limited. The existing AGP/KSP compatibility property `android.disallowKotlinSourceSets=false` remains.

## Release verification still required

Configure active Play subscription products and deploy the HTTPS verifier with the app's Play Console service-account access. Validate real purchases/restores/refunds with licence testers, cloud export with the intended provider, and behavior on representative physical devices and Android versions before a public release. These account/device checks cannot be replaced by local unit tests.

See [the release review](RELEASE_READINESS.md) for the fixes, validation evidence and remaining release requirements.
