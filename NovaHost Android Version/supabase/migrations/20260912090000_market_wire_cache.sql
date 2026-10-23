-- Market wire cache: one row per upstream feed.
--
-- The News and Events sections on Trading Symbols read from free public
-- sources (FairEconomy's weekly calendar XML, public forex newswire RSS).
-- Those are courtesy endpoints with no contract and no rate-limit headers, so
-- every handset hitting them directly on every screen open is both rude and
-- fragile -- one popular hour and the source starts refusing everyone.
--
-- This table is the shared buffer. `market-wire` serves from it while the row
-- is fresh and only reaches upstream when it is not, so N users cost one fetch
-- per TTL rather than N per screen open.
--
-- Deliberately NOT per-user and NOT per-symbol: the upstream payload is the
-- same worldwide feed for everybody, and filtering to a licence's symbols is a
-- cheap in-memory pass at serve time. Caching the filtered result instead would
-- multiply the row count by every distinct symbol set in the fleet and cache
-- almost nothing.
create table if not exists public.market_wire_cache (
  feed        text primary key,          -- 'news' | 'events'
  payload     jsonb not null default '[]'::jsonb,
  fetched_at  timestamptz not null default now(),
  source      text,                      -- which upstream answered, for triage
  ok          boolean not null default true
);

comment on table public.market_wire_cache is
  'Shared cache for the public news/economic-calendar feeds served by market-wire. One row per feed, not per user.';

-- No RLS policies on purpose: the table is unreachable from the client.
--
-- `market-wire` reads and writes it under the service role, which bypasses RLS
-- entirely. Enabling RLS with no policy is therefore the correct end state --
-- it denies anon and authenticated outright while leaving the function
-- unaffected. Granting the app a direct read would put an uncontrolled fetch
-- path back and defeat the point of the cache.
alter table public.market_wire_cache enable row level security;

revoke all on public.market_wire_cache from anon, authenticated;
