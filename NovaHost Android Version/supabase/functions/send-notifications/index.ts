import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { getVapid, sendWebPush } from '../_shared/webpush.ts'

/**
 * Drains `notifications`: Web Push to every phone that turned it on, email to
 * licences that have no phone subscribed.
 *
 * Called by a statement trigger on `notifications` (latency) and a one-minute
 * cron (reliability), with the `notify_cron` secret. Rows are claimed with
 * SKIP LOCKED, so the two can overlap without sending anything twice.
 *
 * ## Voice
 *
 * Every alert speaks as the user's robot. It never says a mentor sent it:
 * on the phone, the robot is the one trading. The robot's name is the one the
 * user gave it on that phone (sent up with the subscription), falling back to
 * the robot's own display name.
 *
 * ## Push or email, never both
 *
 * A licence with at least one live push subscription gets push only, even if
 * the user switched that category off: switching an alert off must not turn
 * it into an email. Email is for licences with no phone subscribed at all.
 */

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-cron-secret',
}

const APP_URL = 'https://app.novahost-ea.app'
const VAPID_SUBJECT = 'mailto:support@novahost-ea.app'

/** An alert older than this is noise, not news. Wake-ups stay useful a little longer. */
const MAX_AGE_MS: Record<string, number> = { wake: 30 * 60_000 }
const DEFAULT_MAX_AGE_MS = 15 * 60_000

/** Settings › Notifications key for each kind. Absent in prefs means on. */
const CATEGORY: Record<string, string> = {
  wake: 'c_wake', signal: 'c_signal', placed: 'c_placed', refused: 'c_refused', target: 'c_target',
}
/** Allowed through quiet hours when "Still allow urgent alerts" is on. */
const URGENT = new Set(['refused', 'target'])

type Row = {
  id: string; license_id: string; ea_id: string | null; kind: string
  data: Record<string, unknown>; created_at: string; attempts: number
}
type Sub = {
  id: string; license_id: string; endpoint: string; p256dh: string; auth: string
  robot_name: string | null; prefs: Record<string, unknown> | null; tz: string | null
  failure_count: number
}

const num = (v: unknown) => {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}
const lvl = (v: unknown) => num(v) === null ? 'none' : String(num(v))

/** Refusal codes the executor sends without a sentence of its own. */
const REFUSAL_TEXT: Record<string, string> = {
  NO_SIZE_CONFIGURED: 'No lot size is set for this symbol. Open Trading Symbols and set one.',
  ACCOUNT_DISCONNECTED: 'Your trading account is disconnected. Reconnect it in Accounts.',
  ACCOUNT_WRONG_CREDENTIALS: 'Your broker password was rejected. Update it in Accounts.',
  INSUFFICIENT_MARGIN: 'Not enough free margin for this trade.',
  AUTOTRADING_DISABLED: 'Your broker would not accept this symbol name.',
  EXECUTOR_UNREACHABLE: 'Could not reach the trading server. It will try again on the next trade.',
}

function render(row: Row, robot: string): { title: string; body: string; url: string } {
  const d = row.data ?? {}
  const pair = String(d.pair ?? '').toUpperCase()
  const side = String(d.side ?? '').toUpperCase()

  switch (row.kind) {
    case 'wake':
      return { title: robot, body: 'Please turn me on as I want to trade', url: '/home' }
    case 'signal': {
      const entry = num(d.price) ?? 'market'
      return {
        title: `${robot} just spotted a ${side} on ${pair}`,
        body: `Entry ${entry} · TP ${lvl(d.tp)} · SL ${lvl(d.sl)}`,
        url: '/home',
      }
    }
    case 'placed':
      return {
        title: `${robot} opened a ${side} on ${pair}`,
        body: `${num(d.volume) ?? ''} lots · TP ${lvl(d.tp)} · SL ${lvl(d.sl)}`.trim(),
        url: '/trades',
      }
    case 'refused': {
      const detail = typeof d.detail === 'string' && d.detail.trim()
        ? d.detail.trim()
        : REFUSAL_TEXT[String(d.code)] ?? 'The broker refused the order.'
      return { title: `${robot} could not place ${side} ${pair}`.replace(/\s+/g, ' '), body: detail, url: '/trades' }
    }
    case 'target':
      return { title: robot, body: String(d.message ?? 'Daily target reached.'), url: '/home' }
    default:
      return { title: robot, body: String(d.message ?? ''), url: '/home' }
  }
}

