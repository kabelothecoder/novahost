import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * The account's recent closed trades, reduced to the one figure the scanner
 * needs: how many losses in a row it is currently on.
 *
 * ## Why this exists
 *
 * The scanner's fourth guardrail -- "consecutive losses < 3, the engine locks"
 * -- was passed a hardcoded `0`. It rendered as a passing rule, was counted in
 * "3 of 4 rules checked on device", and could never fire however many trades
 * went against the user. A risk control that reports a number nobody measured
 * is worse than no risk control: it is the reassurance without the protection.
 *
 * MetaCopier exposes `GET /accounts/{id}/history/positions`, which carries
 * `netProfit` and `closeTime` per closed position. That is everything the count
 * needs, so the rule can be real.
 *
 * ## Why the count is computed here and not on the handset
 *
 * The raw history is dozens of positions with prices, swaps and commissions on
 * each. Shipping all of it to a phone to derive one integer wastes the response
 * and puts the account's whole trading record on the device. One number goes
 * back instead.
 */

/**
 * Losses in a row, counting back from the most recently closed trade.
 *
 * `netProfit` is profit after swap and commission, which is the figure that
 * decides whether a trade actually lost money -- a position closed at
 * break-even on price is a loss once the broker's costs are in, and a streak
 * that ignored them would let a run of small bleeds pass as neutral.
 *
 * Exactly zero is treated as NOT a loss. A break-even close is not a losing
 * trade, and counting it as one would lock the engine on a scratch.
 */
function consecutiveLosses(positions: Array<Record<string, unknown>>): number {
  const closed = positions
    .filter((p) => p?.closeTime)
    .sort((a, b) => String(b.closeTime).localeCompare(String(a.closeTime)))

  let streak = 0
  for (const p of closed) {
    const net = Number(p?.netProfit)
    // An unreadable profit figure stops the count rather than being guessed
    // either way: continuing would invent a loss, skipping would invent a win.
    if (!Number.isFinite(net)) break
    if (net < 0) streak++
    else break
  }
  return streak
}

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
      console.error('[broker-history] METACOPIER_API_KEY is not configured')
      return json({ success: false, code: 'SERVER_MISCONFIGURED', error: 'Server misconfiguration.' }, 500)
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))
    const rawKey = body.license_key

    if (!rawKey || !String(rawKey).trim()) {
      return json({ success: false, code: 'MISSING_PARAMETERS', error: 'A licence key is required.' }, 400)
    }

    // Authorised by licence, like every other device-facing function. The
    // account id is resolved from the licence and never taken from the caller.
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
    if (license.status !== 'active') {
      return json({ success: false, code: 'LICENCE_INACTIVE', error: 'Licence is not active.' }, 403)
    }

    const metadata = (license.metadata ?? null) as Record<string, unknown> | null
    const accountId = metadata?.metacopier_account_id as string | undefined

    if (!accountId) {
      return json({
        success: false,
        code: 'NO_ACCOUNT_LINKED',
        error: 'No trading account is connected to this licence.',
      }, 409)
    }

    const res = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/history/positions`,
      { headers: { 'X-API-KEY': METACOPIER_API_KEY, 'Accept': 'application/json' } }
    )

    if (!res.ok) {
      const detail = await res.text()
      console.error(`[broker-history] history query failed ${res.status}: ${detail}`)
      const disconnected = /NOT_CONNECTED|OFFLINE|DISCONNECT|ACCOUNT_WAS_DELETED/i.test(detail)
      return json({
        success: false,
        code: disconnected ? 'ACCOUNT_DISCONNECTED' : 'HISTORY_UNAVAILABLE',
        error: disconnected
          ? 'Your trading account is not connected to the broker right now.'
          : 'Could not read your trade history.',
      }, 502)
    }

    const positions = await res.json().catch(() => null)
    if (!Array.isArray(positions)) {
      return json({ success: false, code: 'HISTORY_UNAVAILABLE', error: 'Could not read your trade history.' }, 502)
    }

    const streak = consecutiveLosses(positions)
    const closedCount = positions.filter((p) => p?.closeTime).length

    console.log(`[broker-history] licence ${license.id}: ${streak} consecutive losses of ${closedCount} closed`)

    return json({
      success: true,
      consecutive_losses: streak,
      closed_positions: closedCount,
    })

  } catch (err) {
    console.error('[broker-history] failed:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
