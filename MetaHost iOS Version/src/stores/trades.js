/**
 * Live trades and account performance (display state).
 *
 * ## Why this is not derived from anything already in the app
 *
 * Nothing in NovaHost knew what positions existed. `trade_logs` has zero rows
 * and no writer anywhere in the codebase; `signal_deliveries` records that a
 * signal was handed out, which is a claim about DELIVERY and not about a
 * position; `broker-execute` returns the moment the broker accepts an
 * order and never mentions it again.
 *
 * So this store is fed entirely by the broker, through `broker-positions` and
 * `broker-performance`. That is the only place the truth exists.
 *
 * ## Polling, not realtime
 *
 * There is no push channel for broker state — the broker bridge does not offer one
 * and Postgres has nothing to notify on. Open positions are polled while the
 * screen is visible and stopped when it is not, because a phone in a pocket
 * hitting the broker every few seconds costs battery for a screen nobody is
 * reading.
 *
 * History is NOT polled. The broker bridge documents it as lagging live trading by
 * up to three minutes, so a fast poll cannot make it fresher — it is loaded
 * on open and on pull-to-refresh.
 */

import { reactive, computed } from 'vue'
import { loadPositions, loadHistory } from '../services/brokerService.js'

/** Open positions move; a phone watching a fill wants this tight-ish. */
const POSITIONS_POLL_MS = 20_000

export const trades = reactive({
  /** Open and pending, newest first is NOT imposed — broker order is kept. */
  positions: [],
  openCount: 0,
  pendingCount: 0,
  /** Floating net P/L across everything open, after costs. Null when flat. */
  floatingNet: null,

  balance: null,
  equity: null,
  /** Never assume USD. These accounts are ZAR. Null means genuinely unknown. */
  currency: null,

  /** Closed trades, newest first. */
  closed: [],
  /** Cumulative net profit, oldest first. */
  curve: [],
  /** False when no balance anchor existed; the curve is then profit, not equity. */
  anchored: false,
  stats: null,

  positionsLoading: false,
  historyLoading: false,
  /** Distinguished from "nothing open": an RLS or broker refusal and a flat
   *  account look identical in the data and could not be more different. */
  positionsError: null,
  historyError: null,
  loadedAt: null,
})

export const hasOpen = computed(() => trades.openCount > 0)
export const hasAnyPosition = computed(() => trades.positions.length > 0)

/** Signed, with the account's own currency. A figure without its unit is not
 *  a figure — R596 rendered as dollars is wrong by about eighteen times. */
export function money(v, currency) {
  if (v == null || !Number.isFinite(Number(v))) return '—'
  const n = Number(v)
  const cur = currency || trades.currency
  const body = Math.abs(n).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  const sign = n < 0 ? '-' : n > 0 ? '+' : ''
  return cur ? `${sign}${cur} ${body}` : `${sign}${body}`
}

/** Unsigned, for balances rather than results. */
export function amount(v, currency) {
  if (v == null || !Number.isFinite(Number(v))) return '—'
  const cur = currency || trades.currency
  const body = Number(v).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  return cur ? `${cur} ${body}` : body
}

/** 'up' | 'down' | 'flat' — drives colour, and flat is a real third answer. */
export function tone(v) {
  if (v == null || !Number.isFinite(Number(v))) return 'flat'
  return Number(v) > 0 ? 'up' : Number(v) < 0 ? 'down' : 'flat'
}

export async function refreshPositions() {
  trades.positionsLoading = true
  const res = await loadPositions()

  if (res.ok) {
    trades.positions = res.positions || []
    trades.openCount = res.open_count ?? 0
    trades.pendingCount = res.pending_count ?? 0
    trades.floatingNet = res.floating_net ?? null
    trades.balance = res.balance ?? trades.balance
    trades.equity = res.equity ?? trades.equity
    if (res.currency) trades.currency = res.currency
    trades.positionsError = null
    trades.loadedAt = Date.now()
  } else {
    /* The rows are NOT cleared on a failed poll. A network blip must not
       redraw the screen as "no open trades" — that is a statement about the
       user's money, and it would be a false one. The error is surfaced
       alongside whatever was last known to be true. */
    trades.positionsError = res.message || 'Could not read your open trades.'
  }

  trades.positionsLoading = false
  return res
}

export async function refreshHistory({ days = 90, limit = 50 } = {}) {
  trades.historyLoading = true
  const res = await loadHistory({ days, limit })

  if (res.ok) {
    trades.closed = res.closed || []
    trades.curve = res.curve || []
    trades.anchored = res.anchored !== false
    trades.stats = res.stats || null
    if (res.currency) trades.currency = res.currency
    if (res.balance != null) trades.balance = res.balance
    trades.historyError = null
  } else {
    trades.historyError = res.message || 'Could not read your trade history.'
  }

  trades.historyLoading = false
  return res
}

/* ── Polling ─────────────────────────────────────────────────────────
   Reference-counted, because more than one surface wants open positions at
   once: the Trades screen and the Home widget. Without the count, the first
   of the two to unmount would stop the poll for the other. */
let timer = null
let subscribers = 0

function onVisible() {
  if (document.visibilityState === 'visible') refreshPositions()
}

export function startPositionsPolling() {
  subscribers++
  if (timer) return
  refreshPositions()
  timer = setInterval(() => {
    /* A backgrounded tab has its timers throttled anyway, but an occluded
       page that still fires costs the broker a request for a screen nobody
       can see. Skipped explicitly. */
    if (document.visibilityState === 'visible') refreshPositions()
  }, POSITIONS_POLL_MS)
  document.addEventListener('visibilitychange', onVisible)
}

export function stopPositionsPolling() {
  subscribers = Math.max(0, subscribers - 1)
  if (subscribers > 0) return
  if (timer) clearInterval(timer)
  timer = null
  document.removeEventListener('visibilitychange', onVisible)
}