/** Minutes since midnight in the phone's own time zone. */
function minutesIn(tz: string | null): number {
  try {
    const parts = new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: tz || 'Africa/Johannesburg',
    }).formatToParts(new Date())
    const h = Number(parts.find((p) => p.type === 'hour')?.value)
    const m = Number(parts.find((p) => p.type === 'minute')?.value)
    return h * 60 + m
  } catch {
    return -1
  }
}
const hhmm = (s: unknown) => {
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(s ?? ''))
  return m ? Number(m[1]) * 60 + Number(m[2]) : null
}

function wanted(kind: string, sub: Sub): boolean {
  const p = sub.prefs ?? {}
  if (p.nOn === false) return false
  const cat = CATEGORY[kind]
  if (cat && p[cat] === false) return false

  if (p.quietOn === true) {
    const from = hhmm(p.qFrom), to = hhmm(p.qTo), now = minutesIn(sub.tz)
    if (from !== null && to !== null && now >= 0) {
      const inQuiet = from <= to ? now >= from && now < to : now >= from || now < to
      if (inQuiet && !(p.quietAllow !== false && URGENT.has(kind))) return false
    }
  }
  return true
}

const esc = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

function emailHtml(title: string, body: string, robot: string, accent: string, url: string): string {
  return `<!doctype html><html><body style="margin:0;background:#0E0E11;padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:440px;background:#17171C;border:1px solid #2A2A33;border-radius:24px;overflow:hidden">
<tr><td style="height:4px;background:${accent}"></td></tr>
<tr><td align="center" style="padding:32px 28px 8px">
<img src="${APP_URL}/robot_avatar.png" width="96" height="96" alt="${esc(robot)}" style="display:block;border-radius:48px;border:2px solid ${accent}">
<div style="margin-top:14px;font-size:12px;letter-spacing:2px;text-transform:uppercase;color:${accent};font-weight:700">${esc(robot)}</div>
</td></tr>
<tr><td style="padding:8px 28px 4px;text-align:center;color:#F4F4F6;font-size:20px;line-height:28px;font-weight:700">${esc(title)}</td></tr>
<tr><td style="padding:4px 28px 24px;text-align:center;color:#A6A6B3;font-size:15px;line-height:22px">${esc(body)}</td></tr>
<tr><td align="center" style="padding:0 28px 32px">
<a href="${APP_URL}${url}" style="display:inline-block;background:${accent};color:#0E0E11;text-decoration:none;font-weight:700;font-size:15px;padding:14px 32px;border-radius:9999px">Open NovaHost</a>
</td></tr>
</table>
<div style="max-width:440px;margin-top:16px;color:#6B6B78;font-size:12px;line-height:18px;text-align:center">
You get these emails because notifications are not turned on in the NovaHost app on your iPhone. Turn them on in Settings › Notifications to get alerts on your phone instead.<br>NovaHost
</div>
</td></tr></table></body></html>`
}

