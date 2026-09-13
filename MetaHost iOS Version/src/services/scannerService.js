/**
 * Chart scan and order dispatch.
 *
 * ## What this replaces
 *
 * The old `ScannerView.executeTrade()` had four separate faults, all of them
 * live:
 *
 *   1. `localStorage.getItem('MH_LICENSE_KEY') || 'DEMO_KEY_123'` — every
 *      other file in the app reads `secStorage.getItem('LICENSE_KEY')`.
 *      Nothing has ever written `MH_LICENSE_KEY`, so the fallback was not a
 *      fallback: it was the only branch that ever ran, and every scan
 *      dispatched under a fake licence.
 *   2. The pair was hardcoded to XAUUSD with a "defaulting for demo" note.
 *      Scan a EUR/USD chart, press execute, trade gold.
 *   3. It posted the broker `password` from the client. Credentials are not
 *      the app's to hold or forward; `metacopier-execute` does not even
 *      accept the field.
 *   4. It called `dispatch-signal`, the old MetaAPI path, rather than
 *      `metacopier-execute`.
 *
 * ## Dispatch shape
 *
 * `metacopier-execute` takes `{ license_key, pair, side, volume, sl, tp,
 * order_type, open_price, dry_run }` and enforces the symbol plan written by
 * `sync-symbol-config` — it can refuse with SYMBOL_NOT_LICENSED,
 * SYMBOL_DISABLED or POSITION_CAP. One call opens ONE position.
 */

import { supabase } from '../utils/supabaseClient.ts'
import { secStorage } from '../utils/secStorage.js'
import { getDeviceId } from '../utils/deviceId.js'
import { authState } from './authService.js'

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL
const SUPABASE_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY

/* ── Scan ────────────────────────────────────────────────────────────
   `analyze-chart` has `verify_jwt: true`, so the anon key has to travel on
   the call. The old code sent no auth header at all.

   ## The identity has to be in the BODY, not the header

   The anon key satisfies `verify_jwt` and nothing else. The scanner is a
   separate R349 purchase, and `analyze-chart` checks that entitlement itself
   — deliberately, since "the only credential the client holds is the anon
   key, which ships inside the APK, so a gate enforced only in the UI is a
   gate anyone can walk around."

   It reads the identity from `body.email` and `body.android_id ?? deviceId`.
   This call sent neither, so **every scan from iOS returned 401 NO_IDENTITY**
   — "Sign in with the email you bought the scanner with." — no matter who
   was signed in or what they had paid for. Confirmed against the live
   endpoint with a real MT5 screenshot.

   `android_id` is the field name Android happens to use and the server
   accepts either; `deviceId` is sent because this is not an Android handset
   and naming it otherwise would be a lie that the next reader has to
   untangle. */
export async function scanChart(imageBase64, mode, strategy) {
  const res = await fetch(`${SUPABASE_URL}/functions/v1/analyze-chart`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: SUPABASE_KEY,
      Authorization: `Bearer ${SUPABASE_KEY}`,
    },
    body: JSON.stringify({
      imageBase64,
      trading_mode: mode || 'day',
      /* Which school of reading produces the setup. `analyze-chart` keys
         STRATEGY_BRIEFS off this and frames the whole read with it.

         This call never sent the field, so the server fell through to its
         `?? STRATEGY_BRIEFS.SMC` default and **every scan this app has ever
         run was a Smart Money read** — including the ones a user picked Price
         Action, CRT or Breakout for on the other handset. The four briefs have
         been live server-side the whole time; iOS simply could not reach
         three of them. */
      strategy: String(strategy || 'SMC').toUpperCase(),
      email: authState.email || '',
      deviceId: getDeviceId(),
    }),
  })

  if (!res.ok) {
    /* The server's own words. It distinguishes "not signed in" from "you have
       not bought the scanner" from "your quota is spent", and those have three
       different fixes — collapsing them into one generic is how a user who
       needs to sign in is told to buy something they already own. */
    const body = await res.json().catch(() => null)
    if (body?.error) {
      const err = new Error(String(body.error))
      err.code = body.code
      throw err
    }
    const text = await res.text().catch(() => '')
    throw new Error(text ? `Analysis refused: ${text.slice(0, 160)}` : 'Could not reach the analysis service.')
  }

  const data = await res.json()

  /* The model is allowed to say the chart is unreadable, and that answer is
     more useful than a confident reading of a screenshot it could not parse.
     Surfaced rather than swallowed. */
  if (data && data.chart_readable === false) {
    const why = data.unreadable_reason || 'The chart could not be read.'
    const err = new Error(why)
    err.unreadable = true
    throw err
  }

  return data
}

/* ── Dispatch ────────────────────────────────────────────────────────
   Codes come back from the executor; the message beside them is the
   server's own where it sent one. MetaCopier returns plain-English broker
   refusals ("market closed", "not enough money") and overwriting those with
   a generic is how a user ends up unable to tell a closed market from a
   rejected licence. */
