/**
 * Fleet telemetry — what the Fleet Status widget and the Signal Feed strip
 * actually report.
 *
 * ## Why this polls `license-status` and NOT `claim-signals`
 *
 * `claim-signals` looks like the obvious choice: it already writes
 * `device_activations.last_seen_at`, which is the only server-side
 * liveness record that exists. It is also the wrong choice here, and
 * dangerously so.
 *
 * Claiming is CONSUMPTION. The claim is an INSERT into `signal_deliveries`
 * with a unique constraint on (signal_id, license_id), and that constraint
 * is deliberately what stops two handsets on one licence both trading the
 * same call — they point at the same broker account, so two positions
 * would be opened for one signal. A device that claims a signal and does
 * not execute it does not "miss" the trade: it takes the trade away from
 * the device that would have made it, permanently and silently.
 *
 * This app does not execute trades. Polling `claim-signals` from here
 * would therefore destroy every signal the user's Android handset was
 * supposed to act on. So it doesn't.
 *
 * `license-status` is the read-only sibling — its own docstring notes it
 * "binds nothing, so the ignition may call it on every START without side
 * effects". It gives us the round trip (ping), the broker link state, the
 * licence's health, and the current symbol allowance.
 *
 * ## What is therefore NOT reported
 *
 * `beat` and `bots` need `last_seen_at` written and `device_activations`
 * counted, and neither is reachable without either executing signals or a
 * new server endpoint. Both stay null, and the UI shows an em dash rather
 * than inventing a reading. See the Fleet Status widget.
 */

import { supabase } from '../utils/supabaseClient.ts'
import { secStorage } from '../utils/secStorage.js'
import { getDeviceId } from '../utils/deviceId.js'
import { session, pushEvent } from '../stores/session.js'
import { robot } from '../stores/robot.js'

/** Read-only and cheap, but still a network round trip on a phone. */
const POLL_MS = 45_000

let timer = null
let inFlight = false

function sameSymbols(a, b) {
  if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) return false
  return a.every((v, i) => v === b[i])
}

/**
 * One poll. Safe to call directly (the Fleet widget's pull-to-refresh could
 * use it) and never overlaps itself — a slow network must not queue up
 * requests behind a phone that has been backgrounded.
 */
export async function pollFleet() {
  if (inFlight) return
  const licenseKey = secStorage.getItem('LICENSE_KEY')

  /* Email-door users have no licence key at all — entitlement came from
     `subscriptions`, not `licenses`. There is nothing to ask about, so the
     figures stay unknown rather than showing a failure they cannot fix. */
  if (!licenseKey) return

  inFlight = true
  const startedAt = performance.now()

  try {
    const { data, error } = await supabase.functions.invoke('license-status', {
      body: { license_key: licenseKey, device_id: getDeviceId() },
    })

    /* Measured around the whole invoke, including the failure path: a call
       that took four seconds and then errored is still information about
       the link, and blanking the ping on error would hide exactly the
       condition the user is looking at this widget to understand. */
    session.pingMs = Math.round(performance.now() - startedAt)

    if (error) throw error
    if (!data?.success) throw new Error(data?.error || 'Licence status unavailable')

    applyStatus(data)
  } catch (err) {
    console.warn('[fleet] poll failed:', err)
    // Left as-is rather than forced false: one failed poll is a network
    // blip, not proof the broker disconnected. TerminalView owns the
    // authoritative connect/disconnect transitions.
    session.pingMs = session.pingMs ?? null
  } finally {
    inFlight = false
  }
}

function applyStatus(data) {
  const wasLinked = session.brokerConnected

  /* `linked` means a trading account is attached to the licence.
     `metacopier_status` refines it to whether the broker session is
     actually up — absent on licences linked before that was recorded, and
     the documented reading of absent is "assume connected". */
  const linked = Boolean(data.linked)
  const status = data.metacopier_status
  session.brokerConnected = linked && status !== 'connecting'
  session.brokerServer = data.broker_server || null

  if (session.brokerConnected !== wasLinked) {
    pushEvent(
      session.brokerConnected ? 'Broker link up' : 'Broker link down',
      session.brokerConnected ? 'ok' : 'error',
    )
  }

  /* The allowance travels on every status call for a reason: it was
     written once at activation and never refreshed, so a mentor who
     edited their robot's symbols changed everything except the phones in
     the field. Rewritten only on a real change, so the persistence watch
     in accent.js does not fire on every poll. */
  const allowed = Array.isArray(data.allowed_symbols) ? data.allowed_symbols.map(String) : []
  if (allowed.length && !sameSymbols(allowed, robot.allowedSymbols)) {
    robot.allowedSymbols = allowed
    pushEvent(`Symbols updated · ${allowed.length}`, 'info')
  }
}

export function startFleetPolling() {
  if (timer) return
  pollFleet()
  timer = setInterval(pollFleet, POLL_MS)

  /* A backgrounded tab has its timers throttled to minutes or stopped
     outright, so the first thing a returning user would see is a stale
     ping. Refresh on the way back in. */
  document.addEventListener('visibilitychange', onVisible)
}

export function stopFleetPolling() {
  if (timer) clearInterval(timer)
  timer = null
  document.removeEventListener('visibilitychange', onVisible)
}

function onVisible() {
  if (document.visibilityState === 'visible') pollFleet()
}
