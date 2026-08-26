import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * Registers a user's MT4/MT5 account with MetaCopier and returns the MetaCopier
 * account id, which is what metacopier-execute places orders against.
 *
 * Replaces the MetaAPI broker bridge. Region and account-type ids are looked up
 * from MetaCopier at runtime rather than hardcoded, because those ids are
 * project/environment specific.
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
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL') ?? ''
    const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    const METACOPIER_API_KEY = Deno.env.get('METACOPIER_API_KEY') ?? ''

    if (!METACOPIER_API_KEY) {
      console.error('METACOPIER_API_KEY is not configured')
      return json({ success: false, error: 'Server misconfiguration.' }, 500)
    }

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

    const body = await req.json().catch(() => ({}))
    const { license_key, account_number, password, server, platform, region_name } = body

    if (!license_key || !account_number || !password || !server) {
      return json({
        success: false,
        error: 'Account number, password and server are all required.',
      }, 400)
    }

    // ---- Authorize by licence -----------------------------------------------
    const key = String(license_key).trim().toUpperCase()
    const { data: license, error: licErr } = await supabase
      .from('licenses')
      .select('id, ea_id, status, expires_at')
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr
    if (!license) return json({ success: false, error: 'Licence not recognised.' }, 401)
    if (license.status !== 'active') return json({ success: false, error: 'Licence is not active.' }, 403)
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return json({ success: false, error: 'Licence has expired.' }, 403)
    }

    const mcHeaders = {
      'X-API-KEY': METACOPIER_API_KEY,
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    }

    // ---- Resolve account type (MT4 / MT5) and region ------------------------
    const wantPlatform = String(platform ?? 'MT5').toUpperCase().replace(/[^A-Z0-9]/g, '')

    const [typesRes, regionsRes] = await Promise.all([
      fetch(`${METACOPIER_BASE}/rest/api/v1/types/accountTypes`, { headers: mcHeaders }),
      fetch(`${METACOPIER_BASE}/rest/api/v1/types/regions`, { headers: mcHeaders }),
    ])

    if (!typesRes.ok || !regionsRes.ok) {
      const detail = `accountTypes=${typesRes.status} regions=${regionsRes.status}`
      console.error(`[MetaCopier] lookup failed: ${detail}`)
      return json({ success: false, error: 'Could not reach MetaCopier.', details: detail }, 502)
    }

    const accountTypes = await typesRes.json()
    const regions = await regionsRes.json()

    const normalize = (s: unknown) => String(s ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '')

    const matchedType = (Array.isArray(accountTypes) ? accountTypes : []).find(
      (t: { name?: string }) => normalize(t?.name).includes(wantPlatform)
    )
    if (!matchedType) {
      return json({
        success: false,
        error: `Platform ${wantPlatform} is not offered by this MetaCopier project.`,
        available: (Array.isArray(accountTypes) ? accountTypes : []).map((t: { name?: string }) => t?.name),
      }, 400)
    }

    const regionList = Array.isArray(regions) ? regions : []
    const matchedRegion = region_name
      ? regionList.find((r: { name?: string }) => normalize(r?.name) === normalize(region_name))
      : regionList[0]

    if (!matchedRegion) {
      return json({
        success: false,
        error: 'No MetaCopier region available.',
        available: regionList.map((r: { name?: string }) => r?.name),
      }, 400)
    }

    // ---- Register the account ----------------------------------------------
    const accountPayload = {
      loginAccountNumber: String(account_number).trim(),
      loginAccountPassword: String(password),
      loginServer: String(server).trim(),
      type: { id: matchedType.id },
      region: { id: matchedRegion.id },
      alias: `NovaEdge ${key}`,
      // Don't create a duplicate if this login already exists in the project.
      failIfAccountExistsInProject: false,
    }

    const createRes = await fetch(`${METACOPIER_BASE}/rest/api/v1/accounts`, {
      method: 'POST',
      headers: mcHeaders,
      body: JSON.stringify(accountPayload),
    })

    if (!createRes.ok) {
      const detail = await createRes.text()
      console.error(`[MetaCopier] account create failed ${createRes.status}: ${detail}`)
      // Broker credentials are never echoed back to the client.
      return json({
        success: false,
        error: 'Could not connect this trading account. Check the login, password and server.',
        details: detail,
      }, 502)
    }

    const created = await createRes.json().catch(() => ({}))
    const metacopierAccountId = created?.id
    if (!metacopierAccountId) {
      return json({ success: false, error: 'MetaCopier did not return an account id.' }, 502)
    }

    // Start the account so it connects to the broker.
    await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(metacopierAccountId)}/actions/start`,
      { method: 'POST', headers: mcHeaders }
    ).catch((e) => console.warn('[MetaCopier] start failed (non-fatal):', e))

    // ---- Record the link, WITHOUT storing broker credentials ----------------
    // Kept on the licence rather than broker_accounts: app users authenticate by
    // licence key and have no auth.users row, but broker_accounts.user_id is a
    // NOT NULL FK to auth.users. Binding here also lets metacopier-execute
    // resolve the account server-side instead of trusting the client.
    const { data: current } = await supabase
      .from('licenses')
      .select('metadata')
      .eq('id', license.id)
      .maybeSingle()

    const metadata = (current?.metadata ?? {}) as Record<string, unknown>
    metadata.metacopier_account_id = String(metacopierAccountId)
    metadata.broker_server = String(server).trim()
    metadata.platform = wantPlatform
    metadata.connected_at = new Date().toISOString()

    const { error: linkErr } = await supabase
      .from('licenses')
      .update({ metadata })
      .eq('id', license.id)

    if (linkErr) throw linkErr

    return json({
      success: true,
      account_id: metacopierAccountId,
      platform: matchedType.name ?? wantPlatform,
      region: matchedRegion.name ?? null,
      message: 'Trading account connected.',
    })

  } catch (error) {
    console.error('[metacopier-connect] fatal:', error)
    return json({ success: false, error: (error as Error).message }, 500)
  }
})
