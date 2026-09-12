# Administrator-Managed Navigation and Layout Assessment

## Request interpretation

The requested capability is a server-driven presentation configuration for the installed RTC Community Android client. A System Administrator should be able to hide or show approved navigation modules, add approved custom navigation entries, select from approved layout variants for Home and Explore, preview changes, and publish one active configuration version for clients to fetch at startup and on refresh.

## Mandatory security boundary

The Android app must not accept arbitrary code, arbitrary Compose definitions, external executable links, or free-form scripts from an administrator or an AI request. Such behaviour would allow the active client experience to be changed without normal application review, would create a security risk, and cannot safely make unavailable native functionality appear.

The safe approach is a constrained catalogue. A configuration may reference only pre-built and authorised modules already shipped in the APK, for example Home sections, Projects, Centres, Opportunities, Notices, Help, Community, and a vetted external-link or informational page module. A custom menu entry must contain constrained labels, an icon selected from an approved set, a visibility rule, a target from the approved catalogue, and a display order. It must never contain executable code.

## Publishing model

A System Administrator creates a draft configuration, sees a resident preview, submits it for review if a second-administrator control is selected, and publishes an immutable version after MFA and typed confirmation. The server must retain a version history, support rollback to a previous published version, record the actor, reason, affected menus, and publication time, and expose only the active configuration to ordinary clients.

RTC AI may translate a natural-language request into a draft proposal, such as a proposal to hide Centres from Explore. It must show the exact affected audience and client modules, require human review and confirmation, and never publish directly.

## Immediate defect findings to separate from this feature

1. `admin_privacy_analytics_dashboard` is currently broken because two aggregate subqueries refer to `metric_value` without qualifying the table column. The function’s output parameter has the same name, producing the reported PostgreSQL ambiguity.
2. `staff_work_preferences` policies call `private.is_any_staff()`, but the function is executable only by `postgres`. This causes the reported permission-denied error during the Operations Hub preference refresh.
3. The staff profile icon is explicitly routed to the Operations Hub instead of Account, making the existing profile editor and sign-out control unreachable for administrator and staff accounts.
4. Community Guidelines blocks the acknowledgement while its status is unresolved. A status lookup failure therefore creates a no-action dialog rather than a recoverable retry/acceptance experience. The UI should show a retry state and the backend result instead of permanently disabling the acknowledgement action.
5. Access Management already exists but is protected by verified System Administrator MFA. Its discoverability and account-profile routing need improvement; no evidence supports bypassing MFA.

## Required design decisions

The implementation needs decisions on the catalogue of modules/layouts, whether all residents or selected audiences receive a published version, whether a second System Administrator must approve every publication or only high-impact changes, how custom tabs should behave offline, which links can be externally opened, and whether AI may create drafts only or should remain informational until a later release.
