begin;

alter table public.community_posts
  add column if not exists client_post_id uuid;

create unique index if not exists community_posts_author_client_post_id_uq
  on public.community_posts(author_id, client_post_id)
  where client_post_id is not null;

create or replace function public.create_community_post_draft(
  p_body text default '',
  p_client_post_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_post_id uuid;
    v_sanitized text;
begin
    if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
    v_sanitized := public.sanitize_input_text(p_body);
    if char_length(v_sanitized) > 280 then raise exception 'INVALID_POST'; end if;
    perform private.ensure_community_profile_for_account(v_user_id);
    if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;

    if p_client_post_id is not null then
      select id into v_post_id
      from public.community_posts
      where author_id = v_user_id
        and client_post_id = p_client_post_id
        and deleted_at is null
      limit 1;
      if v_post_id is not null then return v_post_id; end if;
    end if;

    insert into public.community_posts(author_id, body, client_post_id, state, is_locked, report_count)
    values (v_user_id, v_sanitized, p_client_post_id, 'DRAFT', false, 0)
    returning id into v_post_id;
    return v_post_id;
end;
$$;

revoke all on function public.create_community_post_draft(text, uuid) from public, anon;
grant execute on function public.create_community_post_draft(text, uuid) to authenticated, service_role;

commit;
