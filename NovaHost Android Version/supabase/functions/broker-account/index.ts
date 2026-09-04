import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * What the linked broker account is actually worth, right now.
 *
 * ## Why this exists
 *
 * The app has a `MetaAPIManager.balance` StateFlow that every sizing screen
 * reads, and until this endpoint it was **always zero**. The only thing that
 * ever wrote it was `startBalanceSync`, a Postgres realtime subscription on
 * `broker_accounts` -- a table with no rows and nothing anywhere that inserts
 * one. It listened for UPDATE events that could not occur, and it never did an
 * initial read, so the flow sat at its 0.0 initial value for the life of every
 * install.
 *
 * That zero was not inert. The scanner falls back through
 * `sizingBalance -> balance -> SAMPLE_BALANCE`, so a subscriber who had not
 * typed a balance into the calculator had every position sized against a
 * stand-in $10,000 at 1% -- roughly 0.3 lots of gold, on an account holding
 * R596. The number looked deliberate, appeared on every scan, and was wrong by
 * two orders of magnitude.
 *
 * The balance was never unavailable. MetaCopier's `/information` has it, and
 * `broker-diagnostics` was already reading it for support sessions. This is the
 * same read, on its own, cheap enough to call whenever a sizing screen opens.
 *
 * ## Currency is not optional
 *
 * `currency` is returned alongside the figure and callers MUST use it. This
 * project's live accounts are ZAR: an account holding R596.29 handed to a
 * calculator that assumes dollars sizes every position about eighteen times too
 * large, which is the exact bug `TradeCalculatorCard` already carries a comment
 * about. A balance without its unit is not a balance.
 *
 * Read-only. It places nothing and writes nothing.
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

    const novaHost = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))
    const rawKey = body.license_key

    if (!rawKey || !String(rawKey).trim()) {
      return json({ success: false, code: 'MISSING_PARAMETERS', error: 'A licence key is required.' }, 400)
    }

    // Authorised by licence, and the account is resolved FROM it -- never taken
    // from the request, so this cannot be pointed at somebody else's account.
    const key = String(rawKey).trim().toUpperCase()
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, status, expires_at, metadata')
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr
    if (!license) {
      return json({ success: false, code: 'LICENCE_UNKNOWN', error: 'Licence not recognised.' }, 401)
    }
    if (license.status !== 'active') {
      return json({ success: false, code: 'LICENCE_INACTIVE', error: 'Licence is not active.' }, 403)
    }
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return json({ success: false, code: 'LICENCE_EXPIRED', error: 'Licence has expired.' }, 403)
    }

    const metadata = (license.metadata ?? {}) as Record<string, unknown>
    const accountId = metadata.metacopier_account_id as string | undefined
    if (!accountId) {
      return json({ success: false, code: 'NO_ACCOUNT_LINKED', error: 'No trading account is connected.' }, 409)
    }

    const res = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/information`,
      {
        headers: { 'X-API-KEY': METACOPIER_API_KEY, 'Accept': 'application/json' },
        // A sizing screen is waiting on this. Better to render with the user's
        // own typed figure than to hold the UI on a slow broker.
        signal: AbortSignal.timeout(8000),
      }
    )

    if (!res.ok) {
      return json(
        { success: false, code: 'BROKER_UNREACHABLE', error: 'Could not read your account from the broker just now.' },
        502
      )
    }

    const info = await res.json() as Record<string, unknown>
    const num = (v: unknown) => (Number.isFinite(Number(v)) ? Number(v) : null)

    // `credit` is deliberately NOT folded into the balance. Bonus credit cannot
    // be withdrawn and most brokers exclude it from the loss a stop can realise,
    // so risking a percentage of it sizes against money the subscriber does not
    // have. It is reported separately for display, never for sizing.
    return json({
      success: true,
      account_id: accountId,
      balance: num(info.balance),
      equity: num(info.equity),
      credit: num(info.credit),
      free_margin: num(info.freeMargin),
      // Upper-cased and clipped to the ISO shape the app's FxRates speaks.
      currency: String(info.currency ?? 'USD').toUpperCase().slice(0, 3),
      leverage: num(info.leverage),
      connected: typeof info.connected === 'boolean' ? info.connected : null,
      /** True when the broker refuses orders regardless of anything the app sends. */
      trading_disabled: info.tradingDisabled === true || info.tradeDisabled === true,
      investor_password: info.isInvestorPassword === true,
    })

  } catch (err) {
    console.error('[broker-account] fatal:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
