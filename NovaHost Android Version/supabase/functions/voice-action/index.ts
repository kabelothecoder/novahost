import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { openBridge, resolveLink } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * The tap that follows a voice proposal. Nothing `voice-turn` says can move
 * money; only this, and only on a person's tap.
 *
 * - `confirm_trade` { idea_id }: places a `voice_trade_ideas` row if it is this
 *   licence's, unexpired (30 s) and unused. It is forwarded to
 *   `metacopier-execute` with no `signal_id`, i.e. as a manual order, so the
 *   budget, margin, symbol-plan and halt checks are exactly the ones the
 *   scanner's Execute button gets. The row is claimed first so a double tap
 *   cannot place it twice.
 * - `kill_switch`: closes every open position on the linked account. Like the
 *   auto-execute OFF switch, it has no preconditions beyond a known licence.
 */
Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS_HEADERS })

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } })

  try {
    const url = Deno.env.get('SUPABASE_URL') ?? ''
    const service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    const db = createClient(url, service)
    const body = await req.json().catch(() => ({}))

    const key = String(body.license_key ?? '').trim().toUpperCase()
    if (!key) return json({ success: false, code: 'NO_LICENCE', error: 'A licence key is required.' }, 400)

    const { data: lic, error: licErr } = await db
      .from('licenses').select('id, metadata').eq('license_key', key).maybeSingle()
    if (licErr) throw licErr
    if (!lic) return json({ success: false, code: 'LICENCE_UNKNOWN', error: 'This licence key is not recognised.' }, 404)

    if (body.action === 'kill_switch') {
      const link = resolveLink(lic.metadata as Record<string, unknown>)
      if (!link) return json({ success: false, code: 'NO_ACCOUNT_LINKED', error: 'No trading account is connected.' }, 409)
      const bridge = openBridge(link, db)
      if (!bridge) return json({ success: false, code: 'SERVER_MISCONFIGURED', error: 'Could not reach your broker. Try again.' }, 503)
      const r = await bridge.closeAll()
      if (!r.ok) return json({ success: false, code: r.code, error: 'Your broker did not accept the close. Try again.' }, 502)
      return json({ success: true, message: 'Closing all open trades now.' })
    }

    if (body.action === 'confirm_trade') {
      const ideaId = String(body.idea_id ?? '')
      // Claim: only an unexpired, unconfirmed row of THIS licence flips.
      const { data: idea, error: claimErr } = await db
        .from('voice_trade_ideas')
        .update({ confirmed_at: new Date().toISOString() })
        .eq('id', ideaId).eq('license_id', lic.id)
        .is('confirmed_at', null).gt('expires_at', new Date().toISOString())
        .select('id, pair, side, volume, sl, tp')
        .maybeSingle()
      if (claimErr) throw claimErr
      if (!idea) return json({ success: false, code: 'IDEA_EXPIRED', error: 'That idea has expired. Ask again for a fresh one.' }, 410)

      const r = await fetch(`${url}/functions/v1/metacopier-execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${service}`, apikey: service },
        body: JSON.stringify({
          license_key: key, pair: idea.pair, side: idea.side,
          volume: idea.volume ?? undefined, sl: idea.sl ?? undefined, tp: idea.tp ?? undefined,
          account_id: body.account_id ?? undefined,
        }),
      })
      const result = await r.json().catch(() => ({ success: false, error: 'No answer from the executor.' }))
      await db.from('voice_trade_ideas').update({ result }).eq('id', idea.id)
      return json(result, r.status)
    }

    return json({ success: false, code: 'BAD_REQUEST', error: 'Unknown action.' }, 400)
  } catch (e) {
    console.error('[voice-action]', e)
    return json({ success: false, code: 'SERVER_ERROR', error: 'Something went wrong. Try again.' }, 500)
  }
})
