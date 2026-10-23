/**
 * Reads the mentor signal feed for display.
 *
 * ## Why this is not a table read any more
 *
 * It used to be `supabase.from('signals').select(...)` with the anon key, and
 * that had never returned a row. The SELECT policy on `signals` is
 *
 *     licenses.owner_id = auth.uid()
 *
 * and this app authenticates by licence key — there is no `auth.users` row for
 * `auth.uid()` to match. RLS answers a refusal with an empty set rather than an
 * error, so `signals.error` stayed null, the list stayed empty, and the screen
 * said "No signals from your mentor yet" against a table holding 31 of them.
 * Nothing looked broken, which is why it survived so long.
 *
 * The policy was deliberately NOT widened. The anon key ships inside this
 * bundle, so an anon SELECT on `signals` would publish every mentor's calls to
 * anyone who opens devtools. The read moves server-side to `signal-feed`, which
 * holds the service role and scopes by licence key — the same shape as
 * `license-status` and the `broker-*` family.
 *
 * ## Still not `claim-signals`
 *
 * Claiming is delivery: it writes a `signal_deliveries` row whose unique
 * constraint is what prevents a signal being handed out twice. Rendering the
 * feed must never consume a signal, or opening Home on a second device would
 * silently burn the signal that device was supposed to trade. `signal-feed`
 * writes nothing.
 */

import { supabase } from '../utils/supabaseClient.ts'
import { secStorage } from '../utils/secStorage.js'
import { signals } from '../stores/signals.js'

const DEFAULT_LIMIT = 12

export async function loadRecentSignals({ limit = DEFAULT_LIMIT } = {}) {
  const licenseKey = secStorage.getItem('LICENSE_KEY')
  if (!licenseKey) {
    /* Not an error state. Nothing reaches Home without a validated key, so
       this is only hit in the moment before one exists. */
    signals.rows = []
    signals.error = null
    return
  }

  signals.loading = true
  signals.error = null

  try {
    const { data, error } = await supabase.functions.invoke('signal-feed', {
      body: { license_key: licenseKey, limit },
    })

    if (error) throw error
    if (!data || data.success !== true) {
      throw new Error(data?.error || 'The signal feed refused.')
    }

    signals.rows = data.signals || []
    signals.loadedAt = Date.now()
  } catch (err) {
    console.error('loadRecentSignals:', err)
    /* Distinguished from "no signals yet" on purpose. A failed read and an
       empty mentor feed look identical in the data and could not be more
       different to the user. */
    signals.error = err?.message || 'Could not reach the signal feed'
    signals.rows = []
  } finally {
    signals.loading = false
  }
}
