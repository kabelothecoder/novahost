/* Web Push for the installed iPhone app.
 *
 * Pulled into the generated Workbox service worker through
 * `workbox.importScripts` (vite.config.js), so precaching stays exactly as it
 * was and this file only adds the two events Web Push needs.
 *
 * iOS requires every push to show a notification (`userVisibleOnly`); a push
 * that shows nothing gets the subscription revoked after a few strikes. So
 * `showNotification` is unconditional, even when the app is open. An open app
 * additionally gets the alert by postMessage, which is what draws the in-app
 * island.
 */
self.addEventListener('push', (event) => {
  let msg = {}
  try { msg = event.data ? event.data.json() : {} } catch (e) { msg = { title: 'NovaHost', body: event.data && event.data.text() } }

  const title = msg.title || 'NovaHost'
  const options = {
    body: msg.body || '',
    icon: '/app_logo.png',
    badge: '/app_logo.png',
    tag: msg.tag || msg.kind || 'novahost',
    renotify: true,
    data: { url: msg.url || '/home', kind: msg.kind, id: msg.id },
  }

  event.waitUntil((async () => {
    await self.registration.showNotification(title, options)
    const note = { type: 'novahost-push', id: msg.id || options.tag + ':' + Date.now(), title, body: options.body, kind: msg.kind, url: options.data.url }
    /* Two routes to the open app, because Safari is unreliable about each:
       postMessage to window clients, and a BroadcastChannel. The page drops
       whichever arrives second (same id). */
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const w of wins) w.postMessage(note)
    try { const bc = new BroadcastChannel('novahost-push'); bc.postMessage(note); bc.close() } catch (e) { /* not supported */ }
  })())
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data && event.notification.data.url) || '/home'

  event.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const w of wins) {
      if ('focus' in w) {
        await w.focus()
        w.postMessage({ type: 'novahost-open', url })
        return
      }
    }
    if (self.clients.openWindow) await self.clients.openWindow(url)
  })())
})
