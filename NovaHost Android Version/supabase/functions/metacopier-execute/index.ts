import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/** MetaCopier orderType enum. Anything else is rejected rather than guessed. */
function toOrderType(side: string): string | null {
  switch ((side ?? '').trim().toUpperCase()) {
    case 'BUY':  return 'Buy'
    case 'SELL': return 'Sell'
    default:     return null
  }
}

/**
 * The order comment MetaTrader shows against the position.
 *
 * Format is `<robot>-NovaHost`, e.g. `Quantum Breaker EA-NovaHost`, so anyone
 * reading the trade history can tell at a glance which positions were placed by
 * automation and which robot placed them.
 *
 * Built here rather than sent by the client: the robot name is an attribute of
 * the licence, and a device that could choose its own comment could attribute
 * its trades to somebody else's robot.
 *
 * MetaTrader truncates comments around 31 characters, so the suffix is
 * protected and the robot name is what gets shortened -- losing the tail of a
 * long robot name is survivable, losing "-NovaHost" defeats the point.
 */
const COMMENT_SUFFIX = '-NovaHost'
const COMMENT_MAX = 31

function tradeComment(license: Record<string, unknown>): string {
  const ea = license.expert_advisors as { name?: string; display_name?: string } | null
  const raw = (ea?.display_name || ea?.name || 'NovaHost Bot').toString()

  // Strip what MetaTrader will not carry cleanly in a comment field.
  const cleaned = raw.replace(/[^\w \-.]/g, '').trim() || 'NovaHost Bot'

  const room = COMMENT_MAX - COMMENT_SUFFIX.length
  return cleaned.slice(0, room).trim() + COMMENT_SUFFIX
}

/**
 * Stable 32-bit request id derived from the signal id. MetaCopier uses
 * requestId to deduplicate, so a retry of the SAME signal must produce the
 * SAME number -- that is what stops a network retry opening a second position.
 */
function requestIdFrom(seed: string): number {
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return Math.abs(h | 0)
}

/**
 * How many positions are already open on this account for one symbol.
 *
 * Returns null when the broker could not be asked, which callers must treat as
 * "unknown" and never as "none" -- reading a failed request as zero would turn
 * every outage into an unlimited position cap.
 */
