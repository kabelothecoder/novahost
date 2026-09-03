import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Entitlement check for the NovaHost app.
 *
 *   R599 once-off  -> lifetime app access   (subscriptions.is_lifetime)
 *   R349 once-off  -> AI chart scanner      (subscriptions.has_scanner)
 *
 * One paid email is bound to ONE device. Presenting a paid email on a second
 * device is refused -- that is the whole anti-sharing rule, so it fails closed.
 *
 * This also hands the app two things it cannot work out for itself:
 *
 *   token        the device session. Rotated on every move, so the handset a
 *                licence moved AWAY from stops matching.
 *   checked_at   when the server last answered. The app locks itself after
 *                MAX_OFFLINE_DAYS without one of these, which is the only
 *                thing that stops "keep the old phone in aeroplane mode" from
 *                being a second licence for free.
 *
 * On a device_mismatch it also returns `move`, so the paygate can say "you can
 * move again on 3 October" instead of sending the user to a checkout that
 * generate-payfast-checkout is about to refuse.
 */

// ── Move policy ────────────────────────────────────────────────────────────
//
// Duplicated verbatim in generate-payfast-checkout and payfast-webhook. Three
// copies for the same reason payfastEncode has two -- edge functions here do
// not share a module -- and with the same rule: if one changes, all of them
// change.
//
// The cap is counted from subscription_device_events, not from
// subscriptions.reactivation_count. The counter is a lifetime stat; the cap is
// a rolling 12 months, and only the ledger knows when each move happened.
const MOVE_COOLDOWN_DAYS = 30
const MOVE_LIMIT_PER_YEAR = 2

/** How long the app may run on a cached answer before it locks itself. */
const MAX_OFFLINE_DAYS = 5

const DAY_MS = 24 * 60 * 60 * 1000

type Eligibility = {
  eligible: boolean
  reason: 'ok' | 'cooldown' | 'limit_reached'
  available_at: string | null
  moves_used: number
  moves_allowed: number
}

/**
 * Whether this email may pay to move its licence right now.
 *
 * Read by the paygate for its copy and enforced for real in
 * generate-payfast-checkout and again in payfast-webhook. Three places because
 * the first is advisory, the second stops the user reaching a checkout they
 * cannot use, and only the third is holding the money.
 */
async function moveEligibility(
  // deno-lint-ignore no-explicit-any
  novaHost: any,
  email: string,
): Promise<Eligibility> {
  const since = new Date(Date.now() - 365 * DAY_MS).toISOString()

  const { data: moves, error } = await novaHost
    .from('subscription_device_events')
    .select('created_at')
    .eq('email', email)
    .eq('event', 'move')
    .gte('created_at', since)
    .order('created_at', { ascending: false })

  if (error) throw error

  const used = moves?.length ?? 0
  const lastAt = moves?.[0]?.created_at ? new Date(moves[0].created_at) : null

  // Cooldown first: it is the more informative refusal. Someone who moved
  // yesterday wants a date, not "you have used 1 of 2".
  if (lastAt) {
    const until = new Date(lastAt.getTime() + MOVE_COOLDOWN_DAYS * DAY_MS)
    if (until > new Date()) {
      return {
        eligible: false,
        reason: 'cooldown',
        available_at: until.toISOString(),
        moves_used: used,
        moves_allowed: MOVE_LIMIT_PER_YEAR,
      }
    }
  }

  if (used >= MOVE_LIMIT_PER_YEAR) {
    // The oldest move in the window is the one that has to age out.
    const oldest = moves?.[moves.length - 1]?.created_at
    return {
      eligible: false,
      reason: 'limit_reached',
      available_at: oldest ? new Date(new Date(oldest).getTime() + 365 * DAY_MS).toISOString() : null,
      moves_used: used,
      moves_allowed: MOVE_LIMIT_PER_YEAR,
    }
  }

  return {
    eligible: true,
    reason: 'ok',
    available_at: null,
    moves_used: used,
    moves_allowed: MOVE_LIMIT_PER_YEAR,
  }
}

