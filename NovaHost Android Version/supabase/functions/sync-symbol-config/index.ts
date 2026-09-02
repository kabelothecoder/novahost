import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Stores a subscriber's per-symbol trading plan against their licence.
 *
 * The Trading Symbols screen is where a user decides which of their robot's
 * permitted symbols they actually want, at what size, and how many at once.
 * That decision has to exist somewhere the executor can read it:
 * metacopier-execute applies it as a ceiling before placing any order, because
 * a cap kept only on the handset is a cap that a stale, rolled-back or modified
 * client silently ignores.
 *
 * The app has been posting here since the Trading Symbols rewrite, but the
 * function was never created -- so every sync failed, the screen sat in a
 * permanent "could not reach server" state, and nothing was enforced.
 *
 * Authorises by licence key, like every other device-facing function: an app
 * install holds a mentor-issued key and has no Supabase auth session.
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

    const { data: license, error: licErr } = await supabase
      .from('licenses')
      .select('id, status, expires_at, metadata')
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr
    if (!license) return json({ success: false, error: 'Licence not recognised.' }, 401)
    if (license.status !== 'active') return json({ success: false, error: 'Licence is not active.' }, 403)
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return json({ success: false, error: 'Licence has expired.' }, 403)
    }

    // ---- Risk profile ------------------------------------------------------
    // Balance, risk % and trade count are one set of figures for the whole
    // licence, not per symbol, so they live on the licence's metadata. The
    // executor reads smart_lot_size from here when a symbol is set to smart.
    const metadata = (license.metadata ?? {}) as Record<string, unknown>
    metadata.risk_profile = {
      balance: Number(body.balance) || 0,
      currency: String(body.currency ?? 'USD').toUpperCase().slice(0, 3),
      risk_percent: Number(body.risk_percent) || 0,
      risk_trades: Number(body.risk_trades) || 1,
      smart_lot_size: Number(body.smart_lot_size) || 0,
      updated_at: new Date().toISOString(),
    }

    const { error: metaErr } = await supabase
      .from('licenses')
      .update({ metadata })
      .eq('id', license.id)

    if (metaErr) throw metaErr

    // ---- Per-symbol rows ---------------------------------------------------
    const symbols = Array.isArray(body.symbols) ? body.symbols : []

    if (symbols.length === 0) {
      return json({ success: true, synced: 0, message: 'Risk profile saved; no symbols sent.' })
    }

    const rows = symbols
      .filter((s: { symbol?: string }) => s && typeof s.symbol === 'string' && s.symbol.trim())
      .map((s: Record<string, unknown>) => ({
        license_id: license.id,
        // Normalised the same way the executor normalises an incoming signal,
        // or the lookup there would miss rows written here.
        symbol: String(s.symbol).replace(/[^A-Za-z0-9]/g, '').toUpperCase(),
        enabled: s.enabled !== false,
        lot: Number(s.lot) || 0,
        max_trades: Number(s.max_trades) || 0,
        smart_lot: s.smart_lot === true,
        // ---- What THIS user's broker calls the instrument -------------------
        //
        // The one field here that is NOT normalised, because it is not ours to
        // normalise: it goes into an order verbatim, and `XAUUSD.m` stripped to
        // `XAUUSDM` is a symbol no broker lists. Only trimmed, and reduced to
        // the characters MetaTrader permits inside a symbol name.
        //
        // Absent means "I have not said", which is different from a blank, and
        // must stay null -- the executor falls back to suffix and alias
        // discovery on null, and would otherwise send an empty symbol.
        broker_symbol: (() => {
          const raw = String(s.broker_symbol ?? '').trim().replace(/[^A-Za-z0-9._-]/g, '')
          return raw.length > 0 ? raw : null
        })(),
        updated_at: new Date().toISOString(),
      }))

    if (rows.length === 0) {
      return json({ success: false, error: 'No usable symbols in the request.' }, 400)
    }

    const { error: upsertErr } = await supabase
      .from('license_symbol_config')
      .upsert(rows, { onConflict: 'license_id,symbol' })

    if (upsertErr) throw upsertErr

    // Anything this licence had that is no longer in the plan is stale -- a
    // symbol the user removed must stop being tradeable, not linger as an
    // enabled row with an old ceiling.
    const keep = rows.map((r: { symbol: string }) => r.symbol)
    const { error: pruneErr } = await supabase
      .from('license_symbol_config')
      .delete()
      .eq('license_id', license.id)
      .not('symbol', 'in', `(${keep.map((s: string) => `"${s}"`).join(',')})`)

    if (pruneErr) {
      // Pruning is housekeeping. A failure here leaves an extra row that the
      // executor still caps correctly, so it is logged rather than surfaced.
      console.warn('[sync-symbol-config] prune failed:', pruneErr.message)
    }

    return json({ success: true, synced: rows.length })

  } catch (err) {
    console.error('[sync-symbol-config] fatal:', err)
    return json({ success: false, error: (err as Error).message }, 500)
  }
})
