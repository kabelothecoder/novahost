-- The 24 Oct cut-off never ran: the project was paused at 22:00 UTC that night.
-- Moved to 22:00 UTC on 25 Oct (midnight SAST), after broker-connect went live.

select cron.alter_job(
  (select jobid from cron.job where jobname = 'retire-metacopier-links'),
  schedule := '0 22 25 10 *'
);
