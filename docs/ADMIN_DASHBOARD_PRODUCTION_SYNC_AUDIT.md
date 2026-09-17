# Administration Dashboard Production Sync Audit

**Repository:** `silvanotitusonline/RTC-New`  
**Production Supabase project:** `pbzzfzfgwzwdstvnwzqu`  
**Audit date:** 2026-09-18  
**Scope:** Administration dashboard capabilities, data authority, authorization, refresh behavior, and Supabase Realtime coverage.

## Executive conclusion

The administration dashboard is **connected to the production database for authoritative reads**, but it is **not real-time synchronized**. The Android dashboard calls the protected RPC `admin_get_moderation_dashboard_summary_v1()` when the ViewModel is created and when the administrator presses Refresh. It does not subscribe to Supabase Realtime, use a polling loop, or receive push invalidations when the underlying production data changes.

The production Realtime publication query returned no entries for the dashboard source tables checked: `civic_reports`, `official_notices`, `community_events`, `operational_work_items`, `access_role_change_requests`, and `notification_events`. Therefore, an administrator can see stale counts until the dashboard is reopened or manually refreshed.

## Verified capabilities

| Capability | Result | Evidence |
|---|---:|---|
| Production-backed summary read | Pass | `AdminDashboardViewModel` invokes `admin_get_moderation_dashboard_summary_v1` through PostgREST. |
| Bounded server projection | Pass | The RPC returns five aggregate counts in one row. |
| Staff authorization | Pass | The RPC calls `private.ops_assert_staff()`. An unauthenticated direct invocation was rejected with `A signed-in account is required.` |
| Fail-closed error handling | Pass | The client does not substitute zeros after an RPC failure; it displays an unavailable/error state. |
| Manual refresh | Pass | Workspace refresh action calls both operations refresh and `dashboardViewModel.refreshCounts()`. |
| Automatic initial load | Pass | The dashboard ViewModel calls `refreshCounts()` in `init`. |
| Realtime subscription | **Fail** | No Realtime channel or `postgresChangeFlow` exists in `AdminDashboardViewModel`. |
| Background polling | No | The previous 15-second polling loop was intentionally removed. |
| Production Realtime publication coverage | **Fail** | The live `supabase_realtime` publication returned no matching dashboard source tables. |
| Count freshness indicator | Partial | `lastRefreshedMillis` is stored, but the summary card does not visibly show the timestamp or a live/stale status. |

## Current data flow

1. `AdminWorkspace` obtains `AdminDashboardViewModel` through Hilt.
2. `AdminDashboardViewModel.init` calls `refreshCounts()` once.
3. `refreshCounts()` invokes the PostgREST RPC `admin_get_moderation_dashboard_summary_v1`.
4. The RPC counts unresolved/open records in `civic_reports`, pending editorial records in `official_notices`, draft/review records in `community_events`, and non-terminal records in `operational_work_items`.
5. The summary card renders the returned values and offers a manual Retry/Refresh path.
6. No database change event causes step 2 or step 3 to run again.

## Live production evidence

The RPC definition in production is:

```text
public.admin_get_moderation_dashboard_summary_v1()
returns table (
  reports_count bigint,
  notices_count bigint,
  events_count bigint,
  work_queue_count bigint,
  total_pending_tasks bigint
)
security definer
```

The function begins with `private.ops_assert_staff()`, uses a fixed `search_path` of `public, pg_temp`, and returns counts from the four dashboard domains. This is an appropriate server-authoritative boundary for staff-only aggregate reads.

The live source-table counts observed during the audit were:

| Source | Rows observed |
|---|---:|
| `civic_reports` | 8 |
| `official_notices` | 5 |
| `operational_work_items` | 1 |
| `access_role_change_requests` | 0 |

A direct unauthenticated RPC execution was rejected by the staff guard, confirming that the aggregate cannot be anonymously invoked through the production database API.

The production Realtime publication query returned an empty result for the dashboard source tables. This confirms that there is currently no database publication coverage for those tables, independent of the missing Android subscription.

## Impact

The dashboard is suitable for **on-demand operational review**, but it is not suitable for a “live command centre” expectation. If another administrator, moderator, backend worker, or resident action changes a report, notice, event, or work item, the already-open dashboard will continue displaying the previous counts until a refresh occurs.

The risk is primarily operational freshness rather than data authority: when refreshed, the counts come from the production RPC; between refreshes, the UI can be stale.

## Recommended production implementation

To achieve genuine real-time synchronization, implement both halves of the feature:

1. Add the dashboard source tables to the `supabase_realtime` publication using a forward-only migration, after confirming the desired replica identity and RLS behavior.
2. Add a lifecycle-scoped Realtime channel in `AdminDashboardViewModel` for the dashboard source tables. On each insert, update, or delete event, debounce/coalesce events and invoke the authoritative summary RPC rather than calculating counts from event payloads.
3. Unsubscribe the channel in `ViewModel.onCleared()` and handle subscription errors with a visible stale-data indicator plus manual refresh fallback.
4. Display `lastRefreshedMillis` as a human-readable “Updated just now / Updated N minutes ago” label, and show a “Live” or “Refresh required” status based on subscription health.
5. Add a focused contract test requiring the admin dashboard to create and dispose a Realtime subscription and to retain the guarded RPC as the sole count authority.
6. Verify the publication and subscription with an authenticated staff session in a non-production rehearsal before applying the production migration.

## Final verdict

**Database connectivity:** verified.  
**Authorization boundary:** verified and fail-closed.  
**Authoritative count correctness path:** verified by source and live RPC definition.  
**Real-time synchronization:** **not currently implemented or enabled**.  
**Production readiness for live dashboard monitoring:** **not complete until Realtime publication, client subscription, lifecycle cleanup, and freshness UX are added.**
