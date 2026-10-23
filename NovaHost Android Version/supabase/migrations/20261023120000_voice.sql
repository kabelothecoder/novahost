-- Phase 4 voice: per-licence daily usage (the cap voice-turn enforces) and
-- spoken trade ideas that only a tap can place. Both are written by edge
-- functions with the service role; app users hold a licence key, not a
-- session, so neither table gets an anon or authenticated policy.

create table if not exists public.voice_usage (
  license_id uuid not null references public.licenses(id) on delete cascade,
  day date not null default (now() at time zone 'utc')::date,
  turns int not null default 0,
  asr_seconds numeric not null default 0,
  tts_bytes int not null default 0,
  primary key (license_id, day)
);
alter table public.voice_usage enable row level security;

create table if not exists public.voice_trade_ideas (
  id uuid primary key default gen_random_uuid(),
  license_id uuid not null references public.licenses(id) on delete cascade,
  pair text not null,
  side text not null check (side in ('BUY', 'SELL')),
  volume numeric,
  sl numeric,
  tp numeric,
  reason text,
  created_at timestamptz not null default now(),
  -- 30 s review window, then the idea is dead. Never auto-placed.
  expires_at timestamptz not null default now() + interval '30 seconds',
  confirmed_at timestamptz,
  result jsonb
);
alter table public.voice_trade_ideas enable row level security;
create index if not exists voice_trade_ideas_license_idx on public.voice_trade_ideas (license_id, created_at desc);

-- A Fish Audio model id per robot. Empty = the NovaHost robot default.
alter table public.expert_advisors add column if not exists voice_id text;

-- Atomic bump so two quick turns cannot both slip under the cap.
create or replace function public.voice_usage_bump(p_license uuid, p_asr numeric, p_tts int)
returns int language sql security definer set search_path = public as $$
  insert into voice_usage (license_id, turns, asr_seconds, tts_bytes)
  values (p_license, 1, p_asr, p_tts)
  on conflict (license_id, day) do update
    set turns = voice_usage.turns + 1,
        asr_seconds = voice_usage.asr_seconds + excluded.asr_seconds,
        tts_bytes = voice_usage.tts_bytes + excluded.tts_bytes
  returning turns;
$$;
revoke all on function public.voice_usage_bump(uuid, numeric, int) from public, anon, authenticated;
