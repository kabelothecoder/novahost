-- Closes two holes that the shipped anon key could reach.
--
-- Both were harmless only because `licenses.auto_execute` is false on every
-- row. The opt-in toggle lands in the same change set, so they stop being
-- theoretical the moment a user switches it on — an injected signal would be
-- executed against a real broker account.

-- ── 1. Anyone could INSERT a signal ──────────────────────────────────────────
--
-- Policy `Enable insert for all` was `{public}` with `with_check: true`, so a
-- holder of the anon key — which ships inside every APK and every PWA bundle —
-- could write straight into the mentor signal feed.
--
-- Nothing legitimate depended on it. Every writer is a service-role edge
-- function (`broadcast-signal`, `execute-trade-v2`), and the service role
-- bypasses RLS, so dropping the policy leaves the real insert paths untouched
-- while removing the anon one. Verified by grep across both apps, the portal
-- and every deployed function before this ran.
drop policy if exists "Enable insert for all" on public.signals;

-- ── 2. Anyone could fire the executor ────────────────────────────────────────
--
-- `20260911151806_server_side_signal_execution` revoked EXECUTE from `anon` and
-- `authenticated`, but those roles never held a grant of their own: the
-- privilege came from the implicit PUBLIC grant every function is created with
-- (`=X/postgres` in proacl). Revoking from a role that inherits from PUBLIC
-- changes nothing, so `POST /rest/v1/rpc/trigger_auto_execute` still answered
-- 204 for anon. Confirmed against the live endpoint.
--
-- PUBLIC is the grant that has to go — the same correction
-- `20260902123637_lock_down_public_security_definer_functions` made for the
-- other SECURITY DEFINER functions. `postgres` (which the pg_cron job runs as)
-- and `service_role` hold explicit grants and are unaffected, and
-- `on_signal_inserted` is SECURITY DEFINER owned by postgres, so the trigger
-- path still reaches the function.
revoke execute on function public.trigger_auto_execute() from public;
revoke execute on function public.trigger_auto_execute() from anon, authenticated;

revoke execute on function public.on_signal_inserted() from public;
revoke execute on function public.on_signal_inserted() from anon, authenticated;

-- ── 3. What replaces the app's read ──────────────────────────────────────────
--
-- The SELECT policy is deliberately NOT widened. It reads
-- `licenses.owner_id = auth.uid()`, which is correct for the portal, and wrong
-- to relax: app users hold a licence key and have no `auth.users` row, so the
-- only way RLS could recognise them is a credential carried in from the client
-- — and the one credential they share is the anon key that ships in the
-- bundle. Widening it to `anon` would publish every mentor's signal feed.
--
-- So the app's feed moves server-side to `signal-feed`, which holds the
-- service role and scopes by licence key. That is the same shape as
-- `license-status`, `claim-signals` and every `broker-*` function, and the
-- reason is the one `license-status` already records: "Opening `licenses` to
-- anon was never an option: the anon key ships inside the APK."
comment on policy "Users can only read signals for their active licenses" on public.signals is
  'Portal/mentor read path only. Handsets authenticate by licence key and have no auth.uid(), so their feed is served by the signal-feed edge function under the service role — do not widen this to anon.';
