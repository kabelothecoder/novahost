/**
 * Currency conversion for the risk calculators.
 *
 * The iOS port of `sdk/FxRates.kt`, and it exists for the same reason that one
 * does: the sizing arithmetic is denominated in the INSTRUMENT's quote
 * currency, which is whatever the broker's contract spec says and is usually
 * not what the user's account is funded in. Gold quotes in dollars. A South
 * African trader's account holds rand. Printing a dollar figure under a rand
 * balance is not a formatting slip — it is a risk figure the user will act on,
 * out by a factor of about seventeen.
 *
 * Until now this screen had no conversion at all. It read `trades.currency` —
 * the ACCOUNT's currency — and pasted that code in front of a number computed
 * from `contract_size × pip_size`, which is in the QUOTE currency. On any
 * account not funded in the instrument's quote currency, every money figure on
 * the plan carried a label from a different currency than the arithmetic.
 *
 * ## Why every rate, and not just the three
 *
 * The picker offers [[SUPPORTED]] — USD, ZAR, EUR — because those are the
 * account currencies this product is sold into. But the FROM side is not the
 * user's choice: it is whatever the instrument quotes in, and a book with
 * GBPJPY on it needs JPY. One response carries the whole table, so
 * [[convert]] crosses any pair through USD rather than the module needing to
 * know in advance which currencies a broker will name.
 */

/** Currencies the account-currency picker offers. */
export const SUPPORTED = ['USD', 'ZAR', 'EUR']

export const CURRENCY_SIGN = {
  USD: '$', EUR: '€', GBP: '£', JPY: '¥', ZAR: 'R',
  AUD: 'A$', CAD: 'C$', CHF: 'CHF', NZD: 'NZ$',
}

/** The symbol to put in front of an amount, or the code when there is none. */
export function signFor(code) {
  const c = String(code || '').toUpperCase()
  return CURRENCY_SIGN[c] || (c ? c + ' ' : '')
}

/**
 * Last-resort rates, used only when nothing has ever been fetched and the
 * network is down. Deliberately round numbers: they exist so the calculator
 * produces a sane figure rather than a zero, and anything sized against them is
 * marked an estimate on screen.
 */
const FALLBACK = { USD: 1, ZAR: 16.1, EUR: 0.86, GBP: 0.74, JPY: 147, CHF: 0.79, AUD: 1.5, CAD: 1.36, NZD: 1.66 }

const CACHE_KEY = 'NH_FX_RATES'
/** Six hours. FX does not move fast enough to justify asking more often. */
const MAX_AGE_MS = 6 * 60 * 60 * 1000

/** `{ rates: { CODE: unitsPerUsd }, fetchedAt: epochMs }`, or null. */
let memo = null

function readCache() {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed && parsed.rates && parsed.fetchedAt ? parsed : null
  } catch {
    /* A private window, cleared site data, or storage the browser refuses
       outright. None of those are worth failing a scan over. */
    return null
  }
}

function writeCache(value) {
  try { localStorage.setItem(CACHE_KEY, JSON.stringify(value)) } catch { /* see readCache */ }
}

/**
 * The whole USD table, from cache when it is fresh enough.
 *
 * Never throws. A failed fetch falls back to the cached table, then to
 * [[FALLBACK]] — a calculator that shows nothing is worse than one that shows a
 * figure it has labelled as an estimate.
 *
 * `open.er-api.com` needs no key and returns every rate in one response, which
 * is the same provider Android moved to after FMP retired `/api/v3/quote` for
 * non-legacy keys. That retirement was silent: the parse failed, the catch
 * swallowed it, and every conversion fell through to a hardcoded 18.0 while the
 * live rate was 16.11 — a position size twelve per cent wrong on every trade,
 * with nothing on screen suggesting anything was amiss.
 */
export async function loadRates() {
  const cached = memo || readCache()
  if (cached && Date.now() - cached.fetchedAt < MAX_AGE_MS) {
    memo = cached
    return cached
  }

  try {
    const res = await fetch('https://open.er-api.com/v6/latest/USD')
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const body = await res.json()
    if (body?.result !== 'success' || !body?.rates) throw new Error('provider did not report success')

    const fresh = { rates: body.rates, fetchedAt: Date.now() }
    memo = fresh
    writeCache(fresh)
    return fresh
  } catch (err) {
    /* Logged, not swallowed. An empty catch here is exactly how the stale
       fallback above went unnoticed for as long as it did. */
    console.warn('[fx] rate fetch failed:', err?.message || err)
    // Stale beats absent.
    if (cached) { memo = cached; return cached }
    return { rates: FALLBACK, fetchedAt: 0 }
  }
}

/** True when this table is the hardcoded one rather than a fetched rate. */
export function isEstimate(table) {
  return !table || table.fetchedAt === 0
}

/**
 * `amount` in `from`, expressed in `to`.
 *
 * Crossed through USD, which is the base the table is published against.
 * Returns null rather than a number when either leg is missing — a money figure
 * on a trading screen is either right or it is a lie, and there is no useful
 * third state. Every call site renders an em dash for the null.
 */
export function convert(amount, from, to, table) {
  const n = Number(amount)
  if (!Number.isFinite(n)) return null

  const a = String(from || '').toUpperCase()
  const b = String(to || '').toUpperCase()
  if (!a || !b) return null
  if (a === b) return n

  const rates = (table && table.rates) || FALLBACK
  const perUsdFrom = a === 'USD' ? 1 : Number(rates[a])
  const perUsdTo = b === 'USD' ? 1 : Number(rates[b])
  if (!(perUsdFrom > 0) || !(perUsdTo > 0)) return null

  return (n / perUsdFrom) * perUsdTo
}
