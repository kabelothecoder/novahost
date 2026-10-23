/**
 * NovaHost — mentor signal feed (display state).
 *
 * Read-only, and deliberately separate from delivery. Delivery is a
 * claim-based poll: `claim-signals` writes a `signal_deliveries` row
 * whose unique constraint IS the claim, and that is what stops two
 * handsets on one licence both trading the same signal. Rendering a
 * signal here must never claim it — the Signal Feed layout is a
 * window onto the feed, not a participant in it.
 */

import { reactive, computed } from 'vue'

export const signals = reactive({
  /** Newest first. */
  rows: [],
  loading: false,
  /** Set when the read failed, so the empty state can say why rather
   *  than implying the mentor has sent nothing. */
  error: null,
  loadedAt: null,
})

export const hasSignals = computed(() => signals.rows.length > 0)

/** "2m", "18m", "1h", "3d" — the doc's compact age column. */
export function age(ts) {
  if (!ts) return ''
  const s = Math.max(0, (Date.now() - new Date(ts).getTime()) / 1000)
  if (s < 60) return `${Math.floor(s)}s`
  const m = s / 60
  if (m < 60) return `${Math.floor(m)}m`
  const h = m / 60
  if (h < 24) return `${Math.floor(h)}h`
  return `${Math.floor(h / 24)}d`
}

/**
 * Direction for display. `side` is the newer column and `type` the
 * older one; rows exist with only the latter, so both are consulted
 * before giving up rather than rendering an empty chip.
 */
export function direction(row) {
  const raw = (row.side || row.type || '').toString().toUpperCase()
  if (raw.includes('BUY'))  return 'BUY'
  if (raw.includes('SELL')) return 'SELL'
  return raw || '—'
}

/**
 * Closed vs live. The doc draws a third row reading
 * "CLOSED · Target hit · +42 pips", but the P/L half of that has no
 * source — `trade_logs` is empty and nothing on the client joins to it
 * — so only the closed/open state is reported and the outcome is left
 * out rather than invented.
 */
export function isClosed(row) {
  const s = (row.status || '').toLowerCase()
  return s === 'closed' || s === 'filled' || s === 'cancelled' || s === 'expired'
}

/** MARKET orders have no entry price: they fill at whatever the
 *  subscriber's broker shows, which is why `price` is null on them. */
export function entryLabel(row) {
  if (row.price != null) return String(row.price)
  return row.order_type === 'MARKET' || !row.order_type ? 'Market' : '—'
}