async function countOpenPositions(
  accountId: string,
  symbol: string,
  apiKey: string
): Promise<number | null> {
  try {
    const res = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/positions`,
      { headers: { 'X-API-KEY': apiKey, 'Accept': 'application/json' } }
    )
    if (!res.ok) {
      console.warn(`[metacopier-execute] positions query failed ${res.status}`)
      return null
    }
    const positions = await res.json()
    if (!Array.isArray(positions)) return null

    return positions.filter((p: Record<string, unknown>) =>
      String(p?.symbol ?? '').replace(/[^A-Za-z0-9]/g, '').toUpperCase() === symbol
    ).length
  } catch (e) {
    console.warn(`[metacopier-execute] positions query threw: ${(e as Error).message}`)
    return null
  }
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
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL') ?? ''
    const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    const METACOPIER_API_KEY = Deno.env.get('METACOPIER_API_KEY') ?? ''

    // Fail closed and loudly rather than pretending a trade went out.
    if (!METACOPIER_API_KEY) {
      console.error('METACOPIER_API_KEY is not configured')
      return json({ success: false, error: 'Server misconfiguration.' }, 500)
    }

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

    const body = await req.json().catch(() => ({}))
    const { license_key, pair, side, volume, sl, tp, signal_id } = body

    // NOTE: the MetaCopier account is resolved from the licence server-side.
    // It is deliberately NOT taken from the request -- otherwise a caller could
    // point a trade at any account id they liked.
    if (!license_key || !pair || !side) {
      return json({ success: false, error: 'Missing required trade parameters.' }, 400)
    }

    const orderType = toOrderType(side)
    if (!orderType) {
      return json({ success: false, error: `Unsupported side "${side}". Expected BUY or SELL.` }, 400)
    }

    const lots = Number(volume)
    if (!Number.isFinite(lots) || lots <= 0) {
      return json({ success: false, error: 'Volume must be a positive number of lots.' }, 400)
    }

    // ---- Authorize: the licence must exist, be active, and be unexpired ------
    // The caller is a device holding a mentor-issued key, not a logged-in user,
    // so the licence IS the credential. Never trust the client's word for it.
    const { data: license, error: licErr } = await supabase
      .from('licenses')
      .select(
        'id, ea_id, status, expires_at, allowed_symbols, metadata, ' +
        'expert_advisors:expert_advisors!licenses_ea_id_fkey(name, display_name)'
      )
      .eq('license_key', String(license_key).trim().toUpperCase())
      .maybeSingle()


    if (licErr) throw licErr

    if (!license) {
      return json({ success: false, error: 'Licence not recognised.' }, 401)
    }
    if (license.status !== 'active') {
      return json({ success: false, error: 'Licence is not active.' }, 403)
    }
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return json({ success: false, error: 'Licence has expired.' }, 403)
    }

    const account_id = (license.metadata as Record<string, unknown> | null)
      ?.metacopier_account_id as string | undefined

    if (!account_id) {
      return json({
        success: false,
        error: 'No trading account is connected to this licence.',
      }, 409)
    }

    // Respect a per-licence symbol restriction when one is set.
    const allowed = Array.isArray(license.allowed_symbols) ? license.allowed_symbols : []
    const cleanPair = String(pair).replace(/[^A-Za-z0-9]/g, '').toUpperCase()
    if (allowed.length > 0 && !allowed.map((s: string) => s.toUpperCase()).includes(cleanPair)) {
      return json({ success: false, error: `Symbol ${cleanPair} is not enabled on this licence.` }, 403)
    }

    // ---- Apply the subscriber's own per-symbol plan --------------------------
    //
    // licenses.allowed_symbols above is the MENTOR's allowance. This is the
    // other half: of the symbols the robot permits, which ones this subscriber
    // actually enabled, at what size, and how many at once. Set on the Trading
    // Symbols screen and pushed here by sync-symbol-config.
    //
    // Enforced server-side because the handset is not a trustworthy place to
    // keep a cap. A stale, rolled-back or tampered-with client would otherwise
    // size positions off numbers the user thought they had changed.
    //
    // Absent configuration is NOT a block: a licence that has never synced has
    // no rows here, and those installs must keep trading exactly as before.
    const { data: symbolCfg, error: cfgErr } = await supabase
      .from('license_symbol_config')
      .select('enabled, lot, max_trades, smart_lot')
      .eq('license_id', license.id)
      .eq('symbol', cleanPair)
      .maybeSingle()

    if (cfgErr) {
      // Reading the plan failed, which says nothing about what the user wants.
      // Logged and skipped rather than thrown: a status query hiccup must not
      // become a trading outage.
      console.warn(`[metacopier-execute] symbol config unreadable: ${cfgErr.message}`)
    }

    let effectiveLots = lots

    if (symbolCfg) {
      if (symbolCfg.enabled === false) {
        return json({
          success: false,
          error: `${cleanPair} is switched off in your trading symbols.`,
        }, 403)
      }

      // The configured size is a ceiling, not a replacement. A client sending
      // less than the user configured is honoured -- it may be sizing down for
      // a reason this function cannot see -- but nothing may exceed the number
      // the user actually set on the screen.
      const riskProfile = (license.metadata as Record<string, unknown> | null)
        ?.risk_profile as Record<string, unknown> | undefined

      const smartLotSize = Number(riskProfile?.smart_lot_size) || 0
      const ceiling = symbolCfg.smart_lot && smartLotSize > 0
        ? smartLotSize
        : Number(symbolCfg.lot) || 0

      if (ceiling > 0 && effectiveLots > ceiling) {
        console.log(
          `[metacopier-execute] ${cleanPair} volume ${effectiveLots} capped to configured ${ceiling}`
        )
        effectiveLots = ceiling
      }

      // ---- Concurrency cap --------------------------------------------------
      const maxTrades = Number(symbolCfg.max_trades) || 0
      if (maxTrades > 0) {
        const open = await countOpenPositions(account_id, cleanPair, METACOPIER_API_KEY)

        // null means the broker could not be asked. Fail open and say so: a
        // transient failure on the positions endpoint blocking every order is a
        // worse outcome than briefly exceeding a self-imposed cap, and silence
        // here would make that choice invisible.
        if (open === null) {
          console.warn(
            `[metacopier-execute] could not count open ${cleanPair} positions; cap not enforced`
          )
        } else if (open >= maxTrades) {
          return json({
            success: false,
            error: `Already holding ${open} ${cleanPair} position(s); your limit is ${maxTrades}.`,
          }, 409)
        }
      }
    }

    // ---- Place the position -------------------------------------------------
    const positionRequest = {
      symbol: cleanPair,
      orderType,
      volume: effectiveLots,
      openPrice: 0,                       // 0 => market order
      stopLoss: Number(sl) || 0,          // 0 => no stop loss
      takeProfit: Number(tp) || 0,        // 0 => no take profit
      requestId: requestIdFrom(String(signal_id ?? `${license.id}:${Date.now()}`)),
      comment: tradeComment(license),
    }

    const mcResponse = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(account_id)}/positions`,
      {
        method: 'POST',
        headers: {
          'X-API-KEY': METACOPIER_API_KEY,
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: JSON.stringify(positionRequest),
      }
    )

    // Success is 204 No Content -- there is no body to parse.
    if (!mcResponse.ok) {
      const detail = await mcResponse.text()
      console.error(`[MetaCopier] open position failed ${mcResponse.status}: ${detail}`)

      await supabase.from('signal_logs').insert([{
        license_id: license.id,
        license_key: String(license_key).trim().toUpperCase(),
        ea_id: license.ea_id,
        raw_data: { status: mcResponse.status, detail, request: positionRequest },
        status: 'failed',
      }])

      return json({
        success: false,
        error: 'Trade could not be placed with the broker.',
        details: detail,
      }, 502)
    }

    await supabase.from('signal_logs').insert([{
      license_id: license.id,
      license_key: String(license_key).trim().toUpperCase(),
      ea_id: license.ea_id,
      raw_data: { request: positionRequest, account_id },
      status: 'executed',
    }])

    return json({
      success: true,
      // effectiveLots, not lots: reporting the requested size when a cap reduced
      // it tells the user they hold a position they do not hold.
      message: `${orderType} ${cleanPair} ${effectiveLots} lots sent.`,
      requestId: positionRequest.requestId,
    })

  } catch (error) {
    console.error('[metacopier-execute] fatal:', error)
    return json({ success: false, error: (error as Error).message }, 500)
  }
})
