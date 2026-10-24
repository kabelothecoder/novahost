-- Daily: stop the broker terminals of licences that can no longer trade.
-- MetaRPC bills each connected terminal by the minute; without this a lapsed
-- licence's terminal bills for ever. See functions/reclaim-terminals.

create or replace function public.trigger_reclaim_terminals()
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
    raise warning '[reclaim-terminals] no cron secret configured; skipping';
    return;
  end if;

  perform net.http_post(
    url     := 'https://epulmnfbxjmaimefhofp.supabase.co/functions/v1/reclaim-terminals',
    headers := jsonb_build_object(
                 'Content-Type',  'application/json',
                 'x-cron-secret', v_secret
               ),
    body    := '{}'::jsonb,
    timeout_milliseconds := 120000
  );
end;
$$;

revoke all on function public.trigger_reclaim_terminals() from public, anon, authenticated;

select cron.unschedule('reclaim-terminals')
  where exists (select 1 from cron.job where jobname = 'reclaim-terminals');

-- 03:00 UTC, outside the main trading sessions.
select cron.schedule(
  'reclaim-terminals',
  '0 3 * * *',
  $$ select public.trigger_reclaim_terminals(); $$
);
