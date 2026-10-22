/**
 * The in-app island: alerts drawn as a Dynamic-Island-style pill while the
 * app is open.
 *
 * The real Dynamic Island (Live Activities) belongs to native apps; a Home
 * Screen web app cannot reach it. What it can do is draw over the status bar
 * (`black-translucent`), right where the hardware island sits, and grow out
 * of it. Closed-app alerts are ordinary iOS banners from push-sw.js.
 *
 * Alerts queue rather than stack: one island at a time, the next one after
 * the current one has shrunk away.
 */
import { reactive } from 'vue'

export const island = reactive({
  current: null,   // { title, body, kind, url, side }
  queue: [],
})

export function showIsland(msg) {
  if (!msg || !msg.title) return
  const side = /\bSELL\b/i.test(msg.title) ? 'sell' : /\bBUY\b/i.test(msg.title) ? 'buy' : null
  const item = { id: Date.now() + Math.random(), side, kind: msg.kind || 'info', url: msg.url || '/home', title: msg.title, body: msg.body || '' }
  if (island.current) island.queue.push(item)
  else island.current = item
}

/** Called by the island once it has fully collapsed. */
export function nextIsland() {
  island.current = island.queue.shift() || null
}
