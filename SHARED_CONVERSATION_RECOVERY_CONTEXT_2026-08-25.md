# Shared Conversation Recovery Context — 25 August 2026

The user supplied a shared ChatGPT conversation as context for resuming RTC Community work. The shared page reports that an uploaded package was represented as containing the Android/Kotlin/Jetpack Compose source, Concept 6 implementation, Gradle/CI configuration, Supabase migrations, regression tests, and source for six active production Supabase Edge Functions.

It also reports these **unverified claims**, which must be validated against the actual source archive or Git repository before being used as implementation evidence:

| Claim | Reported value | Required verification |
|---|---|---|
| Source archive SHA-256 | `94900bad536e46f1598fdc1755141e2f15839100240218a8fa2fb591be48f8ae` | Compute SHA-256 of the recovered archive. |
| Regression contracts | `29/29` passed | Run the included regression command in the restored workspace. |
| Edge Function sources | Six active production functions included | Compare repository functions with the read-only production function catalogue; never deploy as part of inspection. |
| Runtime secret handling | Secrets, signing materials, and `google-services.json` excluded | Inspect ignore rules, templates, and CI secret restoration path without exposing values. |
| Release state | Source/backend release candidate, pending Android compile/lint/APK/AAB/device gates | Preserve as a NO-GO status until those gates actually pass. |

The shared conversation does not expose a repository URL or the archive attachment itself. It therefore provides context, not a recoverable source location. The next task is to identify the latest authorized RTC Community GitHub repository and verify it against these claims.
