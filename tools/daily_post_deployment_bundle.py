#!/usr/bin/env python3
"""Render only the reviewed Daily Post feature migrations for existing hosted RTC.

No historical recovery or unrelated enterprise migrations are deployed by this tool.
The output is one transaction so no partially secured feature becomes visible.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / 'supabase/migrations'


def bundle():
    names = [
        '20260912010000_daily_post_core.sql',
        '20260912010500_daily_post_storage.sql',
        '20260912010750_daily_post_data_api_grants.sql',
        '20260912011000_daily_post_security_and_workflows.sql',
        '20260912011200_daily_post_security_corrections.sql',
        '20260912011500_daily_post_storage_security.sql',
        '20260912012000_daily_post_scheduler_activation_and_comment_delete.sql',
        '20260913005405_daily_post_live_delivery_readiness.sql',
    ]
    sections = [re.sub(r'(?im)^(BEGIN|COMMIT);\s*$', '', (MIGRATIONS/name).read_text()) for name in names]
    hardening = (MIGRATIONS/'20260914000006_reconcile_definer_authority.sql').read_text()
    sections.append(hardening[hardening.index('-- Storage policies'):].removesuffix('COMMIT;\n'))
    sections.append('REVOKE ALL ON FUNCTION public.daily_post_comment_count_sync() FROM PUBLIC,anon,authenticated;\n'
                    'REVOKE ALL ON FUNCTION public.daily_post_require_editor() FROM PUBLIC,anon,authenticated;')
    return 'BEGIN;\n'+'\n'.join(sections)+'\nCOMMIT;\n'


if __name__ == '__main__':
    print(bundle())
