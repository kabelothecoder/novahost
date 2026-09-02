import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * Everything the broker will tell us about why an account is or is not able to
 * trade one instrument.
 *
 * ## Why this exists
 *
 * `metacopier-execute` reports what MetaCopier hands back, and for a refused
 * order that is frequently the single token `[BROKER_REJECTION]` -- no reason,
 * no field, no number. That is enough to know a trade failed and useless for
 * knowing why, which left "the robot does not trade" and "your account cannot
 * trade 0.05 lots of Gold" looking identical from every screen we own.
 *
 * The information is all available; it is just on endpoints the executor has no
 * reason to call on the hot path:
 *
 *  - `/accounts/{id}` and `/information` -- is it connected, is it read-only,
 *    what is the free margin.
 *  - `/symbols/{symbol}` -- the volume floor, ceiling and step, and whether the
 *    instrument is tradeable at all right now.
 *  - `/logs` -- the broker's own account activity, which is where a rejection
 *    reason usually appears in words.
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
    const METACOPIER_API_KEY = Deno.env.get('METACOPIER_API_KEY') ?? ''
    if (!METACOPIER_API_KEY) {
      return json({ success: false, code: 'SERVER_MISCONFIGURED', error: 'Server misconfiguration.' }, 500)
    }

    const supabase = createClient(
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
    const { data: license, error: licErr } = await supabase
      .from('licenses')
      .select('id, status, metadata')
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr
    if (!license) {
      return json({ success: false, code: 'LICENCE_UNKNOWN', error: 'Licence not recognised.' }, 401)
    }

    const metadata = (license.metadata ?? {}) as Record<string, unknown>
    const accountId = metadata.metacopier_account_id as string | undefined
    if (!accountId) {
      return json({ success: false, code: 'NO_ACCOUNT_LINKED', error: 'No trading account is connected.' }, 409)
    }

    const headers = { 'X-API-KEY': METACOPIER_API_KEY, 'Accept': 'application/json' }

    /** Fetch that reports its own failure instead of throwing the batch away. */
    const get = async (path: string): Promise<unknown> => {
      try {
        const res = await fetch(`${METACOPIER_BASE}${path}`, { headers })
        const text = await res.text()
        if (!res.ok) return { _error: res.status, _body: text.slice(0, 500) }
        return text ? JSON.parse(text) : null
      } catch (e) {
        return { _error: 'threw', _body: (e as Error).message }
      }
    }

    const acct = encodeURIComponent(accountId)

    // Asked together: each answers a different candidate cause, and a rejection
    // is usually only explicable by looking at two of them side by side.
    const [account, information, positions, logs, symbolSpec, quote] = await Promise.all([
      get(`/rest/api/v1/accounts/${acct}`),
      get(`/rest/api/v1/accounts/${acct}/information`),
      get(`/rest/api/v1/accounts/${acct}/positions`),
      get(`/rest/api/v1/accounts/${acct}/logs`),
      symbol ? get(`/rest/api/v1/accounts/${acct}/symbols/${encodeURIComponent(symbol)}`) : Promise.resolve(null),
      // The quote does double duty: it prices the margin calculation, and its
      // absence or staleness is how a closed market announces itself. Those two
      // causes of a bare [BROKER_REJECTION] call for opposite advice -- resize
      // the trade, or wait for the session -- so guessing between them is worse
      // than not answering.
      symbol ? get(`/rest/api/v1/accounts/${acct}/quote/${encodeURIComponent(symbol)}`) : Promise.resolve(null),
    ])

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
