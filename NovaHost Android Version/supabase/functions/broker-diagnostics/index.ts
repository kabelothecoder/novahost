import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { openBridge, resolveLink } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Everything the broker will tell us about why an account is or is not able to
 * trade one instrument.
 *
 * ## Why this exists
 *
 * `broker-execute` reports what MetaCopier hands back, and for a refused
 * order that is frequently the single token `[BROKER_REJECTION]` -- no reason,
 * no field, no number. That is enough to know a trade failed and useless for
 * knowing why, which left "the robot does not trade" and "your account cannot
 * trade 0.05 lots of Gold" looking identical from every screen we own.
 *
 * The information is all available; it is just on endpoints the executor has no
 * reason to call on the hot path. For a MetaCopier account those are:
 *
 *  - `/accounts/{id}` and `/information` -- is it connected, is it read-only,
 *    what is the free margin.
 *  - `/symbols/{symbol}` -- the volume floor, ceiling and step, and whether the
 *    instrument is tradeable at all right now.
 *  - `/logs` -- the broker's own account activity, which is where a rejection
 *    reason usually appears in words.
 *
 * The payloads are the provider's own, untranslated -- this is a support tool,
 * and the raw answer is the point. Which provider's shape you get depends on
 * the licence's `bridge_provider`.
 *
 * Read-only. It places nothing and changes nothing, so it is always safe to
 * call while diagnosing a live account.
 */
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
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))
    const rawKey = body.license_key
    const symbol = body.symbol ? String(body.symbol).trim() : null

    if (!rawKey) {
      return json({ success: false, code: 'MISSING_PARAMETERS', error: 'A licence key is required.' }, 400)
    }

    // Authorised by licence, and the account is resolved FROM it -- never taken
    // from the request, so this cannot be pointed at somebody else's account.
    const key = String(rawKey).trim().toUpperCase()
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, status, metadata')
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr
    if (!license) {
      return json({ success: false, code: 'LICENCE_UNKNOWN', error: 'Licence not recognised.' }, 401)
    }

    const metadata = (license.metadata ?? {}) as Record<string, unknown>
    const link = resolveLink(metadata)
    if (!link) {
      return json({ success: false, code: 'NO_ACCOUNT_LINKED', error: 'No trading account is connected.' }, 409)
    }
    const accountId = link.accountId

    const bridge = openBridge(link, novaHost)
    if (!bridge) {
      return json({ success: false, code: 'SERVER_MISCONFIGURED', error: 'Server misconfiguration.' }, 500)
    }

    // Asked together: each answers a different candidate cause, and a rejection
    // is usually only explicable by looking at two of them side by side. The
    // quote does double duty: it prices the margin calculation, and its absence
    // or staleness is how a closed market announces itself.
    const { account, information, positions, logs, symbol_spec: symbolSpec, quote } =
      await bridge.diagnostics(symbol)

    return json({
      success: true,
      account_id: accountId,
      symbol_queried: symbol,
      account,
      information,
      positions,
      symbol_spec: symbolSpec,
      quote,
      // Newest last from most providers, so the tail is what matters.
      logs,
    })

  } catch (err) {
    console.error('[broker-diagnostics] fatal:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
