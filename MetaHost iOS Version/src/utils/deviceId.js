/**
 * deviceId.js
 *
 * The handset identity this PWA presents to `check-subscription-status` and
 * `generate-payfast-checkout`, both of which enforce one-email-one-device.
 *
 * Android reads a real hardware id (`ANDROID_ID`). A web app has nothing
 * equivalent and never will, so this mints a random UUID on first run and
 * keeps it. That is genuinely weaker: it identifies an *installation*, not a
 * device.
 *
 * Two consequences worth knowing before changing anything here:
 *
 *   - Clearing site data mints a new id, which the server reads as a new
 *     device and refuses (`device_mismatch`). It fails closed, not open, which
 *     is the right direction — but a user who cleared Safari's storage is
 *     indistinguishable from one lending their login to a friend. The escape
 *     hatch is the `support-reset-device` function, which needs a human.
 *   - iOS Safari evicts storage for sites unused for ~7 days. A PWA added to
 *     the Home Screen is exempt, which is why onboarding step 08 teaches that
 *     — it is load-bearing for entitlement here, not just polish.
 *
 * Never regenerate this on a whim: a new id costs the user R150.
 */
import { secStorage } from './secStorage.js'

const KEY = 'DEVICE_ID'

/** RFC-4122 v4, via the platform when it is available. */
function mint () {
  // randomUUID needs a secure context. The PWA always has one; a plain-http
  // dev server does not, hence the fallback rather than a throw.
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }

  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)
    return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
  }

  // Last resort. Weak, but an id that is merely unlikely to collide still
  // beats every install sharing one — which is what returning a constant
  // would do, and it locked out an entire class of Android handsets once
  // already (see check-subscription-status on "UNKNOWN_DEVICE").
  return 'w' + Date.now().toString(16) + Math.random().toString(16).slice(2, 14)
}

/**
 * @description This installation's stable device id, minted on first call.
 * @returns {string}
 */
export function getDeviceId () {
  let id = secStorage.getItem(KEY)

  if (!id || typeof id !== 'string' || id.length < 8) {
    id = mint()
    secStorage.setItem(KEY, id)
  }

  return id
}
