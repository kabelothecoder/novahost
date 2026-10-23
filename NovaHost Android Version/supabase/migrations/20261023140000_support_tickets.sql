create table public.support_tickets (
  id uuid primary key default gen_random_uuid(),
  ref text not null unique,
  license_id uuid references public.licenses(id) on delete set null,
  topic text not null,
  message text not null,
  attachments jsonb not null default '{}'::jsonb,
  status text not null default 'open',
  emailed boolean not null default false,
  created_at timestamptz not null default now()
);
create index support_tickets_license_created on public.support_tickets (license_id, created_at desc);
-- Written and read only by the support-desk function (service role).
alter table public.support_tickets enable row level security;
