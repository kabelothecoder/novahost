-- Several broker accounts under one licence key.
--
-- Until now a licence held exactly one link, in
-- licenses.metadata (bridge_provider / bridge_account_id). This table makes
-- the link a row, so one key can trade N accounts (e.g. a personal account
-- plus two prop-firm challenges), each on its own MetaRPC terminal.
--
-- metadata stays the record for the PRIMARY account during rollout, so every
-- function that reads it keeps working; the backfill below copies it in.

create table if not exists public.license_broker_accounts (
  id               uuid primary key default gen_random_uuid(),
  license_id       uuid not null references public.licenses(id) on delete cascade,
  provider         text not null check (provider in ('metacopier', 'metarpc')),
  -- MetaRPC terminalInstanceGuid / MetaCopier account id.
  bridge_account_id text not null,
  platform         text not null default 'MT5' check (platform in ('MT4', 'MT5')),
  login            text not null,
  server           text not null,
  label            text,
  is_primary       boolean not null default false,
  -- Per-account switches: a challenge account can be paused without
  -- touching the personal one.
  auto_execute     boolean not null default true,
  -- Lot size scaling against the signal's plan (1 = as planned).
  lot_multiplier   numeric not null default 1 check (lot_multiplier > 0 and lot_multiplier <= 10),
  -- How this broker spells our symbols, learnt from fills. Per ACCOUNT, not
  -- per licence: an IFX account's XAUUSD.ifx must never be sent to Exness.
  -- The primary row is not read for this; the primary keeps using
  -- licenses.metadata.symbol_map until the rollout finishes.
  symbol_map       jsonb not null default '{}'::jsonb,
  symbol_suffix    text,
  status           text not null default 'connecting',
  last_health_at   timestamptz,
  created_at       timestamptz not null default now(),
  disconnected_at  timestamptz,
  unique (provider, bridge_account_id)
);

-- The same broker login live twice under one key would double every trade.
-- Live rows only, so a removed account can be added back.
create unique index if not exists license_broker_accounts_one_login
  on public.license_broker_accounts (license_id, lower(server), login)
  where disconnected_at is null;

-- At most one primary per licence.
create unique index if not exists license_broker_accounts_one_primary
  on public.license_broker_accounts (license_id) where is_primary;

create index if not exists license_broker_accounts_live
  on public.license_broker_accounts (license_id) where disconnected_at is null;

-- How many accounts a key may link. Default 1 keeps today's behaviour;
-- the add-on purchase raises it.
alter table public.licenses
  add column if not exists max_broker_accounts integer not null default 1
  check (max_broker_accounts between 1 and 10);

alter table public.license_broker_accounts enable row level security;

-- Edge functions use the service role; handsets never read this directly.
-- The licence owner may see their own rows (portal / web).
create policy "owner reads own broker accounts"
  on public.license_broker_accounts for select
  using (exists (
    select 1 from public.licenses l
    where l.id = license_id and l.user_id = auth.uid()
  ));

-- Backfill: every licence linked today becomes its primary row.
insert into public.license_broker_accounts
  (license_id, provider, bridge_account_id, platform, login, server, is_primary, status)
select
  l.id,
  coalesce(nullif(l.metadata->>'bridge_provider', ''), 'metacopier'),
  coalesce(nullif(l.metadata->>'bridge_account_id', ''), l.metadata->>'metacopier_account_id'),
  case when upper(coalesce(l.metadata->>'platform', 'MT5')) like '%4%' then 'MT4' else 'MT5' end,
  -- Live keys as of 2026-10-23: `username` is the broker login, `broker_server` the server.
  coalesce(l.metadata->>'username', ''),
  coalesce(l.metadata->>'broker_server', ''),
  true,
  coalesce(l.metadata->>'bridge_status', l.metadata->>'metacopier_status', 'connected')
from public.licenses l
where coalesce(nullif(l.metadata->>'bridge_account_id', ''), l.metadata->>'metacopier_account_id') is not null
on conflict do nothing;

-- Which account an execution went to, now that a licence can have several.
alter table public.signal_executions
  add column if not exists broker_account_id text;
