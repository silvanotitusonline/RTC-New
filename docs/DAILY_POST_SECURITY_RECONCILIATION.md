# Daily Post integration security reconciliation

## Integration boundary

The candidate incorporates `main` through `612973bc7d92ebda84b44cabaae2cd2549e378f0`
(PRs #15–#17): Android smoke-test interface compatibility and server-authoritative
sign-up/session restoration. The destination remains `integration/daily-post-full`,
PR #11. This work does not deploy migrations or Edge Functions to production.

## Reviewed database authority

CI at `0ffd35f297096f066feae0da0793b2aee95b3807` observed 198 authenticated-callable
project SECURITY DEFINER signatures with fingerprint
`656f031a7e278433a69ed6d037d26c1e`. That observation alone was not approval: four
functions lacked fixed search paths and several newly exposed APIs required repair.

The forward migration removes ten signatures from that client definer surface:

| Boundary | Disposition |
|---|---|
| MFA helper and editor assertion | Internal execution only; canonical roles and current session authority |
| Comment count, trust score, feed fan-out, notification and signup triggers | Trigger execution only; no client EXECUTE |
| Authentication rate-limit writer | Server execution only; clients cannot consume another identifier's allowance |
| Unintegrated civic routing placeholder | No client EXECUTE; fixed search path |
| Arbitrary realtime broadcast | Service-role execution only |
| Cached feed and avatar readers | SECURITY INVOKER; caller RLS, authenticated access; no shared feed cache |

The retained Daily Post RPCs separate editor, moderator, resident, and service
authority. Administrator editing, media policies, and moderation require `aal2`;
enrollment metadata cannot establish current MFA assurance. Dedicated content-editor
and moderator roles retain their existing role-specific access. Publication jobs
and scheduler verification remain service-role-only.

The generic `admin_access_guard()` retains a staff boundary: any canonical staff
role with a current session and `aal2`. It is not a substitute for an RPC's more
specific role check.

The security classification test remains fail-closed and pins the verified final
188-signature surface to `97400b5f6408be76b8fb3d6ee737f260`. Removing the nineteen
explicitly reviewed additions from the actual CI catalogue exactly reproduces
both count 169 and the previously approved fingerprint
`42f7bc4ca9978dfe129a65cd0e6ded86`. The additions are thirteen Daily Post RPCs,
two operations workspace RPCs, the staff guard, and three engagement RPCs.

The comment-count trigger and editor assertion already lacked authenticated
execution in this replay; their explicit revocations also cover environments
with broader default grants. They do not contribute to the ten-signature reduction.
Unexpected signature drift continues to fail. CI replay and all 32 new behavior/
authority assertions passed on `a03c634`; the initial catalogue expectation was
corrected using the exact emitted signatures, not by relaxing the security gate.

## Verification scope

- Repository source contracts and dedicated Daily Post contracts are run locally.
- New pgTAP cases exercise role denial, administrator MFA, session expiry, internal
  execution grants, and invoker read boundaries in the disposable CI database.
- Explore's Compose smoke test injects local publication content and exercises
  both tabs plus the preserved Community Updates navigation callbacks.
- Full migration replay, pgTAP, Android JVM tests, lint, APK assembly, and smoke-test
  compilation remain required CI gates on the final candidate commit.
- Smoke-test compilation is not emulator execution. Push delivery, translation/TTS
  provider configuration, and production scheduling require deployment verification.

## Separate follow-up

The pre-existing community repost/bookmark Android handlers decode JSONB objects
as lists and recover failures as optimistic success. This is outside the current
Daily Post verification repair and must be tracked before claiming those existing
features are verified end to end. Other engineers should preserve the authoritative
sign-up/session changes while addressing that client path.
