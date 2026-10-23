import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { resolveLink } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-cron-secret',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * Executes mentor signals without waiting for a handset to be awake.
 *
 * ## Why execution moved off the device
 *
 * It was on the handset, and a handset is not awake. Android holds a
 * foreground service and mostly survives; the iOS build is a PWA and its
 * timers stop the moment Safari is backgrounded or the phone locks. Since
 * `claim-signals` retires anything older than five minutes as stale, a phone
 * in a pocket does not execute late -- it MISSES, silently, which for a
 * copy-trading product is the entire feature failing.
 *
 * This runs on a trigger from the `signals` INSERT (for latency) and on a
 * two-minute cron (for reliability, since pg_net is fire-and-forget).
 *
 * ## How this cannot double-trade
 *
 * It claims through the SAME mechanism the handsets use: an INSERT into
 * `signal_deliveries`, unique on (signal_id, license_id). Whichever of the
 * server and the phone inserts first wins; the loser's insert is a no-op and
 * it does nothing. Two overlapping cron runs are safe for the same reason.
 * There is no new race here because there is no new claim path.
 *
 * ## Nothing trades unless a licence opted in
 *
 * Only licences with `auto_execute = true` are considered, and that column
 * defaults to false. A licence that has not opted in is never claimed for,
 * so its signals stay outstanding for its own handset exactly as before.
 *
 * ## Claiming is consumption, including when it fails
 *
 * A claimed signal is gone: the handset will never see it. That is correct
 * when the order was placed and uncomfortable when it was refused, but the
 * alternative -- leaving it unclaimed after an attempt -- means a refusal on
 * the server followed by a second attempt from the phone, which is how one
 * mentor call becomes two positions. So every outcome is written to
 * `signal_executions` instead, and a refusal is a record rather than a
 * silence.
 */

/** How recent a signal must be to still be worth acting on. Mirrors `claim-signals`. */
const FRESH_SECONDS = 300

/**
 * How far back to consider at all.
 *
 * Wider than the freshness window on purpose: signals between the two are
 * claimed and recorded as `missed` rather than left outstanding, which
 * retires them permanently. Without that, every run would reconsider the same
 * dead signals forever.
 */
const LOOKBACK_MINUTES = 60

/**
 * Ceiling on orders placed per invocation.
 *
 * An edge function has a wall-clock limit and each order is a round trip to a
 * broker. Hitting this is not data loss -- the cron runs again in two minutes
 * and the freshness window is five -- but it is logged loudly, because a run
 * that is regularly truncated means the cadence is wrong.
 */
const MAX_EXECUTIONS = 40

