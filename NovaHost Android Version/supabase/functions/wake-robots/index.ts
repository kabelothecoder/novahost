import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

/**
 * The portal's "wake the robots" button: tell every user of a mentor's robot
 * to switch it on, before the mentor sends a trade.
 *
 * Same gate as `broadcast-signal`, in the same order: a real session, an
 * approved account, and ownership of the robot. "All my robots" means the
 * robots THIS mentor owns, never the platform.
 *
 * The alert itself is queued in `notifications` and delivered by
 * `send-notifications`, which words it as the robot ("Please turn me on as I
 * want to trade"); the mentor is never named on the phone.
 *
 * One wake-up per robot every five minutes. The button is pressed in a hurry
 * and a double tap must not buzz every subscriber twice.
 *
 * `{ preview: true }` answers what a press would do right now (how many
 * subscribers, how many phones, and when the cooldown ends) and sends nothing,
 * so the portal can show "N phones will buzz" before the mentor presses.
 */

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const COOLDOWN_MS = 5 * 60_000

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS_HEADERS })

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } })

  try {
    const db = createClient(Deno.env.get('SUPABASE_URL') ?? '', Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '')

    const jwt = (req.headers.get('authorization') ?? '').replace(/^Bearer\s+/i, '')
    const { data: { user } } = jwt ? await db.auth.getUser(jwt) : { data: { user: null } }
    if (!user) return json({ error: 'Forbidden: Not signed in' }, 403)

    const { data: approval } = await db
      .from('profiles').select('approval_status').eq('id', user.id).maybeSingle()
    if (approval?.approval_status !== 'approved') {
      return json({ error: 'Your account is pending approval.' }, 403)
    }

    const body = await req.json().catch(() => ({}))
    const eaId = String(body.ea_id ?? '')

    const { data: owned, error: eaErr } = await db.from('expert_advisors').select('id').eq('user_id', user.id)
    if (eaErr) throw eaErr
    const ownedIds = (owned ?? []).map((e: { id: string }) => e.id)
    if (!ownedIds.length) return json({ error: 'Forbidden: No bots owned by this account' }, 403)

    const all = eaId === 'MASTER_OVERRIDE' || eaId === 'ALL' || eaId === ''
    if (!all && !ownedIds.includes(eaId)) return json({ error: 'Forbidden: You do not own this bot' }, 403)
    const targets = all ? ownedIds : [eaId]

    // ---- Cooldown, per robot ----------------------------------------------
    const since = new Date(Date.now() - COOLDOWN_MS).toISOString()
    const { data: recent } = await db
      .from('notifications').select('ea_id, created_at')
      .eq('kind', 'wake').in('ea_id', targets).gte('created_at', since)
      .order('created_at', { ascending: false })
    const cooling = new Map<string, string>()
    for (const r of recent ?? []) if (!cooling.has(r.ea_id)) cooling.set(r.ea_id, r.created_at)

    const due = targets.filter((id) => !cooling.has(id))
    /* When the button can be pressed again: the moment the first cooling
       robot comes off cooldown. Null when nothing is cooling. */
    const nextAt = cooling.size
      ? new Date(Math.min(...[...cooling.values()].map((t) => new Date(t).getTime())) + COOLDOWN_MS).toISOString()
      : null

    const { data: lics, error: licErr } = await db
      .from('licenses').select('id, ea_id, expires_at')
      .in('ea_id', due.length ? due : ['00000000-0000-0000-0000-000000000000']).eq('status', 'active')
    if (licErr) throw licErr
    const live = (lics ?? []).filter((l) => !l.expires_at || new Date(l.expires_at) > new Date())

    // How many will actually buzz a phone, so the portal can say so honestly.
    // `phones` counts devices (one phone may hold several licences);
    // `onPush` counts licences, as before.
    const reach = async () => {
      if (!live.length) return { onPush: 0, phones: 0 }
      const { data: subs } = await db
        .from('push_subscriptions').select('license_id, endpoint')
        .in('license_id', live.map((l) => l.id)).is('disabled_at', null)
      const rows = (subs ?? []) as Array<{ license_id: string; endpoint: string }>
      return {
        onPush: new Set(rows.map((s) => s.license_id)).size,
        phones: new Set(rows.map((s) => s.endpoint)).size,
      }
    }

    // ---- Preview: what a press would do right now. Sends nothing. ----------
    if (body.preview === true) {
      return json({
        success: true, preview: true,
        queued: live.length, ...(await reach()),
        robots: due.length, cooling: cooling.size,
        cooldownUntil: due.length ? null : nextAt,
      })
    }

    if (!due.length) {
      const wait = Math.max(1, Math.ceil((new Date(nextAt!).getTime() - Date.now()) / 60_000))
      return json({
        error: `You just woke ${targets.length > 1 ? 'these robots' : 'this robot'}. Try again in ${wait} min.`,
        code: 'COOLDOWN',
        cooldownUntil: nextAt,
      }, 429)
    }

    if (!live.length) return json({ success: true, queued: 0, onPush: 0, phones: 0, robots: due.length })

    const stamp = Date.now()
    const { error: insErr } = await db.from('notifications').insert(
      live.map((l) => ({
        license_id: l.id, ea_id: l.ea_id, kind: 'wake', data: {},
        dedupe_key: `wake:${l.ea_id}:${l.id}:${stamp}`,
      })),
    )
    if (insErr) throw insErr

    return json({
      success: true,
      queued: live.length,
      ...(await reach()),
      robots: due.length,
      skippedCooling: targets.length - due.length,
      cooldownUntil: new Date(stamp + COOLDOWN_MS).toISOString(),
    })
  } catch (err) {
    console.error('[wake-robots] fatal:', err)
    return json({ error: (err as Error).message }, 500)
  }
})
