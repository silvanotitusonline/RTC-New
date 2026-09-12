# RTC Community — Isolated Appetize Resident Navigation Test

This suite validates only the **debug-only synthetic Resident A** path. It does not accept credentials, register an account, grant a device permission, post data, upload media, change an administrative setting, or call production.

The target must be a freshly uploaded debug APK built after the local non-production configuration boundary repair. Provide the resulting Appetize build ID only through the local environment:

```bash
export RTC_APPETIZE_BUILD_ID='verified-nonproduction-build-id'
pnpm install
pnpm test:resident-menu
```

The suite uses the Android ADB command channel to launch `MainActivity` with the debug-only `RESIDENT_A` intent extra. It then verifies the Home-to-Account user-menu route, the visible Sign out action, and each resident primary destination. The activity ignores this extra in any release build.

The report is written to `playwright-report/`. Test output and reports are local test artifacts and must not be committed.
