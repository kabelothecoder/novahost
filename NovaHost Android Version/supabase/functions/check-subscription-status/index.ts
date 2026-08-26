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
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))
    const rawEmail = body.email
    const rawDevice = body.android_id ?? body.deviceId

    if (!rawEmail || !rawDevice) {
      return json({ success: false, error: 'Email and device id are required.' }, 400)
    }

    const email = String(rawEmail).trim().toLowerCase()
    const deviceId = String(rawDevice).trim()

    if (!deviceId) {
      return json({ success: false, error: 'Device id is required.' }, 400)
    }

    const { data: sub, error } = await supabase
      .from('subscriptions')
      .select('id, email, is_premium, is_lifetime, has_scanner, device_id, subscription_expiry')
      .eq('email', email)
      .maybeSingle()

    if (error) throw error

    // No purchase on record -- send them to the paywall, don't hint at anything.
    if (!sub) {
      return json({
        success: true,
        is_premium: false,
        has_scanner: false,
        reason: 'no_purchase',
        message: 'No purchase found for this email address.',
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
      })
    }

    // Non-lifetime records may still carry an expiry.
    if (!sub.is_lifetime && sub.subscription_expiry &&
        new Date(sub.subscription_expiry) < new Date()) {
      return json({
        success: true,
        is_premium: false,
        has_scanner: sub.has_scanner === true,
        reason: 'expired',
        message: 'This subscription has expired.',
      })
    }

    // ---- One email, one device ---------------------------------------------
    if (!sub.device_id) {
      // First run on a device: bind it. Guarded on device_id still being null
      // so two devices racing the first activation cannot both succeed.
      const { data: bound, error: bindErr } = await supabase
        .from('subscriptions')
        .update({
          device_id: deviceId,
          device_bound_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
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
        })
      }
    } else if (sub.device_id !== deviceId) {
      // The anti-sharing rule. Fails closed.
      return json({
        success: true,
        is_premium: false,
        has_scanner: false,
        reason: 'device_mismatch',
        message: 'This purchase is already active on another device. Contact support to move it.',
      })
    }

    return json({
      success: true,
      is_premium: true,
      is_lifetime: sub.is_lifetime === true,
      has_scanner: sub.has_scanner === true,
      reason: 'active',
    })

  } catch (err) {
    console.error('check-subscription-status error:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
