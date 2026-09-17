# Remaining Work Checklist (post 2026-09-17 hardening)

## Completed in this pass

- [x] Enable RLS + policies on the 5 open public tables
- [x] Explicit deny policies on RLS-enabled-but-empty tables
- [x] Tighten anon SECURITY DEFINER grants + document intentional public RPCs
- [x] Confirm storage least-privilege posture
- [x] Index remaining application FK paths
- [x] Document Edge Function JWT exceptions
- [x] Document system overview + ADRs for RPC boundary, JWT policy, archival
- [x] Confirm service-centre payment functions are retired 410 stubs (safe)

## Still requires human / CI / device action

### Binary & device gates (P1)
- [ ] Ensure `GOOGLE_SERVICES_JSON_BASE64` secret is set for release assemble
- [ ] Run GitHub Actions `android-ci.yml` to green (unit tests, lint, assembleDebug; release if secret present)
- [ ] Device smoke: notifications, media playback, deep links, MFA QR scan, TalkBack, 200% font, light/dark/system, process death, compact/medium/expanded layouts

### Tests (P1–P2)
- [ ] Expand Compose UI tests beyond current androidTest smoke suite
- [ ] Integration tests against a non-production Supabase branch for critical RPCs
- [ ] Instrumentation coverage for protected-route fail-closed behaviour

### Service Centre (P1)
- [x] Payment create/webhook already return 410 Gone
- [ ] After deprecation window, delete the retired Edge Function slugs if desired
- [ ] Confirm no client code still targets payment endpoints

### Product / abuse signals (P2)
- [ ] Surface clearer rate-limit / abuse feedback in marketplace and civic-report client flows
- [ ] Client Crashlytics (or equivalent) wiring for production builds
- [ ] Lightweight admin visibility into notification delivery success / Edge latency (dashboard or log views)

### Accessibility & density (ongoing)
- [ ] Device validation of Simplified Reading Mode, high-contrast, expanded window classes

### Repo hygiene (P2)
- [ ] Move historical `PHASE*.md` files under `docs/archive/` in a follow-up PR
- [ ] Enable branch protection on `main` with required status check for Android CI
- [ ] Keep Edge Function source and `DEPLOYED_SOURCE_MANIFEST.json` hashes aligned after every deploy

### Supabase plan
- [ ] Enable leaked-password protection when the subscription exposes it
