/**
 * State and rules for the per-account symbol setup on the Broker Terminal.
 *
 * One instance per mounted setup. Each account keeps its own config — which
 * symbols are on, which broker name was picked, the risk preset, any
 * per-symbol lot and trade overrides — so switching account never carries one
 * broker's names or sizes onto another.
 *
 * The 10% / 25% thresholds come from the design: above 10% of the balance at
 * risk is a warning, above 25% blocks saving until it is lowered. They apply
 * to the estimate, which is labelled as one.
 */

import { reactive, computed } from 'vue'
import * as svc from '../../../services/symbolSetupService.js'
import { loadRates } from '../../../services/fxRates.js'

const WARN = 0.10
const BLOCK = 0.25
const MAX_TRADES = 10

const blankCfg = (o = {}) => ({
  on: {}, picks: {}, preset: 'medium', lots: {}, trades: {}, adv: false,
  phase: 'idle', res: {}, problems: {}, ready: false, trading: false, ...o,
})

export function useSymbolSetup() {
  const st = reactive({
    mode: 'real',
    order: [],
    accounts: {},
    rows: {},
    cfg: {},
    acc: null,
    screen: 'wizard',
    step: 1,
    sheet: null,
    celebrate: false,
    busy: '',          // spinner text for the CTA
    loadError: {},     // per account: symbol list could not be read
    saveError: '',
    fx: null,
    copyFrom: null,
    copyTo: null,
    booted: false,
  })

  /* ── Lookups ───────────────────────────────────────────────────── */
  const A = computed(() => st.accounts[st.acc] || null)
  const C = computed(() => st.cfg[st.acc] || blankCfg())
  const R = computed(() => st.rows[st.acc] || [])

  const presetOf = (c) => svc.PRESETS.find((p) => p.id === c.preset) || svc.PRESETS[1]
  const isCustom = (c) => Object.keys(c.lots).length > 0 || Object.keys(c.trades).length > 0

  /** The name an order would carry for this row, or null if none is settled. */
  function nameFor(row, c) {
    if (row.kind === 'match') return row.match
    if (row.kind === 'pick') return c.picks[row.id] || null
    return null
  }
  const usable = (row, c) => c.on[row.id] !== false && !!nameFor(row, c)
  const tradesFor = (row, c) => (c.trades[row.id] != null ? c.trades[row.id] : presetOf(c).trades)
  const mpl = (a) => svc.moneyPerLot(a.currency, st.fx)

  function lotFor(a, row, c) {
    if (c.lots[row.id] != null) return c.lots[row.id]
    let l = Math.max(1, Math.floor((a.balance * presetOf(c).pct) / 100 / mpl(a) * 100)) / 100
    if (a.maxLot) l = Math.min(l, a.maxLot)
    return l
  }
  const lotMoney = (a, row, c) => lotFor(a, row, c) * mpl(a)
  const comb = (a, row, c) => lotMoney(a, row, c) * tradesFor(row, c)
  const activeRows = (a, rows, c) => rows.filter((r) => usable(r, c) && tradesFor(r, c) > 0)
  const total = (a, rows, c) => activeRows(a, rows, c).reduce((t, r) => t + comb(a, r, c), 0)
  const sumTrades = (a, rows, c) => activeRows(a, rows, c).reduce((t, r) => t + tradesFor(r, c), 0)
  const readyCount = (a, rows, c) => activeRows(a, rows, c).filter((r) => c.res[r.id] === 'ok').length

  /** Any edit invalidates a finished test: the robot would trade something
   *  other than what was checked. */
  function touch(patch) {
    Object.assign(C.value, patch, { phase: 'idle', res: {}, problems: {}, ready: false })
    st.saveError = ''
  }

  /* ── Edits ─────────────────────────────────────────────────────── */
  const toggle = (row) => { const c = C.value; touch({ on: { ...c.on, [row.id]: c.on[row.id] === false } }) }
  const pick = (row, name) => touch({ picks: { ...C.value.picks, [row.id]: name } })
  const setPreset = (id) => touch({ preset: id, lots: {}, trades: {} })

  function setLot(row, v) {
    const a = A.value
    v = Math.round(v * 100) / 100
    v = Math.max(0.01, Math.min(a.maxLot || 100, v))
    touch({ lots: { ...C.value.lots, [row.id]: v } })
  }
  function setTrades(row, v) {
    v = Math.max(0, Math.min(MAX_TRADES, v))
    touch({ trades: { ...C.value.trades, [row.id]: v } })
  }

  /** The design's "Lower it for me": shave the most expensive symbol first,
   *  trades before lots, until the total is back under the warning line. */
  function lowerIt() {
    const a = A.value, rows = R.value, c0 = C.value
    const c = { ...c0, lots: {}, trades: {} }
    rows.forEach((r) => { c.lots[r.id] = lotFor(a, r, c0); c.trades[r.id] = tradesFor(r, c0) })
    for (let k = 0; k < 400; k++) {
      if (total(a, rows, c) <= a.balance * WARN) break
      const act = activeRows(a, rows, c).filter((r) => c.trades[r.id] > 1 || c.lots[r.id] > 0.01)
      if (!act.length) break
      let best = act[0]
      act.forEach((r) => { if (comb(a, r, c) > comb(a, best, c)) best = r })
      if (c.trades[best.id] > 1) c.trades[best.id]--
      else c.lots[best.id] = Math.round((c.lots[best.id] - 0.01) * 100) / 100
    }
    touch({ lots: c.lots, trades: c.trades, adv: true })
  }

  /* ── Derived for the screen ────────────────────────────────────── */
  const pickNeeded = computed(() =>
    R.value.find((r) => r.kind === 'pick' && C.value.on[r.id] !== false && !C.value.picks[r.id]) || null)

  const riskTotals = computed(() => {
    const a = A.value
    if (!a) return { total: 0, pct: 0, trades: 0, level: 'ok' }
    const t = total(a, R.value, C.value)
    const pct = a.balance > 0 ? t / a.balance : 0
    return { total: t, pct, trades: sumTrades(a, R.value, C.value), level: pct > BLOCK ? 'block' : pct > WARN ? 'warn' : 'ok' }
  })

  function statusOf(id) {
    const a = st.accounts[id], c = st.cfg[id]
    if (!a || !c) return { text: 'Needs setup', tone: 'amber' }
    if (!a.connected) return { text: 'Disconnected', tone: 'red' }
    if (c.ready && c.trading) return { text: 'Trading', tone: 'green' }
    if (c.ready) return { text: 'Configured', tone: 'green' }
    return { text: 'Needs setup', tone: 'amber' }
  }

  /* ── Boot ──────────────────────────────────────────────────────── */
  async function boot({ mock = false } = {}) {
    loadRates().then((t) => { st.fx = t }).catch(() => {})
    if (mock) {
      st.mode = 'mock'
      for (const a of svc.mockAccounts()) {
        st.order.push(a.id)
        st.accounts[a.id] = a
        st.rows[a.id] = a.rows
        const done = a.id === 'm-exness'
        st.cfg[a.id] = blankCfg(done
          ? { picks: { NAS100: 'USTEC' }, phase: 'done', ready: true, trading: true, res: { XAUUSD: 'ok', US30: 'ok', NAS100: 'ok', EURUSD: 'ok' } }
          : a.id === 'm-hfm' ? { preset: 'low', phase: 'done', ready: true, trading: true, res: { XAUUSD: 'ok', US30: 'ok', NAS100: 'ok', EURUSD: 'ok' } } : {})
      }
      st.acc = 'm-deriv'
      st.booted = true
      return
    }

    st.mode = 'real'
    const res = await svc.loadRealAccount()
    if (res.account) {
      const a = res.account
      st.order = [a.id]
      st.accounts[a.id] = a
      st.acc = a.id
      const saved = svc.savedRealConfig()
      st.cfg[a.id] = blankCfg({
        preset: saved.preset, picks: saved.picks, lots: saved.lots, trades: saved.trades,
        on: saved.known ? saved.on : {},
        ready: saved.known, trading: saved.known,
      })
      if (res.ok) st.rows[a.id] = res.rows
      else { st.rows[a.id] = []; st.loadError[a.id] = res.message }
    }
    st.booted = true
  }

  async function retryLoad() {
    st.busy = `Loading ${A.value.broker} symbols…`
    if (st.mode === 'mock') { await new Promise((r) => setTimeout(r, 1200)); delete st.loadError[st.acc] }
    else {
      const res = await svc.loadRealAccount()
      if (res.ok) { st.rows[st.acc] = res.rows; delete st.loadError[st.acc] }
      else st.loadError[st.acc] = res.message
    }
    st.busy = ''
  }

  async function reconnect() {
    st.busy = `Reconnecting to ${A.value.broker}…`
    await svc.reconnectMock()
    A.value.connected = true
    st.busy = ''
  }

  /* ── Save (end of step 2) ──────────────────────────────────────── */
  async function save() {
    const a = A.value, c = C.value, rows = R.value
    st.saveError = ''
    if (st.mode === 'real' && a.real) {
      st.busy = 'Saving…'
      const out = rows.map((r) => ({
        symbol: r.id,
        brokerSymbol: nameFor(r, c),
        enabled: usable(r, c) && tradesFor(r, c) > 0,
        lot: lotFor(a, r, c),
        trades: tradesFor(r, c),
      }))
      const res = await svc.saveReal({ rows: out, preset: presetOf(c), account: a })
      st.busy = ''
      if (!res.ok) { st.saveError = res.message; return false }
    }
    return true
  }

  /* ── Test (step 3) ─────────────────────────────────────────────── */
  async function runTest() {
    const id = st.acc, a = A.value, c = C.value
    const act = activeRows(a, R.value, c)
    c.phase = 'checking'
    c.ready = false
    c.problems = {}
    c.res = Object.fromEntries(act.map((r) => [r.id, 'chk']))
    for (const r of act) {
      const name = nameFor(r, c)
      const out = a.real ? await svc.testReal(r.id, name) : await svc.testMock(r, name)
      if (st.acc !== id) return              // switched away mid-test
      c.res = { ...c.res, [r.id]: out.ok ? 'ok' : 'bad' }
      if (!out.ok) c.problems = { ...c.problems, [r.id]: out }
    }
    const bad = act.some((r) => c.res[r.id] === 'bad')
    c.phase = 'done'
    c.ready = !bad && act.length > 0
    if (c.ready) { c.trading = true; st.celebrate = true }
  }

  /* ── Navigation ────────────────────────────────────────────────── */
  function switchTo(id) {
    st.acc = id
    st.step = 1
    st.sheet = null
    st.screen = 'wizard'
    st.saveError = ''
  }
  const nextUnready = computed(() =>
    st.order.find((o) => o !== st.acc && !st.cfg[o]?.ready && st.accounts[o]?.connected) || null)

  /** Copy one account's choices onto another. Symbols are re-matched to the
   *  target broker's own names (never the source's), and risk is carried as
   *  a PERCENTAGE so it converts to the target's balance and currency. */
  function copy() {
    const from = st.cfg[st.copyFrom], to = st.copyTo
    const picks = {}
    for (const r of st.rows[to] || []) if (r.kind === 'pick' && r.ok) picks[r.id] = r.ok
    st.cfg[to] = blankCfg({ preset: from.preset, on: { ...from.on }, picks, trading: st.cfg[to]?.trading })
    switchTo(to)
  }

  return {
    st, A, C, R, presetOf, isCustom, nameFor, usable, tradesFor, lotFor, lotMoney, comb,
    activeRows, total, sumTrades, readyCount, statusOf, pickNeeded, riskTotals, nextUnready,
    toggle, pick, setPreset, setLot, setTrades, lowerIt,
    boot, retryLoad, reconnect, save, runTest, switchTo, copy,
    WARN, BLOCK, MAX_TRADES,
  }
}
