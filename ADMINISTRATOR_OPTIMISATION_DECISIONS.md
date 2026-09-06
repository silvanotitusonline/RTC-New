# RTC Community — Administrator and Staff Optimisation Decisions

**Prepared:** 22 August 2026  
**Status:** Requirements complete; implementation awaits explicit approval.

## Review Outcome

The administrator review confirmed that **Access Management**, **Audit and Privacy Analytics**, and **Community Alerts** already have live protected backend paths. The notification test also verified live device delivery after repairing the Community-alert dispatcher’s ambiguous database reference. By contrast, the Work Queue, Moderation queue actions, Operational Controls, My Work Profile, some Content Management actions, and the AI confirmation flow are currently partial, static, local-only, or demonstrative.

The approved target state is a mobile-first, role-aware operational environment. It preserves server-side authorisation, MFA safeguards, privacy controls, immutable auditability, and least-privilege access while replacing non-functional panels with scoped live workflows.

## Approved Experience and Navigation Decisions

| No. | Area | Approved decision |
|---:|---|---|
| 1 | Workspace structure | Use one role-aware **Operations Hub** with a live **Needs attention** queue at the top and grouped operating areas for Content & Alerts, Community Safety, Case Work, and System Administration. |
| 2 | High-risk administration | Place high-risk System Administrator functions inside a separately labelled, MFA-gated **System Control Centre**. |
| 3 | System Control Centre scope | Retain Access Management and Audit/Privacy Analytics; replace the placeholder controls with live safeguarded controls; add a read-only System Health & Delivery view. Keep content and moderation in their role-specific areas. |
| 4 | Live operational controls | Support a maintenance banner with expiry, temporary Community posting/commenting pause with reason and expiry, and controlled retry of failed alert delivery only. Do not expose authentication, privacy-retention, database, or destructive-data controls in-app. |
| 5 | Queue | Replace seeded work cards with one live role-aware queue that ranks authorised work by urgency and due time, and opens the associated workflow directly. |
| 6 | Work ownership | Eligible staff can claim, release, or mark work ready for review. System Administrators can reassign with a reason. Preserve immutable assignment history. |
| 7 | Responsive navigation | On phones, use an Operations Hub entry point, contextual back navigation, sticky primary workflow actions, and compact status chips. On larger screens, add a persistent role-aware rail and two-pane queue/detail view. |
| 8 | Guidance | Add optional role-specific first-use walkthroughs, contextual high-impact-action guidance, and clear access/MFA block explanations. |
| 9 | Incident handling | Add a lightweight incident workflow with impact, owner, severity, linked controls/alerts, closing summary, and full auditability. |

## Approved Security, Access, and Accountability Decisions

| No. | Area | Approved decision |
|---:|---|---|
| 10 | Staff access | Retain one effective role per account. Add optional review date/expiry for non-administrator staff roles; expiry returns the account to Resident access and revokes sessions. |
| 11 | Administrator change control | Keep second-System-Administrator approval for Administrator role changes. |
| 12 | MFA | Require verified MFA on entry to the System Control Centre. The privileged session may remain valid for up to 12 hours and must be re-established after timeout, sign-out, role change, or sensitive-device change. |
| 13 | MFA recovery | Permit self-service System Administrator MFA reset through verified email. Record the reset, revoke active privileged sessions, and require fresh MFA enrolment before protected access resumes. |
| 14 | Administrative Activity | Add a unified, view-only, paginated and non-exportable Administrative Activity view with safe filters. Keep the purpose-gated account lookup separate. |
| 15 | System health | Provide green/amber/red aggregate cards for alert dispatch, notification delivery, scheduled jobs, Community availability, and protected-control status. Expose safe incident categories/counts and permitted retry, never raw logs, secrets, tokens, or resident identifiers. |

## Approved Live Workflow Decisions

| No. | Area | Approved decision |
|---:|---|---|
| 16 | Moderator Centre | Implement a live, role-scoped Moderator Centre with reports, content context, auto-hidden content, appeals, required reasons, safeguarded decisions, and immutable audit history. Moderators can dismiss, hide, lock comments, or remove content without routine System Administrator approval. |
| 17 | Auto-hide policy | Temporarily hide only after a high-confidence automated safety flag or three independent reports within 24 hours. Require moderator review within 24 hours, neutral under-review status for the author, and an appeal path. |
| 18 | Editorial workflow | Implement Draft → Submit for review → Approve → Schedule or Publish → Correct/Retire. Content Editors create/submit; a different authorised editor or System Administrator approves publication. |
| 19 | Alert authoring | Replace raw ISO fields/manual original ID entry with a guided mobile wizard: category templates, date/time picker, expiry suggestions, resident preview, selected-alert corrections/retractions, typed final confirmation, and delivery-status follow-up. |
| 20 | Alert authority | Content Editors and System Administrators can publish/schedule ordinary updates, events, opportunities, and service-disruption alerts. Safety/emergency alerts are System Administrator-only with enhanced confirmation and operational-reason safeguards. |
| 21 | My Work Profile | Add self-managed Available/Away/Off duty status, operational-notification preferences, saved filters, and personal assignment history. Do not add location tracking, productivity scoring, or activity monitoring. |
| 22 | Operational notifications | Use ownership-aware inbox and optional device push for assignment, reassignment, urgent work, and review requests. Respect quiet hours for ordinary work; safety and expiry-critical controls may bypass quiet hours. |
| 23 | RTC AI | Make RTC AI a live role-scoped proposal assistant. It may draft/summarise/recommend from authorised data but must show sources and affected records, require normal human confirmation, create an audit event, and never execute directly. |
| 24 | Case Work | Add a live role-scoped Case Work workspace for Case Staff and authorised System Administrators. Staff see only assigned or explicitly team-available cases and can claim, update, note, request review, and hand off with a reason. |
| 25 | Evidence Review | Add a separate secure Evidence Review workspace. Evidence Reviewers see only explicitly assigned items and may accept, request clarification, or reject with a reason; every view/decision is audited and unrelated case or Community browsing is prohibited. |

## Delivery Plan

### Release 1 — Operations Hub and Core Administrator Workflows

The first release will deliver the Operations Hub, live role-aware queue and ownership, Moderator Centre, editorial workflow, Community Alert wizard, System Control Centre, operational controls, System Health & Delivery view, Administrative Activity view, incident workflow, contextual guidance, and responsive administrator navigation.

### Release 2 — Controlled Case, Evidence, Preferences, and AI Workflows

The second release will add the Case Work workspace, Evidence Review workspace, staff availability and operational-notification preferences, and the live role-scoped RTC AI proposal integration.

## Non-Negotiable Safeguards

All protected actions will remain server-authorised, purpose-limited where appropriate, confirmed before execution, and recorded in immutable audit history. The optimised administration experience will not add GPS tracking, employee monitoring, staff productivity scoring, unrestricted raw-log access, data exports, in-app destructive database controls, or direct AI execution.
