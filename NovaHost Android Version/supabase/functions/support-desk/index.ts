import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Help & Support backend for the iOS app. Two actions, both keyed by licence:
 *
 * - `overview`: what needs fixing and why trades were refused, read from
 *   `signal_executions` (the executor's own record — `trade_logs` has no
 *   writer) and the licence's expiry. A refusal only counts as an open fix
 *   while nothing has been placed since that would prove it resolved.
 *
 * - `ticket`: saves to `support_tickets` (service role only) and emails the
 *   support inbox through Resend. The row is the record; a failed email
 *   still returns success with `emailed: false`, so a ticket is never lost
 *   to a mail outage.
 *
 * The client never receives a broker account id or the full licence key.
 */

const SUPPORT_INBOX = Deno.env.get('SUPPORT_INBOX') ?? 'kabelongcwatywa@icloud.com'
const TICKETS_PER_DAY = 5
const TOPICS = ['Trades', 'Broker', 'Payment', 'Something else']

type Exec = { status: string; code: string | null; detail: string | null; pair: string | null; side: string | null; executed_at: string }

// What a refusal code means to the user, and which screen fixes it.
function fixFor(e: Exec): { id: string; sev: 'red' | 'amber'; title: string; btn: string; go: string } | null {
  const pair = e.pair || 'this symbol'
  switch (e.code) {
    case 'ACCOUNT_DISCONNECTED':
    case 'ACCOUNT_WAS_DELETED':
      return { id: 'broker', sev: 'red', title: 'Broker disconnected', btn: 'Reconnect broker', go: '/accounts' }
    case 'ACCOUNT_WRONG_CREDENTIALS':
      return { id: 'password', sev: 'red', title: 'Wrong broker password', btn: 'Update password', go: '/accounts' }
    case 'AUTOTRADING_DISABLED':
      return { id: 'autotrading:' + pair, sev: 'red', title: `${pair} was blocked by your broker`, btn: 'Check symbol name', go: '/pairs' }
    case 'NO_SIZE_CONFIGURED':
      return { id: 'size:' + pair, sev: 'amber', title: `${pair} isn't set up`, btn: `Set up ${pair}`, go: '/pairs' }
    case 'INSUFFICIENT_MARGIN':
      return { id: 'margin', sev: 'amber', title: 'Not enough free margin', btn: 'See open trades', go: '/trades' }
    case 'POSITION_CAP':
      return { id: 'cap:' + pair, sev: 'amber', title: `${pair} hit its trade limit`, btn: 'Change limit', go: '/pairs' }
    case 'BROKER_REJECTED':
      return { id: 'rejected:' + pair, sev: 'amber', title: `Broker refused ${pair}`, btn: 'Check symbol', go: '/pairs' }
    default:
      return null
  }
}

