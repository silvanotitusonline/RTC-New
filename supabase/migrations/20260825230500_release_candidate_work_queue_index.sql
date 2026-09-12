-- Cover the remaining assigned-work foreign-key path surfaced by the staging advisor.
create index if not exists idx_operational_work_items_assigned_to
  on public.operational_work_items(assigned_to);
