import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * Closed trades, and the equity curve they add up to.
 *
 * ## Why this is a second function and not more of `broker-history`
 *
 * `broker-history` answers exactly one question -- how many losses in a row
 * the account is currently on -- because that is what the scanner's fourth
 * guardrail needs and shipping the whole trading record to a phone to derive
 * one integer was the thing it was written to avoid.
 *
 * This answers a different question for a different screen: what has the
 * account actually done. It returns rows, so it takes a window and a limit.
 * Folding both into one endpoint would mean the guardrail -- called on every
 * scan -- started paying for a payload it discards.
 *
 * ## The window is required, and that was a live bug
 *
 * `GET /accounts/{id}/history/positions` documents `start` and `stop` as
 * **required** query parameters, ISO 8601. `broker-history` sends neither, so
 * MetaCopier refuses it and the consecutive-loss guardrail has been returning
 * HISTORY_UNAVAILABLE rather than a count -- a risk control that reports a
 * number nobody measured, which is the exact failure its own docstring was
 * written to end. Fixed there too; stated here because this function exists
 * partly because that was found.
 *
 * ## The curve is cumulative net profit, not equity
 *
 * A true equity curve needs the account's balance at each point in time, and
 * MetaCopier does not expose balance history -- only the current figure from
 * `/information`. What can be built honestly is the running total of realised
 * net profit, anchored at the present balance and walked backwards, which is
 * the same shape and is labelled for what it is.
 *
 * `netProfit` rather than `profit` throughout: it is after swap and
 * commission, and a curve drawn on gross profit slopes upward through a run
 * of trades that actually bled the account dry on costs.
 *
 * Read-only. It places nothing, closes nothing and writes nothing.
 */

const num = (v: unknown) => (Number.isFinite(Number(v)) ? Number(v) : null)

/** Letters and digits, upper case -- brokers decorate symbols. */
function baseSymbol(s: unknown): string {
  return String(s ?? '').replace(/[^A-Za-z0-9]/g, '').toUpperCase()
}

