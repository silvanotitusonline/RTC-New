# RTC Community Non-Production Customizer Discovery Notes

## Scope and Security Boundary

This assessment is limited to the isolated RTC Community non-production Supabase project and the local `recovery/nonproduction-baseline` Android branch. The second supplied specification contains an account credential intended for a cloud crawl; it is treated as sensitive data and has **not** been used, stored in source, echoed, or submitted to any provider.

## Read-Only Findings

| Area | Finding | Implication for the Home layout customizer |
| --- | --- | --- |
| Role model | `public.user_roles` has the existing `SYSTEM_ADMIN` role and RLS is enabled. | The customizer must require a verified `SYSTEM_ADMIN` session server-side; client-visible admin navigation is not authorization. |
| Account and identity | `public.profiles` and `public.account_preferences` are present and RLS-protected. | Per-user visual preferences should remain separate from a centrally published Home layout definition. |
| Staff surfaces | `public.staff_work_preferences`, `public.bulk_action_batches`, and `public.bulk_action_items` exist with RLS enabled. | The new customizer should reuse the established typed confirmation/audit approach rather than expose direct editable configuration rows. |
| Publishing discipline | RLS-protected official-notice and lifecycle tables already track creation, review, publication, correction, and audit-oriented relationships. | The new layout workflow should mirror this lifecycle: draft → validated proposal → confirmed publish → immutable revision/audit record. |
| Generic configuration store | `public.app_content` has a JSON payload and basic publishing timestamps, but no audience field, schema version, prior-version linkage, approver, typed confirmation, or customizer-specific RPC surface. | Do not repurpose it through direct Android writes. A dedicated versioned layout model and privileged lifecycle RPCs are required. |
| Existing generic write policy | `public.app_content` currently permits direct writes to Content Editors and System Administrators. Direct client write access is unsuitable for system-wide layout releases. | Keep the customizer in a separate table with direct client access denied; expose only validated read and lifecycle RPCs. |
| Server-side privilege gate | `private.access_assert_system_admin()` verifies an authenticated actor, the `SYSTEM_ADMIN` role, and JWT assurance level `aal2`. | New customizer draft/publish/revert RPCs should invoke this helper; the Android route gate must remain a second UX layer, not the authorization mechanism. |
| Debug environment | Debug configuration requires ignored non-production Supabase properties; production is not a Gradle fallback. | The customizer and all fixtures must stay on the existing non-production endpoint. |
| Test stack | Existing Android tests include JUnit/coroutines unit support and Compose UI testing under `androidTest`; the source currently has no Robolectric, Roborazzi, or Paparazzi configuration. | Do not blindly add an older snapshot stack. Select and validate a compatible Compose test/screenshot strategy before modifying dependencies. |
| Cloud execution | No local `gcloud`, `adb`, KVM, Firebase/Google Cloud connector, or authorized device-cloud credential is available. | No Firebase Test Lab matrix, Robo crawl, device log, screenshot, or video can be produced yet. |

## Guardrails for the Proposed Feature

The Home customizer must not provide direct database updates from the Android client. It should validate a strict JSON schema; use authenticated, MFA-gated RPCs or an equivalent server-authorized function for draft and publish transitions; scope audience segments through server-side rules; preserve the prior published revision; and create immutable audit events. The client should read only the active published layout appropriate for its authorized audience and retain a safe default layout if a remote configuration is unavailable or invalid.

## Current Absence and Scope Result

The read-only schema inventory has no dedicated Home-layout, remote-configuration, or layout-version table. The Android route constants, native `HomeScreen`, System Administrator workspace, and protected Supabase adapter likewise contain no Home customizer route, UI state, domain model, or fetch/publish RPC. The feature therefore requires a deliberately bounded vertical slice; it is not a small UI switch.

The isolated Supabase advisor reports existing `SECURITY DEFINER` functions callable by authenticated users. For the reviewed customizer pattern, that is acceptable only when each RPC independently asserts identity, `SYSTEM_ADMIN`, and `aal2`, validates every input, uses a pinned search path, and records an audit event. Any new function must be reviewed alongside the existing advisor warning class rather than granted broadly or exposed through direct table writes.
