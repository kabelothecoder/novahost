-- MetaCopier cut-off: midnight SAST at the end of 2026-10-24 (22:00 UTC).
--
-- Every new link goes to MetaRPC since 2026-10-23, and license-status tells a
-- licence still on MetaCopier to re-link (relink_required). Whatever has not
-- re-linked by then is unlinked here, so the app shows NOT LINKED and the user
-- connects again -- which lands on MetaRPC. We never stored broker passwords,
-- so this cannot be done for them.
--
-- The MetaCopier accounts themselves are NOT deleted by this job (MetaCopier
-- bills them until deleted): delete them through the MetaCopier dashboard or
-- MCP after the cut-off.

create or replace function public.retire_metacopier_links()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  n integer;
begin
  update public.licenses
  set metadata = (metadata - 'metacopier_account_id' - 'metacopier_status' - 'bridge_account_id')
    || jsonb_build_object(
      'bridge_status', 'disconnected',
      'disconnected_at', now(),
      'disconnected_reason', 'metacopier_retired'
    )
  where coalesce(metadata->>'bridge_provider', 'metacopier') = 'metacopier'
    and coalesce(nullif(metadata->>'bridge_account_id', ''), metadata->>'metacopier_account_id') is not null;
  get diagnostics n = row_count;

  update public.license_broker_accounts
  set disconnected_at = now(), status = 'metacopier_retired'
  where provider = 'metacopier' and disconnected_at is null;

  return n;
end;
$$;

revoke all on function public.retire_metacopier_links() from public, anon, authenticated;

-- One shot: runs at the cut-off, then removes itself.
select cron.schedule(
  'retire-metacopier-links',
  '0 22 24 10 *',
  $job$
    select public.retire_metacopier_links();
    select cron.unschedule('retire-metacopier-links');
  $job$
);
