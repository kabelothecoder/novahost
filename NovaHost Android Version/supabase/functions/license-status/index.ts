import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Answers "is this licence usable, and does it have a trading account attached".
 *
 * This exists because the Android app has no Supabase auth session. It holds a
 * mentor-issued licence key and talks to PostgREST with the anon key, and RLS on
 * `licenses` grants SELECT only to `authenticated` or to a row's own
 * `auth.uid() = user_id`. So the app's direct query returned an empty list for
 * every key ever issued, and the ignition read that as "no trading account
 * connected" -- the "Activation Failed" that valid keys could not get past.
 *
 * Opening `licenses` to anon was never an option: the anon key ships inside the
 * APK, so an anon SELECT policy would let anyone dump every licence key in the
 * product. The read moves server-side to the service role instead, and this
 * returns only booleans and display data -- never the MetaCopier account id,
 * which the client has no use for and must not be able to aim a trade at.
 *
 * Read-only. Unlike validate-license it binds nothing, so the ignition may call
 * it on every START without side effects.
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
    const rawKey = body.license_key

    if (!rawKey || !String(rawKey).trim()) {
      return json({ success: false, error: 'A licence key is required.' }, 400)
    }

    const key = String(rawKey).trim().toUpperCase()

    const { data: license, error } = await supabase
      .from('licenses')
      .select('id, ea_id, status, expires_at, allowed_symbols, metadata')
      .eq('license_key', key)
      .maybeSingle()

    if (error) throw error

    if (!license) {
      return json({
        success: true,
        active: false,
        linked: false,
        reason: 'not_found',
        message: 'This licence key is not recognised.',
      })
    }

    if (license.status !== 'active') {
      return json({
        success: true,
        active: false,
        linked: false,
        reason: 'inactive',
        message: 'This licence is not active.',
      })
    }

    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return json({
        success: true,
        active: false,
        linked: false,
        reason: 'expired',
        message: 'This licence has expired.',
      })
    }

    const metadata = (license.metadata ?? {}) as Record<string, unknown>
    const accountId = metadata.metacopier_account_id
    const linked = typeof accountId === 'string' && accountId.trim().length > 0

    return json({
      success: true,
      active: true,
      linked,
      // Deliberately not the account id -- only whether one exists.
      ea_id: license.ea_id ?? null,
      allowed_symbols: license.allowed_symbols ?? [],
      broker_server: (metadata.broker_server as string) ?? null,
      platform: (metadata.platform as string) ?? null,
      connected_at: (metadata.connected_at as string) ?? null,
      reason: linked ? 'ready' : 'no_account',
      message: linked
        ? 'Trading account linked.'
        : 'No trading account is connected to this licence yet.',
    })

  } catch (err) {
    console.error('[license-status] fatal:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
