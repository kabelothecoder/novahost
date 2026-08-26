-- Per-symbol trading configuration, as set by the subscriber on the device.
--
-- Distinct from licenses.allowed_symbols, which is the MENTOR's allowance and
-- is not the subscriber's to change. This table is the other half of that
-- permission: of the symbols the robot permits, which ones this licence holder
-- actually wants traded, at what size, and how many at once.
--
-- It exists server-side rather than only on the handset because the caps have
-- to be enforced where the order is placed. A device deciding its own lot size
-- is fine until the device is out of date, wound back, or tampered with, and
-- "no more than three XAUUSD positions" is not a promise a client can keep.

create table if not exists public.license_symbol_config (
  id          uuid primary key default gen_random_uuid(),
  license_id  uuid not null references public.licenses(id) on delete cascade,
  symbol      text not null,

  -- The subscriber's tick. False means "the robot may trade this, I do not
  -- want it" -- a deliberate opt-out, not an absence of configuration.
  enabled     boolean not null default true,

  -- Lots per trade when smart_lot is false.
  lot         numeric(10,2) not null default 0.05,

  -- How many positions on this symbol may be open at once.
  max_trades  integer not null default 2,

  -- True when the size comes from the trade calculator's risk budget rather
  -- than the stepper, in which case `lot` is the last manual value, kept so
  -- toggling smart lot off restores what the user had set.
  smart_lot   boolean not null default true,

  updated_at  timestamptz not null default now(),

  constraint license_symbol_config_unique unique (license_id, symbol),
  constraint license_symbol_config_lot_range check (lot > 0 and lot <= 50),
  constraint license_symbol_config_trades_range check (max_trades between 1 and 20)
);

create index if not exists license_symbol_config_license_idx
  on public.license_symbol_config (license_id);

-- Lookup is always (licence, symbol) on the execution path, which is the hot
-- one: every order placed reads exactly this row before it goes to the broker.
create index if not exists license_symbol_config_lookup_idx
  on public.license_symbol_config (license_id, symbol);

alter table public.license_symbol_config enable row level security;

-- Mentors may read the configuration on licences they issued, so the portal can
-- show what a subscriber has actually enabled.
--
-- There is deliberately no insert/update/delete policy and no policy at all for
-- `anon`. Writes arrive only through the sync-symbol-config edge function using
-- the service role, which bypasses RLS -- so a handset holding nothing but a
-- licence key cannot reach this table directly, and the function is the single
-- place the licence is checked.
drop policy if exists "owners read symbol config" on public.license_symbol_config;
create policy "owners read symbol config"
  on public.license_symbol_config
  for select
  to authenticated
  using (
    exists (
      select 1 from public.licenses l
      where l.id = license_symbol_config.license_id
        and l.owner_id = auth.uid()
    )
  );

-- Merges a risk profile into licenses.metadata without disturbing what is
-- already there.
--
-- A read-modify-write from the edge function would race metacopier-connect,
-- which writes metacopier_account_id into the same column -- and losing that
-- key means the licence silently stops being able to trade at all. The `||`
-- happens inside one statement so there is no window to lose a concurrent
-- write.
create or replace function public.set_license_risk_profile(
  p_license_id uuid,
  p_profile    jsonb
)
returns void
language sql
security definer
set search_path = public
as $$
  update public.licenses
     set metadata   = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('risk_profile', p_profile),
         updated_at = now()
   where id = p_license_id;
$$;

revoke all on function public.set_license_risk_profile(uuid, jsonb) from public;
revoke all on function public.set_license_risk_profile(uuid, jsonb) from anon;
revoke all on function public.set_license_risk_profile(uuid, jsonb) from authenticated;
