BEGIN;

-- Keep engagement mutations behind caller-bound RPCs. Direct table writes would
-- bypass source visibility and the aggregate counter maintained by these RPCs.
REVOKE INSERT, DELETE ON public.community_bookmarks FROM authenticated, anon, PUBLIC;

CREATE OR REPLACE FUNCTION public.bookmark_community_post(p_post_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_count integer;
BEGIN
  IF v_actor IS NULL OR NOT private.access_session_is_current() THEN
    RAISE EXCEPTION 'AUTH_REQUIRED';
  END IF;
  -- Serialize all bookmark/repost changes to this source before inspecting it.
  PERFORM 1 FROM public.community_posts WHERE id=p_post_id FOR UPDATE;
  IF NOT FOUND OR NOT private.is_public_community_post(p_post_id) THEN
    RAISE EXCEPTION 'POST_NOT_AVAILABLE';
  END IF;
  PERFORM private.ensure_community_profile_for_account(v_actor);
  INSERT INTO public.community_bookmarks(user_id,post_id) VALUES(v_actor,p_post_id)
    ON CONFLICT (user_id,post_id) DO NOTHING;
  SELECT count(*)::integer INTO v_count FROM public.community_bookmarks WHERE post_id=p_post_id;
  UPDATE public.community_posts SET bookmark_count=v_count WHERE id=p_post_id;
  RETURN jsonb_build_object('bookmarked',true,'bookmark_count',v_count);
END;
$$;

CREATE OR REPLACE FUNCTION public.unbookmark_community_post(p_post_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_count integer;
  v_visible boolean;
BEGIN
  IF v_actor IS NULL OR NOT private.access_session_is_current() THEN
    RAISE EXCEPTION 'AUTH_REQUIRED';
  END IF;
  PERFORM 1 FROM public.community_posts WHERE id=p_post_id FOR UPDATE;
  v_visible := private.is_public_community_post(p_post_id);
  -- A resident may always remove their own bookmark after a source is hidden.
  -- Never disclose that source's aggregate count or distinguish nonexistent IDs.
  DELETE FROM public.community_bookmarks WHERE user_id=v_actor AND post_id=p_post_id;
  SELECT count(*)::integer INTO v_count FROM public.community_bookmarks WHERE post_id=p_post_id;
  UPDATE public.community_posts SET bookmark_count=v_count WHERE id=p_post_id;
  RETURN jsonb_build_object('bookmarked',false,'bookmark_count',CASE WHEN v_visible THEN v_count ELSE 0 END);
END;
$$;

CREATE OR REPLACE FUNCTION public.repost_community_post(p_post_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_existing uuid;
  v_count integer;
  v_reposted boolean;
BEGIN
  IF v_actor IS NULL OR NOT private.access_session_is_current() THEN
    RAISE EXCEPTION 'AUTH_REQUIRED';
  END IF;
  PERFORM 1 FROM public.community_posts WHERE id=p_post_id FOR UPDATE;
  IF NOT FOUND OR NOT private.is_public_community_post(p_post_id) THEN
    RAISE EXCEPTION 'POST_NOT_AVAILABLE';
  END IF;
  PERFORM private.ensure_community_profile_for_account(v_actor);
  IF NOT private.community_guidelines_accepted() THEN RAISE EXCEPTION 'GUIDELINES_NOT_ACCEPTED'; END IF;
  -- The source-row lock also serializes concurrent toggles for the same actor.
  SELECT id INTO v_existing FROM public.community_posts
    WHERE author_id=v_actor AND repost_of_id=p_post_id AND deleted_at IS NULL
    ORDER BY id LIMIT 1;
  IF v_existing IS NOT NULL THEN
    UPDATE public.community_posts SET deleted_at=now()
      WHERE author_id=v_actor AND repost_of_id=p_post_id AND deleted_at IS NULL;
    v_reposted := false;
  ELSE
    INSERT INTO public.community_posts(author_id,body,state,is_locked,report_count,repost_of_id)
      VALUES(v_actor,'','PUBLISHED',false,0,p_post_id);
    v_reposted := true;
  END IF;
  SELECT count(*)::integer INTO v_count FROM public.community_posts
    WHERE repost_of_id=p_post_id AND deleted_at IS NULL;
  UPDATE public.community_posts SET repost_count=v_count WHERE id=p_post_id;
  RETURN jsonb_build_object('reposted',v_reposted,'repost_count',v_count);
END;
$$;

REVOKE ALL ON FUNCTION public.bookmark_community_post(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.unbookmark_community_post(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.repost_community_post(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.bookmark_community_post(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.unbookmark_community_post(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.repost_community_post(uuid) TO authenticated;

COMMIT;