const CODE_HINTS = {
  NO_ACCOUNT_LINKED: 'No trading account is linked to this licence yet.',
  SYMBOL_NOT_LICENSED: 'This symbol is not on your robot’s allowance.',
  SYMBOL_DISABLED: 'You turned this symbol off in Trading Symbols.',
  POSITION_CAP: 'You are already at your open-trade limit for this symbol.',
  INVALID_STOPS: 'The stop and target are not on the same price scale.',
  BAD_VOLUME: 'That lot size is below the broker minimum.',
  LICENCE_EXPIRED: 'This licence has expired.',
  LICENCE_INACTIVE: 'This licence is not active.',
  LICENCE_UNKNOWN: 'This licence is not recognised.',
  MISSING_ENTRY_PRICE: 'A pending order needs an entry price.',
  ENTRY_OUTSIDE_BRACKET: 'The entry price sits outside the stop and target.',
}

function describe(data) {
  if (!data) return 'No response from the executor.'
  if (data.error) return String(data.error)
  if (data.code && CODE_HINTS[data.code]) return CODE_HINTS[data.code]
  return data.code ? `Refused (${data.code}).` : 'Refused.'
}

/**
 * One order.
 *
 * `pair` is the caller's — taken from the reading or the user's own
 * selection, never a constant. `dry_run` asks the executor to validate
 * everything (licence, allowance, cap, stops, symbol resolution) without
 * placing anything.
 */
export async function placeOrder({ pair, side, volume, sl, tp, entry, orderType, dryRun }) {
  const licenseKey = secStorage.getItem('LICENSE_KEY')
  if (!licenseKey) return { ok: false, message: 'No licence key on this device.' }
  if (!pair) return { ok: false, message: 'No symbol to trade — the chart did not name one.' }

  const body = {
    license_key: licenseKey,
    pair,
    side: String(side || '').toUpperCase(),
    volume: Number(volume),
    sl: Number(sl),
    tp: Number(tp),
    dry_run: !!dryRun,
  }
  /* Only sent for pending orders. A MARKET order with an open_price is what
     produces MISSING_ENTRY_PRICE / ENTRY_OUTSIDE_BRACKET confusion. */
  if (orderType && orderType !== 'MARKET') {
    body.order_type = orderType
    body.open_price = Number(entry)
  }

  try {
    const { data, error } = await supabase.functions.invoke('metacopier-execute', { body })
    if (error) throw error
    if (!data || data.success !== true) {
      return { ok: false, code: data && data.code, message: describe(data) }
    }
    return { ok: true, data }
  } catch (err) {
    return { ok: false, message: err?.message || 'Could not reach the executor.' }
  }
}

/**
 * Preflight. Runs the FIRST leg as `dry_run` so the licence, allowance,
 * position cap, symbol resolution and stop sanity are all checked by the
 * server before anything real is sent.
 *
 * Worth the extra round trip: the alternative on a ladder is discovering at
 * leg 2 that the symbol was disabled, having already opened leg 1.
 */
export async function preflight(plan) {
  const first = plan.legs[0]
  return placeOrder({
    pair: plan.pair,
    side: plan.side,
    volume: first.lot,
    sl: plan.sl,
    tp: first.price,
    entry: plan.entry,
    orderType: plan.orderType,
    dryRun: true,
  })
}

/**
 * Send the plan.
 *
 * `mode` is the user's explicit choice, never inferred:
 *   'single' — one order, the full lot, targeting TP1.
 *   'ladder' — one order per funded leg, sequentially.
 *
 * ## Why sequential, and why it stops on failure
 *
 * Each leg is a separate position at the broker. Firing them in parallel
 * would race the executor's own position-cap check, so a cap of 2 could
 * admit 3. Sequential also means a mid-ladder refusal leaves a KNOWN state:
 * legs before it are open, legs after it were never sent. The result reports
 * exactly which, because a partially-filled ladder the user cannot see is
 * worse than one that failed outright.
 */
export async function sendPlan(plan, mode, onProgress) {
  const legs = mode === 'ladder'
    ? plan.legs.filter(l => l.lot > 0)
    : [{ ...plan.legs[0], lot: plan.totalLot }]

  if (!legs.length) {
    return { ok: false, placed: [], failed: [], message: 'Every slice rounds below the 0.01 minimum. Raise the lot or use Single.' }
  }

  const placed = []
  const failed = []

  for (let i = 0; i < legs.length; i++) {
    const leg = legs[i]
    if (onProgress) onProgress({ index: i, total: legs.length, level: leg.level })

    const res = await placeOrder({
      pair: plan.pair,
      side: plan.side,
      volume: leg.lot,
      sl: plan.sl,
      tp: leg.price,
      entry: plan.entry,
      orderType: plan.orderType,
    })

    if (res.ok) {
      placed.push({ level: leg.level, lot: leg.lot, price: leg.price })
    } else {
      failed.push({ level: leg.level, message: res.message, code: res.code })
      break   // stop, so the surviving state is describable
    }
  }

  const ok = failed.length === 0
  let message
  if (ok) {
    message = placed.length === 1
      ? `Order sent · ${placed[0].lot.toFixed(2)} lots on ${plan.pair}.`
      : `${placed.length} orders sent on ${plan.pair}.`
  } else if (placed.length) {
    message = `${placed.length} of ${legs.length} sent, then ${failed[0].level} was refused — ${failed[0].message} The open legs are live; the rest were not sent.`
  } else {
    message = failed[0].message
  }

  return { ok, placed, failed, message }
}
