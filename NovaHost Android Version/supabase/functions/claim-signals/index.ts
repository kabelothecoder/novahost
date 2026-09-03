import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Hands a device the signals it has not yet taken, exactly once.
 *
 * ## Why a pull path exists at all
 *
 * Signals reached handsets over a NovaHost realtime broadcast and nothing else.
 * Broadcast is fire-and-forget: there is no acknowledgement, no redelivery and
 * no cursor. A phone that dozed, backgrounded, changed network or dropped its
 * socket for two seconds lost the signal permanently -- and neither end
 * recorded that anything had gone missing. Sixteen live broadcasts produced
 * zero executions and zero explanations, which is the worst possible failure
 * mode for the one feature people paid for.
 *
 * So realtime is demoted to what it is actually good at: telling the app to
 * look now. This endpoint is what it looks at, and it is also polled on a timer
 * so that a socket which never arrives costs latency instead of the trade.
 *
 * ## Exactly once
 *
 * The claim is an INSERT into `signal_deliveries` with a unique constraint on
 * (signal_id, license_id), and only rows the INSERT actually created come back.
 * That makes claiming atomic without a transaction or a lock:
 *
 *  - a poll racing the realtime nudge cannot deliver the same signal twice;
 *  - two handsets on one licence cannot both trade it, which matters because
 *    they point at the SAME broker account -- two positions, one call;
 *  - a retry after a dropped response returns nothing rather than re-firing.
 *
 * ## Why old signals are never returned
 *
 * A phone that was off for three hours must not wake up and open positions on
 * three-hour-old calls. That is not catch-up, it is a stale trade placed into a
 * market that has moved, and it is worse than the miss it is trying to repair.
 *
 * Anything older than the freshness window is claimed anyway -- so it can never
 * fire later -- and reported as `stale` instead of returned. The user is told
 * they missed it. They are not silently entered into it.
 */

/**
 * How recent a signal must be to still be worth acting on.
 *
 * Five minutes: long enough to cover a socket bounce, a tunnel, a screen-off
 * doze or an app restart, short enough that nothing here is a different trade
 * to the one the mentor called. Overridable so it can be tuned without a
 * redeploy of the app.
 */
