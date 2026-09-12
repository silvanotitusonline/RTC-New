
begin;

-- a. Civic Issue Clustering (For Heat-mapping)
create table if not exists public.civic_issue_clusters (
    id uuid primary key default gen_random_uuid(),
    cluster_name text,
    category_id uuid references public.civic_report_categories(id),
    centroid geography(POINT, 4326),
    report_count integer default 0,
    severity_index float default 0.0,
    detected_at timestamp with time zone default now(),
    last_updated_at timestamp with time zone default now()
);

-- b. Sentiment Tracking for Community Mood
create table if not exists public.community_sentiment_logs (
    id uuid primary key default gen_random_uuid(),
    post_id uuid references public.community_posts(id) on delete cascade,
    sentiment_score float, -- -1.0 (Very Negative) to 1.0 (Very Positive)
    dominant_emotion text, -- 'Angry', 'Happy', 'Frustrated', 'Hopeful'
    analyzed_at timestamp with time zone default now()
);

-- c. Government SLA Tracking (Response Times)
create table if not exists public.government_sla_metrics (
    category_id uuid references public.civic_report_categories(id),
    avg_resolution_time interval,
    current_backlog_count integer,
    last_calculated_at timestamp with time zone default now(),
    primary key (category_id)
);

-- d. AI-Driven Routing Logic
create or replace function public.route_civic_report_to_dept(p_category_id uuid)
returns text as $$
begin
    -- Simple mapping for now, but can be expanded to a full routing table
    return case 
        when p_category_id = '...' then 'DEPT_INFRASTRUCTURE'
        when p_category_id = '...' then 'DEPT_SANITATION'
        else 'DEPT_GENERAL_ADMIN'
    end;
end;
$$ language plpgsql security definer;

commit;
