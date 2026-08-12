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
      .select('id, ea_id, status, expires_at, allowed_symbols, metadata')
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

    // ---- Place the position -------------------------------------------------
    const positionRequest = {
      symbol: cleanPair,
      orderType,
      volume: lots,
      openPrice: 0,                       // 0 => market order
      stopLoss: Number(sl) || 0,          // 0 => no stop loss
      takeProfit: Number(tp) || 0,        // 0 => no take profit
      requestId: requestIdFrom(String(signal_id ?? `${license.id}:${Date.now()}`)),
      comment: 'NovaEdge',
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
      message: `${orderType} ${cleanPair} ${lots} lots sent.`,
      requestId: positionRequest.requestId,
    })

  } catch (error) {
    console.error('[metacopier-execute] fatal:', error)
    return json({ success: false, error: (error as Error).message }, 500)
  }
})
