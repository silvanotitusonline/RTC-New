# Coder A compose-mvp-v1 status

## Repository status

- Working branch: `integration/compose-mvp-v1`
- Created from: `integration/resident-modernisation-v1` @ `4c0494eb2fb110a2be6919208e2683c51ca87081`
- Target for the final PR: `main` (`57511f61`) after verification
- This first boundary commit is documentation + owner contract only

## What is already on the foundation (do not reimplement)

- Public Reports RPC client matching live Non-Production civic RPCs
- `publicReportRoutes()` registered next to Marketplace in the NavHost
- Community hub callback `onOpenPublicReports`
- Events, Inbox, Service Centre packages
- Fail-closed `ResidentModernisationFeatureFlags`

## What must still be ported semantically (not merged)

From PR #28, in this order:

1. `fix(auth)` browser Google OAuth + Auth deep-link scheme `rtc://community`
2. `refactor(navigation)` ResidentNavigationPolicy / SurfacePolicy / route content
3. `feat(resident)` Home modernisation contract, Services, Account split, Inbox resolver
4. `build(ci)` connected non-production debug runtime

From PR #29, only after #28 ports compile:

5. Admin Events screen + `ADMIN_EVENTS` + Admin Workspace entry
6. Connect Home metrics to `PublicReportRepository.dashboard()` (typed filters)
7. Set `reportsHostAvailable = true` against the *integration* Public Reports host, not the PR #29 client

## Verification (this commit)

Not run. No Gradle / emulator in this integration-owner session. Do not claim green gates.

Required later, using repo CI commands only:

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:compileDebugAndroidTestKotlin`
- `python3 tools/tests/run_contract_tests.py`
- existing Supabase local verification workflow

## Production blockers

| Item | State |
|---|---|
| Integration branch exists | Completed |
| Wholesale PR #28 / #29 merge | Rejected by owner rules |
| Auth browser OAuth port | Not yet committed |
| Five-icon flag-gated shell | Not yet committed |
| Public Reports host attached to PR #28 boundary | Not yet committed |
| Admin Events | Not yet committed |
| Feature flags on | Blocked until device smoke |
| Site URL password-recovery (`localhost:3000`) | External Supabase dashboard |
| Release signing secrets | Blocked by credentials |
| Production Supabase deploy | Deferred |

## Device acceptance

Not executed in this session. The APK currently on the owner device tracks `main`, not this branch.
