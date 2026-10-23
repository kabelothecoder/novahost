/**
 * Per-account symbol setup: which of the robot's symbols an account trades,
 * under what name its broker lists them, and how much each may risk.
 *
 * Backs the three-step setup on the Broker Terminal screen
 * (components/terminal/symbols). Two adapters behind one surface:
 *
 *   REAL  The account linked to this licence today. Names come from
 *         `broker-symbols` (the broker's own Market Watch, matched to the
 *         licence's allowance), the plan is saved through `sync-symbol-config`
 *         (the executor's ceiling), and the test step asks `broker-quote`
 *         to price each symbol under the name that was saved.
 *   MOCK  Every other account, until the multi-account schema exists
 *         (design-briefs/connection-area-plan.md, section 3), and the whole
 *         screen in dev with `?symbols=mock`. Nothing it does leaves the device.
 *
 * ## Why the lot is sent explicitly
 *
 * `broker-execute` treats a symbol's `lot` as a hard per-order CEILING,
 * except when `smart_lot` is on, when it uses one global `smart_lot_size` for
 * every symbol. A per-account, per-symbol size can only be expressed the first
 * way, so this saves `smart_lot: false` with the exact lot the screen showed.
 * What the user approved is what the robot is held to.
 *
 * ## The money figures are estimates, and say so
 *
 * "At risk" uses the same simplification the Trading Symbols calculator always
 * has: a 20-pip stop at $10 per lot per pip, converted to the account's
 * currency. It is a sizing guide, not a broker-accurate loss.
 */

import { secStorage } from '../utils/secStorage.js'
import { supabase } from '../utils/supabaseClient.ts'
import { loadLinkState, loadAccount, loadQuote } from './brokerService.js'
import { loadPlan, savePlan, syncPlan, normaliseSymbol } from './symbolPlanService.js'
import { convert } from './fxRates.js'

/* ── Shared vocabulary ─────────────────────────────────────────────── */

export const PRESETS = [
  { id: 'low',    name: 'Low',    pct: 1, trades: 1 },
  { id: 'medium', name: 'Medium', pct: 2, trades: 2 },
  { id: 'high',   name: 'High',   pct: 5, trades: 3 },
]

/** Friendly names for the rows. Display only — the canonical symbol is what
 *  is saved, and anything not listed here shows as itself. */
const FRIENDLY = {
  XAUUSD: 'Gold', XAGUSD: 'Silver', NAS100: 'Nasdaq', US30: 'US30', SPX500: 'S&P 500',
  GER40: 'DAX', UK100: 'FTSE', JP225: 'Nikkei', USOIL: 'Oil (WTI)', UKOIL: 'Oil (Brent)', BTCUSD: 'Bitcoin',
}
export const friendly = (sym) => FRIENDLY[sym] || sym

/** Dollars at risk per 1.00 lot: a 20-pip stop at $10 per pip. */
const USD_PER_LOT = 200

export function moneyPerLot(currency, fxTable) {
  const v = convert(USD_PER_LOT, 'USD', currency || 'USD', fxTable)
  return Number.isFinite(v) && v > 0 ? v : USD_PER_LOT
}

export const PRESET_KEY = 'SYMBOL_PRESET'

/* ── Real adapter ──────────────────────────────────────────────────── */

async function bodyOf(error) {
  try { return await error?.context?.json?.() } catch { return null }
}

/**
 * Turn one `broker-symbols` mapping into a row the screen can draw.
 *
 *   match  one name, settled: either saved and still listed, or an exact /
 *          suffix match from the broker's own list
 *   pick   the broker lists something plausible, but the user must choose
 *   none   nothing on the broker resembles it
 */
function resolveRow(m, brokerSymbols) {
  const sym = normaliseSymbol(m.symbol)
  const stem = sym.slice(0, 3)
  const near = brokerSymbols
    .filter((n) => normaliseSymbol(n).includes(sym) || normaliseSymbol(n).startsWith(stem))
    .slice(0, 4)

  if (m.current && m.current_is_valid) return { id: sym, kind: 'match', match: m.current, candidates: [] }
  const sure = m.confidence === 'exact' || m.confidence === 'suffix'
  if (m.suggested && sure) return { id: sym, kind: 'match', match: m.suggested, candidates: [] }

  const candidates = [...new Set([m.suggested, ...near].filter(Boolean))]
  if (candidates.length) return { id: sym, kind: 'pick', match: null, candidates, suggested: m.suggested || null }
  return { id: sym, kind: 'none', match: null, candidates: [] }
}

/**
 * The linked account and its symbol rows.
 * Returns `{ ok, account, rows, brokerSymbols }` or `{ ok:false, code, message }`.
 */
