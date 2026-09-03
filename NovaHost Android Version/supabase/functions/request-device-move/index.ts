import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Step one of moving a paid licence to a new handset: prove you can read the
 * mailbox it belongs to.
 *
 * Emails a six-digit code to the licence address and records a ticket bound to
 * the handset that asked for it. generate-payfast-checkout will not price a
 * R150 move without that code, and payfast-webhook will not grant one without
 * the ticket.
 *
 * This exists because both halves of the move used to be attacker-supplied.
 * The target email (custom_str1..3) and the target device both come back from
 * the browser, so R150 bought the eviction of a stranger: pay with someone
 * else's address in the field, bind their licence to your phone, and they lose
 * an app they paid R599 for. Nothing in the chain asked whether the payer had
 * any connection to the email they were moving.
 *
 * The code is bound to `target_device_id`, so even the code itself only moves
 * the licence to the handset that requested it. A victim who receives one of
 * these unexpectedly can ignore it and nothing happens.
 */

// ── Move policy ────────────────────────────────────────────────────────────
// Keep identical to check-subscription-status, generate-payfast-checkout and
// payfast-webhook.
const MOVE_COOLDOWN_DAYS = 30
const MOVE_LIMIT_PER_YEAR = 2

const DAY_MS = 24 * 60 * 60 * 1000

/** How long a code is good for. Long enough to switch apps and read an email. */
const TICKET_TTL_MINUTES = 15

/** Tickets per email per hour. Stops this endpoint being a mail bomb. */
const MAX_TICKETS_PER_HOUR = 5

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/** Six digits, uniformly drawn. `Math.random` is not what guards a licence. */
function mintCode(): string {
  const bytes = new Uint32Array(1)
  crypto.getRandomValues(bytes)
  return String(bytes[0] % 1_000_000).padStart(6, '0')
}

/** Duplicated from check-subscription-status. If one changes, all change. */
async function moveEligibility(
  // deno-lint-ignore no-explicit-any
  novaHost: any,
  email: string,
) {
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

  if (lastAt) {
    const until = new Date(lastAt.getTime() + MOVE_COOLDOWN_DAYS * DAY_MS)
    if (until > new Date()) {
      return { eligible: false, reason: 'cooldown' as const, available_at: until.toISOString(), moves_used: used }
    }
  }

  if (used >= MOVE_LIMIT_PER_YEAR) {
    const oldest = moves?.[moves.length - 1]?.created_at
    return {
      eligible: false,
      reason: 'limit_reached' as const,
      available_at: oldest ? new Date(new Date(oldest).getTime() + 365 * DAY_MS).toISOString() : null,
      moves_used: used,
    }
  }

  return { eligible: true, reason: 'ok' as const, available_at: null, moves_used: used }
}