/** Kept byte-identical to `magicFor` in metacopier-execute and broker-positions. */
function magicFor(eaId: unknown): string {
  const src = String(eaId ?? '')
  let h = 0x811c9dc5
  for (let i = 0; i < src.length; i++) {
    h ^= src.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return '77' + String((h >>> 0) % 1_000_000).padStart(6, '0')
}

/**
 * Is this history row an actual trade?
 *
 * `/history/positions` is the account's LEDGER, not its trade list. Alongside
 * closed positions it returns deposits, withdrawals, credits, bonuses,
 * commissions, dividends, tax and cancelled orders -- and every one of them
 * carries a `closeTime`, so filtering on that alone admits all of them.
 *
 * This is not theoretical. A live account here returned exactly one "trade"
 * for the year: a R500 deposit, with an empty symbol, no volume, no open or
 * close price, `openTime` equal to `closeTime`, and `netProfit` 500. Counted
 * as a trade it read as a 100% win rate on a single winning position.
 *
 * Three independent tests, because no one of them holds on every broker:
 *
 *  - `dealType` names it outright, but only when the broker populates it.
 *    Anything that is not DealBuy/DealSell is a ledger entry -- including
 *    `CanceledBuy`/`CanceledSell`, which are orders that never became
 *    positions and would otherwise score as break-even trades.
 *  - A trade has a SYMBOL. Balance operations do not.
 *  - A trade has a positive VOLUME. Balance operations do not.
 *
 * The symbol and volume tests are what carry a broker that leaves `dealType`
 * empty, which is why the filter does not simply trust the enum.
 */
function isClosedTrade(p: Record<string, unknown>): boolean {
  if (!p?.closeTime) return false
  const dealType = String(p?.dealType ?? '')
  if (dealType && !/^Deal(Buy|Sell)$/.test(dealType)) return false
  if (!String(p?.symbol ?? '').trim()) return false
  return Number(p?.volume) > 0
}

/** How far back to look when the caller does not say. */
const DEFAULT_DAYS = 90
const DEFAULT_LIMIT = 100

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
      console.error('[broker-performance] METACOPIER_API_KEY is not configured')
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

    const key = String(rawKey).trim().toUpperCase()
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, ea_id, status, expires_at, metadata')
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

    // Clamped rather than trusted: an unbounded window asks the broker for
    // every trade the account has ever made, on a request a phone triggered.
    const days = Math.min(Math.max(Number(body.days) || DEFAULT_DAYS, 1), 365)
    const limit = Math.min(Math.max(Number(body.limit) || DEFAULT_LIMIT, 1), 500)

    const stop = new Date()
    const start = new Date(stop.getTime() - days * 86_400_000)

    const qs = new URLSearchParams({ start: start.toISOString(), stop: stop.toISOString() })
    const mcHeaders = { 'X-API-KEY': METACOPIER_API_KEY, 'Accept': 'application/json' }

    const [res, infoRes] = await Promise.all([
      fetch(
        `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/history/positions?${qs}`,
        { headers: mcHeaders, signal: AbortSignal.timeout(12000) }
      ),
      fetch(
        `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/information`,
        { headers: mcHeaders, signal: AbortSignal.timeout(9000) }
      ).catch(() => null),
    ])

    if (!res.ok) {
      const detail = await res.text().catch(() => '')
      console.error(`[broker-performance] history query failed ${res.status}: ${detail}`)
      const disconnected = /NOT_CONNECTED|OFFLINE|DISCONNECT|ACCOUNT_WAS_DELETED/i.test(detail)
      return json({
        success: false,
        code: disconnected ? 'ACCOUNT_DISCONNECTED' : 'HISTORY_UNAVAILABLE',
        error: disconnected
          ? 'Your trading account is not connected to the broker right now.'
          : 'Could not read your trade history.',
      }, 502)
    }

    const raw = await res.json().catch(() => null)
    if (!Array.isArray(raw)) {
      return json({ success: false, code: 'HISTORY_UNAVAILABLE', error: 'Could not read your trade history.' }, 502)
    }

    const info = infoRes?.ok ? await infoRes.json().catch(() => null) : null
    const currency = info?.currency ? String(info.currency).toUpperCase().slice(0, 3) : null
    const balance = num(info?.balance)

    const ourMagic = magicFor(license.ea_id)

    const closedAll = raw
      .filter(isClosedTrade)
      .map((p: Record<string, unknown>) => {
        const orderType = String(p?.orderType ?? '')
        return {
          id: String(p?.id ?? ''),
          symbol: String(p?.symbol ?? ''),
          base: baseSymbol(p?.symbol),
          side: /^Buy/.test(orderType) ? 'BUY' : /^Sell/.test(orderType) ? 'SELL' : '—',
          volume: num(p?.volume),
          open_price: num(p?.openPrice),
          close_price: num(p?.closePrice),
          open_time: p?.openTime ?? null,
          close_time: p?.closeTime ?? null,
          profit: num(p?.profit),
          net_profit: num(p?.netProfit),
          swap: num(p?.swap),
          commission: num(p?.commission),
          ours: String(p?.magicNumber ?? '') === ourMagic,
        }
      })
      // Oldest first: the curve is built by walking forward, and sorting once
      // here means neither the stats below nor the client has to re-sort.
      .sort((a, b) => String(a.close_time).localeCompare(String(b.close_time)))

    /* ── Stats ────────────────────────────────────────────────────────
       Over the whole window, not the truncated list. A user who asks for the
       last 20 rows still wants the 90-day win rate, and computing it off the
       slice would make the figure change every time they scrolled. */
    const scored = closedAll.filter((t) => Number.isFinite(t.net_profit as number))
    const wins = scored.filter((t) => (t.net_profit as number) > 0)
    const losses = scored.filter((t) => (t.net_profit as number) < 0)
    const grossWin = wins.reduce((t, x) => t + (x.net_profit as number), 0)
    const grossLoss = Math.abs(losses.reduce((t, x) => t + (x.net_profit as number), 0))
    const netTotal = scored.reduce((t, x) => t + (x.net_profit as number), 0)

    /**
     * The curve, anchored at today's balance and walked backwards.
     *
     * Drawing from zero would be a chart of profit, which looks identical in
     * shape but answers a question nobody asked; anchoring at the balance
     * makes the last point the number the user can also see on their account.
     *
     * Null balance means no anchor is available, and the curve then starts at
     * zero and is flagged `anchored: false` so the screen can label the axis
     * as profit rather than quietly implying an account size.
     */
    const startEquity = balance != null ? balance - netTotal : 0
    let running = startEquity
    const curve = scored.map((t) => {
      running += t.net_profit as number
      return { t: t.close_time, v: Math.round(running * 100) / 100 }
    })

    // Losses in a row, counting back from the most recent close. Exactly zero
    // is NOT a loss -- a break-even close is a scratch, and counting it would
    // lock the engine on one.
    let streak = 0
    for (let i = scored.length - 1; i >= 0; i--) {
      const net = scored[i].net_profit as number
      if (net < 0) streak++
      else break
    }

    const ledgerRows = raw.length - closedAll.length
    console.log(
      `[broker-performance] licence ${license.id}: ${closedAll.length} closed in ${days}d, ` +
      `${wins.length}W/${losses.length}L, net ${netTotal.toFixed(2)}, ` +
      `${ledgerRows} non-trade ledger row(s) excluded`
    )

    return json({
      success: true,
      /** Newest first for the list -- the curve stays oldest-first. */
      closed: closedAll.slice(-limit).reverse(),
      curve,
      /** False when no balance anchor was available; the axis is then profit. */
      anchored: balance != null,
      stats: {
        closed_count: closedAll.length,
        wins: wins.length,
        losses: losses.length,
        /** Null rather than 0 on an empty window: "no trades yet" and "a 0%
         *  win rate" are opposite messages. */
        win_rate: scored.length ? Math.round((wins.length / scored.length) * 1000) / 10 : null,
        net_total: Math.round(netTotal * 100) / 100,
        gross_win: Math.round(grossWin * 100) / 100,
        gross_loss: Math.round(grossLoss * 100) / 100,
        /** Null when nothing has lost yet -- the ratio is undefined, and
         *  Infinity rendered on a tile reads as a bug. */
        profit_factor: grossLoss > 0 ? Math.round((grossWin / grossLoss) * 100) / 100 : null,
        consecutive_losses: streak,
        best: scored.length ? Math.max(...scored.map((t) => t.net_profit as number)) : null,
        worst: scored.length ? Math.min(...scored.map((t) => t.net_profit as number)) : null,
      },
      currency,
      balance,
      equity: num(info?.equity),
      window_days: days,
      /** MetaCopier documents history as lagging live trading by up to three
       *  minutes. Surfaced so the screen can say so rather than looking stuck. */
      history_delay_note: 'Broker history can lag live trading by up to 3 minutes.',
      as_of: stop.toISOString(),
    })

  } catch (err) {
    console.error('[broker-performance] fatal:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
