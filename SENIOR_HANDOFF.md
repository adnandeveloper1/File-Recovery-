# File Recovery: senior review and mobile QA

Status: suitable for senior review and QA. **Not approved for public production release.** This review changed documentation only; the two app issues below remain unresolved.

## Findings to fix

1. **Selected-photo permission blocks Photos scanning.** In `FullDeviceScanViewModel.kt`, `hasAccess()` checks only `fullImagesAccess` for Photos, while the screen accepts `partialVisualAccess`. On Android 14+, choose selected-photo access without full library access: the Photos scan cannot start. Include partial visual access in the Photos gate and test that path.
2. **Deep Scan category flow is incomplete.** Home/Tools launch Deep Scan with no category; `MediaScanPolicy` then scans photos/videos only. The Deep Scan screen also advertises audio. The category-specific Deep Scan action is only displayed when visible results are empty. Provide an explicit category choice or an always-available category-specific action, and align the default scope with its description.

## Requirement status

| Requirement | Current implementation |
| --- | --- |
| Selective Photos / Videos / Audio | Separate MediaStore collections and MIME/extension filters. Quick scan does not recursively scan the device. Photos has the permission issue above. |
| Free quick scan | Implemented with standard previews. Free saves are blocked; no limited free-recovery quota exists. |
| Premium deep scan | Reads accessible selected folders, including hidden files and detectable cache copies. Folder integration test passes. Category UI needs the fix above. |
| Sector-level deleted-file recovery | Not implemented. This scanner reads accessible existing files; it cannot recover erased sectors or private app caches. |
| Unlimited original-quality recovery | Premium copies readable source bytes without a file-count quota. It preserves the quality that still exists, including thumbnail quality if only a thumbnail remains. |
| Advanced preview / repair | Premium photo zoom and video playback; standard audio playback. Repair creates a separate PNG from decodable image data, capped at 2048 px. It does not reconstruct missing pixels or remove blur. |
| Cloud export | ZIP output through Android's document picker. Local ZIP bytes are tested; a real remote Drive upload is still unverified. |

## Test on a mobile phone

1. Install `app/build/outputs/apk/debug/app-debug.apk` on a QA phone. Transfer and open the APK, or run `adb install -r app/build/outputs/apk/debug/app-debug.apk` with USB debugging enabled.
2. Open **Settings > Recovery Plus > Test Premium features**. Enable the switch. This is debug-only test access; it does not charge or test Google Play billing.
3. Put known JPG/PNG and MP4 files in a folder such as `Download/RecoveryQA`. Include a copy of a PNG renamed to `.cache` and an unrelated `.txt` file. Keep the originals for comparison.
4. Open **Home > Deep scan > Choose a folder**. Grant access to `RecoveryQA`, then tap **Start deep scan**. Check the ordinary image and `.cache` image are found and the text file is excluded. Deep Scan also checks permitted indexed media, so additional gallery results are expected. Home's default Deep Scan currently covers photos/videos.
5. Select fixture results, tap **Recover**, and choose a different destination folder. Open the copies and compare their contents with the sources. Check **Saved** history.
6. Open a photo preview and zoom; open a valid video and play it. Select one photo and use **Repair copy**; check that a separate readable PNG is created and the source is unchanged.
7. For cloud QA, install/sign in to Drive. Select results, tap **Export ZIP**, choose Drive in the system picker, and save. After sync finishes, verify from another device or Drive web that the remote ZIP exists and its extracted files open correctly. A local success message alone does not prove remote upload.
8. Test **Photos**, **Videos**, and **Audio** from Home separately, using valid playable files. Confirm unrelated categories are absent. Test full access and Android's selected-photo access; the latter currently exposes finding 1.
9. Turn **Test Premium features** off. Free scanning/previews should still work; recovery, Deep Scan, repair and ZIP export should require Premium. Force-stop/reopen also resets the debug entitlement.

Keep recovery/export in the foreground. Test denied/revoked permissions, cancellation, low storage, large libraries and representative older/physical devices before release.

## Validation evidence

- Existing local checks: 18 JVM tests and 13 verifier tests passed; debug/release lint had zero errors with 176/183 warnings. APK/AAB builds and 16 KB APK alignment passed.
- Earlier emulator run: 7 media integration tests and 1 Home flow test passed.
- This review: reran `MediaRecoveryIntegrationTest#deepFolderScanFindsCacheImagesAndCopiesOriginalBytes`; 1 passed, no skip, on API 37. It tests folder/media copies and ZIP bytes, not actual Play billing or remote cloud synchronization.
- Review found missing permission/Deep Scan UI cases despite those passing tests. Do not describe the app as error-free.

## Production work remaining

1. Fix the two findings and add targeted regression/UI checks.
2. Deploy the purchase verifier with the owner's Play service account; set `PURCHASE_VERIFICATION_URL` and activate matching monthly/yearly subscription base plans. Setup: `billing-server/README.md`.
3. Configure the owner's upload key, produce a signed release AAB and install it through a Play internal-testing track. The current AAB is unsigned; debug Premium does not exist in release builds.
4. Use Play licence testers for purchase, pending payment, restore, cancellation, expiry and refund verification.
5. Publish the privacy policy, complete Data safety/media permission declarations, and confirm support contacts.
6. Complete the physical-device and remote cloud checks above and run the updated GitHub Actions workflow.

## Short message to send

Please review File Recovery as a QA build. Folder Deep Scan and original-copy/ZIP tests pass, but selected-photo permission handling and the Deep Scan category flow need fixes. Premium is testable in the debug APK via Settings > Recovery Plus > Test Premium features. Sector recovery and automatic deblurring are not provided. Public release still needs signed Play testing, the live billing verifier/products, privacy declarations and real-device/Drive validation.