function codeEmail(code: string, deviceLabel: string): string {
  return `
  <!DOCTYPE html>
  <html lang="en">
  <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
  <body style="margin:0;padding:0;background-color:#0b0f19;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#f3f4f6;">
    <div style="max-width:600px;margin:0 auto;padding:40px 20px;">
      <div style="text-align:center;margin-bottom:40px;">
        <div style="font-size:28px;font-weight:800;letter-spacing:-0.05em;color:#5C9CE6;margin:0 0 10px 0;">NOVAHOST</div>
        <div style="font-size:12px;color:#4b5563;text-transform:uppercase;letter-spacing:0.15em;">Device Move</div>
      </div>

      <div style="background-color:#111827;border:1px solid #1f2937;border-radius:24px;padding:40px;text-align:center;">
        <h1 style="font-size:24px;font-weight:700;color:#ffffff;margin-top:0;margin-bottom:10px;">Confirm your device move</h1>
        <p style="font-size:14px;color:#9ca3af;margin-bottom:30px;">
          Someone asked to move your NovaHost licence to a new phone (<strong style="color:#d1d5db;">${deviceLabel}</strong>).
          Enter this code in the app to continue.
        </p>

        <div style="background:rgba(92,156,230,0.12);border:1px solid rgba(92,156,230,0.3);border-radius:16px;padding:18px;margin:20px 0;font-family:'Courier New',Courier,monospace;font-size:32px;font-weight:700;color:#ffffff;letter-spacing:8px;">${code}</div>

        <p style="color:#9ca3af;font-size:13px;line-height:1.5;margin:20px 0;">
          The code expires in ${TICKET_TTL_MINUTES} minutes and only works on the phone that asked for it.
        </p>

        <p style="color:#6b7280;font-size:12px;line-height:1.6;margin:24px 0 0 0;">
          <strong style="color:#9ca3af;">Didn't ask for this?</strong> Ignore this email. Nothing changes and your
          licence stays on the phone you are using. No one can move it without this code.
        </p>
      </div>

      <div style="text-align:center;margin-top:40px;font-size:11px;color:#4b5563;">
        <p>Sent by NovaHost because a device move was requested for this address.</p>
      </div>
    </div>
  </body>
  </html>`
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
    const body = await req.json().catch(() => ({}))
    const email = String(body.email ?? '').trim().toLowerCase()
    const deviceId = String(body.android_id ?? body.deviceId ?? '').trim()

    if (!email || !deviceId || deviceId === 'UNKNOWN_DEVICE') {
      return json({ success: false, error: 'Email and a valid device id are required.' }, 400)
    }

    const novaHost = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const { data: sub, error: subErr } = await novaHost
      .from('subscriptions')
      .select('id, email, is_premium, is_lifetime, device_id')
      .eq('email', email)
      .maybeSingle()

    if (subErr) throw subErr

    // Deliberately the same answer for "no such email" and "that email never
    // paid". This endpoint sends mail to an address the caller supplies, so a
    // distinguishable response turns it into a way to test which addresses hold
    // paid licences.
    const paid = sub && (sub.is_lifetime === true || sub.is_premium === true)
    if (!paid) {
      return json({ success: false, error: 'No paid licence found for that email address.', code: 'NO_LICENCE' }, 404)
    }

    if (sub.device_id === deviceId) {
      return json({ success: false, error: 'This licence is already on this device.', code: 'ALREADY_HERE' }, 409)
    }

    const eligibility = await moveEligibility(novaHost, email)
    if (!eligibility.eligible) {
      return json({
        success: false,
        code: eligibility.reason === 'cooldown' ? 'MOVE_COOLDOWN' : 'MOVE_LIMIT_REACHED',
        error: eligibility.reason === 'cooldown'
          ? 'This licence was moved recently. It can be moved again later.'
          : 'This licence has been moved the maximum number of times this year.',
        move: eligibility,
      }, 429)
    }

    // ---- Throttle -----------------------------------------------------------
    const anHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString()
    const { count, error: countErr } = await novaHost
      .from('device_move_tickets')
      .select('id', { count: 'exact', head: true })
      .eq('email', email)
      .gte('created_at', anHourAgo)

    if (countErr) throw countErr

    if ((count ?? 0) >= MAX_TICKETS_PER_HOUR) {
      return json({
        success: false,
        code: 'TOO_MANY_REQUESTS',
        error: 'Too many codes requested. Wait an hour and try again.',
      }, 429)
    }

    // ---- Email must be able to leave the building ---------------------------
    // Checked BEFORE the ticket is written. A ticket whose code was never
    // delivered is worse than no ticket: the user is told to check their inbox
    // and waits for something that is not coming.
    const resendApiKey = Deno.env.get('RESEND_API_KEY')
    if (!resendApiKey) {
      console.error('[device-move] RESEND_API_KEY is unset -- cannot send move codes.')
      return json({
        success: false,
        code: 'EMAIL_NOT_CONFIGURED',
        error: 'Device moves are unavailable right now. Please contact support.',
      }, 503)
    }

    const code = mintCode()
    const expiresAt = new Date(Date.now() + TICKET_TTL_MINUTES * 60 * 1000)

    const from = Deno.env.get('RESEND_FROM') ?? 'NovaHost <onboarding@resend.dev>'
    const deviceLabel = deviceId.slice(-6).toUpperCase()

    const res = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${resendApiKey}`,
      },
      body: JSON.stringify({
        from,
        to: [email],
        subject: `Your NovaHost device move code: ${code}`,
        html: codeEmail(code, deviceLabel),
      }),
    })

    if (!res.ok) {
      const errText = await res.text()
      console.error(`[device-move] Resend ${res.status}: ${errText}`)
      // The overwhelmingly likely cause is RESEND_FROM being an unverified
      // domain, or still the shared sandbox sender (which delivers only to the
      // Resend account owner). Surface Resend's own words -- a support person
      // seeing "domain not verified" is far ahead of "contact support".
      let reason = errText
      try { const p = JSON.parse(errText); reason = p?.message ?? p?.error ?? errText } catch (_) {}
      return json({
        success: false,
        code: 'EMAIL_SEND_FAILED',
        error: 'Could not send the confirmation code. Please contact support.',
        detail: `Resend ${res.status}: ${String(reason).slice(0, 200)}`,
      }, 502)
    }

    // Written only after the mail is away.
    const { error: ticketErr } = await novaHost.from('device_move_tickets').insert({
      email,
      code_hash: await sha256Hex(code),
      target_device_id: deviceId,
      expires_at: expiresAt.toISOString(),
    })

    if (ticketErr) throw ticketErr

    return json({
      success: true,
      sent: true,
      expires_at: expiresAt.toISOString(),
      message: `We sent a 6-digit code to ${email}. It expires in ${TICKET_TTL_MINUTES} minutes.`,
    })

  } catch (err) {
    console.error('request-device-move error:', err)
    return json({ success: false, error: 'Could not start the device move. Try again.' }, 500)
  }
})
