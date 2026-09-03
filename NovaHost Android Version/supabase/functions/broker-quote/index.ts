import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * The live price and contract spec for one instrument, from the user's own broker.
 *
 * ## Why this exists
 *
 * The scanner used to price off Finnhub. Two things were wrong with that, and
 * only one of them was the plan:
 *
 *  - Finnhub's plan here excludes OANDA forex. `GET /quote?symbol=OANDA:EUR_USD`
 *    answers "You don't have access to this resource", the websocket accepts the
 *    subscribe and then simply never sends a forex trade, and the client's
 *    `onMessage` silently discards anything that is not a trade -- so a refused
 *    subscription and a quiet market look identical. The quote card sat on
 *    "Waiting for a price" indefinitely.
 *  - Even on a paid plan it would have been the wrong price. A proxy feed is not
 *    what the user's order fills against. The spread they pay, the digits their
 *    broker quotes, the minimum lot it will accept -- none of that is knowable
 *    from OANDA, and all of it changes the numbers on the plan screen.
 *
 * MetaCopier is already paid for and is connected to the actual account the
 * order will reach, so it answers both questions at once.
 *
 * ## What this replaces on the handset
 *
 * `Instrument` in `ScanModels.kt` derives pip size and decimals by inspecting
 * the symbol string -- contains "JPY", starts with "XAU", has a digit. Those
 * heuristics are right often enough to be dangerous: they cannot tell a 5-digit
 * book from a 4-digit one, they call every index a 1.0 pip, and getting one
 * wrong sizes the position by a factor of ten in silence.
 *
 * `digits` and `points` come off the broker's own contract spec, so the pip is
 * whatever that broker says it is.
 *
 * ## What it still does not answer
 *
 * ATR. MetaCopier exposes no candle, bar or OHLC endpoint -- the whole v1
 * surface is accounts, positions, symbols and a single live quote. Volatility
 * has to come from somewhere else, and the only source this product has that
 * has actually seen the price history is the chart screenshot itself.
 */

/** Canonical NovaHost name: letters and digits, upper case. */
function canon(s: unknown): string {
  return String(s ?? '').replace(/[^A-Za-z0-9]/g, '').toUpperCase()
}

/**
 * Kept in step with the tables in `metacopier-execute` and `broker-symbols`.
 * A quote fetched under one name and an order sent under another would put a
 * price on the plan screen that belongs to a different instrument.
 */
const SYMBOL_ALIASES: Record<string, string[]> = {
  XAUUSD: ['GOLD', 'GOLDUSD', 'XAUUSD'],
  XAGUSD: ['SILVER', 'SILVERUSD', 'XAGUSD'],
  NAS100: ['USTEC', 'US100', 'NDX100', 'NAS100', 'USTECH', 'NASDAQ'],
  US30:   ['DJ30', 'WS30', 'US30', 'USA30', 'DOW'],
  SPX500: ['US500', 'SP500', 'SPX500', 'USA500'],
  GER40:  ['DE40', 'DAX40', 'GER40', 'GER30', 'DAX'],
  UK100:  ['FTSE100', 'UK100'],
  JP225:  ['JPN225', 'JP225', 'NIKKEI'],
  USOIL:  ['WTI', 'XTIUSD', 'USOIL', 'CRUDE', 'OIL'],
  UKOIL:  ['BRENT', 'XBRUSD', 'UKOIL'],
  BTCUSD: ['BTCUSD', 'BITCOIN', 'BTC'],
  ETHUSD: ['ETHUSD', 'ETHEREUM', 'ETH'],
  VIX:    ['VIX', 'VOLATILITY', 'VIXX'],
}

/**
 * Names to try, most trustworthy first.
 *
 * Identical ordering to the executor's: the confirmed broker symbol, then an
 * explicit map entry, then the suffix form, then the alias list. The quote must
 * describe the instrument the order would actually reach.
 */
function symbolCandidates(
  cleanPair: string,
  metadata: Record<string, unknown> | null,
  userSymbol: string | null
): string[] {
  const out: string[] = []
  const push = (v: unknown) => {
    const s = String(v ?? '').trim()
    if (s && !out.includes(s)) out.push(s)
  }

  push(userSymbol)

  const map = metadata?.symbol_map as Record<string, string> | undefined
  if (map) push(map[cleanPair])

  const suffix = String(metadata?.symbol_suffix ?? '').trim().replace(/[^A-Za-z0-9._-]/g, '')
  if (suffix) push(cleanPair + suffix)

  push(cleanPair)
  for (const alias of SYMBOL_ALIASES[cleanPair] ?? []) push(alias)

  return out
}

/**
 * The size of one pip, from the broker's own digit count.
 *
 * A pip is ten points on a fractional-pip book (5 digits on FX, 3 on JPY
 * crosses) and one point everywhere else. This is the conversion the handset
 * was guessing from the symbol's spelling.
 */
