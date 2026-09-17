# Security Hardening — 17 September 2026

Applied directly to the connected Supabase production project and reflected here for source control visibility.

## Migrations applied

1. `security_rls_gaps_and_indexes_20260917`
2. `tighten_anon_security_definer_grants_20260917`
3. `remaining_fk_indexes_20260917`

## What changed

### RLS gaps closed (previous ERROR advisors)

| Table | Policy model |
|-------|--------------|
| `public.messages` | Participant read; sender insert/update; no direct delete |
| `public.conversations` | Participant read/update; authenticated insert |
| `public.conversation_members` | Self + peers + SYSTEM_ADMIN |
| `public.notifications` | Own rows only; no direct client insert/delete |
| `public.trending_hashtags` | Public read; no direct client write |

All new policies use `(SELECT auth.uid())` and `private.has_role(...)` conventions.

### Empty-policy tables closed (explicit deny)

- Service Centre: `service_centre_bookings`, `service_centre_booking_events`, `service_centre_booking_messages`, `service_centre_provider_profiles`
- MD3: `md3.posts`, `md3.comments`, `md3.likes`, `md3.media`, `md3.profiles`

These surfaces are RPC / Edge-Function only.

### Anon SECURITY DEFINER grants tightened

Mutation and private civic-report / MD3 RPCs had `EXECUTE` revoked from `anon`.
Intentional public-read RPCs remain callable by anon and are documented with `COMMENT ON FUNCTION`.

### Indexes

High-value foreign-key indexes added across messages, conversations, notifications, community, daily posts, marketplace, service centre, UI configuration, and MD3 tables.

## Storage

All media buckets remain private. Existing least-privilege policies (owner prefix, role-gated evidence, public community media via `private.can_view_community_media_path`) were reviewed and left unchanged.

## Accepted remaining WARN advisors

- Intentional public SECURITY DEFINER RPCs (civic report public surface, MD3 public feed, UI configuration). Documented.
- Large set of authenticated SECURITY DEFINER RPCs — by design for this architecture; role checks live inside the functions.

## Leaked-password protection

Still disabled because the current Supabase plan does not expose the feature. Revisit when the plan allows it.
