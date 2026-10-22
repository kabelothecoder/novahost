import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { getVapid, sendWebPush } from '../_shared/webpush.ts'

/**
 * The phone's side of Web Push.
 *
 *   { action: 'config' }                      → { publicKey }
 *   { action: 'subscribe', license_keys, subscription, robot_name, prefs, tz }
 *   { action: 'unsubscribe', subscription }
 *   { action: 'test', license_key, subscription }
 *
 * The app authenticates by licence key, same as `set-auto-execute`: holding a
 * key is the credential. A phone can hold several licences, so `subscribe`
 * takes all of them and the endpoint is registered once per licence; a
 * licence no longer in the list stops alerting this phone.
 *
 * The endpoint is checked against the push services phones actually use.
 * `send-notifications` POSTs to whatever is stored here, so an arbitrary URL
 * would turn it into a way to make our servers call anything.
 */

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const PUSH_HOSTS = [
  /^web\.push\.apple\.com$/,
  /^fcm\.googleapis\.com$/,
  /^updates\.push\.services\.mozilla\.com$/,
  /\.notify\.windows\.com$/,
]

type Subscription = { endpoint?: string; keys?: { p256dh?: string; auth?: string } }

function validEndpoint(s: Subscription | undefined): s is Required<Subscription> {
  if (!s?.endpoint || !s.keys?.p256dh || !s.keys?.auth) return false
  try {
    const u = new URL(s.endpoint)
    return u.protocol === 'https:' && PUSH_HOSTS.some((re) => re.test(u.hostname))
  } catch {
    return false
  }
}

/** Only the keys Settings › Notifications owns. Nothing else is stored. */
function cleanPrefs(p: unknown): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  if (!p || typeof p !== 'object') return out
  for (const [k, v] of Object.entries(p as Record<string, unknown>)) {
    if (/^c_[a-z]+$/.test(k) || k === 'nOn' || k === 'quietOn' || k === 'quietAllow') {
      if (typeof v === 'boolean') out[k] = v
    } else if ((k === 'qFrom' || k === 'qTo') && typeof v === 'string' && /^\d{1,2}:\d{2}$/.test(v)) {
      out[k] = v
    }
  }
  return out
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS_HEADERS })

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } })

  try {
    const db = createClient(Deno.env.get('SUPABASE_URL') ?? '', Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '')
    const body = await req.json().catch(() => ({}))
    const action = String(body.action ?? '')

    if (action === 'config') {
      const vapid = await getVapid(db)
      return json({ success: true, publicKey: vapid.publicKey })
    }

    const sub = body.subscription as Subscription | undefined
    if (!validEndpoint(sub)) {
      return json({ success: false, code: 'BAD_SUBSCRIPTION', error: 'This phone did not give a usable push address.' }, 400)
    }

    if (action === 'unsubscribe') {
      await db.from('push_subscriptions')
        .update({ disabled_at: new Date().toISOString() })
        .eq('endpoint', sub.endpoint)
      return json({ success: true })
    }

    const rawKeys: unknown[] = Array.isArray(body.license_keys)
      ? body.license_keys
      : body.license_key ? [body.license_key] : []
    const keys = [...new Set(rawKeys.map((k) => String(k ?? '').trim().toUpperCase()).filter(Boolean))].slice(0, 20)
    if (!keys.length) {
      return json({ success: false, code: 'NO_LICENCE', error: 'A licence key is required.' }, 400)
    }

    const { data: lics, error: licErr } = await db
      .from('licenses').select('id, license_key, status, expires_at').in('license_key', keys)
    if (licErr) throw licErr
    const live = (lics ?? []).filter((l) =>
      l.status === 'active' && (!l.expires_at || new Date(l.expires_at) > new Date()))
    if (!live.length) {
      return json({ success: false, code: 'LICENCE_INACTIVE', error: 'None of these licences is active.' }, 403)
    }

    if (action === 'test') {
      const vapid = await getVapid(db)
      const robot = String(body.robot_name ?? '').trim().slice(0, 40) || 'Your robot'
      const res = await sendWebPush(
        { endpoint: sub.endpoint, p256dh: sub.keys.p256dh!, auth: sub.keys.auth! },
        { title: robot, body: 'Notifications are working. I will tell you when I spot a trade.', url: '/settings', kind: 'test', tag: 'test' },
        vapid,
        { subject: 'mailto:support@novahost-ea.app', ttl: 60 },
      )
      if (!res.ok) {
        return json({ success: false, code: res.gone ? 'SUBSCRIPTION_GONE' : 'PUSH_FAILED', error: 'Apple did not accept the test notification.', status: res.status }, 502)
      }
      return json({ success: true })
    }

    if (action !== 'subscribe') {
      return json({ success: false, code: 'BAD_REQUEST', error: 'Unknown action.' }, 400)
    }

    const now = new Date().toISOString()
    const shared = {
      endpoint: sub.endpoint,
      p256dh: sub.keys.p256dh,
      auth: sub.keys.auth,
      robot_name: String(body.robot_name ?? '').trim().slice(0, 40) || null,
      prefs: cleanPrefs(body.prefs),
      tz: typeof body.tz === 'string' ? body.tz.slice(0, 64) : null,
      user_agent: (req.headers.get('user-agent') ?? '').slice(0, 300),
      updated_at: now,
      disabled_at: null,
      failure_count: 0,
    }
    const { error: upErr } = await db.from('push_subscriptions').upsert(
      live.map((l) => ({ ...shared, license_id: l.id })),
      { onConflict: 'license_id,endpoint' },
    )
    if (upErr) throw upErr

    // A licence removed from this phone stops alerting it.
    await db.from('push_subscriptions')
      .update({ disabled_at: now })
      .eq('endpoint', sub.endpoint)
      .not('license_id', 'in', `(${live.map((l) => l.id).join(',')})`)
      .is('disabled_at', null)

    return json({ success: true, licences: live.length })
  } catch (err) {
    console.error('[push-subscribe] fatal:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
