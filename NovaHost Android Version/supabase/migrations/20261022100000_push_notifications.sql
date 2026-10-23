-- Push notifications: iOS Web Push, with email for anyone who has not turned push on.
--
-- ## Shape
--
-- Nothing that trades writes a notification itself. `broadcast-signal` and
-- `auto-execute-signals` are left exactly as they are; triggers on the tables
-- they already write (`signals`, `signal_executions`) queue one row per licence
-- in `notifications`, and `send-notifications` drains that queue. A failure to
-- notify can therefore never roll back, delay or double a trade.
--
-- ## Who can read this
--
-- Nobody but the service role. The app authenticates by licence key with no
-- auth session, so it reaches these tables only through `push-subscribe`, which
-- checks the key. A push endpoint is a capability: anyone holding one can send
-- to that phone, so it must never be readable with the anon key.

create table if not exists public.push_subscriptions (
  id               uuid primary key default gen_random_uuid(),
  license_id       uuid not null references public.licenses(id) on delete cascade,
  /* Not unique on its own: one phone can hold several licences (my-licenses),
     and each of them alerts that same phone. */
  endpoint         text not null,
  p256dh           text not null,
  auth             text not null,
  /* The name the user gave their robot on this phone. Alerts speak as it. */
  robot_name       text,
  /* Category switches and quiet hours from Settings › Notifications, as the
     app sends them. Read by the sender; an absent key means "on". */
  prefs            jsonb not null default '{}'::jsonb,
  tz               text,
  user_agent       text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  last_success_at  timestamptz,
  failure_count    integer not null default 0,
  /* Set when the push service says the endpoint is gone (404/410) or the user
     turned notifications off. Kept, not deleted, so a resubscribe is an update. */
  disabled_at      timestamptz,
  unique (license_id, endpoint)
);

create index if not exists push_subscriptions_license_idx
  on public.push_subscriptions (license_id) where disabled_at is null;

create table if not exists public.notifications (
  id            uuid primary key default gen_random_uuid(),
  license_id    uuid not null references public.licenses(id) on delete cascade,
  ea_id         uuid,
  /* wake | signal | placed | refused | target */
  kind          text not null,
  data          jsonb not null default '{}'::jsonb,
  /* One alert per (thing, licence). A retried trigger is a no-op. */
  dedupe_key    text unique,
  /* pending → sending → done | expired | failed */
  status        text not null default 'pending',
  attempts      integer not null default 0,
  push_sent     integer not null default 0,
  email_sent    boolean not null default false,
  error         text,
  created_at    timestamptz not null default now(),
  sent_at       timestamptz
);

create index if not exists notifications_pending_idx
  on public.notifications (created_at) where status in ('pending', 'sending');
create index if not exists notifications_wake_idx
  on public.notifications (ea_id, created_at desc) where kind = 'wake';

alter table public.push_subscriptions enable row level security;
alter table public.notifications      enable row level security;
revoke all on public.push_subscriptions from anon, authenticated;
revoke all on public.notifications      from anon, authenticated;

-- ---------------------------------------------------------------------------
-- Queue: one row per live licence on the robot a signal was sent to.
-- ---------------------------------------------------------------------------
create or replace function public.queue_signal_notifications()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
begin
  -- Never let a failure here roll back the signal, same rule as
  -- on_signal_inserted: an alert that was not sent is a nuisance, a signal
  -- that was never recorded is a missed trade.
  begin
    insert into public.notifications (license_id, ea_id, kind, data, dedupe_key)
    select l.id, l.ea_id, 'signal',
           jsonb_build_object(
             'signal_id', new.id,
             'pair', new.pair,
             'side', upper(coalesce(new.side, new.type, 'BUY')),
             'order_type', coalesce(new.order_type, 'MARKET'),
             'price', new.price,
             'sl', new.sl,
             'tp', new.tp
           ),
           'signal:' || new.id || ':' || l.id
      from public.licenses l
     where l.ea_id::text = new.ea_id
       and l.status = 'active'
       and (l.expires_at is null or l.expires_at > now())
    on conflict (dedupe_key) do nothing;
  exception when others then
    raise warning '[notify] could not queue signal %: %', new.id, sqlerrm;
  end;
  return new;
end;
$$;

