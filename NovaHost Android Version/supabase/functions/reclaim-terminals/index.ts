import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { bridgeFor, resolveLink, withoutLink } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-cron-secret',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Stops the broker terminals of licences that can no longer trade.
 *
 * MetaRPC bills every connected terminal by the minute, and nothing else
 * stops one when a licence lapses: the terminal of a user who never renews
 * would bill for ever. This runs daily from pg_cron.
 *
 * A licence qualifies when it is not `active` (revoked, suspended, expired),
 * or when its `expires_at` passed more than GRACE_DAYS ago. The grace is so a
 * user who renews a day late does not have to re-enter their broker login.
 *
 * Coming back after a reclaim needs the password again -- MetaRPC keeps no
 * parked state -- so the licence's link is cleared and the app shows NOT
 * LINKED rather than a connection that is not there.
 *
 * `{ "dry_run": true }` reports what would be stopped and stops nothing.
 */
const GRACE_DAYS = 3

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS })
  }

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
    })

  try {
    const novaHost = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
    )

    // Same cron secret auto-execute-signals uses; only pg_cron holds it.
    const presented = req.headers.get('x-cron-secret') ?? ''
    const { data: secretRow } = await novaHost
      .from('internal_secrets')
      .select('secret')
      .eq('name', 'auto_execute_cron')
      .maybeSingle()
    if (!secretRow?.secret || presented !== secretRow.secret) {
      console.warn('[reclaim-terminals] refused: bad or missing cron secret')
      return json({ success: false, code: 'FORBIDDEN', error: 'Not authorised.' }, 403)
    }

    const body = await req.json().catch(() => ({}))
    const dryRun = body?.dry_run === true
    const cutoff = new Date(Date.now() - GRACE_DAYS * 86_400_000).toISOString()

    // Every licence that still holds a live account row, or a link on its own
    // metadata (the primary of a licence linked before the accounts table).
    const { data: rows, error: rowsErr } = await novaHost
      .from('license_broker_accounts')
      .select('id, license_id, provider, bridge_account_id, is_primary')
      .is('disconnected_at', null)
    if (rowsErr) throw rowsErr

    const { data: linked, error: licErr } = await novaHost
      .from('licenses')
      .select('id, status, expires_at, metadata')
      .or(`status.neq.active,expires_at.lt.${cutoff}`)
    if (licErr) throw licErr

    const reclaimed: Array<{ license_id: string; provider: string; account_id: string; stopped: boolean }> = []

    for (const lic of linked ?? []) {
      const metadata = (lic.metadata ?? {}) as Record<string, unknown>
      const link = resolveLink(metadata)
      const accounts = new Map<string, { provider: string; rowId: string | null }>()
      for (const r of rows ?? []) {
        if (r.license_id === lic.id) accounts.set(r.bridge_account_id, { provider: r.provider, rowId: r.id })
      }
      if (link && !accounts.has(link.accountId)) accounts.set(link.accountId, { provider: link.provider, rowId: null })
      if (accounts.size === 0) continue

      for (const [accountId, a] of accounts) {
        let stopped = false
        if (!dryRun) {
          stopped = await bridgeFor(a.provider as never, accountId, novaHost)?.disconnect().catch(() => false) ?? false
          if (a.rowId) {
            await novaHost.from('license_broker_accounts')
              .update({ disconnected_at: new Date().toISOString(), status: 'reclaimed' })
              .eq('id', a.rowId)
          }
          if (!stopped) console.warn(`[reclaim-terminals] ${a.provider} ${accountId} (licence ${lic.id}) NOT stopped at the provider`)
        }
        reclaimed.push({ license_id: lic.id, provider: a.provider, account_id: accountId, stopped })
      }

      if (!dryRun && link) {
        await novaHost.from('licenses')
          .update({ metadata: withoutLink(metadata, 'LICENCE_LAPSED') })
          .eq('id', lic.id)
      }
    }

    console.log(`[reclaim-terminals] ${dryRun ? 'dry run: would stop' : 'stopped'} ${reclaimed.length} terminal(s)`)
    return json({ success: true, dry_run: dryRun, grace_days: GRACE_DAYS, count: reclaimed.length, reclaimed })
  } catch (error) {
    console.error('[reclaim-terminals] fatal:', error)
    return json({ success: false, error: (error as Error).message }, 500)
  }
})
