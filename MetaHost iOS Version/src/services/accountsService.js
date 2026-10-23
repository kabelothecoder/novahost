/**
 * Broker accounts on one licence: list, switch, delink.
 *
 * ## This is a MOCK, on purpose
 *
 * The backend holds exactly one broker account per licence
 * (`licenses.metadata.bridge_account_id`) and every `broker-*` function
 * resolves it from that field. Several accounts need a new table and new edge
 * functions, which are waiting on sign-off (design-briefs/connection-area-plan.md,
 * section 3). Until then this module answers from memory so the screen can be
 * built and exercised end to end.
 *
 * The surface is the contract the real implementation will keep: swapping the
 * mock for `supabase.functions.invoke('list-accounts' ...)` is a change to this
 * one file, and nothing under `components/accounts/` knows the difference.
 *
 * Nothing here is written to `session` or `accent`. The rest of the app still
 * believes in one account, and feeding it mock balances would paint a green
 * "connected" pill for a broker nobody linked.
 *
 * ## Shape
 *
 *   item  = account | slot
 *   account { kind:'acc', id, broker, l4, server, platform, balance, currency, tint }
 *   slot    { kind:'slot', id, state: 'pending' | 'failed' | 'ready' }
 *
 * `l4` is the last four digits of the login. The full login is never held, so
 * there is nothing here to leak and nothing to mask later.
 */

/* ── Add-on pricing ───────────────────────────────────────────────────
   PLACEHOLDER. The design prices an extra account at R149/mo, while the
   shipped product is three once-off PayFast purchases with no subscription.
   Nothing in this build charges anything; the figures exist so the sheets have
   something honest-looking to lay out. Resolve before any real wiring. */
export const ACCOUNT_ADDON = Object.freeze({
  PLACEHOLDER: true,
  priceMonthly: 149,
  currencySymbol: 'R',
  maxAccounts: 5,
})

const SEED = [
  { broker: 'Exness',        l4: '4471', server: 'Exness-MT5Real9',    platform: 'MT5', balance: 3412.8,  currency: 'USD', tint: '#F2C200' },
  { broker: 'XM',            l4: '2209', server: 'XMGlobal-MT5 7',     platform: 'MT5', balance: 61904.15, currency: 'ZAR', tint: '#3FBF7F' },
  { broker: 'Trade245',      l4: '7730', server: 'Trade245-Live',      platform: 'MT5', balance: 12480,   currency: 'ZAR', tint: '#3D7BFF' },
  { broker: 'JustMarkets',   l4: '5186', server: 'JustMarkets-Live2',  platform: 'MT5', balance: 890.4,   currency: 'USD', tint: '#2FD07A' },
  { broker: 'Razor Markets', l4: '3051', server: 'RazorMarkets-Live',  platform: 'MT5', balance: 27300,   currency: 'ZAR', tint: '#A58BFF' },
]

let uid = 0
const mkAcc = (s) => ({ kind: 'acc', id: `a${++uid}`, ...s })
const mkSlot = (state) => ({ kind: 'slot', id: `s${++uid}`, state })

/** What the screen looks like for a given scene. Dev-only entry point for
 *  reviewing the awkward states (`#/accounts?scene=pending`). */
function seed(scene) {
  const A = SEED.map(mkAcc)
  switch (scene) {
    case 'empty':   return { items: [], activeId: null }
    case 'pending': return { items: [A[0], A[1], mkSlot('pending')], activeId: A[0].id }
    case 'failed':  return { items: [A[0], mkSlot('failed')], activeId: A[0].id }
    case 'ready':   return { items: [A[0], A[1], mkSlot('ready')], activeId: A[0].id }
    case 'max':     return { items: A, activeId: A[0].id }
    default:        return { items: A.slice(0, 3), activeId: A[0].id }
  }
}

let db = null
const latency = (ms = 220) => new Promise((r) => setTimeout(r, ms))

function ensure(scene) {
  if (!db || scene) db = seed(scene)
  return db
}

