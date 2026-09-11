import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * The opt-in for server-side execution: writes `licenses.auto_execute`.
 *
 * ## Why this had to exist
 *
 * `auto-execute-signals` shipped with the column, the trigger and the cron, and
 * `auto_execute` defaults to false — so it was correct, deployed, healthy, and
 * a no-op for every user. Nothing in either app, the portal or any function
 * ever wrote the column: it could only be flipped by hand in SQL. 0 of 58
 * licences had it on and `signal_executions` had never held a row.
 *
 * That mattered most on iOS, which has no other delivery path — it does not
 * claim signals itself — so an iOS user received nothing at all.
 *
 * ## Why a licence key is the whole credential
 *
 * The same reason it is for `license-status`, `claim-signals` and
 * `metacopier-execute`: app users have no `auth.users` row, and the key is what
 * they hold. This is consistent with the rest of the surface rather than a new
 * trust model.
 *
 * ## Turning it OFF is unconditional
 *
 * Every refusal below guards ENABLING only. A control that trades real money
 * must always be switchable off, including on an expired licence, a licence
 * whose broker link has gone, or one this function would otherwise refuse to
 * talk to. A stop button with preconditions is not a stop button.
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
    const novaHost = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))

    const rawKey = body.license_key
    if (!rawKey || !String(rawKey).trim()) {
      return json({ success: false, code: 'NO_LICENCE', error: 'A licence key is required.' }, 400)
    }
    const key = String(rawKey).trim().toUpperCase()

    /* Strict. `enabled: "false"` and a missing field are both ways to turn
       something on by accident, and this one places orders. */
    if (typeof body.enabled !== 'boolean') {
      return json(
        { success: false, code: 'BAD_REQUEST', error: '`enabled` must be true or false.' },
        400
      )
    }
    const enabled: boolean = body.enabled

    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, status, expires_at, metadata, auto_execute')
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr
    if (!license) {
      return json({ success: false, code: 'LICENCE_UNKNOWN', error: 'This licence key is not recognised.' }, 404)
    }

    // ---- Guards, on the way ON only ----------------------------------------
    if (enabled) {
      if (license.status !== 'active') {
        return json({ success: false, code: 'LICENCE_INACTIVE', error: 'This licence is not active.' }, 403)
      }
      if (license.expires_at && new Date(license.expires_at) < new Date()) {
        return json({ success: false, code: 'LICENCE_EXPIRED', error: 'This licence has expired.' }, 403)
      }

      /* `auto-execute-signals` filters on exactly this before it will consider
         a licence. Refusing here means the user is told now, on the switch they
         just touched, instead of discovering by absence that nothing ever
         traded. */
      const accountId = (license.metadata as Record<string, unknown> | null)?.metacopier_account_id
      const linked = typeof accountId === 'string' && accountId.trim().length > 0
      if (!linked) {
        return json({
          success: false,
          code: 'NO_ACCOUNT_LINKED',
          /* No dash of any kind. This string is the one piece of this file a
             user reads, and it has already shipped once as a literal "--"
             because a deploy path sanitised the em dash. Plain sentences
             survive any transport. */
          error: 'Connect your trading account first. There is nowhere to place a trade yet.',
        }, 409)
      }
    }

    const { error: updErr } = await novaHost
      .from('licenses')
      .update({ auto_execute: enabled })
      .eq('id', license.id)

    if (updErr) throw updErr

    /* A warning, never a refusal. The executor records NO_SIZE_CONFIGURED
       rather than guessing a lot — "the mentor left the size to you and you
       have not set one" — so a user who enables this with no sizing plan would
       watch every signal be refused without being told why. Reported here so
       the switch can say it at the moment it is flipped, while still turning
       on: they may be about to go and set one. */
    let warning: string | null = null
    if (enabled) {
      const { count } = await novaHost
        .from('license_symbol_config')
        .select('symbol', { count: 'exact', head: true })
        .eq('license_id', license.id)
        .eq('enabled', true)
        .gt('lot', 0)

      if (!count) {
        warning = 'No lot size is set for any symbol yet, so signals will be refused until you set one in Trading Symbols.'
      }
    }

    return json({ success: true, auto_execute: enabled, warning })

  } catch (err) {
    console.error('[set-auto-execute] fatal:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
