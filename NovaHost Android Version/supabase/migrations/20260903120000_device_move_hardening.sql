-- Device-move hardening for the three once-off products.
--
-- The paywall sells one paid email bound to ONE device, and a R150 move when
-- that device changes. Everything below exists because the move, as built, was
-- cheaper than the rule it was meant to enforce.

-- ---------------------------------------------------------------------------
-- 1. Close the anonymous read on subscriptions
-- ---------------------------------------------------------------------------
--
-- "Allow anonymous subscription check by email" was USING (true) for every
-- role. Not "by email" -- by anyone. The anon key ships inside the APK, so that
-- policy handed any reader the whole table: every customer email and every
-- device_id.
--
-- device_id is not an identifier here, it is the credential. check-subscription-
-- status and analyze-chart both answer "is this email on this device", so an
-- email/device_id pair read out of this table is a working licence -- including
-- for analyze-chart, which spends Anthropic quota on every call.
--
-- Nothing legitimate loses anything. Every reader in the codebase
-- (check-subscription-status, generate-payfast-checkout, payfast-webhook,
-- analyze-chart) connects with the service role, which bypasses RLS entirely.
drop policy if exists "Allow anonymous subscription check by email" on public.subscriptions;

revoke all on public.subscriptions from anon;

-- The self-read policy stays -- but a signed-in user reading their own row must
-- not be able to read the session token or the device binding out of it. RLS
-- picks rows, not columns, so the column list is a grant.
revoke all on public.subscriptions from authenticated;
grant select (id, email, is_premium, is_lifetime, has_scanner, subscription_expiry, created_at, updated_at)
  on public.subscriptions to authenticated;

-- ---------------------------------------------------------------------------
-- 2. Device session token + move accounting
-- ---------------------------------------------------------------------------
--
-- `token` already existed on this table and was never written by anything. It
-- becomes the device SESSION: device_id says which handset is bound, token says
-- which install on that handset is live. Rotating it on a move is what lets a
-- server-side call be refused now rather than at the next full entitlement
-- check.
--
-- The counters are what make the R150 move a device change instead of a
-- business model. Unlimited moves at R150 is a licence-rental product, and it
-- is the one someone reselling access would actually use.
alter table public.subscriptions
  add column if not exists token_issued_at     timestamptz,
  add column if not exists reactivation_count  integer not null default 0,
  add column if not exists last_reactivated_at timestamptz;

comment on column public.subscriptions.token is
  'Device session token. Minted on bind and rotated on every move; the previous device''s copy stops matching.';
comment on column public.subscriptions.reactivation_count is
  'Lifetime count of paid R150 moves. Support resets do not increment this.';

-- ---------------------------------------------------------------------------
-- 3. The ledger
-- ---------------------------------------------------------------------------
--
-- "My app stopped working" is unanswerable today: the move is an UPDATE that
-- overwrites device_id in place, so the handset it moved away from leaves no
-- trace. Support cannot tell a genuine upgrade from a stolen licence from a bug,
-- and neither can we.
create table if not exists public.subscription_device_events (
  id             uuid primary key default gen_random_uuid(),
  email          text not null,
  -- bind          first device claimed the licence
  -- move          paid R150 reactivation
  -- support_reset binding cleared by hand; does not count against the cap
  -- revoke        session invalidated without a rebind
  event          text not null check (event in ('bind', 'move', 'support_reset', 'revoke')),
  old_device_id  text,
  new_device_id  text,
  amount         numeric(10, 2),
  pf_payment_id  text,
  note           text,
  created_at     timestamptz not null default now()
);

create index if not exists subscription_device_events_email_idx
  on public.subscription_device_events (email, created_at desc);

alter table public.subscription_device_events enable row level security;
-- No policies: service role only. The ledger is support tooling, not app data.

-- ---------------------------------------------------------------------------
-- 4. Move tickets
-- ---------------------------------------------------------------------------
--
-- Proof that whoever is paying to move a licence can read the mailbox it
-- belongs to.
--
-- Without this, both the target email (custom_str3) and the target device
-- (custom_str2) come back from the browser under the payer's control, so R150
-- buys the eviction of a stranger: pay with someone else's email in the field,
-- bind their licence to your handset, and they are locked out of an app they
-- paid R599 for. The cap makes it worse -- an attacker can burn a victim's two
-- annual moves as well.
--
-- The code is stored as a SHA-256 hex digest. It is short-lived and this table
-- is service-role only, so this is belt-and-braces rather than load-bearing,
-- but a six-digit code sitting in plaintext in a database is a habit worth not
-- forming.
create table if not exists public.device_move_tickets (
  id                uuid primary key default gen_random_uuid(),
  email             text not null,
  code_hash         text not null,
  target_device_id  text not null,
  attempts          integer not null default 0,
  expires_at        timestamptz not null,
  verified_at       timestamptz,
  consumed_at       timestamptz,
  created_at        timestamptz not null default now()
);

create index if not exists device_move_tickets_email_idx
  on public.device_move_tickets (email, created_at desc);

alter table public.device_move_tickets enable row level security;
-- No policies: service role only.
