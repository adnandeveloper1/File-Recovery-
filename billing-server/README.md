# Purchase verification service

Deployable Google Play subscription verification for File Recovery. The Android app remains free-scan-only until an HTTPS verification endpoint is configured. No service-account key belongs in the APK or repository.

## Configuration

- Node 22 or newer. Install with `npm ci`; run `npm test`.
- `ANDROID_PACKAGE_NAME=com.nexappra.filerecovery`
- `PREMIUM_PRODUCT_IDS=recovery_monthly,recovery_yearly`
- Application Default Credentials with Android Publisher scope and the Play Console permissions needed to read subscriptions and acknowledge purchases. Prefer an attached service account on Cloud Run.
- Run behind managed HTTPS ingress; expose only /verify and /health. Apply gateway rate limits and instance limits before public rollout. Purchase tokens are bearer credentials: never log request bodies or upstream request URLs.

POST /verify accepts only a purchaseToken. Package name, products, status and expiry are validated using Google Play, not client claims. Pending, paused, on-hold and expired subscriptions fail closed. Active, grace-period and cancelled-but-unexpired subscriptions remain eligible. Acknowledgement is confirmed with Play before returning verified=true. No token or credential storage is required.

The app rechecks purchases on resume and caps each verification to 15 minutes. This is an accountless flow tied to purchases returned by Google Play on the signed-in device. If an app account system is added later, bind tokens uniquely to authenticated accounts before granting access.

## Android release configuration

Supply Gradle properties:
```properties
PREMIUM_MONTHLY_ID=recovery_monthly
PREMIUM_YEARLY_ID=recovery_yearly
PURCHASE_VERIFICATION_URL=https://YOUR_DEPLOYED_SERVICE/verify
```

Create/activate matching monthly and yearly auto-renewing subscription products and base plans in Play Console. Pricing displayed by the app comes from Play. Upload a signed build to an internal test track and test purchase, pending payment, cancellation, restore, expiry and refund using licence testers. Backend deployment, Play products and a real licence purchase have not been performed by this code change.

References: [Billing integration](https://developer.android.com/google/play/billing/integrate), [Subscription states](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2), [Purchase acknowledgement](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions/acknowledge).
