begin;

-- Forward reconciliation for the actor foreign key introduced by the Edge rate-limit contract.
-- IF NOT EXISTS keeps fresh-reset and already-reconciled environments safe.
create index if not exists edge_function_rate_limits_actor_id_idx
  on public.edge_function_rate_limits(actor_id);

commit;
