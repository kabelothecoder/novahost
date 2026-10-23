-- Raw refusals from the trading provider (MetaCopier), kept for operators.
--
-- The app deliberately shows users a generic "unavailable" message, and edge
-- function logs are not queryable from our tooling, so the real reason
-- (e.g. ACCOUNT_LIMIT_PER_PROJECT_REACHED_TRIAL|allowed=10|needed=160) was
-- invisible. Service role only: RLS on, no policies. Holds no credentials.
create table if not exists public.provider_refusals (
  id          bigint generated always as identity primary key,
  created_at  timestamptz not null default now(),
  source      text not null,
  http_status int,
  code        text,
  detail      text,
  context     jsonb
);

alter table public.provider_refusals enable row level security;

create index if not exists provider_refusals_created_at_idx
  on public.provider_refusals (created_at desc);
