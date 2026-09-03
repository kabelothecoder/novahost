-- RLS with no policies already hides every row, so these tables leaked nothing.
-- But the anon/authenticated SELECT grant Supabase adds by default was still
-- there, which meant the only thing standing between a client and this data was
-- the absence of a policy. Adding one later -- for a support screen, say --
-- would open it without anyone intending to.
--
-- The ledger and the move tickets are service-role data. Nothing client-side
-- should ever read them, so nothing client-side should hold a grant on them.
revoke all on public.subscription_device_events from anon, authenticated;
revoke all on public.device_move_tickets from anon, authenticated;
