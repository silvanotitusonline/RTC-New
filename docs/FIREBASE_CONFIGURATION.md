# RTC Firebase configuration

Both supplied files identify Firebase project `gen-lang-client-0507599237`.
The Android file includes the RTC application ID `za.org.rtc.community`.

| Configuration | Location | Consumer |
| --- | --- | --- |
| Firebase Admin service-account JSON | Production Supabase Vault: `rtc_firebase_fcm_service_account` | Trusted notification and speech services |
| Original Android client JSON | Production Supabase Vault: `rtc_firebase_android_config`; ignored local `app/google-services.json` | Android Google Services resource generation |
| Android CI client configuration | GitHub Actions secret `GOOGLE_SERVICES_JSON_BASE64` | Existing CI restore step |

The service-only `get_firebase_fcm_service_account()` RPC returns the existing JSON
text contract. Anonymous and authenticated application users cannot execute it.
Neither uploaded file is committed to source. The Admin private key must never be
included in Android resources, an APK, an artifact, or a resident-accessible table.

The local Android client file has been installed. The GitHub connection used for
this change cannot administer Actions secrets, so the CI secret has not been set.
An authenticated repository administrator can restore it with:

```bash
gh auth login
python3 tools/configure_firebase_android_ci.py --file /path/to/google-services.json
```

The helper validates the RTC package, rejects Admin service accounts, and supplies
the encoded file through standard input to `gh secret set`. It does not print it.
Google Services selects the matching Android client; direct release requests now
fail if the local client configuration has not been restored.

## Verification and release boundary

The supplied Admin credential successfully obtained a Google OAuth access token.
An FCM HTTP v1 request with `validate_only: true` returned HTTP 200; no notification
was delivered. Device receipt and notification/deep-link behavior still require a
registered device and an installed build.

Cloud Text-to-Speech synthesis returned HTTP 403 `SERVICE_DISABLED`. Its API must
be enabled in the Firebase/Google Cloud project before the speech path can succeed.
Gemini translation has separate provider configuration; the Firebase files do not
supply `GEMINI_API_KEY`.

The production release still requires the existing Android signing identity
(`RTC_ANDROID_KEYSTORE_BASE64`, `RTC_ANDROID_KEYSTORE_PASSWORD`,
`RTC_ANDROID_KEY_ALIAS`, `RTC_ANDROID_KEY_PASSWORD`) and production runtime secrets
(`RTC_PROD_SUPABASE_URL`, `RTC_PROD_SUPABASE_PUBLISHABLE_KEY`). The Firebase Admin key
is not an Android signing key. A successful debug verification build does not mean
that a signed production APK was created or distributed.

Provider behavior: [Firebase HTTP v1 validation](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages/send).
