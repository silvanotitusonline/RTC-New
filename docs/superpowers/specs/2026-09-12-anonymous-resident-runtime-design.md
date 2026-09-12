# Anonymous Resident Runtime Design

## Goal
Remove resident authenticated identity as an application requirement while preserving server-authoritative civic data, durable local continuity, and strong authentication for privileged staff and administrator operations.

## Architecture
RTC will operate in two trust domains. The resident domain is anonymous by default: browsing, local drafts, navigation continuity, and public civic interactions must not depend on a Supabase authenticated user session. The privileged domain remains authenticated and server-authorized for moderation, administration, operational controls, publication/notification authoring, and any function that exposes protected data or elevated mutations.

Resident continuity uses a non-security installation-scoped identifier generated and stored locally. This identifier may key local drafts, outbox records, cached preferences, and idempotency keys, but it must never grant a database role, bypass RLS, or be interpreted as authenticated identity.

## Resident Runtime
- App launch must never route a normal resident to a sign-in screen.
- Public Home, Explore, Community, Public Reports, Market/Services, notices, search, and other resident surfaces remain directly reachable.
- Resident local state is keyed to an installation continuity identifier rather than an authenticated account ID.
- Local drafts and pending work are clearly labeled local/pending until the server confirms acceptance.
- Resident-visible server mutations remain authoritative: client state cannot manufacture accepted civic records or successful engagement mutations.
- When a backend operation genuinely requires authenticated ownership and no anonymous server contract exists, the client must fail closed with a truthful capability message rather than fabricate identity or success.

## Privileged Runtime
- Staff/admin entry points may require Supabase authentication.
- Privileged access requires a live Supabase session plus server-side role/guard confirmation.
- Cached roles, email patterns, user metadata, installation identity, or local flags never authorize privileged access.
- Sign-out clears privileged authority without deleting anonymous resident drafts/continuity data.

## Data and Persistence
- Introduce an `InstallationIdentity` boundary whose only purpose is local continuity/idempotency.
- Migrate or adapt account-scoped local draft/outbox access so anonymous resident state is keyed by installation identity.
- Preserve defensive migration behavior: never silently attribute another user's legacy private draft to a new installation identity.
- Keep cached public content usable offline where it represents previously server-confirmed data.
- Production UI must never seed or display mock fixtures as live civic/community truth.

## Navigation and UI
- Remove the resident authentication gate and resident sign-in/sign-up routes from normal onboarding/navigation.
- Keep a clearly separated protected staff/admin authentication entry point.
- Replace session-derived resident profile assumptions with anonymous/local preference state where safe.
- Where user-specific cloud account features cannot function anonymously, hide or disable them with accurate copy rather than simulated behavior.

## Backend Contracts
- Public and anonymous-safe reads use existing RLS/RPC/Edge contracts where permitted.
- Anonymous mutations must use server contracts that explicitly support the anonymous role or another non-user ownership model.
- No client-generated identifier is accepted as proof of authorization.
- Existing Public Reports and Community authoritative wrappers remain in place.

## Error Handling
- Network/auth failures never become local success.
- Missing anonymous server capability is surfaced as unavailable/requires staff access as appropriate.
- Partial success remains explicit (for example, civic report accepted but evidence upload pending).

## Verification
- Source contract: normal resident launch has no sign-in gate.
- Source contract: installation identity cannot map to `SUPABASE_AUTH`, staff/admin roles, or protected navigation.
- Source contract: sign-out of privileged session preserves anonymous drafts/outbox.
- Source contract: resident mutation paths still require server confirmation.
- Compile `testDebugUnitTest` and debug APK.
- Run Edge authorization tests and truth-boundary contract tests.
- Run existing production contract suite and report unrelated inherited failures separately.

## Non-goals
- Do not weaken Supabase RLS or privileged authorization.
- Do not create a fake anonymous Supabase user.
- Do not migrate protected staff data into the anonymous domain.
- Do not claim anonymous support for server operations whose backend policy still requires authentication.
