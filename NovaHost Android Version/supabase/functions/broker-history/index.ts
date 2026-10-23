import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { openBridge, resolveLink, type Deal } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

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
 * The bridge's `history()` carries `netProfit` and `closeTime` per closed
 * position. That is everything the count needs, so the rule can be real.
 *
 * ## Why the count is computed here and not on the handset
 *
 * The raw history is dozens of positions with prices, swaps and commissions on
 * each. Shipping all of it to a phone to derive one integer wastes the response
 * and puts the account's whole trading record on the device. One number goes
 * back instead.
 */

/**
 * Is this history row an actual trade?
 *
 * History is the account's LEDGER. Alongside closed positions it returns
 * deposits, withdrawals, credits, bonuses, commissions, dividends, tax and
 * cancelled orders -- and every one of them carries a `closeTime`, so
 * filtering on that alone admits all of them.
 *
 * For THIS function that is not a cosmetic error, it is the guardrail failing
 * open. A deposit arrives with a positive `netProfit` (a live account here
 * shows a R500 top-up as exactly that), the loop below sees a non-negative
 * figure and stops, and the streak resets to zero. **Funding your account
 * would clear the consecutive-loss lock** -- at the precise moment a user on
 * a losing run is most likely to do it.
 *
 * Three independent tests, because no one of them holds on every broker:
 * `kind` names it outright where the provider reports it; a real trade
 * has a symbol; a real trade has a positive volume. The last two are what
 * carry a broker that leaves it unreported.
 */
function isClosedTrade(p: Deal): boolean {
  if (!p.closeTime) return false
  if (p.kind === 'ledger') return false
  if (!p.symbol.trim()) return false
  return Number(p.volume) > 0
}

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
function consecutiveLosses(positions: Deal[]): number {
  const closed = positions
    .filter(isClosedTrade)
    .sort((a, b) => String(b.closeTime).localeCompare(String(a.closeTime)))

  let streak = 0
  for (const p of closed) {
    // null is "unreadable", not zero -- Number(null) would invent a scratch.
    const net = p.netProfit ?? NaN
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
    const novaHost = createClient(
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
    const { data: license, error: licErr } = await novaHost
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
    const link = resolveLink(metadata)

    if (!link) {
      return json({
        success: false,
        code: 'NO_ACCOUNT_LINKED',
        error: 'No trading account is connected to this licence.',
      }, 409)
    }

    const bridge = openBridge(link, novaHost)
    if (!bridge) {
      return json({ success: false, code: 'SERVER_MISCONFIGURED', error: 'Server misconfiguration.' }, 500)
    }

    /**
     * The window is REQUIRED, and omitting it was silently disarming this rule.
     *
     * MetaCopier refuses a history read without `start` and `stop`. This call
     * once sent neither, so every invocation returned HISTORY_UNAVAILABLE --
     * which the scanner reads as "could not measure" and renders as a rule it could not check.
     *
     * That is precisely the failure this function's own docstring was written
     * to end: "A risk control that reports a number nobody measured is worse
     * than no risk control." It has been reporting one since it shipped.
     *
     * Thirty days: a losing streak is a statement about recent trading, and a
     * run that ended six weeks ago is not the thing the guardrail is asking
     * about. Long enough that a low-frequency account still has history in it.
     */
    const stop = new Date()
    const start = new Date(stop.getTime() - 30 * 86_400_000)

    const read = await bridge.history(start, stop)

    if (!read.ok) {
      console.error(`[broker-history] history query failed ${read.status}: ${read.detail}`)
      const disconnected = read.code === 'ACCOUNT_DISCONNECTED'
      return json({
        success: false,
        code: disconnected ? 'ACCOUNT_DISCONNECTED' : 'HISTORY_UNAVAILABLE',
        error: disconnected
          ? 'Your trading account is not connected to the broker right now.'
          : 'Could not read your trade history.',
      }, 502)
    }

    const positions = read.data

    const streak = consecutiveLosses(positions)
    const closedCount = positions.filter(isClosedTrade).length

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
