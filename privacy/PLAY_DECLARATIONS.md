# File Recovery — Play Console privacy and permission declarations

Prepared from this repository on 5 October 2026. These are release-preparation answers, **not a submitted Play Console declaration**. The publisher/contact and deployed infrastructure settings must be confirmed before publishing. No public policy URL or Play Console session was supplied.

## Privacy policy

- In-app: Settings > Privacy policy, with the full text in `app/src/main/res/raw/privacy_policy.txt`.
- Public web version: `privacy/index.html`. Publish it on the owner's website over HTTPS, accessible without login and without geographic restrictions; enter the final URL in Play Console > App content > Privacy policy.
- Current privacy contact is the existing app support address: `info@nexappra.com`. Confirm ownership/monitoring and the publisher name before release.
- Finish the hosting-log retention and support-correspondence retention decisions before public release. The policy deliberately does not claim logs are disabled or invent a retention duration. If infrastructure or SDK behavior differs, update both the public and in-app policy before submitting.

## Data safety response worksheet

Use the configuration of the production release, including its verification backend and dependencies. Do not choose an unconditional “no data collected” for a paid release that sends purchase tokens off device.

| Data / feature | Code evidence and proposed declaration |
| --- | --- |
| Photos and videos used for on-device scanning/preview/repair | Accessed locally. Local-only processing is outside the collection definition; media permission declarations are still required. |
| Audio recordings, music and other audio | Accessed locally for the selected category and playback. No microphone permission or recording feature. Apply the same local-processing distinction. |
| User-selected cloud exports, including media and filenames | Optional; purpose: app functionality. The ZIP is transferred via the explicitly selected provider and never to the verifier. Conservatively disclose these data types as collected for optional export when completing the form. Assess Google's user-initiated transfer exception separately for **sharing**; that exception does not automatically remove collection disclosure. Do not mark a cloud-retained ZIP as ephemeral. |
| Purchase history / purchase identifiers | Collected when Premium is used: the app sends a purchase token to the verifier, which queries product, state and expiry. Purpose: app functionality and fraud prevention/security. Optional relative to free scanning. Ephemeral only if the deployed service and proxies really keep the token/entitlement in memory for the request and do not log/store it. |
| Payment-card or bank details | Not received by this app or verifier. Checkout is handled by Google Play. Recheck the Play Billing SDK disclosure for the exact shipped dependency. |
| IP addresses / infrastructure request logs | Verify hosting/gateway configuration. Do not claim zero collection without checking request logs, retention and whether IPs are used for location or identifiers. The current app code does not derive location or send an installation/advertising ID. |
| Support messages | Email/WhatsApp open external apps. Disclose support correspondence in the privacy policy; evaluate any publisher-side collection separately. No in-app contact form, address book upload or chat SDK is implemented. |
| Advertising / tracking / analytics | No such SDK integration found in the current app dependencies. Re-audit if dependencies change. |

Additional form answers:

- **Encryption in transit:** the verifier only accepts an HTTPS endpoint in the app. Validate the deployed TLS endpoint and chosen cloud provider before confirming encryption for every declared transfer.
- **Sharing:** service-provider and explicit user-initiated transfers can qualify for exceptions; determine this from the actual service relationship and export flow. Do not label the app as selling data; the code has no data-sale mechanism.
- **Account creation:** the app does not create its own account. Google Play and cloud-provider accounts belong to their respective services. Do not promise deletion of those external accounts through this app.
- **Deletion:** clearing Android app storage removes local settings/metadata/history; exported files must be deleted at the chosen destination. Contact is provided for privacy/support requests. Confirm backend log handling and a monitored deletion-request process before selecting a corresponding declaration.
- **Independent security review:** no independent certification was performed; do not claim one.

## Photo/video permissions declaration draft

READ_MEDIA_IMAGES:
“File Recovery's core function is user-initiated discovery and preview of accessible image files so the user can select files to copy to a destination. The Photos scan queries only the image collection and applies image MIME/extension filters. Library discovery needs to enumerate accessible candidates before the user knows which files to select. Android selected-photo access is supported; the app does not bypass the granted scope.”

READ_MEDIA_VIDEO:
“File Recovery provides a separate user-initiated video discovery and preview flow. It queries only the video collection and applies video MIME/extension filters. Users select discovered videos for original-byte copying. The permission is used for the app's core media discovery functionality, not advertising or background data collection.”

Submit a demonstration recording showing Home > Photos / Videos, Android permission choices, category-limited results, preview and an explicitly chosen recovery destination. Also show Settings > Privacy policy. These explanations are submission drafts; they do not guarantee Google Play approval. If Play requires a picker-only model, the implementation and store claims must change accordingly.

READ_MEDIA_AUDIO is requested only for Audio quick scanning. Deep Scan uses user-selected folder grants; MANAGE_EXTERNAL_STORAGE is not declared. The app does not claim raw-sector recovery, access to private app storage, restoration of erased originals, or automatic deblurring.

## Sources checked

- [Google Play User Data and privacy-policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play Data safety definitions and form guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Photo/video and sensitive-permission requirements](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)

The worksheet is an interpretation of the source code and these definitions. Final submission depends on the actual hosted service, publisher practices and the current Console form.