/** A fresh device session. */
function mintToken(): string {
  return crypto.randomUUID().replace(/-/g, '') + crypto.randomUUID().replace(/-/g, '')
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
    const novaHost = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))
    const rawEmail = body.email
    const rawDevice = body.android_id ?? body.deviceId
    const presentedToken = body.token ? String(body.token).trim() : null

    if (!rawEmail || !rawDevice) {
      return json({ success: false, error: 'Email and device id are required.' }, 400)
    }

    const email = String(rawEmail).trim().toLowerCase()
    const deviceId = String(rawDevice).trim()

    // A device that cannot identify itself must not be bindable. The old
    // DeviceSecurityHelper returned the literal "UNKNOWN_DEVICE" when
    // ANDROID_ID came back null, which meant every such handset shared one
    // binding -- and the first of them to arrive locked out all the others.
    if (!deviceId || deviceId === 'UNKNOWN_DEVICE') {
      return json({ success: false, error: 'This device could not be identified.' }, 400)
    }

    const now = new Date()
    const nowIso = now.toISOString()

    const { data: sub, error } = await novaHost
      .from('subscriptions')
      .select('id, email, is_premium, is_lifetime, has_scanner, device_id, subscription_expiry, token')
      .eq('email', email)
      .maybeSingle()

    if (error) throw error

    /** Every answer carries these, so the client can always age its cache. */
    const envelope = { checked_at: nowIso, max_offline_days: MAX_OFFLINE_DAYS }

    // No purchase on record -- send them to the paywall, don't hint at anything.
    if (!sub) {
      return json({
        success: true,
        is_premium: false,
        has_scanner: false,
        reason: 'no_purchase',
        message: 'No purchase found for this email address.',
        ...envelope,
      })
    }

    const paid = sub.is_lifetime === true || sub.is_premium === true

    if (!paid) {
      return json({
        success: true,
        is_premium: false,
        has_scanner: sub.has_scanner === true,
        reason: 'not_paid',
        message: 'This email has no active purchase.',
        ...envelope,
      })
    }

    // Non-lifetime records may still carry an expiry.
    if (!sub.is_lifetime && sub.subscription_expiry &&
        new Date(sub.subscription_expiry) < now) {
      return json({
        success: true,
        is_premium: false,
        has_scanner: sub.has_scanner === true,
        reason: 'expired',
        message: 'This subscription has expired.',
        ...envelope,
      })
    }

    // ---- One email, one device ---------------------------------------------
    let token = sub.token as string | null

    if (!sub.device_id) {
      // First run on a device: bind it. Guarded on device_id still being null
      // so two devices racing the first activation cannot both succeed.
      token = mintToken()

      const { data: bound, error: bindErr } = await novaHost
        .from('subscriptions')
        .update({
          device_id: deviceId,
          device_bound_at: nowIso,
          token,
          token_issued_at: nowIso,
          updated_at: nowIso,
        })
        .eq('id', sub.id)
        .is('device_id', null)
        .select('id')
        .maybeSingle()

      if (bindErr) throw bindErr

      if (!bound) {
        // Another device won the race.
        return json({
          success: true,
          is_premium: false,
          has_scanner: false,
          reason: 'device_mismatch',
          message: 'This purchase is already active on another device.',
          move: await moveEligibility(novaHost, email),
          ...envelope,
        })
      }

      await novaHost.from('subscription_device_events').insert({
        email,
        event: 'bind',
        new_device_id: deviceId,
        note: 'First device bound.',
      })

    } else if (sub.device_id !== deviceId) {
      // The anti-sharing rule. Fails closed.
      //
      // `move` rides along so the paygate can price and date the offer itself.
      // Without it the gate could only say "contact support to move it", which
      // is what it said while a perfectly good R150 self-serve move existed.
      return json({
        success: true,
        is_premium: false,
        has_scanner: false,
        reason: 'device_mismatch',
        message: 'This purchase is already active on another device.',
        move: await moveEligibility(novaHost, email),
        ...envelope,
      })

    } else if (!token || (presentedToken && presentedToken !== token)) {
      // Right handset, no live session: a reinstall, or a row that predates
      // tokens. Mint one.
      //
      // Deliberately NOT a refusal. device_id is what proves the handset, and
      // it matched -- if the licence had been moved away, the branch above
      // would have caught it. Refusing here would mean a user who cleared app
      // data had to pay R150 to get back into the phone they never left.
      token = mintToken()
      await novaHost
        .from('subscriptions')
        .update({ token, token_issued_at: nowIso, updated_at: nowIso })
        .eq('id', sub.id)
    }

    return json({
      success: true,
      is_premium: true,
      is_lifetime: sub.is_lifetime === true,
      has_scanner: sub.has_scanner === true,
      reason: 'active',
      token,
      ...envelope,
    })

  } catch (err) {
    console.error('check-subscription-status error:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
