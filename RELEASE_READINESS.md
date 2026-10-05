# Release readiness review — 4 October 2026

## Previous session recovered

Reviewed the local “Make app production ready” session and `IMPLEMENTATION_BRIEF.md`. The accepted scope is Photos/Videos only, fast incremental quick scans, Premium folder scans and original-byte copies, detailed previews, bounded photo repair, cloud ZIP export, and a consistent blue interface. Existing uncommitted UI and debug-Premium changes were preserved.

## Fixes in this review

- Cache media detected from bytes now keeps its actual MIME type. Recovered cache images receive a usable extension. Header detection also handles streams that return only a few bytes per read.
- Completed folder copies enter Saved history even when the next copy is cancelled or Premium expires. Cleanup failures cannot mask the original copy failure.
- Repair and ZIP export reject a destination that is the source URI or the same document ID. Failed archives are removed and do not enter history.
- Repair checks access again before writing. Completed archive/repair history is recorded despite coroutine cancellation after the write.
- The app retains read access to exported documents where the provider supports it. Individual documents do not count as deep-scan folder grants.
- Large selections use compact integer positions in Android saved state, avoiding long file IDs that can exceed the Binder transaction limit. Legacy saved selections are still read.
- Subscription offers must match the configured monthly/yearly recurring period. Verification responses must also match the purchased product. Real billing still needs Play testing.
- Target SDK is 36. Cleartext network traffic is disabled. Android backup/device transfer includes preferences and excludes recovery history/destination metadata.
- CI now runs verifier tests, Android unit tests and debug/release lint, and builds debug/test APKs plus unsigned release APK/AAB artifacts. CI execution itself has not been triggered by this local review.

## Validation completed — 5 October 2026

Resumed the unfinished validation from the previous conversation. No additional application changes were needed.

| Check | Result |
| --- | --- |
| Android JVM tests | 16 passed, zero failures or skips, including the two final selection-restoration tests. |
| Debug and release lint | Zero errors; 175 debug and 182 release warnings remain. |
| Android builds | Debug APK, test APK, unsigned release APK and release AAB succeeded in one offline incremental Gradle run. |
| Emulator tests | All 10 passed, zero skips, on Android 17 with 16 KB memory pages. |
| APK alignment | Debug and unsigned release APKs passed `zipalign -c -P 16 4`. |
| Purchase verifier | All 13 tests passed in the preceding session; unchanged verifier tests were not rerun. |

Emulator tests cover Photos/Videos routing, incremental/partial scans, deep-folder cache-image detection, original-byte copies and ZIP export, PNG repair, source preservation, partial-output cleanup, interrupted-copy history, and enabling/revoking debug Premium. JVM tests also verify restoration of 10,000 selected files and a document-picker result arriving before UI loading finishes.

The updated debug app and test APK were installed with `adb install -r`, preserving app data. The deep-folder test used the existing `Download/RecoveryQA_20261004` folder with its persisted read/write grant and ran successfully rather than skipping. Test-created files use a unique prefix and are cleaned up afterward. Emulator data was not wiped.

Local evidence: `app/build/test-results/testDebugUnitTest/`, `app/build/reports/lint-results-debug.xml`, `app/build/reports/lint-results-release.xml`, and `build/remaining-emulator-validation.log`. Device tests ran directly through `adb shell am instrument -w -r com.nexappra.filerecovery.test/androidx.test.runner.AndroidJUnitRunner` after compilation, avoiding another Gradle build. The existing owner-testing instructions remain in `README.md`.

Cloud export follow-up: the debug app and instrumentation APK built successfully, and `MediaRecoveryIntegrationTest.deepFolderScanFindsCacheImagesAndCopiesOriginalBytes` passed. It verifies cache images export to uniquely named ZIP entries with usable `.png` extensions and byte-for-byte content. This test saves to the emulator's existing authorized test folder; a real remote Drive upload still needs to be completed through Drive's document picker.

## Required before public release

1. Deploy the HTTPS purchase verifier using the app owner's Google Cloud/Play service account. Configure `PURCHASE_VERIFICATION_URL` and activate the two matching subscription products/base plans. No live verifier URL or deployment credentials were available in this session; default builds disable real purchases.
2. Sign the release AAB using the owner's upload key and upload it to a Play internal testing track. Locally built unsigned release artifacts are not ready for store upload.
3. Test real purchase, pending payment, restore, renewal, cancellation, expiry and refund with Play licence testers. Debug Premium validates tools only; it does not validate payment.
4. Supply the public privacy policy and complete Data safety and photo/video permission declarations in Play Console. Confirm the existing support contact details. The in-app “Your data” summary is not a substitute for the public policy.
5. Validate the intended cloud provider (such as Drive), physical devices, older supported Android versions, revoked/limited media access, low storage, and large real media libraries. Local ZIP output tests cannot establish that a cloud upload completes remotely.

Public release is **not approved by this review** until these external checks are completed. Foreground recovery can be interrupted by leaving the results screen or ending the app process. Android cannot expose permanently erased sectors or other apps' private storage to this app.

## Reference requirements

- [Google Play target API requirement](https://developer.android.com/google/play/requirements/target-sdk): API 36 is required for new phone-app submissions and updates from 31 August 2026.
- [Google Play user-data requirements](https://support.google.com/googleplay/android-developer/answer/10144311).
- [Photo and video permission declarations](https://support.google.com/googleplay/android-developer/answer/14115180).
- [Verifier setup and Play test instructions](billing-server/README.md).
