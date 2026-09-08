/**
 * Reads the mentor signal feed for display.
 *
 * A plain select, NOT `claim-signals`. Claiming is delivery: it writes
 * a `signal_deliveries` row whose unique constraint is what prevents a
 * signal being handed out twice. Rendering the feed must never consume
 * a signal, or opening Home on a second device would silently burn the
 * signal that device was supposed to trade.
 */

import { supabase } from '../utils/supabaseClient.ts'
import { signals } from '../stores/signals.js'

const DEFAULT_LIMIT = 12

export async function loadRecentSignals({ limit = DEFAULT_LIMIT } = {}) {
  signals.loading = true
  signals.error = null

  try {
    const { data, error } = await supabase
      .from('signals')
      .select('id, pair, side, type, price, sl, tp, lot, status, order_type, created_at')
      .order('created_at', { ascending: false })
      .limit(limit)

    if (error) throw error

    signals.rows = data || []
    signals.loadedAt = Date.now()
  } catch (err) {
    console.error('loadRecentSignals:', err)
    /* Distinguished from "no signals yet" on purpose. An RLS refusal
       and an empty mentor feed look identical in the data and could not
       be more different to the user. */
    signals.error = err?.message || 'Could not reach the signal feed'
    signals.rows = []
  } finally {
    signals.loading = false
  }
}
