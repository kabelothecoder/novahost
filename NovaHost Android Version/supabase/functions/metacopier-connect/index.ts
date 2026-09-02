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
    const { license_key, account_number, password, server, platform, region_name, symbol_suffix, account_type } = body

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
    if (!license) {
      return json({
        success: false,
        code: 'LICENCE_UNKNOWN',
        error: 'This device is not activated. Activate your licence key before connecting a broker account.',
      }, 401)
    }
    if (license.status !== 'active') {
      return json({ success: false, code: 'LICENCE_INACTIVE', error: 'Licence is not active.' }, 403)
    }
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return json({ success: false, code: 'LICENCE_EXPIRED', error: 'Licence has expired.' }, 403)
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

    // Exact match first. A substring match will happily bind an "MT5_INVESTOR"
    // style type to a plain MT5 request, and an investor password cannot place
    // orders -- the broker rejects the login and the user is told their password
    // is wrong. Substring is kept only as a fallback for projects that name the
    // type "METATRADER5" rather than "MT5".
    const typeList = (Array.isArray(accountTypes) ? accountTypes : []) as { id?: string; name?: string }[]
    const matchedType =
      typeList.find((t) => normalize(t?.name) === wantPlatform) ??
      typeList.find((t) => normalize(t?.name).includes(wantPlatform))

    if (!matchedType) {
      return json({
        success: false,
        code: 'PLATFORM_UNAVAILABLE',
        error: `Platform ${wantPlatform} is not offered by this MetaCopier project.`,
        available: typeList.map((t) => t?.name),
      }, 400)
    }

    // Region is the datacenter that dials the broker. Silently taking the first
    // one the API happens to list makes every connection depend on undeclared
    // ordering, so an explicit choice wins: the caller's, then the project
    // default, then the first entry -- and whichever it lands on gets logged so
    // a failure is attributable.
    const regionList = (Array.isArray(regions) ? regions : []) as { id?: string; name?: string }[]
    const preferredRegion = region_name ?? Deno.env.get('METACOPIER_REGION') ?? ''
    const matchedRegion = preferredRegion
      ? regionList.find((r) => normalize(r?.name) === normalize(preferredRegion))
      : regionList[0]

    if (!matchedRegion) {
      return json({
        success: false,
        code: 'REGION_UNAVAILABLE',
        error: preferredRegion
          ? `MetaCopier region "${preferredRegion}" was not found.`
          : 'No MetaCopier region available.',
        available: regionList.map((r) => r?.name),
      }, 400)
    }

    console.log(
      `[MetaCopier] resolved type=${matchedType.name} region=${matchedRegion.name} ` +
      `platform=${wantPlatform} server=${String(server).trim()}`
    )

    // ---- Register the account ----------------------------------------------
    // Whitespace is stripped from all three because these values are pasted out
    // of a broker email far more often than they are typed, and a trailing
    // newline on the password is indistinguishable, to the user, from a correct
    // password. MetaTrader does not accept leading or trailing whitespace in any
    // of them, so nothing legitimate is lost by removing it here.
    const accountPayload = {
      loginAccountNumber: String(account_number).replace(/\s+/g, ''),
      loginAccountPassword: String(password).trim(),
      loginServer: String(server).trim(),
      type: { id: matchedType.id },
      region: { id: matchedRegion.id },
      alias: `NovaHost ${key}`,
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
      console.error(
        `[MetaCopier] account create failed ${createRes.status}: ${detail} ` +
        `(type=${matchedType.name} region=${matchedRegion.name} server=${accountPayload.loginServer})`
      )

      // MetaCopier reports faults as bracketed codes in an `errors` array, e.g.
      // {"errors":["[WRONG_CREDENTIALS]"]}. Match on the code rather than on
      // loose substrings of the raw body: the previous version looked for
      // INVALID_CREDENTIALS and AUTHENTICATION, neither of which MetaCopier ever
      // sends, so every broker rejection fell through to the catch-all and the
      // one message that would have told the user what to fix was dead code.
      const codes = (detail.match(/\[([A-Z0-9_]+)\]/g) ?? []).map((c) => c.slice(1, -1))
      const has = (...names: string[]) => names.some((n) => codes.includes(n))

      // A credential or server fault is the user's to fix and must not be
      // dressed up as a gateway failure -- 502 tells the client the provider
      // broke, which is how a wrong password ended up being retried instead of
      // corrected.
      let status = 400
      let code = codes[0] ?? 'CONNECT_FAILED'
      let message = 'Could not connect this trading account. Check the login, password and server.'

      if (has('PLEASE_FUND_YOUR_PROJECT')) {
        status = 502
        code = 'PROVIDER_UNFUNDED'
        message = 'Account connection is unavailable: the NovaHost trading provider needs funding. This is on our side, not your login details.'
      } else if (codes.some((c) => c.includes('LIMIT'))) {
        status = 502
        code = 'PROVIDER_LIMIT'
        message = 'The connected-account limit has been reached. Please contact support.'
      } else if (has('WRONG_CREDENTIALS', 'INVALID_CREDENTIALS', 'AUTHENTICATION_FAILED', 'AUTHENTICATION')) {
        code = 'WRONG_CREDENTIALS'
        message =
          `Your broker rejected this login. Check that the account number and password are the ` +
          `${wantPlatform} trading password (not the investor password), and that "${accountPayload.loginServer}" ` +
          `is the server name exactly as MetaTrader shows it. If the account is ` +
          `${wantPlatform === 'MT5' ? 'MT4' : 'MT5'}, switch the platform tab and try again.`
      } else if (codes.some((c) => c.includes('SERVER'))) {
        code = 'UNKNOWN_SERVER'
        message =
          `"${accountPayload.loginServer}" was not recognised as a ${wantPlatform} server. Copy it exactly ` +
          `as it appears in MetaTrader under Tools -> Options -> Server.`
      }

      // Broker credentials are never echoed back to the client.
      return json({ success: false, code, error: message, details: detail }, status)
    }

    const created = await createRes.json().catch(() => ({}))
    // Stringified at the boundary so the id has one type everywhere: the client
    // decodes it as a string and the licence row already stored it as one.
    const metacopierAccountId = created?.id == null ? '' : String(created.id)
    if (!metacopierAccountId) {
      return json({ success: false, code: 'NO_ACCOUNT_ID', error: 'MetaCopier did not return an account id.' }, 502)
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
    metadata.metacopier_account_id = metacopierAccountId
    metadata.broker_server = accountPayload.loginServer
    metadata.platform = wantPlatform
    metadata.connected_at = new Date().toISOString()

    // ---- What this broker calls its instruments -----------------------------
    //
    // NovaHost trades canonical names (XAUUSD, NAS100). Brokers decorate them:
    // a micro book is XAUUSD.m, a raw-spread one XAUUSDpro. An order naming a
    // symbol the broker does not list is rejected outright, so the decoration
    // has to be recorded at the moment we know which account this is.
    //
    // It was being collected and thrown away. The app derived the suffix from
    // the account type, passed it to MetaAPIManager.testBrokerConnection, and
    // the request built there dropped it -- so the executor sent bare canonical
    // names to every account and micro users had a robot that placed orders and
    // filled none of them.
    //
    // The client's suffix wins when given; otherwise it is derived from the
    // account type the same way the app does, so an older build that still
    // sends only the type keeps working.
    const derivedSuffix = String(account_type ?? '').trim().toUpperCase().startsWith('MICRO')
      ? '.m'
      : ''
    const suffix = String(symbol_suffix ?? derivedSuffix)
      .trim()
      // Ends up inside an order symbol, so only what MetaTrader uses in a
      // symbol name survives.
      .replace(/[^A-Za-z0-9._-]/g, '')

    // An empty suffix is written, not skipped: a user moving from a micro to a
    // standard account must be able to clear a stale ".m", and leaving the old
    // key in place would silently keep decorating symbols the new book does not
    // have.
    metadata.symbol_suffix = suffix
    if (account_type) metadata.account_type = String(account_type).trim()

    console.log(
      `[MetaCopier] licence ${license.id} symbol suffix = "${suffix}"` +
      (suffix ? ` (orders will read e.g. XAUUSD${suffix})` : ' (bare symbol names)')
    )

    const { error: linkErr } = await supabase
      .from('licenses')
      .update({ metadata })
      .eq('id', license.id)

    // The account now exists at MetaCopier but nothing points at it, so the app
    // would show NOT LINKED forever while the provider bills for it. Reported as
    // its own failure rather than thrown, because the generic 500 handler
    // returns a Postgres message that tells the user nothing and hides the fact
    // that a retry is safe -- failIfAccountExistsInProject is false, so
    // reconnecting reuses the same account instead of creating a second one.
    if (linkErr) {
      console.error(`[metacopier-connect] link write failed for licence ${license.id}: ${linkErr.message}`)
      return json({
        success: false,
        code: 'LINK_NOT_SAVED',
        error: 'Your broker accepted the login but we could not save the connection. Please try again.',
        details: linkErr.message,
      }, 500)
    }

    return json({
      success: true,
      code: 'CONNECTED',
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
