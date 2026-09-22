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

    const novaHost = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

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

    const mcHeaders = {
      'X-API-KEY': METACOPIER_API_KEY,
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    }

    const wantPlatform = String(platform ?? 'MT5').toUpperCase().replace(/[^A-Z0-9]/g, '')
    const finalServer = String(server).trim()
    const wantLogin = String(account_number).replace(/\s+/g, '')
    const normalize = (s: unknown) => String(s ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '')

    /**
     * MetaCopier reports faults as bracketed codes, and many of them carry
     * parameters: `[ACCOUNT_LIMIT_PER_PROJECT_REACHED_TRIAL|allowed=10|needed=160]`.
     *
     * The first version of this matched `/\[([A-Z0-9_]+)\]/g`, which requires the
     * WHOLE bracket to be a bare code -- so every parameterised fault parsed to
     * an empty code list and fell through to the generic "check the login,
     * password and server". That is how a project sitting at its trial account
     * cap told users their credentials were wrong: four different brokers, ten
     * attempts, all correct, all blamed on the password.
     *
     * Stopping at the first `|` keeps the code and discards the parameters.
     */
    const codesIn = (detail: string) =>
      [...detail.matchAll(/\[([A-Z0-9_]+)(?:\|[^\]]*)?\]/g)].map((m) => m[1])

    /** Stops an account, so a DELETE is not refused for one still running. */
    const stopAccount = async (id: string) => {
      try {
        await fetch(
          `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}/actions/stop`,
          { method: 'POST', headers: mcHeaders },
        )
      } catch { /* best effort -- the delete below is what matters */ }
    }

    /**
     * Deletes a MetaCopier account. Best-effort -- a failed cleanup is logged,
     * not fatal.
     *
     * Stops it first. A bare DELETE on a running account was answering 400, and
     * because the failure is non-fatal every orphan it was meant to clear stayed
     * in the project and kept consuming a slot against the plan's account cap.
     */
    const deleteAccount = async (id: string) => {
      try {
        await stopAccount(id)
        const r = await fetch(`${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}`, {
          method: 'DELETE', headers: mcHeaders,
        })
        if (!r.ok) {
          console.warn(`[MetaCopier] delete ${id} refused ${r.status}: ${await r.text()}`)
          return false
        }
        console.log(`[MetaCopier] deleted account ${id}`)
        return true
      } catch (e) {
        console.warn(`[MetaCopier] delete ${id} failed: ${(e as Error).message}`)
        return false
      }
    }

    /**
     * Every account in the project, following pagination.
     *
     * The list call used to be a bare `GET /accounts`, which returns one
     * default-sized page. With ~160 accounts in the project that meant the
     * de-duplication step below could not see most of them: a duplicate past the
     * first page was invisible, so a "no existing account" conclusion was drawn
     * from a partial list and yet another duplicate was created.
     */
    const listAllAccounts = async (): Promise<Record<string, unknown>[]> => {
      const out: Record<string, unknown>[] = []
      const PAGE = 200
      for (let offset = 0; offset < 5000; offset += PAGE) {
        const r = await fetch(
          `${METACOPIER_BASE}/rest/api/v1/accounts?limit=${PAGE}&offset=${offset}`,
          { headers: mcHeaders },
        )
        if (!r.ok) break
        const raw = await r.json()
        // Bare array from the accounts endpoint today; unwrap the common
        // pagination envelopes too so a shape change does not silently turn the
        // de-dup off and let a duplicate through.
        const page: unknown[] = Array.isArray(raw)
          ? raw
          : Array.isArray(raw?.content) ? raw.content
          : Array.isArray(raw?.data) ? raw.data
          : Array.isArray(raw?.accounts) ? raw.accounts
          : []
        out.push(...(page as Record<string, unknown>[]))
        if (page.length < PAGE) break
      }
      return out
    }

    /**
     * Frees account slots by deleting MetaCopier accounts that no licence claims.
     *
     * ## The safety rule
     *
     * An account is deleted only when NOTHING in our own database points at it.
     * The keep-set is built from two independent signals, and an account matching
     * either one survives:
     *
     *   1. `licenses.metadata->>metacopier_account_id` for every licence that is
     *      not revoked -- this is the binding `metacopier-execute` resolves
     *      orders against, so an id in here is a live trading link by definition.
     *   2. the alias `NovaHost <LICENCE KEY>` that `resolveAndCreate` stamps on
     *      every account it makes. This is the backstop for the window where an
     *      account was created but the write-back to the licence had not landed
     *      yet -- without it, a connect interrupted at the wrong moment would
     *      leave an account that looks orphaned and is not.
     *
     * Anything the keep-set does not name was made by a licence that has since
     * been deleted, or by a create whose write-back never happened and whose
     * licence has since linked elsewhere. Those are the ~150 records sitting on
     * this project's cap.
     *
     * ## Why the DB read cannot fail open
     *
     * If the licence query errors, the keep-set would be empty and every account
     * in the project would look like an orphan -- the reclaim would delete live
     * trading links for all users. It returns 0 on any error instead, so the
     * failure mode is "the cap stays full", never "everyone is disconnected".
     */
    const reclaimOrphanAccounts = async (): Promise<number> => {
      const { data: live, error: liveErr } = await novaHost
        .from('licenses')
        .select('license_key, status, metadata')
        .neq('status', 'revoked')

      if (liveErr || !Array.isArray(live)) {
        console.error('[MetaCopier] reclaim aborted: could not read licences', liveErr)
        return 0
      }

      const keepIds = new Set<string>()
      const keepAliases = new Set<string>()
      for (const l of live) {
        const id = String((l.metadata as Record<string, unknown> | null)?.metacopier_account_id ?? '').trim()
        if (id) keepIds.add(id)
        const k = String(l.license_key ?? '').trim().toUpperCase()
        if (k) keepAliases.add(`NOVAHOST ${k}`)
      }

      // A project with no recorded links at all is not a project full of
      // orphans -- it is a read that told us nothing useful. Refuse to treat
      // every account in it as disposable.
      if (keepIds.size === 0 && keepAliases.size === 0) {
        console.error('[MetaCopier] reclaim aborted: no live licence references found')
        return 0
      }

      let all: Record<string, unknown>[]
      try {
        all = await listAllAccounts()
      } catch (e) {
        console.error(`[MetaCopier] reclaim aborted: account list failed: ${(e as Error).message}`)
        return 0
      }
      if (!all.length) return 0

      const orphans = all.filter((a) => {
        if (isDead(a)) return false          // already gone; a second DELETE answers 400
        const id = String(a.id ?? '')
        if (!id || keepIds.has(id)) return false
        const alias = String(a.alias ?? '').trim().toUpperCase()
        return !keepAliases.has(alias)
      })

      console.log(
        `[MetaCopier] reclaim: ${all.length} accounts in project, ` +
        `${keepIds.size} bound to a licence, ${orphans.length} orphaned`
      )

      let freed = 0
      for (const a of orphans) {
        if (await deleteAccount(String(a.id))) freed++
        // One free slot is all this connect needs. Stopping early keeps the
        // request inside its wall-clock budget on a project holding hundreds of
        // orphans; the next connect reclaims the next one.
        if (freed >= 5) break
      }
      return freed
    }

    /**
     * Resolves account type + region and registers the account with MetaCopier.
     *
     * Returns the new account id, or a ready-to-send error Response when the
     * platform/region cannot be resolved or the broker refuses the login.
     */
    const resolveAndCreate = async (): Promise<{ id: string } | { errorResponse: Response; limited?: boolean }> => {
      const [typesRes, regionsRes] = await Promise.all([
        fetch(`${METACOPIER_BASE}/rest/api/v1/types/accountTypes`, { headers: mcHeaders }),
        fetch(`${METACOPIER_BASE}/rest/api/v1/types/regions`, { headers: mcHeaders }),
      ])

      if (!typesRes.ok || !regionsRes.ok) {
        const detail = `accountTypes=${typesRes.status} regions=${regionsRes.status}`
        console.error(`[MetaCopier] lookup failed: ${detail}`)
        return { errorResponse: json({ success: false, error: 'Could not reach MetaCopier.', details: detail }, 502) }
      }

      const accountTypes = await typesRes.json()
      const regions = await regionsRes.json()

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
        return { errorResponse: json({
          success: false,
          code: 'PLATFORM_UNAVAILABLE',
          error: `Platform ${wantPlatform} is not offered by this MetaCopier project.`,
          available: typeList.map((t) => t?.name),
        }, 400) }
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
        return { errorResponse: json({
          success: false,
          code: 'REGION_UNAVAILABLE',
          error: preferredRegion
            ? `MetaCopier region "${preferredRegion}" was not found.`
            : 'No MetaCopier region available.',
          available: regionList.map((r) => r?.name),
        }, 400) }
      }

      console.log(
        `[MetaCopier] resolved type=${matchedType.name} region=${matchedRegion.name} ` +
        `platform=${wantPlatform} server=${finalServer}`
      )

      // Whitespace is stripped from all three because these values are pasted out
      // of a broker email far more often than they are typed, and a trailing
      // newline on the password is indistinguishable, to the user, from a correct
      // password. MetaTrader does not accept leading or trailing whitespace in any
      // of them, so nothing legitimate is lost by removing it here.
      const accountPayload = {
        loginAccountNumber: wantLogin,
        loginAccountPassword: String(password).trim(),
        loginServer: finalServer,
        type: { id: matchedType.id },
        region: { id: matchedRegion.id },
        alias: `NovaHost ${key}`,
        // The de-duplication step above is the real guard against a second
        // account for one login. This stays `false` as a backstop: if that
        // lookup could not run (MetaCopier's list endpoint unreachable or shaped
        // unexpectedly), a reconnect must still succeed rather than fail on an
        // "account exists" error -- the next clean reconnect will converge it.
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
          `(type=${matchedType.name} region=${matchedRegion.name} server=${finalServer})`
        )

        // MetaCopier reports faults as bracketed codes in an `errors` array, e.g.
        // {"errors":["[WRONG_CREDENTIALS]"]}. Match on the code rather than on
        // loose substrings of the raw body.
        const codes = codesIn(detail)
        const has = (...names: string[]) => names.some((n) => codes.includes(n))

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

          /* The numbers go to the LOG, not to the user.
           *
           * `allowed=10 | needed=160` is exactly what an operator needs — it
           * separates a plan ceiling from a genuinely full project, and names
           * the size of the cleanup — and exactly what a paying customer
           * should never read. "Our provider is on a trial plan" is not a
           * status update, it is a reason to ask for a refund.
           *
           * What the user gets is the one fact that changes their behaviour:
           * stop retyping your password, it is not you. `details` still
           * carries the raw body for support. */
          const allowed = detail.match(/allowed=(\d+)/)?.[1] ?? '?'
          const needed  = detail.match(/needed=(\d+)/)?.[1] ?? '?'
          console.error(
            `[MetaCopier] PROJECT AT ACCOUNT CAP: allowed=${allowed} needed=${needed} ` +
            `trial=${codes.some((c) => c.includes('TRIAL'))} — no user can link a broker until this is raised or reclaimed`
          )

          message =
            'Connecting a trading account is unavailable right now. This is a fault on our side, ' +
            'not your login details — there is nothing wrong with your account number, password ' +
            'or server. Please try again shortly, or contact support if it persists.'
        } else if (has('WRONG_CREDENTIALS', 'INVALID_CREDENTIALS', 'AUTHENTICATION_FAILED', 'AUTHENTICATION')) {
          code = 'WRONG_CREDENTIALS'
          message =
            `Your broker rejected this login. Check that the account number and password are the ` +
            `${wantPlatform} trading password (not the investor password), and that "${finalServer}" ` +
            `is the server name exactly as MetaTrader shows it. If the account is ` +
            `${wantPlatform === 'MT5' ? 'MT4' : 'MT5'}, switch the platform tab and try again.`
        } else if (codes.some((c) => c.includes('SERVER'))) {
          code = 'UNKNOWN_SERVER'
          message =
            `"${finalServer}" was not recognised as a ${wantPlatform} server. Copy it exactly ` +
            `as it appears in MetaTrader under Tools -> Options -> Server.`
        }

        // Broker credentials are never echoed back to the client.
        return {
          errorResponse: json({ success: false, code, error: message, details: detail }, status),
          limited: code === 'PROVIDER_LIMIT',
        }
      }

      const created = await createRes.json().catch(() => ({}))
      const id = created?.id == null ? '' : String(created.id)
      if (!id) {
        return { errorResponse: json({ success: false, code: 'NO_ACCOUNT_ID', error: 'MetaCopier did not return an account id.' }, 502) }
      }
      return { id }
    }

    /**
     * Starts the account and waits for the broker session to actually come up.
     *
     * Returning "connected" the instant the account is registered -- which this
     * function used to do implicitly -- is why a signal fired in the first minute
     * after a reconnect failed with `[ACCOUNT_IS_NOT_CONNTECTED]`: the account
     * existed, the MT4/MT5 session behind it did not. Up to ~24s of polling
     * turns that window from a failed trade into a "still connecting" state the
     * app can show honestly.
     */
    const startAndVerify = async (id: string): Promise<'connected' | 'connecting' | 'wrong_credentials' | 'account_gone'> => {
      await fetch(
        `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}/actions/start`,
        { method: 'POST', headers: mcHeaders }
      ).catch((e) => console.warn('[MetaCopier] start failed (non-fatal):', e))

      for (let i = 0; i < 6; i++) {
        await new Promise((r) => setTimeout(r, 4000))
        try {
          const infoRes = await fetch(
            `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}/information`,
            { headers: mcHeaders },
          )
          if (!infoRes.ok) {
            // isDead() above stops an ALREADY-deleted account from being
            // reused. This covers the account dying mid-poll instead -- a
            // concurrent reclaim, or the user deleting it at their broker
            // while we sit in this loop. Only codes that mean "this id is
            // never coming back" short-circuit to account_gone; anything else
            // (rate limits, a transient 5xx, a region still spinning up)
            // keeps polling exactly as before.
            const detail = await infoRes.text().catch(() => '')
            const codes = codesIn(detail)
            if (codes.some((c) => c === 'ACCOUNT_WAS_DELETED' || c === 'ACCOUNT_WAS_ALREADY_DELETED' || c === 'ACCOUNT_NOT_FOUND')) {
              console.warn(`[MetaCopier] account ${id} gone mid-verify: ${detail}`)
              return 'account_gone'
            }
            continue
          }
          const info = await infoRes.json()
          if (info?.wrongCredentials === true) return 'wrong_credentials'
          if (info?.connected === true) return 'connected'
        } catch { /* keep waiting */ }
      }
      return 'connecting'
    }

    /** Fetches one MetaCopier account, or null if it does not exist / cannot be read. */
    const fetchAccount = async (id: string): Promise<Record<string, unknown> | null> => {
      try {
        const r = await fetch(`${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}`, { headers: mcHeaders })
        if (!r.ok) return null
        return await r.json() as Record<string, unknown>
      } catch { return null }
    }

    /**
     * True when MetaCopier still knows about an account but has deleted it.
     *
     * `GET /accounts/{id}` answers 200 for a deleted account -- it does not
     * 404 -- and hands back the same shape as a live one, just with
     * `deleted` set and `status.name === 'Deleted'`. Every call the account
     * needs (`/actions/start`, `/information`) then 400s with
     * `[ACCOUNT_WAS_DELETED]`. Without this check, `fetchAccount` returning a
     * truthy value reads as "still there", the corpse gets reused, and
     * `startAndVerify` cannot tell that 400 apart from "still starting" --
     * so the licence reports CONNECTING forever no matter how many times the
     * user retries, because every retry lands on the same dead id.
     */
    const isDead = (a: Record<string, unknown> | null | undefined): boolean =>
      !!a && (a.deleted === true || (a.status as Record<string, unknown> | undefined)?.name === 'Deleted')

    // ---- De-duplicate: one MetaCopier account per broker login --------------
    //
    // MetaCopier will hold two accounts for one MT4/MT5 login, and they fight
    // over the broker session -- each one's connect knocks the other offline,
    // which surfaces on trades as intermittent [ACCOUNT_IS_NOT_CONNTECTED] and
    // [BROKER_REJECTION]. The old `failIfAccountExistsInProject: false` created a
    // fresh duplicate on every reconnect.
    //
    // Three signals, cheapest and most authoritative first:
    //   1. the id already stored on THIS licence -- if it still exists and still
    //      points at the same login+server, this connect is a no-op: re-start it
    //      and confirm it is up. Nothing is created.
    //   2. any account in the project already registered for this login+server --
    //      catches accounts made before ids were stored, or out of band.
    //   3. neither -> register a new one.
    let metacopierAccountId = ''
    let reused = false

    const priorId = String((license.metadata as Record<string, unknown> | null)?.metacopier_account_id ?? '').trim()
    if (priorId) {
      const prior = await fetchAccount(priorId)
      if (prior && isDead(prior)) {
        // Gone at the provider already -- deleting it again would just 400
        // with [ACCOUNT_WAS_ALREADY_DELETED]. Leave metacopierAccountId unset
        // so a fresh account gets created below, and the write-back at the
        // end of this function replaces the dead id on the licence.
        console.log(`[MetaCopier] licence ${license.id}'s stored account ${priorId} was deleted at the provider -- abandoning it`)
      } else if (prior) {
        const sameLogin =
          normalize(prior.loginAccountNumber) === normalize(wantLogin) &&
          normalize(prior.loginServer) === normalize(finalServer)
        if (sameLogin) {
          metacopierAccountId = priorId
          reused = true
          console.log(`[MetaCopier] licence ${license.id} already holds account ${priorId} for this login -- reusing`)
        } else {
          // The licence is being pointed at a different broker login. The old
          // account is now orphaned -- remove it so it cannot keep a stale
          // session (and a provider charge) alive.
          console.log(`[MetaCopier] licence ${license.id} switching login -- deleting old account ${priorId}`)
          await deleteAccount(priorId)
        }
      }
    }

    if (!metacopierAccountId) {
      try {
        const all = await listAllAccounts()
        const matches = all.filter((a) =>
          !isDead(a) &&
          normalize(a.loginAccountNumber) === normalize(wantLogin) &&
          normalize(a.loginServer) === normalize(finalServer)
        )
        matches.sort((a: Record<string, unknown>, b: Record<string, unknown>) =>
          String(a.created ?? '').localeCompare(String(b.created ?? '')))

        if (matches.length === 1) {
          // Exactly one -- reuse it. A stale password on it is caught by the
          // verify step below and repaired there.
          metacopierAccountId = String(matches[0].id)
          reused = true
          console.log(`[MetaCopier] reusing existing account ${metacopierAccountId} for login ${wantLogin}`)
        } else if (matches.length > 1) {
          // Already in a contending state. Clear them all and build one clean
          // account, so the outcome does not depend on which duplicate wins.
          console.warn(`[MetaCopier] ${matches.length} duplicate accounts for login ${wantLogin} -- clearing`)
          for (const a of matches) await deleteAccount(String(a.id))
        }
      } catch (e) {
        console.warn(`[MetaCopier] account list failed (will create fresh): ${(e as Error).message}`)
      }
    }

    if (!metacopierAccountId) {
      let r = await resolveAndCreate()

      // Out of account slots. Reclaim what this project is holding for licences
      // that no longer exist, then try once more.
      //
      // Not a nicety: a project that has been creating a duplicate on every
      // reconnect accumulates orphans until the cap is hit, and from that moment
      // NOBODY can connect an account -- the failure is total and permanent, and
      // it presents to every user as "your password is wrong". Reclaiming is the
      // difference between a self-healing cap and a dead product.
      if ('errorResponse' in r && r.limited) {
        const freed = await reclaimOrphanAccounts()
        if (freed > 0) {
          console.log(`[MetaCopier] reclaimed ${freed} orphaned account(s); retrying create`)
          r = await resolveAndCreate()
        }
      }

      if ('errorResponse' in r) return r.errorResponse
      metacopierAccountId = r.id
    }

    let linkState = await startAndVerify(metacopierAccountId)

    // A reused account whose stored password no longer works: the credentials
    // cannot be edited in place, so drop it and register the login fresh once.
    if (linkState === 'wrong_credentials' && reused) {
      console.log(`[MetaCopier] reused account ${metacopierAccountId} has stale credentials -- recreating`)
      await deleteAccount(metacopierAccountId)
      reused = false
      const r = await resolveAndCreate()
      if ('errorResponse' in r) return r.errorResponse
      metacopierAccountId = r.id
      linkState = await startAndVerify(metacopierAccountId)
    }

    // The account died while we were polling it (see the TODO in
    // startAndVerify) -- not a credentials problem, so recreate exactly like
    // the branch above rather than reporting a false CONNECTING.
    if (linkState === 'account_gone') {
      console.log(`[MetaCopier] account ${metacopierAccountId} was deleted mid-verify -- recreating`)
      reused = false
      const r = await resolveAndCreate()
      if ('errorResponse' in r) return r.errorResponse
      metacopierAccountId = r.id
      linkState = await startAndVerify(metacopierAccountId)
    }

    if (linkState === 'wrong_credentials') {
      return json({
        success: false,
        code: 'WRONG_CREDENTIALS',
        error:
          `Your broker rejected this login. Check that the account number and password are the ` +
          `${wantPlatform} trading password (not the investor password), and that "${finalServer}" ` +
          `is the server name exactly as MetaTrader shows it.`,
      }, 400)
    }

    // Recreated once above and it STILL came back gone/uncertain -- give up
    // rather than write an outcome the type system never promised callers.
    if (linkState === 'account_gone') {
      return json({
        success: false,
        code: 'CONNECT_FAILED',
        error: 'Could not establish a trading session for this account. Please try again.',
      }, 502)
    }

    // ---- Record the link, WITHOUT storing broker credentials ----------------
    // Kept on the licence rather than broker_accounts: app users authenticate by
    // licence key and have no auth.users row, but broker_accounts.user_id is a
    // NOT NULL FK to auth.users. Binding here also lets metacopier-execute
    // resolve the account server-side instead of trusting the client.
    const { data: current } = await novaHost
      .from('licenses')
      .select('metadata')
      .eq('id', license.id)
      .maybeSingle()

    const metadata = (current?.metadata ?? {}) as Record<string, unknown>
    metadata.metacopier_account_id = metacopierAccountId
    metadata.broker_server = finalServer
    metadata.platform = wantPlatform
    metadata.connected_at = new Date().toISOString()

    // What the verify step actually saw. `connected` means an order placed now
    // will reach the broker; `connecting` means the account is registered and
    // the session is still coming up -- the app shows that as a wait, not a
    // green light, and metacopier-execute holds signals that arrive during it.
    metadata.metacopier_status = linkState
    if (linkState === 'connected') {
      metadata.metacopier_connected_verified_at = new Date().toISOString()
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
      `[MetaCopier] licence ${license.id} symbol suffix = "${suffix}"` +
      (suffix ? ` (orders will read e.g. XAUUSD${suffix})` : ' (bare symbol names)')
    )

    const { error: linkErr } = await novaHost
      .from('licenses')
      .update({ metadata })
      .eq('id', license.id)

    // The account now exists at MetaCopier but nothing points at it, so the app
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
      account_id: metacopierAccountId,
      platform: wantPlatform,
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
