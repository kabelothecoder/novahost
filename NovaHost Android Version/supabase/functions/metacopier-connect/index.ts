import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { bridgeFor, providerOf, resolveLink, withLink } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Links a user's MT4/MT5 account through the licence's broker bridge and
 * records the provider account id, which is what metacopier-execute places
 * orders against.
 *
 * The name is historical: the work is done by whichever adapter
 * `_shared/bridge` picks for the licence. The slug stays because every app
 * build in the field calls it.
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
    const novaHost = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

    const body = await req.json().catch(() => ({}))
    const { license_key, account_number, password, server, platform, region_name, symbol_suffix, account_type } = body

    // `action: 'servers'` is a read-only lookup of the server names the bridge
    // can dial, for autocomplete and "did you mean". It needs a licence and
    // nothing else -- no credentials, and it never creates or touches an account.
    const serversOnly = body?.action === 'servers'

    if (!license_key || (!serversOnly && (!account_number || !password || !server))) {
      return json({
        success: false,
        error: 'Account number, password and server are all required.',
      }, 400)
    }

    // ---- Authorize by licence -----------------------------------------------
    const key = String(license_key).trim().toUpperCase()
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, ea_id, status, expires_at, metadata')
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

    const wantPlatform = String(platform ?? 'MT5').toUpperCase().replace(/[^A-Z0-9]/g, '')
    const normalize = (s: unknown) => String(s ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '')

    // The provider is the licence's, not the caller's. Today every licence is
    // on MetaCopier; once MetaRPC is live, a licence moved there links there.
    const priorMetadata = (license.metadata ?? {}) as Record<string, unknown>
    const provider = providerOf(priorMetadata)
    const bridge = bridgeFor(provider, null, novaHost)
    if (!bridge) {
      return json({ success: false, error: 'Server misconfiguration.' }, 500)
    }

    if (serversOnly) {
      const list = await bridge.servers(wantPlatform)
      if (!list) return json({ success: false, code: 'SERVER_LIST_UNAVAILABLE', error: 'Could not load the server list.' }, 502)
      const q = String(body?.query ?? '').trim()
      const servers = q
        ? list.filter((s) => normalize(s).includes(normalize(q))).slice(0, 50)
        : list.slice(0, 50)
      return json({ success: true, platform: wantPlatform, total: list.length, servers })
    }

    // ---- Link the account ----------------------------------------------------
    // De-duplication, slot reclaim, server-name repair and the wait for the
    // session to come up are all the adapter's: they are how ONE provider
    // behaves, not what linking means.
    const result = await bridge.connect({
      licenseKey: key,
      login: String(account_number ?? ''),
      password: String(password ?? ''),
      server: String(server ?? ''),
      platform: wantPlatform,
      region: region_name ?? null,
      priorAccountId: resolveLink(priorMetadata)?.accountId ?? null,
    })

    if (!result.ok) {
      // Broker credentials are never echoed back to the client.
      return json({
        success: false,
        ...(result.code ? { code: result.code } : {}),
        error: result.error,
        ...(result.extra ?? {}),
      }, result.status)
    }

    const accountId = result.accountId
    const linkState = result.state
    const reused = result.reused

    // ---- Record the link, WITHOUT storing broker credentials ----------------
    // Kept on the licence rather than broker_accounts: app users authenticate by
    // licence key and have no auth.users row, but broker_accounts.user_id is a
    // NOT NULL FK to auth.users. Binding here also lets metacopier-execute
    // resolve the account server-side instead of trusting the client.
    //
    // Re-read rather than reusing the licence row from the top: the connect
    // above can take half a minute, and a stale copy written back would undo
    // anything that changed the metadata in the meantime.
    const { data: current } = await novaHost
      .from('licenses')
      .select('metadata')
      .eq('id', license.id)
      .maybeSingle()

    // What the verify step actually saw. `connected` means an order placed now
    // will reach the broker; `connecting` means the account is registered and
    // the session is still coming up -- the app shows that as a wait, not a
    // green light, and metacopier-execute holds signals that arrive during it.
    const metadata = withLink(
      (current?.metadata ?? {}) as Record<string, unknown>,
      provider,
      accountId,
      linkState,
    )
    metadata.broker_server = result.server
    metadata.platform = result.platform
    metadata.connected_at = new Date().toISOString()
    if (linkState === 'connected') {
      metadata.bridge_verified_at = new Date().toISOString()
      // Compatibility copy for readers that predate the bridge.
      if (provider === 'metacopier') metadata.metacopier_connected_verified_at = metadata.bridge_verified_at
    }

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
      `[metacopier-connect] licence ${license.id} symbol suffix = "${suffix}"` +
      (suffix ? ` (orders will read e.g. XAUUSD${suffix})` : ' (bare symbol names)')
    )

    const { error: linkErr } = await novaHost
      .from('licenses')
      .update({ metadata })
      .eq('id', license.id)

    // The account now exists at the provider but nothing points at it, so the app
    // would show NOT LINKED forever while the provider bills for it. Reported as
    // its own failure rather than thrown, because the generic 500 handler
    // returns a Postgres message that tells the user nothing and hides the fact
    // that a retry is safe -- the de-duplication step reuses this account rather
    // than creating a second one.
    if (linkErr) {
      console.error(`[metacopier-connect] link write failed for licence ${license.id}: ${linkErr.message}`)
      return json({
        success: false,
        code: 'LINK_NOT_SAVED',
        error: 'Your broker accepted the login but we could not save the connection. Please try again.',
        details: linkErr.message,
      }, 500)
    }

    const connected = linkState === 'connected'
    return json({
      success: true,
      code: connected ? 'CONNECTED' : 'CONNECTING',
      status: linkState,
      account_id: accountId,
      platform: result.platform,
      reused,
      message: connected
        ? 'Trading account connected.'
        : 'Account linked. Your broker is still finishing its connection — give it about a minute before you start the robot.',
    })

  } catch (error) {
    console.error('[metacopier-connect] fatal:', error)
    return json({ success: false, error: (error as Error).message }, 500)
  }
})
