import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { openBridge, resolveLink } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * What is open on the linked account right now.
 *
 * ## Why this exists
 *
 * The product could not answer "did my trade actually happen". Not badly --
 * not at all:
 *
 *  - `trade_logs` has **zero rows and no writer anywhere in the codebase**.
 *    Nothing inserts into it, yet the iOS home screen subscribes to realtime
 *    INSERTs on it and shows a "TRADE EXECUTED" toast from that handler. The
 *    toast has never fired and could not.
 *  - `metacopier-execute` returns at the moment the broker accepts an order
 *    and never mentions it again. A 204 from MetaCopier is the last anything
 *    in this system hears about a position.
 *  - `signal_deliveries` records that a signal was HANDED OUT, which is a
 *    different claim entirely from one that says a position exists.
 *
 * The broker's own open-position list is therefore the only record of a live
 * trade that this product has. Everything a user is shown about their open
 * risk has to come from here.
 *
 * ## Why the shape is normalised here
 *
 * `PositionDTO` carries twenty-one fields including broker-local time strings,
 * deal types and MetaCopier's own request ids. A phone needs about eight of
 * them. The rest is the account's trading record, and it does not need to be
 * on the device to draw a list.
 *
 * `orderType` is MetaCopier's `Buy | Sell | BuyLimit | SellLimit | BuyStop |
 * SellStop`. It is split into a plain side and a pending flag, because a
 * BuyLimit that has not filled is NOT an open position and must not be drawn
 * as one -- it has no live profit and closing it is a cancellation. The UI
 * needs to say which it is looking at.
 *
 * ## Whose trades these are
 *
 * All of them. Positions opened by hand in MetaTrader sit in the same account
 * as the ones this product placed, and the broker does not distinguish. The
 * `magicNumber` does: `metacopier-execute` stamps every order it places with a
 * per-robot magic (77 + 6 digits of an ea_id hash), so `ours` marks the ones
 * NovaHost opened. Reported rather than filtered -- a user's open risk
 * includes their own manual trades, and hiding them would understate exposure
 * on the one screen that exists to show it.
 *
 * Read-only. It places nothing, closes nothing and writes nothing.
 */

/** Letters and digits, upper case -- brokers decorate symbols (`XAUUSD.m`, `.US30.`, `Gold`). */
function baseSymbol(s: unknown): string {
  return String(s ?? '').replace(/[^A-Za-z0-9]/g, '').toUpperCase()
}

/**
 * Every name a broker might use for one NovaHost instrument.
 *
 * Kept in step with the tables in `broker-quote` and `metacopier-execute`. A
 * position resolved under one name and a symbol plan written under another do
 * not match, and the app then believes the user holds nothing.
 */
const SYMBOL_ALIASES: Record<string, string[]> = {
  XAUUSD: ['XAUUSD', 'GOLDUSD', 'GOLD'],
  XAGUSD: ['XAGUSD', 'SILVERUSD', 'SILVER'],
  NAS100: ['NAS100', 'USTECH', 'USTEC', 'US100', 'NDX100', 'NASDAQ'],
  US30:   ['US30', 'USA30', 'DJ30', 'WS30', 'DOW'],
  SPX500: ['SPX500', 'USA500', 'US500', 'SP500'],
  GER40:  ['GER40', 'GER30', 'DAX40', 'DE40', 'DAX'],
  UK100:  ['UK100', 'FTSE100'],
  JP225:  ['JP225', 'JPN225', 'NIKKEI'],
  BTCUSD: ['BTCUSD', 'BTCUSDT', 'BITCOIN'],
  ETHUSD: ['ETHUSD', 'ETHUSDT', 'ETHEREUM'],
  VIX:    ['VIX'],
}

/**
 * The canonical NovaHost name for whatever the broker called this instrument.
 *
 * `baseSymbol` alone is NOT enough and assuming it was is a real bug: a micro
 * account lists gold as `XAUUSDm`, which strips to `XAUUSDM` -- a string that
 * matches nothing. The allowance says `XAUUSD`, the symbol plan says `XAUUSD`,
 * and the app concluded the user's open gold position was on an instrument it
 * had never heard of. Observed on a live account.
 *
 * Aliases are matched longest-first so a shorter name can never claim a
 * longer one's symbol. The final fallback takes the first six letters of a
 * plain FX pair, which is what `EURUSDpro` and `GBPUSD.raw` need and what no
 * alias table can enumerate.
 */
function canonicalSymbol(raw: unknown): string {
  const s = baseSymbol(raw)
  if (!s) return ''

  const candidates: Array<[string, string]> = []
  for (const [name, aliases] of Object.entries(SYMBOL_ALIASES)) {
    for (const a of aliases) candidates.push([a, name])
  }
  candidates.sort((x, y) => y[0].length - x[0].length)

  for (const [alias, name] of candidates) {
    if (s === alias || s.startsWith(alias)) return name
  }

  // `EURUSDm`, `GBPUSD.pro` -> the pair. Only when the leading six are all
  // letters, so an index like `US30cash` is never sliced into nonsense.
  if (/^[A-Z]{6}/.test(s)) return s.slice(0, 6)
  return s
}

/**
 * The magic number `metacopier-execute` stamps on this robot's orders.
 *
 * Kept byte-identical to `magicFor` in that function. If the two ever drift,
 * this screen starts calling the robot's own trades manual, so the algorithm
 * is duplicated deliberately rather than approximated: same FNV-style walk,
 * same `77` prefix, same six digits.
 */
function magicFor(eaId: unknown): string {
  const src = String(eaId ?? '')
  let h = 0x811c9dc5
  for (let i = 0; i < src.length; i++) {
    h ^= src.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return '77' + String((h >>> 0) % 1_000_000).padStart(6, '0')
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

    // Authorised by licence, and the account is resolved FROM it -- never taken
    // from the request, so this cannot be pointed at somebody else's account.
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
    const link = resolveLink(metadata)
    if (!link) {
      return json({ success: false, code: 'NO_ACCOUNT_LINKED', error: 'No trading account is connected.' }, 409)
    }

    const bridge = openBridge(link, novaHost)
    if (!bridge) {
      return json({ success: false, code: 'SERVER_MISCONFIGURED', error: 'Server misconfiguration.' }, 500)
    }

    /**
     * Positions and the account's currency, together.
     *
     * The currency is NOT on a position and is not recorded on the licence
     * -- `metacopier-connect` never writes one -- so the only place it exists
     * is the account summary. Fetching it here rather than making the client
     * call `broker-account` as well is the difference between one round trip
     * and two on a screen that opens constantly.
     *
     * It is not optional detail. This project's live accounts are ZAR, and a
     * floating P/L of -412 rendered as dollars when it is rands is wrong by
     * about eighteen times on the one number a user checks in a hurry.
     *
     * Run in parallel, and the currency is allowed to fail on its own: a
     * position list with an unlabelled figure is degraded, but a position list
     * withheld because a second endpoint was slow is useless.
     *
     * A list on a phone is waiting on this. An empty screen that says so beats
     * a spinner held open by a slow broker.
     */
    const [read, infoRead] = await Promise.all([
      bridge.positions(9000),
      bridge.accountSummary(9000),
    ])

    if (!read.ok) {
      console.error(`[broker-positions] positions query failed ${read.status}: ${read.detail}`)
      // A disconnected account and an unreachable API are different problems
      // with different fixes -- one is "reconnect your broker", the other is
      // "try again in a minute" -- so they are not collapsed into one message.
      const disconnected = read.code === 'ACCOUNT_DISCONNECTED'
      return json({
        success: false,
        code: disconnected ? 'ACCOUNT_DISCONNECTED' : 'BROKER_UNREACHABLE',
        error: disconnected
          ? 'Your trading account is not connected to the broker right now.'
          : 'Could not read your open trades just now.',
      }, 502)
    }

    const raw = read.data
    const info = infoRead.ok ? infoRead.data : null
    const currency = info?.currency ?? null

    const ourMagic = magicFor(license.ea_id)

    const positions = raw.map((p) => {
      const orderType = p.orderType
      // `BuyLimit`/`BuyStop` start with Buy but are NOT open positions. Both
      // facts are reported: the side for the chip, the pending flag for
      // whether there is any live profit to show at all.
      const pending = /Limit$|Stop$/.test(orderType)
      const side = /^Buy/.test(orderType) ? 'BUY' : /^Sell/.test(orderType) ? 'SELL' : '—'

      return {
        id: p.id,
        symbol: p.symbol,
        /** The canonical name, so the app can match a position to a signal,
         *  a symbol plan or the robot's allowance without re-deriving the
         *  broker's decoration. `XAUUSDm` resolves to `XAUUSD`, not to the
         *  `XAUUSDM` that matches nothing. */
        base: canonicalSymbol(p.symbol),
        side,
        pending,
        order_type: orderType,
        volume: p.volume,
        open_price: p.openPrice,
        stop_loss: p.stopLoss,
        take_profit: p.takeProfit,
        /** Raw profit, before the broker's costs. */
        profit: pending ? null : p.profit,
        /** Profit after swap and commission -- the figure that says whether
         *  this position is actually making money. Prefer it for display. */
        net_profit: pending ? null : p.netProfit,
        swap: p.swap,
        commission: p.commission,
        open_time: p.openTime,
        /** Placed by this robot rather than by hand in MetaTrader. */
        ours: (p.magic ?? '') === ourMagic,
      }
    })

    const live = positions.filter((p) => !p.pending)
    const sum = (xs: Array<number | null>) =>
      xs.reduce<number>((t, v) => t + (Number.isFinite(v as number) ? (v as number) : 0), 0)

    console.log(
      `[broker-positions] licence ${license.id}: ${live.length} open, ` +
      `${positions.length - live.length} pending`
    )

    return json({
      success: true,
      positions,
      open_count: live.length,
      pending_count: positions.length - live.length,
      /**
       * Floating P/L across everything open, after costs. Null when there is
       * nothing open -- zero would read as "flat", which is a different and
       * wrong statement.
       */
      floating_net: live.length ? sum(live.map((p) => p.net_profit)) : null,
      /** Whose currency the figures above are in. A balance without its unit
       *  is not a balance; neither is a P/L. Null when the account summary could
       *  not be read -- the client must then label the figure as unknown
       *  rather than assuming dollars. */
      currency,
      balance: info?.balance ?? null,
      equity: info?.equity ?? null,
      as_of: new Date().toISOString(),
    })

  } catch (err) {
    console.error('[broker-positions] fatal:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
