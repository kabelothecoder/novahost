import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * The mentor signal feed, for display on a handset.
 *
 * ## Why this exists rather than a direct table read
 *
 * `signalService.js` selected from `signals` with the anon key. It has never
 * returned a row and never could: the SELECT policy is
 *
 *     licenses.owner_id = auth.uid()
 *
 * and app users hold a mentor-issued licence key, not a NovaHost auth session —
 * there is no `auth.users` row for `auth.uid()` to match. RLS answers a
 * refusal with an empty set rather than an error, so the screen rendered "no
 * signals yet" against a table holding 31 of them, and nothing anywhere looked
 * broken.
 *
 * Widening that policy to `anon` was not an option. The anon key ships inside
 * every APK and every PWA bundle, so an anon SELECT on `signals` publishes
 * every mentor's calls to anyone who unzips the app. The read moves here
 * instead, under the service role, scoped by licence key — the same shape as
 * `license-status`, `claim-signals` and the `broker-*` family, for the same
 * reason.
 *
 * ## This must never claim
 *
 * Delivery is an INSERT into `signal_deliveries`, unique on
 * (signal_id, license_id), and that insert IS the claim. Rendering a feed must
 * not consume one, or opening Home would silently burn the signal the device
 * was supposed to trade. Nothing here writes.
 *
 * ## Why the outcome travels with the signal
 *
 * Now that `auto-execute-signals` can place orders while the phone is asleep,
 * a feed that shows only what the mentor sent is a black box: the user cannot
 * tell a call that filled from one the broker refused for a closed market.
 * `signal_executions` holds that answer, so it is joined on and returned per
 * row. A signal claimed by a handset instead has a delivery but no execution
 * record, which reads as `delivered` with a null outcome — a real third state,
 * not a gap.
 */

/** Sane page. The feed is a glanceable list, not an archive. */
const DEFAULT_LIMIT = 20
const MAX_LIMIT = 50

/** Matches `claim-signals` — wide enough to show context, bounded so a dormant
 *  licence does not drag months of history back on first contact. */
const LOOKBACK_HOURS = 72

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

    const requested = Number(body.limit)
    const limit = Number.isFinite(requested) && requested > 0
      ? Math.min(Math.floor(requested), MAX_LIMIT)
      : DEFAULT_LIMIT

    // ---- The licence -------------------------------------------------------
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, ea_id, status, expires_at')
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr

    /* An unknown or dead licence gets an empty feed and a reason, not a 4xx.
       The feed is ambient furniture on Home — it must not turn a screen into
       an error state over something the gate already governs. */
    if (!license) {
      return json({ success: true, signals: [], reason: 'not_found' })
    }
    if (license.status !== 'active') {
      return json({ success: true, signals: [], reason: 'inactive' })
    }
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return json({ success: true, signals: [], reason: 'expired' })
    }
    if (!license.ea_id) {
      return json({ success: true, signals: [], reason: 'no_robot' })
    }

    // ---- The mentor's calls, for THIS licence's robot only ------------------
    const lookbackFrom = new Date(Date.now() - LOOKBACK_HOURS * 3600_000).toISOString()

    const { data: rows, error: sigErr } = await novaHost
      .from('signals')
      .select('id, pair, side, type, price, sl, tp, lot, status, order_type, created_at')
      .eq('ea_id', license.ea_id)
      .gte('created_at', lookbackFrom)
      .order('created_at', { ascending: false })
      .limit(limit)

    if (sigErr) throw sigErr
    if (!rows?.length) {
      return json({ success: true, signals: [], reason: 'none' })
    }

    const ids = rows.map((r) => r.id)

    // ---- What happened to each, for this licence ---------------------------
    //
    // Two reads rather than a join: PostgREST embedding would need a declared
    // FK relationship in both directions, and these are small bounded sets.
    const [{ data: delivered }, { data: executions }] = await Promise.all([
      novaHost
        .from('signal_deliveries')
        .select('signal_id')
        .eq('license_id', license.id)
        .in('signal_id', ids),
      novaHost
        .from('signal_executions')
        .select('signal_id, status, code, detail, volume')
        .eq('license_id', license.id)
        .in('signal_id', ids),
    ])

    const claimed = new Set((delivered ?? []).map((d) => d.signal_id))
    const outcome = new Map((executions ?? []).map((e) => [e.signal_id, e]))

    const signals = rows.map((r) => {
      const ex = outcome.get(r.id)
      return {
        ...r,
        /* Handed to this licence — by a handset poll, by the realtime path, or
           by the server executor. Absent means still outstanding. */
        delivered: claimed.has(r.id),
        /* 'placed' | 'refused' | 'missed' | 'error', or null when a handset
           took it (handsets do not write this table) or it is outstanding. */
        execution_status: ex?.status ?? null,
        execution_code: ex?.code ?? null,
        /* The executor's own words where it sent any — MetaCopier returns
           plain-English broker refusals and a generic in their place is how a
           user ends up unable to tell a closed market from a rejected licence. */
        execution_detail: ex?.detail ?? null,
        execution_volume: ex?.volume ?? null,
      }
    })

    return json({ success: true, signals, ea_id: license.ea_id })

  } catch (err) {
    console.error('[signal-feed] fatal:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
