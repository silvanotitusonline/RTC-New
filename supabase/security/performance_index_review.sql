-- RTC Community Production index review
-- Project ref: pbzzfzfgwzwdstvnwzqu
-- Read-only: this reports candidates; it does not create or remove indexes.

with foreign_keys as (
  select
    n.nspname as schema_name,
    child.oid as child_oid,
    child.relname as table_name,
    c.conname as constraint_name,
    c.conkey as constrained_columns,
    pg_get_constraintdef(c.oid) as constraint_definition
  from pg_constraint c
  join pg_class child on child.oid = c.conrelid
  join pg_namespace n on n.oid = child.relnamespace
  where c.contype = 'f'
    and n.nspname = 'public'
), coverage as (
  select
    fk.*,
    exists (
      select 1
      from pg_index i
      where i.indrelid = fk.child_oid
        and i.indisvalid
        and i.indisready
        and (i.indkey::smallint[] @> fk.constrained_columns::smallint[])
    ) as has_covering_index
  from foreign_keys fk
), table_stats as (
  select
    s.relname as table_name,
    s.n_live_tup::bigint as estimated_rows,
    s.n_mod_since_analyze::bigint as rows_changed_since_analyze,
    s.last_analyze,
    s.last_autoanalyze
  from pg_stat_user_tables s
  where s.schemaname = 'public'
)
select
  c.schema_name,
  c.table_name,
  c.constraint_name,
  c.constraint_definition,
  coalesce(t.estimated_rows, 0) as estimated_rows,
  coalesce(t.rows_changed_since_analyze, 0) as rows_changed_since_analyze,
  t.last_analyze,
  t.last_autoanalyze,
  c.has_covering_index,
  case
    when c.has_covering_index then 'covered'
    when coalesce(t.estimated_rows, 0) >= 10000 then 'high-priority-review'
    when coalesce(t.estimated_rows, 0) >= 1000 then 'review-before-growth'
    else 'defer-until-workload'
  end as recommendation
from coverage c
left join table_stats t using (table_name)
where not c.has_covering_index
order by coalesce(t.estimated_rows, 0) desc, c.table_name, c.constraint_name
limit 100;

select
  s.schemaname,
  s.relname as table_name,
  s.indexrelname as index_name,
  s.idx_scan,
  s.idx_tup_read,
  s.idx_tup_fetch,
  pg_size_pretty(pg_relation_size(s.indexrelid)) as index_size
from pg_stat_user_indexes s
where s.schemaname = 'public'
order by s.idx_scan asc, pg_relation_size(s.indexrelid) desc
limit 100;