export async function loadRealAccount() {
  const link = await loadLinkState()
  if (link.linked !== true) return { ok: false, code: 'NOT_LINKED', message: 'No broker account is linked.' }

  const acct = await loadAccount()
  const account = {
    id: 'linked',
    real: true,
    broker: link.server ? String(link.server).split(/[-_ ]/)[0] : 'Your broker',
    server: link.server || '',
    login: null,
    balance: acct.ok ? Number(acct.balance) || 0 : 0,
    currency: acct.ok && acct.currency ? acct.currency : 'USD',
    connected: !link.connecting && (acct.ok ? acct.connected !== false : false),
  }

  const key = secStorage.getItem('LICENSE_KEY')
  try {
    const { data, error } = await supabase.functions.invoke('broker-symbols', { body: { license_key: key } })
    if (error) {
      const b = await bodyOf(error)
      return { ok: false, account, code: b?.code || 'LOAD_FAILED', message: b?.error || 'Could not load your broker’s symbols.' }
    }
    if (!data?.success) return { ok: false, account, code: data?.code, message: data?.error || 'Could not load your broker’s symbols.' }
    const list = Array.isArray(data.broker_symbols) ? data.broker_symbols.map(String) : []
    const rows = (data.mappings || []).map((m) => resolveRow(m, list))
    return { ok: true, account, rows, brokerSymbols: list }
  } catch (e) {
    return { ok: false, account, code: 'LOAD_FAILED', message: e?.message || 'Could not reach the server.' }
  }
}

/** What this device last saved for the linked account, shaped for the setup. */
export function savedRealConfig() {
  const plan = loadPlan()
  const preset = secStorage.getItem(PRESET_KEY) || 'medium'
  const on = {}, picks = {}, lots = {}, trades = {}
  for (const [sym, r] of Object.entries(plan)) {
    on[sym] = r.enabled
    if (r.broker_symbol) picks[sym] = r.broker_symbol
    if (r.enabled && !r.smart_lot && r.lot > 0) lots[sym] = r.lot
    if (r.enabled && r.max_trades > 0) trades[sym] = r.max_trades
  }
  return { known: Object.keys(plan).length > 0, on, picks, lots, trades, preset }
}

/**
 * Save the linked account's setup locally and to the executor.
 * `rows` = [{ symbol, brokerSymbol, enabled, lot, trades }].
 */
export async function saveReal({ rows, preset, account }) {
  const plan = loadPlan()
  for (const r of rows) {
    plan[r.symbol] = {
      enabled: r.enabled,
      lot: r.lot,
      max_trades: Math.max(1, r.trades),
      smart_lot: false,
      broker_symbol: r.brokerSymbol || null,
    }
  }
  savePlan(plan)
  secStorage.setItem(PRESET_KEY, preset.id)
  const res = await syncPlan(plan, {
    balance: account.balance,
    currency: account.currency,
    riskPercent: preset.pct,
    riskTrades: rows.filter((r) => r.enabled).reduce((n, r) => n + r.trades, 0) || 1,
    smartLot: 0,
  })
  return res.ok ? { ok: true } : { ok: false, message: res.error || 'Could not save to the server.' }
}

/**
 * Price one symbol under its SAVED name. `broker-quote` tries the saved name
 * first and falls back to aliases, so success under a DIFFERENT name means the
 * saved one is wrong even though something worked; that is a problem to show,
 * not a pass.
 */
export async function testReal(symbol, expectedName) {
  const q = await loadQuote(symbol)
  if (!q.ok) return { ok: false, message: q.message || 'The broker did not quote it.' }
  if (q.tradeable === false) return { ok: false, message: `Trading is disabled for ${q.broker_symbol} on this account.`, works: null }
  if (expectedName && q.broker_symbol && q.broker_symbol !== expectedName) {
    return { ok: false, message: `${expectedName} was not accepted.`, works: q.broker_symbol }
  }
  return { ok: true }
}

/* ── Mock adapter ──────────────────────────────────────────────────── */

const MOCK = [
  { id: 'm-exness', broker: 'Exness', login: '4821', balance: 1000, currency: 'ZAR', connected: true,
    rows: [
      { id: 'XAUUSD', kind: 'match', match: 'XAUUSD.ifx' },
      { id: 'US30', kind: 'match', match: 'US30.ifx' },
      { id: 'NAS100', kind: 'pick', candidates: ['USTEC', '.USTECH.', 'NAS100.ifx'], ok: 'USTEC' },
      { id: 'EURUSD', kind: 'match', match: 'EURUSD.ifx' },
    ] },
  { id: 'm-deriv', broker: 'Deriv', login: '0937', balance: 200, currency: 'USD', connected: true, maxPos: 5, maxLot: 0.5,
    rows: [
      { id: 'XAUUSD', kind: 'pick', candidates: ['XAUUSD', 'XAUUSD.s'], ok: 'XAUUSD' },
      { id: 'US30', kind: 'none', note: 'Deriv’s MT5 account doesn’t list US30, so your robot skips it here.' },
      { id: 'NAS100', kind: 'match', match: 'NAS100' },
      { id: 'EURUSD', kind: 'match', match: 'EURUSD' },
    ] },
  { id: 'm-hfm', broker: 'HFM', login: '5512', balance: 2500, currency: 'ZAR', connected: false,
    rows: [
      { id: 'XAUUSD', kind: 'match', match: 'GOLD' },
      { id: 'US30', kind: 'match', match: 'US30' },
      { id: 'NAS100', kind: 'match', match: 'USA100' },
      { id: 'EURUSD', kind: 'match', match: 'EURUSD' },
    ] },
]

export function mockAccounts() {
  return MOCK.map((a) => ({ ...a, real: false, rows: a.rows.map((r) => ({ candidates: [], match: null, ...r })) }))
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms))

/** A pick is accepted by the mock broker only if it is the name it really lists. */
export async function testMock(row, name) {
  await wait(650)
  if (row.kind === 'match') return { ok: true }
  return name === row.ok ? { ok: true } : { ok: false, message: `${name} was not accepted.`, works: row.ok }
}

export async function reconnectMock() { await wait(1400); return { ok: true } }