drop trigger if exists signals_queue_notifications on public.signals;
create trigger signals_queue_notifications
  after insert on public.signals
  for each row execute function public.queue_signal_notifications();

-- ---------------------------------------------------------------------------
-- Queue: what the server-side executor did with it.
-- `missed` (too old to fire) is not news to the user, so it is not queued.
-- ---------------------------------------------------------------------------
create or replace function public.queue_execution_notification()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
begin
  begin
    if new.status in ('placed', 'refused', 'error') then
      insert into public.notifications (license_id, ea_id, kind, data, dedupe_key)
      select new.license_id, l.ea_id,
             case when new.status = 'placed' then 'placed' else 'refused' end,
             jsonb_build_object(
               'signal_id', new.signal_id,
               'pair', new.pair,
               'side', new.side,
               'volume', new.volume,
               'sl', new.sl,
               'tp', new.tp,
               'code', new.code,
               'detail', new.detail
             ),
             'exec:' || new.id
        from public.licenses l
       where l.id = new.license_id
      on conflict (dedupe_key) do nothing;
    end if;
  exception when others then
    raise warning '[notify] could not queue execution %: %', new.id, sqlerrm;
  end;
  return new;
end;
$$;

drop trigger if exists signal_executions_queue_notification on public.signal_executions;
create trigger signal_executions_queue_notification
  after insert on public.signal_executions
  for each row execute function public.queue_execution_notification();

-- ---------------------------------------------------------------------------
-- Drain: claim a batch for one sender run. SKIP LOCKED so two overlapping runs
-- (trigger + cron) never send the same alert twice.
-- ---------------------------------------------------------------------------
create or replace function public.claim_notifications(p_limit integer default 300)
returns setof public.notifications
language sql
security definer
set search_path to 'public'
as $$
  update public.notifications n
     set status = 'sending', attempts = n.attempts + 1
   where n.id in (
     select id from public.notifications
      where status = 'pending'
         -- A 'sending' row whose run died is picked up again after two minutes.
         or (status = 'sending' and attempts < 3 and created_at < now() - interval '2 minutes')
      order by created_at
      limit p_limit
      for update skip locked
   )
  returning n.*;
$$;

revoke all on function public.claim_notifications(integer) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Kick the sender. Same pattern and same rules as trigger_auto_execute.
-- ---------------------------------------------------------------------------
insert into public.internal_secrets (name, secret)
values ('notify_cron', encode(extensions.gen_random_bytes(32), 'hex'))
on conflict (name) do nothing;

create or replace function public.trigger_send_notifications()
returns void
language plpgsql
security definer
set search_path to 'public', 'extensions', 'net'
as $$
declare
  v_secret text;
begin
  -- The cron runs every minute; an empty queue costs a query, not an invocation.
  if not exists (
    select 1 from public.notifications
     where status = 'pending' or (status = 'sending' and attempts < 3)
  ) then
    return;
  end if;

  select secret into v_secret from public.internal_secrets where name = 'notify_cron';
  if v_secret is null then
    raise warning '[notify] no notify_cron secret configured; skipping';
    return;
  end if;

  perform net.http_post(
    url     := 'https://epulmnfbxjmaimefhofp.supabase.co/functions/v1/send-notifications',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', v_secret),
    body    := '{}'::jsonb,
    timeout_milliseconds := 8000
  );
end;
$$;

revoke all on function public.trigger_send_notifications() from public, anon, authenticated;

create or replace function public.on_notifications_queued()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
begin
  -- An `on conflict do nothing` that inserted nothing still fires a statement
  -- trigger; do not wake the sender for an empty batch.
  if not exists (select 1 from inserted) then
    return null;
  end if;
  begin
    perform public.trigger_send_notifications();
  exception when others then
    raise warning '[notify] kick failed: %', sqlerrm;
  end;
  return null;
end;
$$;

-- Statement level: a signal fanned out to 200 licences is one kick, not 200.
drop trigger if exists notifications_kick_sender on public.notifications;
create trigger notifications_kick_sender
  after insert on public.notifications
  referencing new table as inserted
  for each statement execute function public.on_notifications_queued();

-- The sweep that makes fire-and-forget reliable.
select cron.unschedule('send-notifications')
 where exists (select 1 from cron.job where jobname = 'send-notifications');
select cron.schedule('send-notifications', '* * * * *', $$ select public.trigger_send_notifications(); $$);
