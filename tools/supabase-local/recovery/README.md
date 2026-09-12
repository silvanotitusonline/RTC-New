# Supabase local-verification recovery fixtures

These SQL files are **not deployable migrations**. They exist only to reconstruct prerequisite schema inside the disposable Supabase workdir used by CI.

Rules:

- Never copy this directory into `supabase/migrations` for deployment.
- Never run these fixtures against Production.
- Keep environment-specific URLs, API keys, JWTs, dispatch secrets, and real Vault material out of these files.
- Production-facing migrations remain exclusively under `supabase/migrations`.
- The local verification workflow must build an isolated workdir before running `supabase start`, `supabase db reset`, or `supabase test db`.

The recovery fixtures preserve forensic work from the migration-replay investigation without converting historical Non-Production recovery versions into future Production migration backlog.