/** Letters and digits, upper case -- brokers decorate symbols. */
function baseSymbol(s: unknown): string {
  return String(s ?? '').replace(/[^A-Za-z0-9]/g, '').toUpperCase()
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
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL') ?? ''
    const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

    const novaHost = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

    /* ── Who is allowed to call this ──────────────────────────────────
       This places real orders, so it must not be callable by anyone who can
       guess the URL. It cannot be verify_jwt:true -- pg_net has no user to
       sign as -- so the caller proves itself with a secret that lives in a
       table only the service role can read. Compared in full; a wrong or
       missing secret is the same answer, and neither says which. */
    const presented = req.headers.get('x-cron-secret') ?? ''
    const { data: secretRow } = await novaHost
      .from('internal_secrets')
      .select('secret')
      .eq('name', 'auto_execute_cron')
      .maybeSingle()

    if (!secretRow?.secret || presented !== secretRow.secret) {
      console.warn('[auto-execute] refused: bad or missing cron secret')
      return json({ success: false, code: 'FORBIDDEN', error: 'Not authorised.' }, 403)
    }

    const now = Date.now()
    const lookbackFrom = new Date(now - LOOKBACK_MINUTES * 60_000).toISOString()

    // ---- Signals in play -----------------------------------------------------
    const { data: signals, error: sigErr } = await novaHost
      .from('signals')
      .select('id, ea_id, pair, side, type, lot, sl, tp, price, order_type, pending_expiry_seconds, signal_id, created_at')
      .gte('created_at', lookbackFrom)
      .order('created_at', { ascending: true })
      .limit(200)

    if (sigErr) throw sigErr
    if (!signals?.length) {
      return json({ success: true, considered: 0, placed: 0, refused: 0, missed: 0, skipped: 0 })
    }

    // ---- Licences that have asked the server to trade for them ---------------
    const eaIds = [...new Set(signals.map((s) => s.ea_id).filter(Boolean))]
    if (!eaIds.length) {
      return json({ success: true, considered: signals.length, placed: 0, refused: 0, missed: 0, skipped: 0 })
    }

    const { data: licences, error: licErr } = await novaHost
      .from('licenses')
      .select('id, license_key, ea_id, status, expires_at, metadata')
      .in('ea_id', eaIds)
      .eq('auto_execute', true)
      .eq('status', 'active')

    if (licErr) throw licErr
    if (!licences?.length) {
      return json({ success: true, considered: signals.length, placed: 0, refused: 0, missed: 0, skipped: 0, note: 'no licences opted in' })
    }

    /* An expired licence is still `status: 'active'` until something sweeps
       it, and `metacopier-execute` would refuse it anyway -- but refusing it
       here costs no round trip and leaves no claimed-then-rejected signal. */
    const eligible = licences.filter((l) => {
      if (l.expires_at && new Date(l.expires_at) < new Date()) return false
      // Linked on any provider -- the executor resolves which one.
      return resolveLink(l.metadata as Record<string, unknown> | null) !== null
    })

    if (!eligible.length) {
      return json({ success: true, considered: signals.length, placed: 0, refused: 0, missed: 0, skipped: 0, note: 'no eligible licences' })
    }

    const byEa = new Map<string, typeof eligible>()
    for (const l of eligible) {
      const list = byEa.get(l.ea_id) ?? []
      list.push(l)
      byEa.set(l.ea_id, list)
    }

    // ---- What has already been handed out ------------------------------------
    //
    // Read first so the common case -- everything already delivered -- costs one
    // query rather than one failed INSERT per signal per licence. The INSERT is
    // still what decides; this only avoids the work.
    const { data: taken, error: takenErr } = await novaHost
      .from('signal_deliveries')
      .select('signal_id, license_id')
      .in('signal_id', signals.map((s) => s.id))

    if (takenErr) throw takenErr
    const alreadyTaken = new Set((taken ?? []).map((t) => `${t.signal_id}:${t.license_id}`))

    // ---- Per-symbol plans, for sizing ---------------------------------------
    //
    // `broadcast-signal` writes `lot: null` when the mentor leaves the size
    // blank, and documents what that means: "the subscriber decides". Every
    // signal in this database is that case. The subscriber's decision lives in
    // `license_symbol_config`, so it is read here and sent as an explicit
    // volume.
    //
    // Deliberately NOT a fallback to 0.01. That is the exact default
    // `broadcast-signal` calls out as having "silently overrode every user's
    // own sizing plan" -- a signal with no size and a user with no plan is a
    // question nobody has answered, and guessing an answer trades real money
    // on it.
    const { data: plans } = await novaHost
      .from('license_symbol_config')
      .select('license_id, symbol, enabled, lot, smart_lot')
      .in('license_id', eligible.map((l) => l.id))

    const planFor = new Map<string, Record<string, unknown>>()
    for (const p of plans ?? []) {
      planFor.set(`${p.license_id}:${baseSymbol(p.symbol)}`, p)
    }

    /* The size the subscriber chose for this instrument. Mirrors the ceiling
       `metacopier-execute` computes, because on this platform the configured
       size IS the ceiling -- there is no second field. Sending it means the
       executor's cap compares the number against itself and changes nothing. */
    function plannedLot(license: Record<string, unknown>, pair: unknown): number {
      const cfg = planFor.get(`${license.id}:${baseSymbol(pair)}`)
      if (!cfg) return 0
      if (cfg.enabled === false) return 0

      const risk = (license.metadata as Record<string, unknown> | null)?.risk_profile as
        Record<string, unknown> | undefined
      const smart = Number(risk?.smart_lot_size) || 0

      return cfg.smart_lot && smart > 0 ? smart : Number(cfg.lot) || 0
    }

    // ---- Accounts per licence (multi-account) ---------------------------------
    //
    // A licence can trade several broker accounts. Each live row with
    // auto_execute on gets the signal, scaled by its own lot_multiplier. A
    // licence with no rows yet (linked before the table existed and not
    // backfilled) trades its one linked account exactly as before.
    const { data: accountRows, error: acctErr } = await novaHost
      .from('license_broker_accounts')
      .select('license_id, bridge_account_id, auto_execute, lot_multiplier')
      .in('license_id', eligible.map((l) => l.id))
      .is('disconnected_at', null)
    if (acctErr) throw acctErr

    type Target = { accountId: string | null; multiplier: number }
    const accountsFor = new Map<string, { all: number; active: Target[] }>()
    for (const r of accountRows ?? []) {
      const e = accountsFor.get(r.license_id) ?? { all: 0, active: [] }
      e.all++
      if (r.auto_execute) e.active.push({ accountId: r.bridge_account_id, multiplier: Number(r.lot_multiplier) || 1 })
      accountsFor.set(r.license_id, e)
    }
    const targetsOf = (licenceId: string): Target[] => {
      const e = accountsFor.get(licenceId)
      return e ? e.active : [{ accountId: null, multiplier: 1 }]
    }

    /** Scaled size, on the 0.01 grid every broker we serve accepts, never below it. */
    const scaled = (volume: number, multiplier: number) =>
      Math.max(0.01, Math.round(volume * multiplier * 100) / 100)

    // ---- Work ----------------------------------------------------------------
    const freshFrom = now - FRESH_SECONDS * 1000
    let placed = 0, refused = 0, missed = 0, skipped = 0, considered = 0
    let truncated = false

    const executions: Array<Record<string, unknown>> = []

    outer:
    for (const signal of signals) {
      const targets = byEa.get(signal.ea_id) ?? []

      for (const licence of targets) {
        if (alreadyTaken.has(`${signal.id}:${licence.id}`)) { skipped++; continue }
        // Checked BEFORE the claim, with room for every account: a licence is
        // claimed once, so stopping halfway through its accounts would lose
        // the rest of them for good.
        const targetAccounts = targetsOf(licence.id)
        if (placed + refused + Math.max(1, targetAccounts.length) > MAX_EXECUTIONS && placed + refused > 0) {
          truncated = true; break outer
        }

        considered++

        /* ---- Claim ----------------------------------------------------------
           The INSERT is the claim. `ignoreDuplicates` makes a conflict a no-op,
           and the RETURNING set is exactly the rows this call created -- so a
           handset or a concurrent run that got there first simply does not come
           back, and this loop does nothing for that pair. */
        const { data: claimed, error: claimErr } = await novaHost
          .from('signal_deliveries')
          .upsert(
            [{ signal_id: signal.id, license_id: licence.id, device_id: 'server' }],
            { onConflict: 'signal_id,license_id', ignoreDuplicates: true }
          )
          .select('signal_id')

        if (claimErr) {
          console.error(`[auto-execute] claim failed for ${signal.id}/${licence.id}: ${claimErr.message}`)
          continue
        }
        if (!claimed?.length) { skipped++; continue }

        const age = now - new Date(signal.created_at).getTime()

        /* Claimed but too old. Retired rather than fired late: a three-hour-old
           call placed into a market that has moved is worse than the miss it is
           trying to repair. The claim is kept so it can never fire later. */
        if (new Date(signal.created_at).getTime() < freshFrom) {
          missed++
          executions.push({
            signal_id: signal.id, license_id: licence.id,
            status: 'missed', code: 'STALE',
            detail: `Signal was ${Math.round(age / 1000)}s old; the freshness window is ${FRESH_SECONDS}s.`,
            pair: signal.pair, side: signal.side ?? signal.type,
          })
          continue
        }

        // ---- Size ----------------------------------------------------------
        const signalLot = Number(signal.lot)
        const volume = Number.isFinite(signalLot) && signalLot > 0
          ? signalLot
          : plannedLot(licence, signal.pair)

        if (!(volume > 0)) {
          refused++
          executions.push({
            signal_id: signal.id, license_id: licence.id,
            status: 'refused', code: 'NO_SIZE_CONFIGURED',
            detail: `Your mentor left the lot size to you, and no size is set for ${signal.pair}. Open Trading Symbols and set one.`,
            pair: signal.pair, side: signal.side ?? signal.type,
          })
          continue
        }

        if (targetAccounts.length === 0) {
          // Every account on the licence is paused. Claimed, so the handset
          // does not fire it either: the user switched these accounts off.
          skipped++
          executions.push({
            signal_id: signal.id, license_id: licence.id,
            status: 'refused', code: 'ALL_ACCOUNTS_PAUSED',
            detail: 'Auto-trading is switched off on every account linked to this licence.',
            pair: signal.pair, side: signal.side ?? signal.type,
          })
          continue
        }

        // ---- Place, on every account ---------------------------------------
        const side = String(signal.side ?? signal.type ?? 'BUY').toUpperCase()
        const placeOn = async (target: Target) => {
          const lots = scaled(volume, target.multiplier)
          const body: Record<string, unknown> = {
            license_key: licence.license_key,
            pair: signal.pair,
            side,
            volume: lots,
            sl: signal.sl,
            tp: signal.tp,
            signal_id: signal.signal_id ?? signal.id,
            ...(target.accountId ? { account_id: target.accountId } : {}),
          }
          /* Only for pending kinds. A MARKET order carrying an open_price is what
             produces MISSING_ENTRY_PRICE / ENTRY_OUTSIDE_BRACKET confusion. */
          if (signal.order_type && signal.order_type !== 'MARKET') {
            body.order_type = signal.order_type
            body.open_price = signal.price
            if (signal.pending_expiry_seconds) {
              body.pending_expiry_seconds = signal.pending_expiry_seconds
            }
          }
          const base = {
            signal_id: signal.id, license_id: licence.id, broker_account_id: target.accountId,
            pair: signal.pair, side, volume: lots, sl: signal.sl, tp: signal.tp,
          }

          try {
            const res = await fetch(`${SUPABASE_URL}/functions/v1/metacopier-execute`, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                apikey: SERVICE_ROLE_KEY,
                Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
              },
              body: JSON.stringify(body),
              signal: AbortSignal.timeout(25_000),
            })

            const out = await res.json().catch(() => null)

            if (out?.success === true) {
              placed++
              executions.push({ ...base, status: 'placed', code: out.code ?? null, detail: null })
            } else {
              refused++
              /* The executor's own words where it sent any. Brokers return
                 plain-English refusals ("market closed", "not enough money"),
                 and replacing one with a generic is how a user ends up unable
                 to tell a closed market from a rejected licence. */
              executions.push({
                ...base, status: 'refused',
                code: out?.code ?? `HTTP_${res.status}`,
                detail: out?.error ? String(out.error).slice(0, 500) : null,
              })
            }
          } catch (e) {
            refused++
            executions.push({
              ...base, status: 'error', code: 'EXECUTOR_UNREACHABLE',
              detail: (e as Error).message.slice(0, 500),
            })
          }
        }

        // In parallel: each account is its own terminal, and one slow broker
        // must not make the others late or push the run past its wall clock.
        await Promise.all(targetAccounts.map(placeOn))
      }
    }

    // ---- Audit ---------------------------------------------------------------
    // One insert for the whole run. Written last and never allowed to take the
    // run down with it: the orders are already placed, and losing the record of
    // them is bad but losing the response is worse.
    if (executions.length) {
      const { error: auditErr } = await novaHost.from('signal_executions').insert(executions)
      if (auditErr) console.error(`[auto-execute] audit write failed: ${auditErr.message}`)
    }

    if (truncated) {
      console.warn(
        `[auto-execute] hit the ${MAX_EXECUTIONS}-order ceiling; remaining signals wait for the next run`
      )
    }

    console.log(
      `[auto-execute] considered ${considered}, placed ${placed}, refused ${refused}, ` +
      `missed ${missed}, already taken ${skipped}`
    )

    return json({
      success: true,
      considered, placed, refused, missed, skipped,
      truncated,
      licences_opted_in: eligible.length,
    })

  } catch (err) {
    console.error('[auto-execute] fatal:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
