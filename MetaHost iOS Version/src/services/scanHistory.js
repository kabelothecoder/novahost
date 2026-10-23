/**
 * Scan history, kept on this phone.
 *
 * The v3 design's history screen shows outcomes ("Hit TP1 · +R48"). Those need
 * every scan joined to the trade it produced, and no table does that yet. So
 * this stores what the app does know: what was scanned, what the robot said,
 * and whether the user sent it. The screen says outcomes are not tracked
 * rather than inventing them.
 *
 * localStorage, so it can come back empty (private mode, cleared site data).
 * Every access is guarded, and an empty list is a valid answer.
 */

const KEY = 'NH_SCAN_HISTORY_V1'
const MAX = 30

export function loadHistory() {
  try {
    const raw = localStorage.getItem(KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

function save(list) {
  try { localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX))) } catch { /* storage unavailable */ }
}

/** Adds a scan to the top. Returns its id. */
export function recordScan(entry) {
  const id = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
  save([{ id, at: new Date().toISOString(), sent: null, saved: false, ...entry }, ...loadHistory()])
  return id
}

/** Merges fields into one entry (for example `sent` after a trade is placed). */
export function updateScan(id, patch) {
  if (!id) return
  save(loadHistory().map(e => (e.id === id ? { ...e, ...patch } : e)))
}
