-- Every device move now needs an admin's approval before it can be paid for.
--
-- The email code proves the payer can read the licence's mailbox. It does not
-- say WHY a paid licence is changing handsets, and a licence that moves
-- between phones is exactly what resale and account-sharing look like from the
-- outside. So a move now carries a stated reason, lands in the admin console,
-- and only becomes payable once a person has said yes.
--
--   user: reason + email code  ->  ticket verified, approval 'pending'
--   admin: approve / reject    ->  'approved' | 'rejected' (+ note to the user)
--   user: checkout (R150)      ->  only an 'approved' ticket gets a PayFast URL
--   webhook: money lands       ->  only an 'approved' ticket moves the licence
--
-- Approval comes BEFORE payment on purpose. Rejecting after the money has been
-- taken means a refund per rejection.

alter table public.device_move_tickets
  add column if not exists reason          text,
  add column if not exists approval_status text not null default 'pending'
    check (approval_status in ('pending', 'approved', 'rejected', 'cancelled')),
  add column if not exists decided_at      timestamptz,
  add column if not exists decided_by      text,
  add column if not exists decision_note   text;

comment on column public.device_move_tickets.reason is
  'Why the user says the licence is changing device. Required on every new move request.';
comment on column public.device_move_tickets.approval_status is
  'pending until an admin decides. Only approved tickets can be checked out or granted by the webhook.';
comment on column public.device_move_tickets.decision_note is
  'Admin''s message to the user. Shown in the app and emailed. Required on rejection.';

-- Tickets that exist before this migration were issued under the old rule. A
-- verified one may belong to somebody halfway through PayFast right now, and
-- the webhook is about to start refusing anything not approved -- that would be
-- a paid move with no grant. Grandfather them. Nothing new can arrive here
-- without a decision.
update public.device_move_tickets
   set approval_status = 'approved',
       decided_at      = now(),
       decided_by      = 'migration:grandfathered'
 where approval_status = 'pending';

-- The admin queue: verified requests waiting on a decision, oldest first.
create index if not exists device_move_tickets_queue_idx
  on public.device_move_tickets (approval_status, created_at)
  where verified_at is not null and consumed_at is null;