const snapshot = () => ({
  items: db.items.map((i) => ({ ...i })),
  activeId: db.activeId,
})

/** `scene` resets the mock; omit it to read whatever is current. */
export async function listAccounts({ scene } = {}) {
  ensure(scene)
  await latency()
  return { ok: true, ...snapshot() }
}

export async function setActive(id) {
  ensure()
  await latency(300)
  const a = db.items.find((i) => i.id === id)
  if (!a || a.kind !== 'acc') return { ok: false, message: 'That account is not linked any more.' }
  db.activeId = id
  return { ok: true, ...snapshot() }
}

/** Extras only. The active account, and the one included in the plan while it
 *  is the only account, are refused here as well as in the UI. */
export async function delinkAccount(id) {
  ensure()
  await latency(300)
  const a = db.items.find((i) => i.id === id)
  if (!a || a.kind !== 'acc') return { ok: false, message: 'That account is not linked any more.' }
  if (id === db.activeId) return { ok: false, message: 'Make another account active before delinking this one.' }
  db.items = db.items.filter((i) => i.id !== id)
  return { ok: true, ...snapshot() }
}

/* ── Slots (mock checkout) ────────────────────────────────────────────
   Nothing here talks to PayFast or charges anything. `buySlot` stands in for
   "PayFast confirmed", `recheckSlot` for re-polling a payment that had not
   settled. Both resolve to a READY slot: paid, with nowhere linked yet. */

/** Buy a new slot, or settle an existing pending/failed one. */
export async function buySlot({ slotId } = {}) {
  ensure()
  await latency(1500)
  if (slotId) {
    const s = db.items.find((i) => i.id === slotId && i.kind === 'slot')
    if (!s) return { ok: false, message: 'That slot is gone.' }
    s.state = 'ready'
    return { ok: true, slotId, ...snapshot() }
  }
  if (db.items.length >= ACCOUNT_ADDON.maxAccounts) {
    return { ok: false, message: `One plan holds ${ACCOUNT_ADDON.maxAccounts} accounts at most.` }
  }
  const s = mkSlot('ready')
  db.items = [...db.items, s]
  return { ok: true, slotId: s.id, ...snapshot() }
}

export async function recheckSlot(id) {
  ensure()
  await latency(1300)
  const s = db.items.find((i) => i.id === id && i.kind === 'slot')
  if (!s) return { ok: false, message: 'That slot is gone.' }
  s.state = 'ready'
  return { ok: true, ...snapshot() }
}

/**
 * What an extra account costs TODAY and from when the full price starts.
 * Computed from the calendar so the sheets never say "Rest of October" in
 * November. Today counts as a day you can use.
 */
export function proration(now = new Date()) {
  const y = now.getFullYear(), m = now.getMonth()
  const days = new Date(y, m + 1, 0).getDate()
  const left = days - now.getDate() + 1
  const sym = ACCOUNT_ADDON.currencySymbol
  const next = new Date(y, m + 1, 1)
  const monthName = (d) => d.toLocaleString('en-ZA', { month: 'long' })
  const short = (d) => d.toLocaleString('en-ZA', { month: 'short' })
  return {
    today: `${sym}${((ACCOUNT_ADDON.priceMonthly * left) / days).toFixed(2)}`,
    restLabel: `Rest of ${monthName(now)}, prorated`,
    nextLabel: `From 1 ${short(next)}`,
    endLabel: `${days} ${short(now)}`,
  }
}

/** The account the plan includes: the first one linked. Everything after it
 *  is an add-on. Derived, never stored, so deleting the first account promotes
 *  the next without a migration of flags. */
export function includedId(items) {
  const first = items.find((i) => i.kind === 'acc')
  return first ? first.id : null
}

export function maskLogin(a) {
  return `•••• ${a.l4}`
}

export function formatMoney(balance, currency) {
  const sym = currency === 'USD' ? '$' : currency === 'ZAR' ? 'R' : ''
  const n = Number(balance).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return sym ? `${sym}${n}` : `${n} ${currency}`
}
