# File Recovery: senior review and mobile QA

Status: suitable for senior review and QA. **Not approved for public production release.** The permission and category fixes below are implemented; external release checks remain.

## Permission and category fixes

1. Photos and Videos accept Android selected-media access. Audio requires audio access for Quick Scan. A folder grant no longer incorrectly authorizes Quick Scan, which queries the media index only.
2. Deep Scan offers Photos, Videos and Audio, defaults to Photos, and preserves the chosen category. Its Start button and view model use the same permission check: matching media access or a chosen folder. Results always offer category-specific Deep Scan.
3. A denied permission request shows **Open app settings**. Folder selection reports cancellation/success; Android's picker requires **Use this folder > Allow**. Limited photo/video access offers **Update selected media**.

## Requirement status

| Requirement | Current implementation |
| --- | --- |
| Selective Photos / Videos / Audio | Separate MediaStore collections and MIME/extension filters. Quick scan does not recursively scan the device. Category-specific permission gates are shared by the UI and view model. |
| Free quick scan | Implemented with standard previews. Free saves are blocked; no limited free-recovery quota exists. |
| Premium deep scan | Reads permitted indexed media and chosen folders, including hidden files and detectable cache copies, for the selected category only. |
| Sector-level deleted-file recovery | Not implemented. This scanner reads accessible existing files; it cannot recover erased sectors or private app caches. |
| Unlimited original-quality recovery | Premium copies readable source bytes without a file-count quota. It preserves the quality that still exists, including thumbnail quality if only a thumbnail remains. |
| Advanced preview / repair | Premium photo zoom and video playback; standard audio playback. Repair creates a separate PNG from decodable image data, capped at 2048 px. It does not reconstruct missing pixels or remove blur. |
| Cloud export | ZIP output through Android's document picker; original bytes tested. Real Drive attempt was blocked by the signed-in Drive provider: “Can’t load content at the moment”, Save disabled. Remote upload remains unverified. |

## Test on a mobile phone

1. Install `app/build/outputs/apk/debug/app-debug.apk` on a QA phone. Transfer and open the APK, or run `adb install -r app/build/outputs/apk/debug/app-debug.apk` with USB debugging enabled.
2. Open **Settings > Recovery Plus > Test Premium features**. Enable the switch. This is debug-only test access; it does not charge or test Google Play billing.
3. Put known JPG/PNG and MP4 files in a folder such as `Download/RecoveryQA`. Include a copy of a PNG renamed to `.cache` and an unrelated `.txt` file. Keep the originals for comparison.
4. Open **Tools > Open deep scan**, choose **Photos**, then **Choose a folder**. Grant access to `RecoveryQA` with **Use this folder > Allow**, then tap **Start deep scan**. Check the ordinary image and `.cache` image are found and the text file is excluded. Repeat with **Videos** and **Audio**, including a playable MP4 and MP3/WAV. Permitted gallery files of that category may also appear.
5. Select fixture results, tap **Recover**, and choose a different destination folder. Open the copies and compare their contents with the sources. Check **Saved** history.
6. Open a photo preview and zoom; open a valid video and play it. Select one photo and use **Repair copy**; check that a separate readable PNG is created and the source is unchanged.
7. For cloud QA, install/sign in to Drive. Select results, tap **Export ZIP**, choose Drive in the system picker, and save. After sync finishes, verify from another device or Drive web that the remote ZIP exists and its extracted files open correctly. A local success message alone does not prove remote upload.
8. Test **Photos**, **Videos**, and **Audio** from Home separately, using valid playable files. Confirm unrelated categories are absent. Test full access, Android's selected-media access, repeated denial and returning from app settings. With only folder access, Quick Scan must still request category media access; Deep Scan can use the folder.
9. Turn **Test Premium features** off. Free scanning/previews should still work; recovery, Deep Scan, repair and ZIP export should require Premium. Force-stop/reopen also resets the debug entitlement.

Keep recovery/export in the foreground. Test denied/revoked permissions, cancellation, low storage, large libraries and representative older/physical devices before release.

## Validation evidence

- Latest follow-up: **23 JVM tests and 9 emulator tests passed**, zero failures/skips. Debug and instrumentation APKs rebuilt and installed. Tests cover category routing/isolation, permission gates, Deep Scan folder/cache detection, copies, ZIP output and repair.
- Manual denied-permission check: with Audio permanently denied, **Allow media access** showed the explanation and **Open app settings** opened Android app details. Restoring audio access and returning automatically started Audio Quick Scan. Original permission state was restored afterward.
- Existing local checks: 18 JVM tests and 13 verifier tests passed; debug/release lint had zero errors with 176/183 warnings. APK/AAB builds and 16 KB APK alignment passed.
- Earlier emulator run: 7 media integration tests and 1 Home flow test passed.
- This review: reran `MediaRecoveryIntegrationTest#deepFolderScanFindsCacheImagesAndCopiesOriginalBytes`; 1 passed, no skip, on API 37. It tests folder/media copies and ZIP bytes, not actual Play billing or remote cloud synchronization.
- Earlier review found missing permission/Deep Scan UI cases; the latest regression tests cover those fixes. Do not describe the app as error-free.

## Production work remaining

1. Complete physical-device regression checks for the updated permission/category flow.
2. Deploy the purchase verifier with the owner's Play service account; set `PURCHASE_VERIFICATION_URL` and activate matching monthly/yearly subscription base plans. Setup: `billing-server/README.md`.
3. Configure the owner's upload key, produce a signed release AAB and install it through a Play internal-testing track. The current AAB is unsigned; debug Premium does not exist in release builds.
4. Use Play licence testers for purchase, pending payment, restore, cancellation, expiry and refund verification.
5. Publish `privacy/index.html`, confirm publisher/contact/retention details, and submit the prepared `privacy/PLAY_DECLARATIONS.md` answers in Play Console. In-app policy is available under Settings > Privacy policy.
6. Complete the physical-device and remote cloud checks above and run the updated GitHub Actions workflow.

## Short message to send

Please test the updated File Recovery debug APK. Enable Settings > Recovery Plus > Test Premium features, then test Photos, Videos and Audio separately in Deep Scan. Choose a folder, confirm Use this folder > Allow, and tap Start deep scan. Permission/category fixes and privacy drafts are included. Public release still needs signed Play testing, live billing setup, published privacy declarations and completed device/cloud validation. Sector recovery and automatic deblurring are not provided.
