import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { bridgeFor, NEW_LINK_PROVIDER, resolveLink, withLink } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Links a user's MT4/MT5 account through the licence's broker bridge and
 * records the provider account id, which is what broker-execute places
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

    // Multi-account actions. `add_account` links ANOTHER account under the same
    // key without touching the primary; `list_accounts` and `remove_account`
    // need no credentials. The primary link (no action) behaves exactly as before.
    const action = String(body?.action ?? '')
    const addAccount = action === 'add_account'
    const noCredentials = serversOnly || action === 'list_accounts' || action === 'remove_account'

    if (!license_key || (!noCredentials && (!account_number || !password || !server))) {
      return json({
        success: false,
        error: 'Account number, password and server are all required.',
      }, 400)
    }

    // ---- Authorize by licence -----------------------------------------------
    const key = String(license_key).trim().toUpperCase()
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, ea_id, status, expires_at, metadata, max_broker_accounts')
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

    const priorMetadata = (license.metadata ?? {}) as Record<string, unknown>
    // Every new link goes to MetaRPC; MetaCopier takes no new accounts. The
    // licence's current link, on whatever provider, is torn down after.
    const provider = NEW_LINK_PROVIDER
    const priorLink = resolveLink(priorMetadata)
    const bridge = bridgeFor(provider, null, novaHost)
    if (!bridge) {
      return json({ success: false, error: 'Server misconfiguration.' }, 500)
    }

    // ---- Accounts under this key ---------------------------------------------
    const liveAccounts = async () => {
      const { data } = await novaHost
        .from('license_broker_accounts')
        .select('id, provider, bridge_account_id, platform, login, server, label, is_primary, auto_execute, lot_multiplier, status, created_at')
        .eq('license_id', license.id)
        .is('disconnected_at', null)
        .order('is_primary', { ascending: false })
        .order('created_at', { ascending: true })
      return data ?? []
    }
    const maxAccounts = Number(license.max_broker_accounts) || 1

    if (action === 'list_accounts') {
      const accounts = await liveAccounts()
      return json({
        success: true,
        max_accounts: maxAccounts,
        accounts: accounts.map((a) => ({
          account_id: a.bridge_account_id, provider: a.provider, platform: a.platform,
          login: a.login, server: a.server, label: a.label, primary: a.is_primary,
          auto_execute: a.auto_execute, lot_multiplier: Number(a.lot_multiplier), status: a.status,
        })),
      })
    }

    if (action === 'remove_account') {
      const target = String(body?.account_id ?? '')
      const row = (await liveAccounts()).find((a) => a.bridge_account_id === target)
      if (!row) return json({ success: false, code: 'ACCOUNT_NOT_ON_LICENCE', error: 'That account is not linked to this licence.' }, 404)
      // The primary is unlinked through the normal broker setup, which also
      // clears the licence's own link; removing it here would leave that dangling.
      if (row.is_primary) return json({ success: false, code: 'PRIMARY_ACCOUNT', error: 'Change your main account from Broker Setup.' }, 409)
      // Stops the provider billing. A failure here still unlinks: a terminal we
      // no longer trade on is a cost to chase, not a reason to keep trading it.
      const stopped = await bridgeFor(row.provider, row.bridge_account_id, novaHost)?.disconnect().catch(() => false)
      await novaHost.from('license_broker_accounts')
        .update({ disconnected_at: new Date().toISOString(), status: 'disconnected' })
        .eq('id', row.id)
      if (!stopped) console.warn(`[broker-connect] ${row.provider} account ${row.bridge_account_id} unlinked but not stopped at the provider`)
      return json({ success: true, code: 'REMOVED', account_id: target })
    }

    if (addAccount) {
      const accounts = await liveAccounts()
      if (accounts.length === 0) {
        return json({ success: false, code: 'NO_PRIMARY_ACCOUNT', error: 'Connect your main account first.' }, 409)
      }
      if (accounts.length >= maxAccounts) {
        return json({
          success: false, code: 'ACCOUNT_LIMIT_REACHED',
          error: `This licence can trade ${maxAccounts} account${maxAccounts === 1 ? '' : 's'}. Remove one or add more accounts to your plan.`,
          max_accounts: maxAccounts,
        }, 403)
      }
      const login = String(account_number ?? '').replace(/\s+/g, '')
      if (accounts.some((a) => a.login === login && a.server.toLowerCase() === String(server).trim().toLowerCase())) {
        return json({ success: false, code: 'ACCOUNT_ALREADY_LINKED', error: 'That account is already linked to this licence.' }, 409)
      }
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
      // An added account replaces nothing, so it must not be handed the
      // primary's id to tear down.
      priorAccountId: addAccount || priorLink?.provider !== provider ? null : priorLink.accountId,
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
    // NOT NULL FK to auth.users. Binding here also lets broker-execute
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
    // green light, and broker-execute holds signals that arrive during it.
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
    // ---- An added account is a row of its own ---------------------------------
    // The licence metadata stays the primary's. Nothing written above has been
    // saved; for an added account it is simply not used.
    if (addAccount) {
      const { error: addErr } = await novaHost.from('license_broker_accounts').insert({
        license_id: license.id,
        provider,
        bridge_account_id: accountId,
        platform: result.platform === 'MT4' ? 'MT4' : 'MT5',
        login: String(account_number ?? '').replace(/\s+/g, ''),
        server: result.server,
        label: body?.label ? String(body.label).slice(0, 40) : null,
        is_primary: false,
        symbol_suffix: suffix,
        status: linkState,
      })
      if (addErr) {
        // Linked at the provider but not recorded: stop it so it does not bill unseen.
        console.error(`[broker-connect] add_account write failed for licence ${license.id}: ${addErr.message}`)
        await bridgeFor(provider, accountId, novaHost)?.disconnect().catch(() => false)
        return json({ success: false, code: 'LINK_NOT_SAVED', error: 'Your broker accepted the login but we could not save the connection. Please try again.' }, 500)
      }
      return json({
        success: true,
        code: linkState === 'connected' ? 'CONNECTED' : 'CONNECTING',
        status: linkState,
        account_id: accountId,
        platform: result.platform,
        primary: false,
        message: 'Additional trading account connected.',
      })
    }

    metadata.symbol_suffix = suffix
    if (account_type) metadata.account_type = String(account_type).trim()

    console.log(
      `[broker-connect] licence ${license.id} symbol suffix = "${suffix}"` +
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
      console.error(`[broker-connect] link write failed for licence ${license.id}: ${linkErr.message}`)
      return json({
        success: false,
        code: 'LINK_NOT_SAVED',
        error: 'Your broker accepted the login but we could not save the connection. Please try again.',
        details: linkErr.message,
      }, 500)
    }

    // ---- Retire the old provider's account -------------------------------------
    // A re-link that moved provider (MetaCopier -> MetaRPC) leaves the old
    // account billing at the old provider until it is deleted. Best-effort: the
    // new link is already saved, and an orphan is a cost, not a trading risk.
    if (priorLink && priorLink.provider !== provider) {
      const old = bridgeFor(priorLink.provider, priorLink.accountId, novaHost)
      const gone = await old?.disconnect().catch(() => false)
      console.log(`[broker-connect] licence ${license.id} moved ${priorLink.provider} -> ${provider}; old account ${gone ? 'deleted' : 'NOT deleted, reclaim by hand'}`)
    }

    // ---- Keep the primary row in step with the licence -----------------------
    // Best-effort: the licence metadata is still the record for the primary,
    // so a failure here costs only the multi-account view, not trading.
    {
      const login = String(account_number ?? '').replace(/\s+/g, '')
      const now = new Date().toISOString()
      // The old primary, and any added account that is this same login, retire.
      await novaHost.from('license_broker_accounts')
        .update({ is_primary: false, disconnected_at: now, status: 'replaced' })
        .eq('license_id', license.id).eq('is_primary', true)
      await novaHost.from('license_broker_accounts')
        .update({ disconnected_at: now, status: 'replaced' })
        .eq('license_id', license.id).eq('login', login).ilike('server', result.server).is('disconnected_at', null)
      const { error: rowErr } = await novaHost.from('license_broker_accounts').upsert({
        license_id: license.id,
        provider,
        bridge_account_id: accountId,
        platform: result.platform === 'MT4' ? 'MT4' : 'MT5',
        login,
        server: result.server,
        is_primary: true,
        symbol_suffix: suffix,
        status: linkState,
        disconnected_at: null,
      }, { onConflict: 'provider,bridge_account_id' })
      if (rowErr) console.error(`[broker-connect] primary row sync failed for licence ${license.id}: ${rowErr.message}`)
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
    console.error('[broker-connect] fatal:', error)
    return json({ success: false, error: (error as Error).message }, 500)
  }
})