const DEFAULT_WINDOW_SECONDS = 300

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

    const windowSeconds =
      Number(Deno.env.get('SIGNAL_CLAIM_WINDOW_SECONDS')) || DEFAULT_WINDOW_SECONDS

    const body = await req.json().catch(() => ({}))
    const rawKey = body.license_key
    const deviceId = body.device_id ? String(body.device_id).slice(0, 128) : null

    if (!rawKey || !String(rawKey).trim()) {
      return json({ success: false, code: 'MISSING_PARAMETERS', error: 'A licence key is required.' }, 400)
    }

    // ---- Authorise ----------------------------------------------------------
    // The licence is the credential, as everywhere else on the device-facing
    // surface. The robot is resolved FROM it -- never taken from the request --
    // so a caller cannot poll for another mentor's signals by naming their ea_id.
    const key = String(rawKey).trim().toUpperCase()
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, ea_id, status, expires_at, allowed_symbols')
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
    // ---- The robot's current symbol allowance -------------------------------
    //
    // Carried on every poll, because the handset had no other way to learn it.
    // `allowed_symbols` was written into SharedPreferences once, during licence
    // activation, and never refreshed -- so a mentor who edited their robot's
    // symbols changed the database, the licence and the portal, and every phone
    // in the field carried on showing the old list indefinitely. The user's only
    // route to the new symbol was to re-activate their licence, which nobody
    // would think to do and nothing prompted.
    //
    // Free to send: the licence row is already loaded for authorisation, and the
    // poll already runs every twenty seconds. The device compares it against
    // what it holds and rewrites only on a real change.
    const allowedSymbols = Array.isArray(license.allowed_symbols)
      ? license.allowed_symbols.map((s: unknown) => String(s))
      : []

    /** Nothing to hand over -- but the allowance still travels. */
    const nothingOutstanding = () =>
      json({
        success: true,
        signals: [],
        stale: 0,
        window_seconds: windowSeconds,
        allowed_symbols: allowedSymbols,
      })

    // ---- Mark the device alive ----------------------------------------------
    //
    // The heartbeat rides on the poll rather than running on a timer of its own,
    // which makes it a stronger claim than it used to be: a device is recorded
    // as alive exactly when it is asking for signals, so "online" in the mentor
    // portal means "will act on your call" instead of "has the app installed".
    //
    // Scoped to the licence as well as the device. The old client-side version
    // filtered on `device_id` alone, so a handset that had activated four keys
    // pulsed all four rows -- the portal counted four live terminals where there
    // was one phone. (It also never actually ran, so the figure was really
    // "validated a licence recently".)
    //
    // Best effort, and deliberately before the early return below: failing to
    // record liveness must never cost a signal.
    if (deviceId) {
      const { error: seenErr } = await novaHost
        .from('device_activations')
        .update({ last_seen_at: new Date().toISOString(), status: 'active' })
        .eq('license_id', license.id)
        .eq('device_id', deviceId)

      if (seenErr) console.warn(`[claim-signals] heartbeat write failed: ${seenErr.message}`)
    }

    if (!license.ea_id) {
      // Nothing to poll for, and not an error worth alarming anyone about.
      return nothingOutstanding()
    }

    // ---- What is outstanding for this licence -------------------------------
    //
    // A generous lookback, deliberately wider than the freshness window: signals
    // between the two are claimed and reported as stale rather than left to be
    // rediscovered on every future poll. Bounded so a licence that has been
    // dormant for months does not drag its whole history back on first contact.
    const lookbackFrom = new Date(Date.now() - 60 * 60 * 1000).toISOString()

    const { data: recent, error: sigErr } = await novaHost
      .from('signals')
      .select('id, ea_id, pair, side, type, lot, sl, tp, signal_id, created_at, order_type, price, pending_expiry_seconds')
      .eq('ea_id', license.ea_id)
      .gte('created_at', lookbackFrom)
      .order('created_at', { ascending: true })
      .limit(50)

    if (sigErr) throw sigErr
    if (!recent || recent.length === 0) {
      return nothingOutstanding()
    }

    // Already taken by this licence -- by this device on an earlier poll, by the
    // realtime path, or by another handset on the same key.
    const { data: taken, error: takenErr } = await novaHost
      .from('signal_deliveries')
      .select('signal_id')
      .eq('license_id', license.id)
      .in('signal_id', recent.map((s) => s.id))

    if (takenErr) throw takenErr

    const already = new Set((taken ?? []).map((t) => t.signal_id))
    const outstanding = recent.filter((s) => !already.has(s.id))

    if (outstanding.length === 0) {
      return nothingOutstanding()
    }

    // ---- Claim ---------------------------------------------------------------
    //
    // Every outstanding signal is claimed, fresh or stale. Claiming the stale
    // ones is the point: it retires them permanently, so a signal that was too
    // old to trade at 09:00 cannot be reconsidered at 09:05 or on any poll after
    // it.
    //
    // `ignoreDuplicates` makes the conflict a no-op rather than an error, and
    // the RETURNING set is therefore exactly the rows this call created -- which
    // is exactly what this device is allowed to act on. Anything a concurrent
    // caller won simply does not come back.
    const { data: claimed, error: claimErr } = await novaHost
      .from('signal_deliveries')
      .upsert(
        outstanding.map((s) => ({
          signal_id: s.id,
          license_id: license.id,
          device_id: deviceId,
        })),
        { onConflict: 'signal_id,license_id', ignoreDuplicates: true }
      )
      .select('signal_id')

    if (claimErr) throw claimErr

    const won = new Set((claimed ?? []).map((c) => c.signal_id))
    const mine = outstanding.filter((s) => won.has(s.id))

    // ---- Fresh enough to act on? ---------------------------------------------
    const cutoff = Date.now() - windowSeconds * 1000
    const fresh = mine.filter((s) => new Date(s.created_at).getTime() >= cutoff)
    const stale = mine.length - fresh.length

    if (stale > 0) {
      console.log(
        `[claim-signals] licence ${license.id}: ${stale} signal(s) retired unexecuted ` +
        `(older than ${windowSeconds}s)`
      )
    }

    return json({
      success: true,
      // Shaped like the realtime broadcast payload so the handset has one
      // execution path regardless of how a signal reached it.
      signals: fresh.map((s) => ({
        id: s.id,
        ea_id: s.ea_id,
        pair: s.pair,
        side: (s.side ?? s.type ?? 'BUY'),
        lot: s.lot,
        sl: s.sl,
        tp: s.tp,
        // Named as metacopier-execute expects them, so the handset can forward
        // the claim payload straight through. Rows written before pending
        // orders existed carry MARKET by column default, so no backfill is
        // needed and no client needs to special-case a missing value.
        order_type: s.order_type ?? 'MARKET',
        open_price: s.price,
        pending_expiry_seconds: s.pending_expiry_seconds,
        signal_id: s.signal_id,
        created_at: s.created_at,
      })),
      /**
       * Claimed but too old to trade. Surfaced so the app can say "you missed
       * two calls while you were offline" -- a miss the user knows about is a
       * different product to a miss nobody can see.
       */
      stale,
      window_seconds: windowSeconds,
      allowed_symbols: allowedSymbols,
    })

  } catch (err) {
    console.error('[claim-signals] fatal:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
