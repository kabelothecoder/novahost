-- iOS: R349 / 30-day access, licence claim codes, and scan credits.
--
-- ## Monthly access
--
-- No new columns. A monthly purchase is `is_premium = true, is_lifetime = false`
-- with `subscription_expiry` set, which check-subscription-status has always
-- honoured ("Non-lifetime records may still carry an expiry"). payfast-webhook
-- extends the expiry from whichever is later, now or the current expiry, so
-- paying early never loses days. A lifetime row is never downgraded.
--
-- ## Claim codes
--
-- The iOS flow is now pay first, key second. A paid handset asks
-- `request-claim-code` for a short code that lives 30 minutes. The user types it
-- on a mentor's website, the website calls `redeem-claim-code` with its mentor
-- API key, and a fresh licence is minted on that mentor's robot. The phone polls
-- the code and fills the key in itself once it is redeemed.
--
-- Keys are only minted for someone who has already paid, which is the point:
-- no more keys handed to people who never pay.
--
-- ## Who can read this
--
-- Nobody but the service role. The app has no auth session (it authenticates by
-- email + device), and a claim code is a capability to mint a licence.

create table if not exists public.licence_claim_codes (
  id                 uuid primary key default gen_random_uuid(),
  /* Stored bare (no dash), upper case, from an alphabet with no 0/O/1/I. */
  code               text not null,
  email              text not null,
  device_id          text not null,
  created_at         timestamptz not null default now(),
  expires_at         timestamptz not null,
  redeemed_at        timestamptz,
  /* The mentor (profiles.id) whose API key redeemed it. */
  redeemed_by        uuid,
  license_id         uuid references public.licenses(id) on delete set null,
  license_key        text,
  /* Wrong-code attempts are counted per API key in mentor_api_keys instead;
     a code itself is never "locked", it just expires. */
  cancelled_at       timestamptz
);

/* A code is unique while it can still be used. Expired codes may collide with
   new ones, which keeps the alphabet short without risking a wrong match. */
create unique index if not exists licence_claim_codes_live_code_idx
  on public.licence_claim_codes (code)
  where redeemed_at is null and cancelled_at is null;

create index if not exists licence_claim_codes_email_idx
  on public.licence_claim_codes (email, created_at desc);

alter table public.licence_claim_codes enable row level security;

comment on table public.licence_claim_codes is
  'iOS pay-first flow: a 30-minute code a paid handset hands to a mentor website, which swaps it for a freshly minted licence via redeem-claim-code.';

-- ── Mentor API keys ────────────────────────────────────────────────────────
-- What a mentor's website sends to redeem-claim-code. Only the SHA-256 is
-- stored; the plaintext is shown once when it is created.

create table if not exists public.mentor_api_keys (
  id               uuid primary key default gen_random_uuid(),
  mentor_id        uuid not null references public.profiles(id) on delete cascade,
  key_hash         text not null unique,
  /* First characters of the plaintext, so a mentor can tell keys apart. */
  key_prefix       text not null,
  label            text,
  created_at       timestamptz not null default now(),
  last_used_at     timestamptz,
  /* Wrong codes sent with this key in the current window; see redeem-claim-code. */
  failed_attempts  integer not null default 0,
  failed_window_at timestamptz,
  revoked_at       timestamptz
);

create index if not exists mentor_api_keys_mentor_idx
  on public.mentor_api_keys (mentor_id) where revoked_at is null;

alter table public.mentor_api_keys enable row level security;

/* A mentor may see (not create or read the hash of) their own keys from the
   portal. Creation goes through a function so the plaintext never touches a
   table. */
drop policy if exists mentor_api_keys_owner_read on public.mentor_api_keys;
create policy mentor_api_keys_owner_read on public.mentor_api_keys
  for select to authenticated
  using (mentor_id = auth.uid());

-- ── Scan credits ───────────────────────────────────────────────────────────
-- R150 buys 20 scans. Active app access also carries one free scan per calendar
-- month (UTC). `has_scanner` (the old R349 unlock) still means unlimited, so
-- nobody who bought it loses anything.

alter table public.subscriptions
  add column if not exists scan_credits integer not null default 0
    check (scan_credits >= 0),
  /* 'YYYY-MM' of the month whose free scan has been used. */
  add column if not exists free_scan_month text;

comment on column public.subscriptions.scan_credits is
  'Paid chart scans left. Bought 20 at a time (SCANS20, R150). Spent by analyze-chart via spend_scan().';
comment on column public.subscriptions.free_scan_month is
  'UTC YYYY-MM whose included free scan has been used. Free scan = one per month while app access is active.';

/**
 * Takes one scan from an email, atomically. Returns the source spent:
 *   'unlimited'  has_scanner (old R349 unlock): nothing is spent
 *   'free'       this month's included scan
 *   'credit'     one paid credit
 *   null         nothing to spend
 *
 * `p_free_ok` is decided by the caller, which knows whether app access is
 * active. Locks the row so two scans racing for the last credit cannot both win.
 */
create or replace function public.spend_scan(p_email text, p_free_ok boolean)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  r      public.subscriptions%rowtype;
  month  text := to_char(now() at time zone 'utc', 'YYYY-MM');
begin
  select * into r from public.subscriptions where email = p_email for update;
  if not found then return null; end if;

  if r.has_scanner then return 'unlimited'; end if;

  if p_free_ok and coalesce(r.free_scan_month, '') <> month then
    update public.subscriptions set free_scan_month = month, updated_at = now() where id = r.id;
    return 'free';
  end if;

  if r.scan_credits > 0 then
    update public.subscriptions set scan_credits = scan_credits - 1, updated_at = now() where id = r.id;
    return 'credit';
  end if;

  return null;
end;
$$;

/** Gives back what spend_scan took, when the scan itself failed. */
create or replace function public.refund_scan(p_email text, p_source text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_source = 'credit' then
    update public.subscriptions set scan_credits = scan_credits + 1, updated_at = now() where email = p_email;
  elsif p_source = 'free' then
    update public.subscriptions set free_scan_month = null, updated_at = now() where email = p_email;
  end if;
end;
$$;

/** Adds bought credits. Upserts, so a pack can land on an email with no row yet. */
create or replace function public.add_scan_credits(p_email text, p_count integer)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  total integer;
begin
  insert into public.subscriptions (email, scan_credits, updated_at)
  values (p_email, p_count, now())
  on conflict (email) do update
    set scan_credits = public.subscriptions.scan_credits + excluded.scan_credits,
        updated_at = now()
  returning scan_credits into total;
  return total;
end;
$$;

revoke all on function public.spend_scan(text, boolean) from public, anon, authenticated;
revoke all on function public.refund_scan(text, text) from public, anon, authenticated;
revoke all on function public.add_scan_credits(text, integer) from public, anon, authenticated;