const scopedToPair = (id: string) => id.includes(':')

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS_HEADERS })

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } })

  try {
    const db = createClient(Deno.env.get('SUPABASE_URL') ?? '', Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '')
    const body = await req.json().catch(() => ({}))
    const key = String(body.license_key ?? '').trim().toUpperCase()
    if (!key) return json({ success: false, error: 'A licence key is required.' }, 400)

    const { data: lic, error: licErr } = await db
      .from('licenses')
      .select('id, status, expires_at')
      .eq('license_key', key)
      .maybeSingle()
    if (licErr) throw licErr
    if (!lic) return json({ success: false, error: 'This licence key is not recognised.' }, 404)

    if (body.action === 'overview') {
      const since = new Date(Date.now() - 7 * 864e5).toISOString()
      const { data: rows, error } = await db
        .from('signal_executions')
        .select('status, code, detail, pair, side, executed_at')
        .eq('license_id', lic.id)
        .gte('executed_at', since)
        .order('executed_at', { ascending: false })
        .limit(200)
      if (error) throw error
      const execs = (rows ?? []) as Exec[]

      const refused = execs.filter((e) => e.status === 'refused')
      const lastPlacedAt = (pair?: string | null) =>
        execs.find((e) => e.status === 'placed' && (!pair || e.pair === pair))?.executed_at ?? null

      // Newest refusal per cause, kept open only if nothing has been placed since.
      const fixes: Record<string, unknown>[] = []
      const seen = new Set<string>()
      for (const e of refused) {
        const f = fixFor(e)
        if (!f || seen.has(f.id)) continue
        seen.add(f.id)
        const placed = lastPlacedAt(scopedToPair(f.id) ? e.pair : null)
        if (placed && placed > e.executed_at) continue
        fixes.push({ ...f, when: e.executed_at, detail: e.detail || 'Your broker refused the order.' })
      }

      if (lic.expires_at && new Date(lic.expires_at) < new Date()) {
        fixes.unshift({ id: 'plan', sev: 'red', title: 'Licence expired', when: lic.expires_at, detail: 'Your robot stops opening new trades until you renew. Your licence key stays the same.', btn: 'Renew', go: '/payment' })
      }

      return json({
        success: true,
        fixes,
        refused: refused.slice(0, 20).map((e) => {
          const f = fixFor(e)
          return { pair: e.pair, side: e.side, when: e.executed_at, reason: e.detail || 'Your broker refused the order.', btn: f?.btn ?? null, go: f?.go ?? null }
        }),
        refused_count: refused.length,
        licence: { status: lic.status, expires_at: lic.expires_at },
      })
    }

    if (body.action === 'ticket') {
      const topic = TOPICS.includes(body.topic) ? body.topic : 'Something else'
      const message = String(body.message ?? '').trim().slice(0, 4000)
      if (message.length < 5) return json({ success: false, error: 'Tell us a little about what happened.' }, 400)

      const dayAgo = new Date(Date.now() - 864e5).toISOString()
      const { count } = await db
        .from('support_tickets')
        .select('id', { count: 'exact', head: true })
        .eq('license_id', lic.id)
        .gte('created_at', dayAgo)
      if ((count ?? 0) >= TICKETS_PER_DAY) {
        return json({ success: false, error: 'You have sent several tickets today. We will answer those first, or message us on WhatsApp.' }, 429)
      }

      // Only what the user left switched on, each trimmed to a sane size.
      const a = body.attachments && typeof body.attachments === 'object' ? body.attachments : {}
      const attachments: Record<string, unknown> = {}
      if (typeof a.device === 'string') attachments.device = a.device.slice(0, 300)
      if (a.licence) attachments.licence = { key: key.slice(0, 5) + '····' + key.slice(-4), status: lic.status, expires_at: lic.expires_at }
      if (Array.isArray(a.errors)) attachments.errors = a.errors.slice(0, 5).map((x: unknown) => String(x).slice(0, 300))

      const ref = 'NH-' + Math.floor(10000 + Math.random() * 90000)
      const { data: row, error } = await db
        .from('support_tickets')
        .insert({ ref, license_id: lic.id, topic, message, attachments })
        .select('id, ref')
        .single()
      if (error) throw error

      let emailed = false
      const resendKey = Deno.env.get('RESEND_API_KEY')
      const from = Deno.env.get('RESEND_FROM')
      if (resendKey && from) {
        const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!))
        const html = `<h2>Ticket ${row.ref} · ${esc(topic)}</h2><p style="white-space:pre-wrap">${esc(message)}</p><pre>${esc(JSON.stringify(attachments, null, 2))}</pre>`
        try {
          const res = await fetch('https://api.resend.com/emails', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${resendKey}` },
            body: JSON.stringify({ from, to: [SUPPORT_INBOX], subject: `[NovaHost] ${row.ref} · ${topic}`, html }),
          })
          emailed = res.ok
          if (!res.ok) console.error('support-desk: Resend error', await res.text())
        } catch (e) {
          console.error('support-desk: Resend unreachable', e)
        }
        if (emailed) await db.from('support_tickets').update({ emailed: true }).eq('id', row.id)
      } else {
        console.error('support-desk: RESEND_API_KEY / RESEND_FROM not set; ticket saved without email')
      }

      return json({ success: true, ref: row.ref, emailed })
    }

    return json({ success: false, error: 'Unknown action.' }, 400)
  } catch (e) {
    console.error('support-desk', e)
    return json({ success: false, error: 'Support is unavailable right now. Try again in a minute.' }, 500)
  }
})
