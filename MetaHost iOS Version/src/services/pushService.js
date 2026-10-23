/**
 * Web Push for the installed iPhone app.
 *
 * iOS (16.4+) delivers push to a Home Screen web app and to nothing else: not
 * a Safari tab, not before install. Permission must be asked from a tap, so
 * `enablePush` is only ever called from a click handler — never from a
 * watcher or on load, where iOS refuses the prompt without showing it.
 *
 * The subscription is registered against EVERY licence on this phone (a phone
 * can hold several), together with the robot's name and the Settings ›
 * Notifications switches, so the server can word alerts as the robot and
 * leave out the categories the user turned off.
 */
import { reactive } from 'vue'
import { supabase } from '../utils/supabaseClient.ts'
import { secStorage } from '../utils/secStorage.js'
import { robot } from '../stores/robot.js'
import { prefs, NOTIF_CATS } from '../stores/settingsPrefs.js'

export const push = reactive({
  /** unsupported | needs-install | default | denied | on | off */
  state: 'default',
  busy: false,
})

const standalone = () =>
  window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone === true

export function pushSupported() {
  return 'serviceWorker' in navigator && 'PushManager' in window && typeof Notification !== 'undefined'
}

function licenceKeys() {
  const keys = robot.eaList.map(e => e.key).filter(Boolean)
  const main = secStorage.getItem('LICENSE_KEY')
  if (main) keys.push(main)
  return [...new Set(keys.map(k => String(k).trim().toUpperCase()))]
}

function prefsPayload() {
  const out = { nOn: prefs.nOn, quietOn: prefs.quietOn, quietAllow: prefs.quietAllow, qFrom: prefs.qFrom, qTo: prefs.qTo }
  for (const [, list] of NOTIF_CATS) for (const [k] of list) out[k] = prefs[k]
  return out
}

/** The 65-byte VAPID key the server signs with, as the bytes PushManager wants. */
async function serverKey() {
  const { data, error } = await supabase.functions.invoke('push-subscribe', { body: { action: 'config' } })
  if (error || !data?.publicKey) throw new Error('Could not reach the notification service.')
  const s = data.publicKey.replace(/-/g, '+').replace(/_/g, '/')
  const bin = atob(s + '='.repeat((4 - (s.length % 4)) % 4))
  return Uint8Array.from(bin, c => c.charCodeAt(0))
}

async function registration() {
  const reg = await navigator.serviceWorker.ready
  return reg
}

async function send(action, sub, extra = {}) {
  const { data, error } = await supabase.functions.invoke('push-subscribe', {
    body: {
      action,
      subscription: sub.toJSON(),
      license_keys: licenceKeys(),
      license_key: licenceKeys()[0],
      robot_name: robot.name,
      prefs: prefsPayload(),
      tz: Intl.DateTimeFormat().resolvedOptions().timeZone,
      ...extra,
    },
  })
  if (error) {
    let msg = 'Could not reach the notification service.'
    try { const b = await error.context?.json?.(); if (b?.error) msg = b.error } catch { /* keep generic */ }
    throw new Error(msg)
  }
  if (!data?.success) throw new Error(data?.error || 'The notification service refused this phone.')
  return data
}

/** Work out where this phone stands, without prompting. */
export async function refreshPushState() {
  if (!pushSupported()) { push.state = standalone() ? 'unsupported' : 'needs-install'; return push.state }
  if (!standalone()) { push.state = 'needs-install'; return push.state }
  if (Notification.permission === 'denied') { push.state = 'denied'; return push.state }
  if (Notification.permission === 'default') { push.state = 'default'; return push.state }
  try {
    const sub = await (await registration()).pushManager.getSubscription()
    push.state = sub && prefs.nOn ? 'on' : 'off'
  } catch {
    push.state = 'off'
  }
  return push.state
}

/** Call from a tap. Asks iOS for permission, subscribes, and registers. */
export async function enablePush() {
  if (!pushSupported() || !standalone()) {
    await refreshPushState()
    return { ok: false, message: 'Add NovaHost to your Home Screen first, then open it from there.' }
  }
  push.busy = true
  try {
    // Asked first and synchronously off the tap: iOS drops the prompt if
    // anything awaits a network round trip before it.
    const perm = await Notification.requestPermission()
    if (perm !== 'granted') {
      push.state = perm === 'denied' ? 'denied' : 'default'
      return { ok: false, message: perm === 'denied'
        ? 'Notifications are blocked. Open iPhone Settings › Notifications › NovaHost › Allow.'
        : 'Notifications were not allowed.' }
    }

    const reg = await registration()
    let sub = await reg.pushManager.getSubscription()
    if (!sub) {
      sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: await serverKey() })
    }
    prefs.nOn = true
    await send('subscribe', sub)
    push.state = 'on'
    return { ok: true }
  } catch (e) {
    await refreshPushState()
    return { ok: false, message: e?.message || 'Could not turn notifications on.' }
  } finally {
    push.busy = false
  }
}

export async function disablePush() {
  push.busy = true
  try {
    prefs.nOn = false
    const sub = pushSupported() ? await (await registration()).pushManager.getSubscription() : null
    if (sub) {
      await send('unsubscribe', sub).catch(() => {})
      await sub.unsubscribe().catch(() => {})
    }
    push.state = 'off'
    return { ok: true }
  } finally {
    push.busy = false
  }
}

/**
 * Re-send the subscription with the current licences, name and switches.
 * Cheap and silent; called on launch and whenever a notification setting
 * changes. Also heals an endpoint iOS rotated while the app was closed.
 */
export async function syncPush() {
  if (!pushSupported() || !standalone() || Notification.permission !== 'granted' || !prefs.nOn) return
  try {
    const sub = await (await registration()).pushManager.getSubscription()
    if (!sub) { push.state = 'off'; return }
    if (!licenceKeys().length) return
    await send('subscribe', sub)
    push.state = 'on'
  } catch { /* next launch tries again */ }
}

export async function testPush() {
  const sub = pushSupported() ? await (await registration()).pushManager.getSubscription() : null
  if (!sub) return { ok: false, message: 'Turn on Allow notifications first.' }
  try {
    await send('test', sub)
    return { ok: true }
  } catch (e) {
    return { ok: false, message: e.message }
  }
}
