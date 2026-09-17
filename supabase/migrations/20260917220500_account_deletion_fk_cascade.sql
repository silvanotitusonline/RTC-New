begin;

-- The deletion worker removes auth.users only after storage cleanup. The request
-- itself must not prevent that final Auth Admin deletion.
alter table public.account_deletion_requests
drop constraint if exists account_deletion_requests_requester_id_fkey;

alter table public.account_deletion_requests
add constraint account_deletion_requests_requester_id_fkey
foreign key (requester_id) references auth.users(id) on delete cascade;

commit;
