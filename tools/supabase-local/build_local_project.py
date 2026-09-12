#!/usr/bin/env python3
"""Build the disposable Supabase project used by local CI verification.

This script deliberately separates deployable migration history from the historical
recovery material needed to reproduce the current schema from an empty database.
Nothing under .supabase-local is a deployment source.
"""

from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "supabase"
RECOVERY = ROOT / "tools" / "supabase-local" / "recovery"
WORKDIR = ROOT / ".supabase-local"
LOCAL = WORKDIR / "supabase"
MIGRATIONS = LOCAL / "migrations"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(name: str, content: str) -> None:
    target = MIGRATIONS / name
    target.write_text(content.rstrip() + "\n", encoding="utf-8")


def copy_as(source: Path, name: str) -> None:
    write(name, read(source))


def executable_sql(content: str) -> str:
    """Remove SQL comments before checking for environment-specific runtime literals."""
    content = re.sub(r"/\*.*?\*/", "", content, flags=re.DOTALL)
    return re.sub(r"(?m)^\s*--.*$", "", content)


def build() -> None:
    if WORKDIR.exists():
        shutil.rmtree(WORKDIR)
    MIGRATIONS.mkdir(parents=True)

    config = read(SOURCE / "config.toml")
    config = re.sub(
        r'^project_id\s*=\s*"[^"]+"',
        'project_id = "rtc-community-local-verification"',
        config,
        count=1,
        flags=re.MULTILINE,
    )
    (LOCAL / "config.toml").write_text(config, encoding="utf-8")

    if (SOURCE / "tests").exists():
        shutil.copytree(SOURCE / "tests", LOCAL / "tests")
    if (SOURCE / "functions").exists():
        shutil.copytree(SOURCE / "functions", LOCAL / "functions")

    # Historical prerequisites in dependency order. These files are fixtures only;
    # production-facing history stays under supabase/migrations unchanged.
    copy_as(
        RECOVERY / "20260825113238_reconstruct_core_authorization_baseline.sql",
        "20260819000000_local_core_authorization.sql",
    )
    copy_as(
        SOURCE / "migrations" / "20260820_014_production_ux_foundation.sql",
        "20260819001000_local_production_ux_foundation.sql",
    )
    copy_as(
        SOURCE / "migrations" / "20260825091500_reconstruct_core_content_directory_baseline.sql",
        "20260819002000_local_content_directory.sql",
    )

    # The committed legacy alert migration contains an obsolete Production URL/key
    # scheduler bootstrap. Preserve its schema/RPC portion but remove that environment-
    # specific scheduler block. The current protected scheduler migration is replayed later.
    alert = read(SOURCE / "migrations" / "20260822003000_secure_community_alerts.sql")
    scheduler_marker = "-- Cron prepares due records, archives expired records, and invokes the Vault-backed Edge Function."
    if scheduler_marker not in alert:
        raise RuntimeError("Legacy alert scheduler boundary was not found; refusing an unsafe local copy.")
    alert = alert.split(scheduler_marker, 1)[0].rstrip() + "\n\ncommit;\n"
    write("20260819003000_local_secure_community_alerts_without_scheduler.sql", alert)

    copy_as(
        SOURCE / "migrations" / "20260825093000_reconstruct_community_core_baseline.sql",
        "20260819004000_local_community_core.sql",
    )
    copy_as(
        SOURCE / "migrations" / "20260825155000_allow_community_draft_owner_storage_cleanup.sql",
        "20260819005000_local_community_draft_storage_cleanup.sql",
    )

    write(
        "20260819006000_local_access_helpers.sql",
        r"""
create or replace function private.access_assert_system_admin()
returns uuid
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  actor uuid := auth.uid();
  assurance text := coalesce(auth.jwt() ->> 'aal', 'aal1');
begin
  if actor is null then
    raise exception 'A signed-in account is required.';
  end if;
  if not private.has_role('SYSTEM_ADMIN'::public.app_role) then
    raise exception 'System Administrator access is required.';
  end if;
  if assurance <> 'aal2' then
    raise exception 'Verified administrator MFA is required.';
  end if;
  return actor;
end;
$$;
revoke all on function private.access_assert_system_admin() from public, anon, authenticated;

create or replace function private.access_current_role(target_user uuid)
returns public.app_role
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select ur.role
  from public.user_roles ur
  where ur.user_id = target_user
  order by case ur.role
    when 'SYSTEM_ADMIN'::public.app_role then 700
    when 'EVIDENCE_REVIEWER'::public.app_role then 600
    when 'MODERATOR'::public.app_role then 500
    when 'CONTENT_EDITOR'::public.app_role then 400
    when 'CASE_STAFF'::public.app_role then 300
    when 'RESIDENT'::public.app_role then 100
    else 0
  end desc
  limit 1;
$$;
revoke all on function private.access_current_role(uuid) from public, anon, authenticated;
""",
    )

    moderation = read(RECOVERY / "20260825122945_recover_community_reporting_and_moderation_baseline.sql")
    repairs = {
        "v_code text := upper(trim(coalesce(p_reason_code, ''));":
            "v_code text := upper(trim(coalesce(p_reason_code, '')));",
        "v_state text := upper(trim(coalesce(p_state, 'OPEN'));":
            "v_state text := upper(trim(coalesce(p_state, 'OPEN')));",
        "v_decision text := upper(trim(coalesce(p_decision, ''));":
            "v_decision text := upper(trim(coalesce(p_decision, '')));",
    }
    for malformed, corrected in repairs.items():
        if malformed not in moderation:
            raise RuntimeError(f"Expected recovered moderation syntax defect was not found: {malformed}")
        moderation = moderation.replace(malformed, corrected)
    write("20260819007000_local_reporting_and_moderation.sql", moderation)

    copy_as(
        RECOVERY / "20260825123648_recover_operations_work_queue_lifecycle.sql",
        "20260819008000_local_operations_work_queue.sql",
    )

    operations_controls = read(
        RECOVERY / "20260825124021_recover_operations_incidents_and_controls.sql"
    )
    malformed_control_type = "v_type text := upper(trim(coalesce(p_control_type, ''));"
    corrected_control_type = "v_type text := upper(trim(coalesce(p_control_type, '')));"
    if malformed_control_type not in operations_controls:
        raise RuntimeError(
            "Expected recovered operations-control syntax defect was not found."
        )
    operations_controls = operations_controls.replace(
        malformed_control_type,
        corrected_control_type,
        1,
    )
    write(
        "20260819009000_local_operations_incidents_controls.sql",
        operations_controls,
    )

    copy_as(
        RECOVERY / "20260825124753_recover_hardened_support_case_persistence.sql",
        "20260819010000_local_support_cases.sql",
    )
    copy_as(
        RECOVERY / "20260825131018_recover_hardened_editorial_notice_workflow.sql",
        "20260819011000_local_editorial_notice_workflow.sql",
    )
    copy_as(
        SOURCE / "migrations" / "20260825083000_fix_analytics_and_staff_preferences.sql",
        "20260819012000_local_analytics_staff_preferences.sql",
    )
    copy_as(
        RECOVERY / "20260825180701_canonical_production_reconciliation_core.sql",
        "20260819013000_local_reconciliation_core.sql",
    )

    # Replay the canonical post-recovery source chain unchanged. Earlier 2026082522-2524
    # source-only release-candidate files were never recorded in the recovered Non-Production
    # migration ledger and conflict with the canonical reconstructed schema.
    cutoff = 20260826033632
    for source in sorted((SOURCE / "migrations").glob("*.sql")):
        match = re.match(r"^(\d{14})_", source.name)
        if not match or int(match.group(1)) < cutoff:
            continue
        shutil.copy2(source, MIGRATIONS / source.name)

    # The current protected alert scheduler intentionally fails closed unless all four
    # Vault names exist. CI supplies synthetic, non-routable local-only values.
    write(
        "20260828092500_local_scheduler_vault_placeholders.sql",
        r"""
create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron with schema pg_catalog;

do $$
begin
  if not exists (select 1 from vault.secrets where name = 'rtc_alert_dispatch_secret') then
    perform vault.create_secret(
      'local-dispatch-secret-00000000000000000000000000000000',
      'rtc_alert_dispatch_secret',
      'Disposable local verification only'
    );
  end if;
  if not exists (select 1 from vault.secrets where name = 'rtc_alert_scheduler_project_url') then
    perform vault.create_secret(
      'https://local-verification.supabase.co',
      'rtc_alert_scheduler_project_url',
      'Non-routable local verification URL'
    );
  end if;
  if not exists (select 1 from vault.secrets where name = 'rtc_alert_scheduler_publishable_key') then
    perform vault.create_secret(
      'local-publishable-placeholder',
      'rtc_alert_scheduler_publishable_key',
      'Disposable local verification only'
    );
  end if;
  if not exists (select 1 from vault.secrets where name = 'rtc_alert_scheduler_legacy_anon_jwt') then
    perform vault.create_secret(
      'local-legacy-jwt-placeholder',
      'rtc_alert_scheduler_legacy_anon_jwt',
      'Disposable local verification only'
    );
  end if;
end
$$;
""",
    )

    forbidden = (
        "pbzzfzfgwzwdstvnwzqu",
        "eqwstpdjoineycrkhpht",
        "sb_publishable_",
    )
    for migration in MIGRATIONS.glob("*.sql"):
        text = executable_sql(read(migration))
        for literal in forbidden:
            if literal in text:
                raise RuntimeError(f"Forbidden environment-specific runtime literal in {migration.name}: {literal}")

    print(f"Built disposable Supabase verification project with {len(list(MIGRATIONS.glob('*.sql')))} migrations.")


if __name__ == "__main__":
    build()
