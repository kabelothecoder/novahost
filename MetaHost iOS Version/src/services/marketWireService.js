/**
 * The News and Events wire on the Trading Symbols screen.
 *
 * ## What this replaces
 *
 * Two finished layouts over a hardcoded "Not connected" chip. Both sections
 * shipped with real copy and no source behind them — there was no news table,
 * no calendar table, and no edge function returning either. `market-wire` is
 * that source.
 *
 * ## Why the failure shape matters
 *
 * The old screen said "not connected" rather than showing sample headlines,
 * and its comment gave the reason: a plausible-looking headline on a trading
 * screen is the one kind of placeholder that can move real money. That
 * principle is kept. Each feed answers independently with its own `ok`, so
 * News being down does not blank the calendar, and a payload served from cache
 * past its refresh window arrives flagged `stale` so the card can say how old
 * it is rather than presenting six-hour-old prices as current.
 *
 * Nothing here invents a row, and nothing retries in a loop: one call per
 * screen open, served from a shared server-side cache.
 */

import { supabase } from '../utils/supabaseClient.ts'
import { secStorage } from '../utils/secStorage.js'

const EMPTY = { ok: false, stale: false, source: null, fetchedAt: null, error: null, items: [] }

/**
 * Load both feeds, scoped to the symbols this user actually trades.
 *
 * `symbols` narrows the calendar to the currencies that move them and floats
 * matching headlines to the top; an empty list is not an error and returns the
 * whole wire, because a user who has enabled nothing still needs to know that
 * CPI prints in forty minutes.
 *
 * @param {string[]} symbols canonical symbols, e.g. ['XAUUSD', 'NAS100']
 * @returns {Promise<{news: Feed, events: Feed}>}
 */
export async function loadWire(symbols = []) {
  const key = secStorage.getItem('LICENSE_KEY')
  if (!key) {
    const noKey = { ...EMPTY, error: 'This device is not activated.' }
    return { news: noKey, events: noKey }
  }

  try {
    const { data, error } = await supabase.functions.invoke('market-wire', {
      body: { license_key: key, symbols: symbols.filter(Boolean).slice(0, 12) },
    })
    if (error) throw error
    if (!data?.success) throw new Error(data?.error || 'The wire is unavailable.')

    return { news: shape(data.news), events: shape(data.events) }
  } catch (err) {
    /* One message, on both cards. Splitting a transport failure into two
       different-sounding errors would suggest two independent outages. */
    const failed = { ...EMPTY, error: err?.message || 'Could not reach the wire.' }
    return { news: failed, events: failed }
  }
}

function shape(feed) {
  if (!feed) return { ...EMPTY }
  return {
    ok: feed.ok === true,
    stale: feed.stale === true,
    source: feed.source || null,
    fetchedAt: feed.fetched_at || null,
    error: feed.error || null,
    items: Array.isArray(feed.items) ? feed.items : [],
  }
}

/**
 * "in 40m", "2h ago", "Tue 14:30".
 *
 * Relative inside a day in either direction, because that is the window where
 * "in 40m" is the answer to the question being asked; absolute beyond it,
 * where a relative figure stops being legible ("in 83h"). Times render in the
 * device's own zone — the wire sends ISO instants precisely so the phone can
 * do that, rather than the server guessing where the user is.
 */
export function whenLabel(iso, now = Date.now()) {
  if (!iso) return '—'
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return '—'

  const mins = Math.round((t - now) / 60000)
  const abs = Math.abs(mins)

  if (abs < 1) return 'now'
  if (abs < 60) return mins > 0 ? `in ${abs}m` : `${abs}m ago`
  if (abs < 24 * 60) {
    const h = Math.round(abs / 60)
    return mins > 0 ? `in ${h}h` : `${h}h ago`
  }
  /* 24h, so the label stays short enough not to wrap in the calendar's time
     column — "Tue 12:00" fits where "Tue 12:00 PM" broke onto a second line.
     It also matches the session windows above it, which are already 24h. */
  return new Date(t).toLocaleString(undefined, {
    weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false,
  })
}

/** "updated 4m ago" for the card's own freshness line. */
export function ageLabel(iso, now = Date.now()) {
  if (!iso) return ''
  const mins = Math.round((now - new Date(iso).getTime()) / 60000)
  if (Number.isNaN(mins)) return ''
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const h = Math.round(mins / 60)
  return h < 48 ? `${h}h ago` : `${Math.round(h / 24)}d ago`
}
