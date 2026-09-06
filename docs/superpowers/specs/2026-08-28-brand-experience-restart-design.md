# Brand & Experience Restart Design

## Purpose

Complete the existing Brand & Experience integration in pull request #17 without regressing Marketplace, Community, the single-activity navigation model, or the Supabase hardening boundary. The current remote PR head is `cab1a0da61a223ebb4d3b9e44ecc6295b143b8d8`; `main` is `3d2c64c39cffb8384313cb7d33d425b9d3018af7`.

## Scope and delivery boundary

The work remains on `integration/brand-experience-v2` and is delivered through PR #17. `main` is read-only until the PR has complete review and verification evidence. The existing dirty checkout is preserved; all restart work occurs in a clean isolated worktree.

Production Supabase project `pbzzfzfgwzwdstvnwzqu` is read-only. Any database repair is applied only to Non-Production project `eqwstpdjoineycrkhpht`, using a new forward-only source migration and fresh security verification. No synthetic published Brand configuration is created.

## Required behavior

### Storage policy scope

The three Brand object policies must authorize only objects in the `rtc-ui-assets` bucket. The existing helper functions retain object-path and role/state checks; the policies add an explicit `bucket_id = 'rtc-ui-assets'` predicate. A forward migration is required because the previous repair is already recorded in Non-Production history. Other bucket policies must remain unaffected.

### Draft lifecycle

History rows in `DRAFT` state must expose a Resume action. Resume strictly decodes the stored configuration, installs it as the active editor baseline/working/preview state, retains the existing draft identifier, and permits saving/publishing from that draft. Restore remains available only for `PUBLISHED` and `SUPERSEDED` versions.

### Commit truth and editor concurrency

Repository publish/revert results must represent the server mutation result even when best-effort effective-configuration refresh fails after the mutation committed. The UI must state that the mutation committed and that refresh is pending instead of reporting a false failure.

Draft-save completion must be correlated to the configuration snapshot that produced it. If editing occurs while a draft RPC is in flight, an obsolete completion cannot attach its identifier to newer editor state. Publish must use an exact saved snapshot and disable mutating editor actions while publishing.

### Preview validity

Preview is entered only when both structural validation and contrast validation pass. Invalid intermediate input remains editable, preserves the last safe preview, and surfaces a clear validation message rather than reaching `BrandPaletteEngine.parseHex` through the preview composition.

### Image intake and launcher safety

Image bytes and dimensions are read off the Compose main thread through a capped stream that rejects input beyond 8 MiB before allocation of an unbounded byte array. The existing MIME, pixel-dimension, and upload validation remains authoritative in the repository.

Launcher alias changes enable the requested alias before disabling any active alias. A failed enable leaves the current alias unchanged and reports failure through the existing fail-closed path.

## Boundaries preserved

- Only one `MainActivity`; no parallel Activity, navigation graph, or remote executable UI.
- Marketplace and Community implementations remain outside the Brand feature ownership boundary.
- Runtime configuration remains finite, decoded, validated, and falls back to the compiled safe default.
- Privileged UI configuration tables remain RPC-only with RLS and no direct authenticated table SELECT.
- Public effective configuration read remains intentionally bounded to the published global configuration.
- Android client contains no service-role credentials or new production secrets.

## Verification

Each defect begins with a failing focused test, followed by minimal implementation and a passing focused test. Source contracts, targeted JVM tests, Deno authorization tests, lint, debug builds, AndroidTest APK compilation, and exact-head GitHub Actions CI are required before the PR is readied. A final report must distinguish source/CI readiness from device execution, signing, protected-main configuration, and Production Supabase promotion; the overall production status remains NO-GO until those external gates are separately evidenced.