function safeAccent(v: unknown): string {
  const s = String(v ?? '').trim()
  return /^#[0-9a-fA-F]{6}$/.test(s) ? s : '#5C9CE6'
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS_HEADERS })

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } })

  try {
    const db = createClient(Deno.env.get('SUPABASE_URL') ?? '', Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '')

    const presented = req.headers.get('x-cron-secret') ?? ''
    const { data: secretRow } = await db
      .from('internal_secrets').select('secret').eq('name', 'notify_cron').maybeSingle()
    if (!secretRow?.secret || presented !== secretRow.secret) {
      return json({ success: false, code: 'FORBIDDEN', error: 'Not authorised.' }, 403)
    }

    const { data: claimed, error: claimErr } = await db.rpc('claim_notifications', { p_limit: 300 })
    if (claimErr) throw claimErr
    const rows = (claimed ?? []) as Row[]
    if (!rows.length) return json({ success: true, processed: 0 })

    const now = Date.now()
    const fresh: Row[] = []
    const expired: string[] = []
    for (const r of rows) {
      const age = now - new Date(r.created_at).getTime()
      if (age > (MAX_AGE_MS[r.kind] ?? DEFAULT_MAX_AGE_MS)) expired.push(r.id)
      else fresh.push(r)
    }
    if (expired.length) {
      await db.from('notifications').update({ status: 'expired' }).in('id', expired)
    }

    const licenceIds = [...new Set(fresh.map((r) => r.license_id))]
    const eaIds = [...new Set(fresh.map((r) => r.ea_id).filter(Boolean))] as string[]

    const [{ data: subs }, { data: eas }, { data: lics }] = await Promise.all([
      db.from('push_subscriptions')
        .select('id, license_id, endpoint, p256dh, auth, robot_name, prefs, tz, failure_count')
        .in('license_id', licenceIds.length ? licenceIds : ['00000000-0000-0000-0000-000000000000'])
        .is('disabled_at', null),
      db.from('expert_advisors').select('id, name, display_name, accent_color')
        .in('id', eaIds.length ? eaIds : ['00000000-0000-0000-0000-000000000000']),
      db.from('licenses').select('id, license_key, owner_email')
        .in('id', licenceIds.length ? licenceIds : ['00000000-0000-0000-0000-000000000000']),
    ])

    const subsBy = new Map<string, Sub[]>()
    for (const s of (subs ?? []) as Sub[]) {
      const list = subsBy.get(s.license_id) ?? []
      list.push(s)
      subsBy.set(s.license_id, list)
    }
    const eaBy = new Map((eas ?? []).map((e: Record<string, unknown>) => [e.id as string, e]))

    // Emails only matter for licences with no phone subscribed.
    const emailBy = new Map<string, string>()
    const noPush = (lics ?? []).filter((l: Record<string, unknown>) => !subsBy.has(l.id as string))
    for (const l of noPush) if (l.owner_email) emailBy.set(l.id as string, String(l.owner_email))
    const missingKeys = noPush.filter((l) => !emailBy.has(l.id as string)).map((l) => l.license_key as string)
    if (missingKeys.length) {
      const { data: subRows } = await db.from('subscriptions')
        .select('license_key, email, updated_at').in('license_key', missingKeys)
        .not('email', 'is', null).order('updated_at', { ascending: false })
      const byKey = new Map<string, string>()
      for (const s of subRows ?? []) if (!byKey.has(s.license_key)) byKey.set(s.license_key, s.email)
      for (const l of noPush) {
        const e = byKey.get(l.license_key as string)
        if (e && !emailBy.has(l.id as string)) emailBy.set(l.id as string, e)
      }
    }

    const vapid = (subs ?? []).length ? await getVapid(db) : null
    const resendKey = Deno.env.get('RESEND_API_KEY')
    const mailFrom = Deno.env.get('RESEND_FROM')

    const results: Array<{ id: string; status: string; push_sent: number; email_sent: boolean; error: string | null }> = []
    const emails: Array<{ rowId: string; payload: Record<string, unknown> }> = []
    const gone: string[] = []
    const okSubs: string[] = []
    const failedSubs: Sub[] = []

    await Promise.all(fresh.map(async (row) => {
      const ea = row.ea_id ? eaBy.get(row.ea_id) : undefined
      const eaName = String(ea?.display_name || ea?.name || 'Your robot')
      const list = subsBy.get(row.license_id) ?? []

      if (list.length) {
        let sent = 0
        let lastErr: string | null = null
        for (const sub of list) {
          if (!wanted(row.kind, sub)) continue
          const robot = (sub.robot_name || '').trim() || eaName
          const msg = render(row, robot)
          const res = await sendWebPush(sub, {
            title: msg.title, body: msg.body, url: msg.url, kind: row.kind, id: row.id,
            tag: row.kind === 'wake' ? 'wake' : `${row.kind}:${String(row.data?.signal_id ?? row.id)}`,
          }, vapid!, { subject: VAPID_SUBJECT, urgency: 'high', ttl: row.kind === 'wake' ? 1800 : 900 })
          if (res.ok) { sent++; okSubs.push(sub.id) }
          else {
            lastErr = `${res.status} ${res.error ?? ''}`.trim()
            if (res.gone) gone.push(sub.id); else failedSubs.push(sub)
          }
        }
        results.push({ id: row.id, status: sent || !lastErr ? 'done' : 'failed', push_sent: sent, email_sent: false, error: lastErr })
        return
      }

      const to = emailBy.get(row.license_id)
      if (!to || !resendKey || !mailFrom) {
        results.push({ id: row.id, status: 'done', push_sent: 0, email_sent: false, error: to ? 'email not configured' : 'no push, no email' })
        return
      }
      const msg = render(row, eaName)
      emails.push({
        rowId: row.id,
        payload: {
          from: mailFrom, to: [to],
          subject: msg.title,
          html: emailHtml(msg.title, msg.body, eaName, safeAccent(ea?.accent_color), msg.url),
          text: `${msg.title}\n\n${msg.body}\n\n${APP_URL}${msg.url}`,
        },
      })
    }))

    // Resend's batch endpoint takes up to 100 messages per call.
    for (let i = 0; i < emails.length; i += 100) {
      const chunk = emails.slice(i, i + 100)
      let ok = false, err: string | null = null
      try {
        const res = await fetch('https://api.resend.com/emails/batch', {
          method: 'POST',
          headers: { Authorization: `Bearer ${resendKey}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(chunk.map((c) => c.payload)),
          signal: AbortSignal.timeout(15_000),
        })
        ok = res.ok
        if (!ok) err = `resend ${res.status}: ${(await res.text()).slice(0, 200)}`
      } catch (e) { err = (e as Error).message }
      for (const c of chunk) {
        results.push({ id: c.rowId, status: ok ? 'done' : 'failed', push_sent: 0, email_sent: ok, error: err })
      }
    }

    // ---- Bookkeeping. Never allowed to fail the run: the alerts already went.
    const sentAt = new Date().toISOString()
    await Promise.all(results.map((r) =>
      db.from('notifications').update({
        status: r.status, push_sent: r.push_sent, email_sent: r.email_sent, error: r.error, sent_at: sentAt,
      }).eq('id', r.id),
    ))
    if (gone.length) {
      await db.from('push_subscriptions').update({ disabled_at: sentAt }).in('id', [...new Set(gone)])
    }
    if (okSubs.length) {
      await db.from('push_subscriptions')
        .update({ last_success_at: sentAt, failure_count: 0 }).in('id', [...new Set(okSubs)])
    }
    for (const s of failedSubs) {
      await db.from('push_subscriptions').update({ failure_count: s.failure_count + 1 }).eq('id', s.id)
    }

    const pushed = results.reduce((n, r) => n + r.push_sent, 0)
    const mailed = results.filter((r) => r.email_sent).length
    console.log(`[notify] ${fresh.length} alerts: ${pushed} pushes, ${mailed} emails, ${expired.length} expired, ${gone.length} dead endpoints`)

    return json({ success: true, processed: fresh.length, pushed, mailed, expired: expired.length })
  } catch (err) {
    console.error('[notify] fatal:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
