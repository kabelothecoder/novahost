-- ═══════════════════════════════════════════════════════════════════
-- Server-side signal execution.
--
-- WHY THIS EXISTS
--
-- Execution lived on the handset, and a handset is not awake. The Android
-- app holds a foreground service and survives; the iOS build is a PWA and
-- its timers stop the moment Safari is backgrounded or the phone locks.
-- `claim-signals` retires anything older than five minutes as stale, so a
-- phone in a pocket does not execute late, it MISSES -- which for a
-- copy-trading product is the whole feature failing silently.
--
-- Moving execution here makes delivery independent of any device being
-- awake. It also fixes the same problem on Android, where doze has always
-- been able to cost a trade.
--
-- SAFETY: HOW THIS CANNOT DOUBLE-TRADE
--
-- The server claims through the SAME mechanism the handsets do -- an INSERT
-- into `signal_deliveries`, unique on (signal_id, license_id). Whichever of
-- the server and the phone inserts first wins; the loser's insert is a
-- no-op and it does nothing. There is no new race because there is no new
-- claim path.
--
-- SAFETY: NOTHING TRADES UNTIL SOMEONE SAYS SO
--
-- `auto_execute` defaults to FALSE on every licence, including the 56 that
-- already exist. Deploying this changes no behaviour for anyone. A licence
-- trades server-side only once it is explicitly opted in.
-- ═══════════════════════════════════════════════════════════════════

create extension if not exists pg_cron;
create extension if not exists pg_net;

-- ── Opt-in flag ────────────────────────────────────────────────────
alter table public.licenses
  add column if not exists auto_execute boolean not null default false;

comment on column public.licenses.auto_execute is
  'When true, the server executes this licence''s signals without the handset being awake. Default false: turning it on is a deliberate act.';

-- ── Audit ──────────────────────────────────────────────────────────
--
-- NOT `trade_logs`. That table requires a NOT NULL `pl`, and a position that
-- was opened one second ago has no profit or loss -- writing 0 would record
-- a break-even result for every trade the moment it opened. It is also
-- empty with no writer anywhere, so nothing depends on its shape.
--
-- This records the ATTEMPT, which is the thing that was previously
-- unknowable: `metacopier-execute` returns once and never mentions the
-- order again, so a refused signal left no trace at all.
create table if not exists public.signal_executions (
  id           uuid primary key default gen_random_uuid(),
  signal_id    uuid not null references public.signals(id) on delete cascade,
  license_id   uuid not null references public.licenses(id) on delete cascade,
  -- 'placed'  the broker accepted the order
  -- 'refused' the executor or the broker said no, with a reason
  -- 'error'   the attempt itself failed (network, timeout)
  -- 'missed'  claimed but too old to trade, retired rather than fired late
  status       text not null,
  code         text,
  detail       text,
  pair         text,
  side         text,
  volume       numeric,
  sl           numeric,
  tp           numeric,
  executed_at  timestamptz not null default now()
);

create index if not exists signal_executions_license_idx
  on public.signal_executions (license_id, executed_at desc);
create index if not exists signal_executions_signal_idx
  on public.signal_executions (signal_id);

-- Service role only. These rows name a licence and what it traded; app users
-- authenticate by licence key and have no auth.users row to scope against,
-- so there is no policy that could safely expose them to the anon role.
-- Reads go through an edge function that resolves the licence itself.
alter table public.signal_executions enable row level security;
revoke all on public.signal_executions from anon, authenticated;

-- ── The shared secret the scheduler proves it knows ────────────────
--
-- `auto-execute-signals` places real orders, so it must not be callable by
-- anyone who can guess its URL. It cannot be verify_jwt:true -- pg_net has
-- no user to sign as -- and its own env vars are not settable from here.
--
-- So the caller proves itself with a secret that lives in a table only the
-- service role can read. pg_cron sends it as a header; the function reads
-- the same row with its service-role client and compares. No key material
-- is embedded in the function or in this migration.
create table if not exists public.internal_secrets (
  name       text primary key,
  secret     text not null,
  created_at timestamptz not null default now()
);

alter table public.internal_secrets enable row level security;
revoke all on public.internal_secrets from anon, authenticated;

insert into public.internal_secrets (name, secret)
values ('auto_execute_cron', encode(gen_random_bytes(32), 'hex'))
on conflict (name) do nothing;

-- ── How the function gets called ───────────────────────────────────
create or replace function public.trigger_auto_execute()
returns void
language plpgsql
security definer
set search_path = public, extensions, net
as $$
declare
  v_secret text;
begin
  select secret into v_secret
    from public.internal_secrets
   where name = 'auto_execute_cron';

  if v_secret is null then
    raise warning '[auto-execute] no cron secret configured; skipping';
    return;
  end if;

  perform net.http_post(
    url     := 'https://epulmnfbxjmaimefhofp.supabase.co/functions/v1/auto-execute-signals',
    headers := jsonb_build_object(
                 'Content-Type',  'application/json',
                 'x-cron-secret', v_secret
               ),
    body    := '{}'::jsonb,
    -- Fire and forget. pg_net is async, so this returns immediately and
    -- never holds the INSERT that triggered it.
    timeout_milliseconds := 8000
  );
end;
$$;

revoke all on function public.trigger_auto_execute() from anon, authenticated;

-- ── Latency: fire on the signal itself ─────────────────────────────
--
-- A minute of cron latency on a market order is real slippage, so the
-- INSERT itself kicks the executor. pg_net is asynchronous, so the mentor's
-- broadcast is not held waiting for a broker. Measured at ~0.4s from INSERT
-- to a recorded execution attempt.
create or replace function public.on_signal_inserted()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  -- Never let a failure here roll back the signal. A signal that was not
  -- executed is recoverable by the cron sweep below; a signal that was
  -- never RECORDED is gone.
  begin
    perform public.trigger_auto_execute();
  exception when others then
    raise warning '[auto-execute] trigger failed for signal %: %', new.id, sqlerrm;
  end;
  return new;
end;
$$;

drop trigger if exists signals_auto_execute on public.signals;
create trigger signals_auto_execute
  after insert on public.signals
  for each row execute function public.on_signal_inserted();

-- ── Reliability: sweep for anything the trigger missed ─────────────
--
-- The trigger is the fast path and pg_net is fire-and-forget, so a dropped
-- request loses the trade with nothing to notice it. This is the net under
-- it. Every two minutes against a five-minute freshness window means a
-- missed request has two further chances before the signal goes stale.
select cron.unschedule('auto-execute-signals')
  where exists (select 1 from cron.job where jobname = 'auto-execute-signals');

select cron.schedule(
  'auto-execute-signals',
  '*/2 * * * *',
  $$ select public.trigger_auto_execute(); $$
);