function pipSizeFrom(digits: number, points: number): number {
  if (!Number.isFinite(points) || points <= 0) return 0
  return digits === 3 || digits === 5 ? points * 10 : points
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
      console.error('[broker-quote] METACOPIER_API_KEY is not configured')
      return json({ success: false, code: 'SERVER_MISCONFIGURED', error: 'Server misconfiguration.' }, 500)
    }

    const novaHost = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))
    const rawKey = body.license_key
    const cleanPair = canon(body.symbol ?? body.pair)

    if (!rawKey || !String(rawKey).trim() || !cleanPair) {
      return json({ success: false, code: 'MISSING_PARAMETERS', error: 'A licence key and a symbol are required.' }, 400)
    }

    // Authorised by licence, like every other device-facing function. The
    // account id is resolved from the licence and never taken from the caller.
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

    const metadata = (license.metadata ?? null) as Record<string, unknown> | null
    const accountId = metadata?.metacopier_account_id as string | undefined

    if (!accountId) {
      return json({
        success: false,
        code: 'NO_ACCOUNT_LINKED',
        error: 'Connect your trading account to price this instrument.',
      }, 409)
    }

    const { data: cfg } = await novaHost
      .from('license_symbol_config')
      .select('broker_symbol')
      .eq('license_id', license.id)
      .eq('symbol', cleanPair)
      .maybeSingle()

    const candidates = symbolCandidates(cleanPair, metadata, (cfg?.broker_symbol ?? null) as string | null)

    // ---- Ask the broker -----------------------------------------------------
    // Walked in the same order the executor would. A 404 means this book does
    // not carry that name and the next one is worth trying; anything else is a
    // property of the account rather than the name, so the walk stops.
    let quote: Record<string, unknown> | null = null
    let usedName: string | null = null
    let lastStatus = 0
    let lastDetail = ''

    for (const candidate of candidates) {
      const res = await fetch(
        `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/quote/${encodeURIComponent(candidate)}`,
        { headers: { 'X-API-KEY': METACOPIER_API_KEY, 'Accept': 'application/json' } }
      )

      if (res.ok) {
        quote = await res.json().catch(() => null)
        usedName = candidate
        break
      }

      lastStatus = res.status
      lastDetail = await res.text()
      if (res.status !== 404 && !/SYMBOL|NOT_FOUND/i.test(lastDetail)) break
    }

    if (!quote || !usedName) {
      const disconnected = /NOT_CONNECTED|OFFLINE|DISCONNECT|ACCOUNT_WAS_DELETED/i.test(lastDetail)
      console.error(`[broker-quote] ${cleanPair} failed ${lastStatus}: ${lastDetail}`)
      return json({
        success: false,
        code: disconnected ? 'ACCOUNT_DISCONNECTED' : 'NO_QUOTE',
        error: disconnected
          ? 'Your trading account is not connected to the broker right now.'
          : `Your broker did not quote ${cleanPair}. Tried: ${candidates.join(', ')}.`,
        details: lastDetail,
      }, 502)
    }

    const bid = Number(quote.bid)
    const ask = Number(quote.ask)
    const info = (quote.symbolInfo ?? {}) as Record<string, unknown>

    const digits = Number(info.digits)
    const points = Number(info.points)
    const pipSize = pipSizeFrom(digits, points)

    // Mid rather than bid. The scanner prices a plan, not a fill, and quoting
    // one side makes a buy and a sell of the same setup disagree about where
    // price is.
    const mid = Number.isFinite(bid) && Number.isFinite(ask) ? (bid + ask) / 2 : NaN
    const spreadPips = pipSize > 0 && Number.isFinite(bid) && Number.isFinite(ask)
      ? (ask - bid) / pipSize
      : null

    return json({
      success: true,
      symbol: cleanPair,
      /** What this broker calls it -- the name an order would carry. */
      broker_symbol: usedName,
      bid: Number.isFinite(bid) ? bid : null,
      ask: Number.isFinite(ask) ? ask : null,
      price: Number.isFinite(mid) ? mid : null,
      spread_pips: spreadPips,
      /** Contract spec, so the handset stops inferring these from the spelling. */
      digits: Number.isFinite(digits) ? digits : null,
      point: Number.isFinite(points) ? points : null,
      pip_size: pipSize > 0 ? pipSize : null,
      base_currency: info.baseCurrency ?? null,
      quote_currency: info.quoteCurrency ?? null,
      contract_size: Number(info.lotSize) || null,
      min_volume: Number(info.minimalVolume) || null,
      volume_step: Number(info.stepVolume) || null,
      max_volume: Number(info.maximalVolume) || null,
      tradeable: info.disabled !== true,
      timestamp: quote.timestamp ?? null,
    })

  } catch (err) {
    console.error('[broker-quote] failed:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
