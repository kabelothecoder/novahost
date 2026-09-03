import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Clears a licence's device binding by hand.
 *
 * The escape hatch the 30-day move cooldown makes necessary. A phone that is
 * lost, stolen, bricked or sold cannot request a move code from itself, and the
 * paid R150 route is closed to it for a month -- so without this a customer in
 * that position has no route at all except waiting.
 *
 * A reset here does NOT count against the two-moves-a-year cap: the cap exists
 * to price licence-sharing, and someone whose phone was stolen is not sharing
 * anything. It is logged as `support_reset` for exactly that reason, and
 * moveEligibility counts only `move`.
 *
 * After a reset the row has no device and no session, so the next handset to
 * present the email binds to it free of charge -- the same path a first-time
 * buyer takes. That is the whole point, and it is also why this endpoint is
 * worth protecting properly.
 *
 * AUTH: a signed-in user who appears in `admin_users`.
 *
 * That table is the real admin tier -- RLS on with no policies at all, so only
 * the service role can write to it and nobody can award themselves a place in
 * it by signing up or by updating their own profile.
 *
 * Note that `verify_jwt` alone would NOT be enough here, which is why the check
 * below is in the function body. The project's anon key is itself a valid
 * signed JWT, so the platform's verification accepts it happily -- and the anon
 * key ships inside the APK. `auth.getUser()` is what separates a real signed-in
 * person from the public key everybody has.
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

    // ---- Who is asking ------------------------------------------------------
    const authHeader = req.headers.get('Authorization') ?? ''
    const jwt = authHeader.replace(/^Bearer\s+/i, '').trim()

    if (!jwt) {
      return json({ success: false, error: 'Not authorised.' }, 401)
    }

    const novaHostAuth = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? ''
    )

    const { data: { user }, error: authErr } = await novaHostAuth.auth.getUser(jwt)

    // Handing the anon key here returns no user, which is the point.
    if (authErr || !user) {
      console.warn('[support-reset] Rejected a call with no valid user session.')
      return json({ success: false, error: 'Not authorised.' }, 401)
    }

    const { data: admin, error: adminErr } = await novaHost
      .from('admin_users')
      .select('user_id')
      .eq('user_id', user.id)
      .maybeSingle()

    if (adminErr) throw adminErr

    if (!admin) {
      console.warn('[support-reset] Rejected a non-admin: ' + user.id)
      return json({ success: false, error: 'Not authorised.' }, 403)
    }

    const body = await req.json().catch(() => ({}))
    const email = String(body.email ?? '').trim().toLowerCase()
    const note = String(body.note ?? '').trim()

    if (!email) {
      return json({ success: false, error: 'email is required.' }, 400)
    }

    const { data: sub, error: readErr } = await novaHost
      .from('subscriptions')
      .select('id, device_id, is_premium, is_lifetime, reactivation_count')
      .eq('email', email)
      .maybeSingle()

    if (readErr) throw readErr

    if (!sub) {
      return json({ success: false, error: 'No subscription found for that email.' }, 404)
    }

    const now = new Date().toISOString()

    const { error: updErr } = await novaHost
      .from('subscriptions')
      .update({
        device_id: null,
        device_bound_at: null,
        // The session dies with the binding. Without this the old handset
        // keeps a token that no longer corresponds to anything.
        token: null,
        token_issued_at: null,
        updated_at: now,
      })
      .eq('id', sub.id)

    if (updErr) throw updErr

    // The acting admin goes in the note. A reset is the one action that hands
    // a licence to whoever asks next, so "who did this" has to survive in the
    // ledger rather than only in the function logs.
    await novaHost.from('subscription_device_events').insert({
      email,
      event: 'support_reset',
      old_device_id: sub.device_id,
      new_device_id: null,
      note: `${note || 'Device binding cleared by support.'} [by ${user.email ?? user.id}]`,
    })

    // Any outstanding move codes are meaningless now -- the licence is unbound,
    // so the next device binds for free. Leaving them live would let a stale
    // code be spent on a R150 move the customer no longer needs to pay for.
    await novaHost
      .from('device_move_tickets')
      .update({ consumed_at: now })
      .eq('email', email)
      .is('consumed_at', null)

    console.log('[support-reset] Cleared binding for ' + email + ' (was ' + sub.device_id + ').')

    return json({
      success: true,
      email,
      previous_device_id: sub.device_id,
      message: 'Binding cleared. The next device to sign in with this email will claim it.',
    })

  } catch (err) {
    console.error('support-reset-device error:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
