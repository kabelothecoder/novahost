import { reactive } from 'vue'
import { supabase } from '../utils/supabaseClient'
import { secStorage } from '../utils/secStorage'
import { getDeviceId } from '../utils/deviceId'
import { accent } from '../stores/accent.js'
import { swatchByName } from '../stores/palette.js'

/**
 * How long a verification may hang before we stop waiting on it.
 *
 * Nothing below this line has a deadline of its own: fetch waits forever by
 * default, so on a dead connection or a captive portal the gate's CTA sits in
 * "Verifying" with no way out, and initializeAuth() — which the router guard
 * awaits on every cold start — holds the whole app on the splash screen.
 */
const VERIFY_TIMEOUT_MS = 10000

const TIMEOUT_MESSAGE = 'That took too long. Check your connection and try again.'

/**
 * How long a confirmed purchase keeps working with no server in reach.
 *
 * `check-subscription-status` has owned this number since it was written — it
 * sends `max_offline_days: 5` in the envelope of every answer, and its own
 * header says "the app locks itself after MAX_OFFLINE_DAYS without one of
 * these". Nothing ever read it. `CHECKED_AT` was stored and never looked at
 * either, so the grace existed on paper and nowhere else, and a paying
 * customer on a plane was treated exactly like a refusal.
 *
 * This is the fallback for the first launch after upgrading, before any answer
 * has carried the real figure. The server's value wins whenever we have one.
 */
const FALLBACK_OFFLINE_DAYS = 5

/**
 * And a ceiling on whatever the server said.
 *
 * `secStorage` is obfuscated, not secure — it is a salt and an XOR, and the
 * salt sits in the same bucket. A stored `max_offline_days` is therefore user
 * input, and an unbounded one is a free licence. Clamped rather than trusted.
 */
const MAX_OFFLINE_DAYS_CEILING = 14

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * Whether a previously confirmed purchase is still inside its offline window.
 *
 * Deliberately only consulted when the server could not be REACHED. A refusal
 * is an answer and ends the grace immediately — this is for silence, not for
 * "no".
 *
 * The clock is the device's, so setting it back extends the window. That is
 * inherent to any offline grace and is not worth defending against here: the
 * window is five days, it ends at the next successful check, and the server
 * stays the only authority on entitlement. A user willing to reset their phone
 * clock every five days has done more work than the R599 is worth.
 *
 * @returns {{within: boolean, daysLeft: number|null, since: Date|null}}
 */
function offlineGrace() {
  const stamp = secStorage.getItem('CHECKED_AT')
  if (!stamp) return { within: false, daysLeft: null, since: null }

  const since = new Date(stamp)
  if (Number.isNaN(since.getTime())) return { within: false, daysLeft: null, since: null }

  const allowed = Math.min(
    Number(secStorage.getItem('MAX_OFFLINE_DAYS')) || FALLBACK_OFFLINE_DAYS,
    MAX_OFFLINE_DAYS_CEILING
  )

  /* A stamp in the future means the clock moved, not that the check is fresh.
     Treated as "now" so it neither grants nor destroys anything. */
  const elapsed = Math.max(0, Date.now() - since.getTime())
  const daysLeft = allowed - elapsed / DAY_MS

  return { within: daysLeft > 0, daysLeft: Math.max(0, daysLeft), since }
}

export const authState = reactive({
  /**
   * Entitlement: a paid, device-matched purchase confirmed by
   * check-subscription-status. This is the Home gate. A validated licence key
   * alone does NOT set this — see `keyValidated`.
   */
  isVerified: false,
  isVerifying: true, // true initially so we can block routes on load

  /**
   * The mentor-issued licence key has cleared validate-license this session
   * (or on a previous one — seeded from LICENSE_VALID). It is the entry gate:
   * the onboarding will not show the payment screen without it, and
   * generate-payfast-checkout refuses an ios checkout that does not carry one.
   * Necessary for Home, not sufficient — payment still has to land.
   */
  keyValidated: secStorage.getItem('LICENSE_VALID') === 'true',

  /**
   * The email the purchase is registered to. Collected on the payment screen
   * (the gate takes a key, not an email), and kept so a later launch can
   * re-check entitlement and the payment screen can prefill it. PENDING_EMAIL
   * is the not-yet-verified half; verifySubscription promotes it to
   * VERIFIED_EMAIL.
   */
  email: secStorage.getItem('VERIFIED_EMAIL') || secStorage.getItem('PENDING_EMAIL') || null,

  /**
   * Why the last check refused, straight from check-subscription-status:
   * 'no_purchase' | 'not_paid' | 'expired' | 'device_mismatch'. The payment
   * screen reads this to decide whether to offer a purchase, a device move, or
   * nothing.
   */
  reason: null,

  /** Move eligibility, present only alongside a 'device_mismatch'. */
  move: null,

  /** Scanner entitlement rides along on the same answer. */
  hasScanner: secStorage.getItem('HAS_SCANNER') === 'true',

  /**
   * True when `isVerified` is being carried by the offline grace rather than by
   * a live answer — the purchase was confirmed before, and the server could not
   * be reached this launch.
   *
   * Worth exposing rather than hiding: the app is working normally but on
   * borrowed time, and a user who is about to lose access after five days
   * offline should be able to find that out before it happens rather than
   * after.
   */
  offline: false,

  /** Days of grace left, or null when not running on it. */
  graceDaysLeft: null,
})

/**
 * Restores a session on cold start, in the order the flow is gated:
 *
 *   1. Licence key — the entry gate. No stored key, or a key the server now
 *      rejects, and the session has nothing; the router guard sends it to
 *      /unlock. A network miss on a key that was valid last time is trusted,
 *      so a dead connection never locks someone out of what they hold.
 *   2. Payment — the Home gate. The key is good but entitlement still has to
 *      be paid for: with no email on file the guard routes to /payment; with
 *      one, its entitlement is re-checked against check-subscription-status.
 *
 * A validated key never implies payment and a payment is never checked before
 * the key — the two are strictly sequential here, the same order the UI walks.
 */
export async function initializeAuth() {
  authState.isVerifying = true

  try {
    const storedKey = secStorage.getItem('LICENSE_KEY')
    if (!storedKey) {
      authState.keyValidated = false
      authState.isVerified = false
      return
    }

    // ── 1. The key ──────────────────────────────────────────────────────────
    const keyResult = await activateLicenseKey(storedKey)

    if (keyResult.rejected) {
      // A definite server refusal — the key is gone. Clear it and let the
      // guard drop the session back on the gate.
      secStorage.removeItem('LICENSE_KEY')
      secStorage.removeItem('LICENSE_VALID')
      authState.keyValidated = false
      authState.isVerified = false
      return
    }

    if (!keyResult.success) {
      // Could not ask (network, timeout). Trust the last real answer if there
      // was one; otherwise treat the key as unproven and gate on it.
      if (secStorage.getItem('LICENSE_VALID') !== 'true') {
        authState.keyValidated = false
        authState.isVerified = false
        return
      }
      authState.keyValidated = true
    }
    // On success activateLicenseKey() has already set keyValidated + identity.

    // ── 2. Payment ─────────────────────────────────────────────────────────
    if (!authState.email) {
      // Key is good, nothing bought yet. Guard routes to /payment.
      authState.isVerified = false
      return
    }

    /* An email on file means the server confirmed this purchase at some point
       — VERIFIED_EMAIL is written nowhere else. A build that predates the
       grace never stamped CHECKED_AT, so stamp it now rather than denying the
       window to everyone who upgrades: the confirmation it represents really
       did happen, we just never wrote down when. */
    if (!secStorage.getItem('CHECKED_AT')) {
      secStorage.setItem('CHECKED_AT', new Date().toISOString())
    }

    const subResult = await verifySubscription(authState.email)
    if (subResult.success) {
      authState.isVerified = true
      return
    }

    if (subResult.rejected) {
      /* The server answered no. That ends it — grace covers silence, never a
         refusal. The email is still kept: PaymentView prefills it and acts on
         `reason` to offer a purchase or a device move, and making them retype
         it helps nobody. */
      authState.isVerified = false
      authState.offline = false
      authState.graceDaysLeft = null
      secStorage.removeItem('VERIFIED_EMAIL')
      return
    }

    /* Could not ask — flat network, captive portal, timeout. This is where a
       paying customer used to be dropped on the payment screen with an empty
       email field, because the old code treated silence as a refusal AND
       deleted VERIFIED_EMAIL on the way out. Both were wrong: the purchase is
       lifetime, and nothing about it changed because a plane took off. */
    const grace = offlineGrace()

    if (grace.within) {
      authState.isVerified = true
      authState.offline = true
      authState.graceDaysLeft = grace.daysLeft
      return
    }

    /* Out of grace. Locked, which is the point of the window having an end —
       but the email survives so the next launch with a signal re-checks it
       automatically instead of asking for it again. */
    authState.isVerified = false
    authState.offline = true
    authState.graceDaysLeft = 0
  } finally {
    authState.isVerifying = false
  }
}

/**
 * Checks entitlement for the given email on this device.
 *
 * Goes through the `check-subscription-status` edge function rather than
 * reading `subscriptions` directly. That is not a preference — it is the only
 * thing that works:
 *
 *   - The table has no `status` column. The previous implementation selected
 *     one, so PostgREST answered `42703 column does not exist` on every call
 *     and the gate reported "Network error" to everybody, paid or not.
 *   - Entitlement is `is_lifetime` / `is_premium`, which is what payfast-webhook
 *     actually grants.
 *   - RLS is on with one owner-scoped policy, so an anon client cannot read
 *     another email's row regardless. The function holds the service role.
 *   - One-email-one-device, the offline grace window and the session token all
 *     live server-side there. Re-deriving any of it here would give two
 *     answers to the same question.
 *
 * @param {string} email
 * @returns {Promise<{success: boolean, message?: string, reason?: string, move?: object}>}
 */
export async function verifySubscription(email) {
  if (!email || typeof email !== 'string') {
    return { success: false, message: 'Please enter a valid email address.' }
  }

  const controller = new AbortController()
  let timedOut = false
  const timer = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, VERIFY_TIMEOUT_MS)

  try {
    const { data, error } = await supabase.functions.invoke('check-subscription-status', {
      body: {
        email: email.trim().toLowerCase(),
        deviceId: getDeviceId(),
        // Proves this install still holds the live session. The server rotates
        // it on every move, so a handset the licence moved away from stops
        // matching. Absent on a fresh install, which is not a refusal.
        token: secStorage.getItem('DEVICE_TOKEN') || undefined,
      },
      signal: controller.signal,
    })

    // The abort surfaces as a rejected fetch inside invoke(), so the flag is
    // what separates "we gave up waiting" from "the network said no".
    if (timedOut) return { success: false, message: TIMEOUT_MESSAGE }

    if (error || !data) {
      console.error('Entitlement check failed:', error)
      return { success: false, message: 'Network error. Please try again later.' }
    }

    // A 4xx from the function still arrives as data, carrying its own reason.
    // It is an ANSWER, so it ends any offline grace — see `rejected` below.
    if (data.success === false) {
      return { success: false, rejected: true, message: data.error || 'Verification failed.' }
    }

    authState.hasScanner = data.has_scanner === true
    secStorage.setItem('HAS_SCANNER', String(data.has_scanner === true))

    if (data.is_premium === true) {
      authState.isVerified = true
      authState.email = email.trim().toLowerCase()
      authState.reason = null
      authState.move = null
      secStorage.setItem('VERIFIED_EMAIL', authState.email)
      secStorage.removeItem('PENDING_EMAIL')
      if (data.token) secStorage.setItem('DEVICE_TOKEN', data.token)
      // Stamps the last server answer so a later offline-grace check has
      // something to age, and records the window the server is willing to
      // grant. Both are read by offlineGrace(); storing only the first is what
      // left the grace inert for as long as it existed.
      secStorage.setItem('CHECKED_AT', data.checked_at || new Date().toISOString())
      if (Number(data.max_offline_days) > 0) {
        secStorage.setItem('MAX_OFFLINE_DAYS', String(Number(data.max_offline_days)))
      }
      // A live answer supersedes any grace that was carrying the session.
      authState.offline = false
      authState.graceDaysLeft = null
      return { success: true }
    }

    // Refused. The reason is what lets the gate offer the right next step
    // instead of a dead end, and the address is kept so that next step has
    // something to act on.
    authState.isVerified = false
    authState.email = email.trim().toLowerCase()
    authState.reason = data.reason || null
    authState.move = data.move || null
    secStorage.setItem('PENDING_EMAIL', authState.email)

    return {
      success: false,
      /* The server answered and the answer was no. This is what ends an
         offline grace rather than extending it — the distinction the whole
         `rejected` flag exists for, and the same one activateLicenseKey
         already draws for the licence key. */
      rejected: true,
      reason: data.reason,
      move: data.move,
      message: data.message || 'No active purchase found for this email address.',
    }
  } catch (err) {
    // A throw is still possible if a future transport rethrows instead of
    // folding the abort into `error`, so the flag is checked on both paths.
    if (timedOut) return { success: false, message: TIMEOUT_MESSAGE }
    console.error('Unexpected error verifying subscription:', err)
    return { success: false, message: 'An unexpected error occurred. Please try again later.' }
  } finally {
    clearTimeout(timer)
  }
}

/**
 * The entry gate: a mentor-issued licence key, validated against `licenses`
 * via `validate-license`.
 *
 * This is what every user types first. It is checked here (`status: active`,
 * device binding), and again server-side by generate-payfast-checkout before
 * it will issue an ios checkout. It does NOT grant entitlement — a key holder
 * still pays once through PayFast, and check-subscription-status stays the
 * authority on whether that has happened.
 *
 * `license_key` in the body is what selects the app branch of that function;
 * the portal branch answers a different shape entirely.
 *
 * @param {string} rawKey
 * @returns {Promise<{success: boolean, message?: string, rejected?: boolean}>}
 *          `rejected` separates "the server said no" from "we could not ask",
 *          so a flat network never destroys a stored session.
 */
export async function activateLicenseKey(rawKey) {
  const key = String(rawKey || '').trim().toUpperCase()
  if (!key) return { success: false, message: 'Enter your license key.' }

  const controller = new AbortController()
  let timedOut = false
  const timer = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, VERIFY_TIMEOUT_MS)

  try {
    let { data, error } = await supabase.functions.invoke('validate-license', {
      body: { license_key: key, deviceId: getDeviceId() },
      signal: controller.signal,
    })

    if (timedOut) return { success: false, message: TIMEOUT_MESSAGE }

    // supabase-js v2 hands a non-2xx back as a FunctionsHttpError with `data`
    // null and the body on `error.context` (a Response). validate-license
    // answers a bad key with 401/403/404, so without this unwrap every
    // rejected key read as "Network error". A parse failure here means there
    // really was no body — an infra fault, not a refusal.
    if (error && !data && error.context && typeof error.context.json === 'function') {
      try {
        data = await error.context.json()
        error = null
      } catch { /* leave as a network failure below */ }
    }

    if (error && !data) {
      console.error('License validation failed:', error)
      return { success: false, message: 'Network error. Please try again later.' }
    }

    if (!data || data.success !== true) {
      // The function's own wording is more specific than anything worth writing
      // here — "locked to another device" and "not found" want different
      // reactions from the user.
      return {
        success: false,
        rejected: true,
        message: (data && (data.error || data.message)) || 'That license key was not accepted.',
      }
    }

    // Persisted under the keys the rest of the app already reads.
    const robotName = data.product_name || data.display_name || 'NovaHost AI'
    const allowedSymbols = data.allowed_symbols || []

    /* The server returns a HEX. It used to be mapped through a three-entry
       lookup — #FF6B35, #00E5FF, #A259FF — and anything else fell through to
       the raw hex, which `SWATCHES.find(name === …)` could never match. So
       every mentor colour outside those three collapsed to Crimson, silently.
       Measured against production: the four robots on the busiest device carry
       #ffbf00, #3b82f6, #3b82f6 and #ff00ff. None of them are in the map; all
       four were rendering as Crimson.

       The lookup is gone. `swatchByName` takes a preset name OR a hex and
       derives the ink and the two accent-as-text siblings for the latter with
       the same contrast maths the presets were measured with — which is what
       the spectrum picker needed too, and is why this now works at all. The
       three legacy names still resolve, because a name is still a name. */
    const swatchName = data.accent_color || 'Crimson'

    secStorage.setItem('LICENSE_KEY', data.license_key || key)
    secStorage.setItem('ROBOT_NAME', robotName)
    secStorage.setItem('AVATAR_URL', data.avatar_url || '')
    secStorage.setItem('ALLOWED_SYMBOLS', allowedSymbols)
    secStorage.setItem('LICENSE_VALID', 'true')

    /* The mentor's accent is kept under its OWN key as well as the live
       one. `ACCENT_COLOR` is also where a user's manual pick lands, so
       writing only there means the first time someone changes the accent
       the mentor's choice is gone with no way back — and the Interface
       Style sheet's "Mentor default / My override" toggle has nothing to
       restore. Written on every validation so a mentor changing their
       colour still propagates. */
    secStorage.setItem('MENTOR_ACCENT', swatchName)

    /* The live accent only follows the mentor while the user has not
       overridden it. Re-validating a licence must not silently undo a
       colour the user deliberately chose. */
    const overridden = secStorage.getItem('ACCENT_OVERRIDDEN') === 'true'
    if (!overridden) secStorage.setItem('ACCENT_COLOR', swatchName)

    const liveName = overridden ? (secStorage.getItem('ACCENT_COLOR') || swatchName) : swatchName
    accent.current = swatchByName(liveName)
    accent.robotName = robotName
    accent.avatarUrl = data.avatar_url || ''
    accent.allowedSymbols = allowedSymbols

    // A validated key is the entry gate, not entitlement — Home still needs a
    // paid subscription (authState.isVerified), which the payment screen and
    // initializeAuth() confirm separately.
    authState.keyValidated = true
    authState.reason = null
    authState.move = null

    return { success: true }
  } catch (err) {
    if (timedOut) return { success: false, message: TIMEOUT_MESSAGE }
    console.error('Unexpected error validating license key:', err)
    return { success: false, message: 'An unexpected error occurred. Please try again later.' }
  } finally {
    clearTimeout(timer)
  }
}

export function logout() {
  authState.isVerified = false
  authState.keyValidated = false
  authState.email = null
  authState.reason = null
  authState.move = null
  authState.offline = false
  authState.graceDaysLeft = null
  secStorage.removeItem('VERIFIED_EMAIL')
  secStorage.removeItem('PENDING_EMAIL')
  secStorage.removeItem('DEVICE_TOKEN')
  /* The grace material too. Leaving CHECKED_AT behind would hand the NEXT
     email to sign in on this handset up to five days of unverified access on
     the strength of the previous owner's purchase. */
  secStorage.removeItem('CHECKED_AT')
  secStorage.removeItem('MAX_OFFLINE_DAYS')
  // The key door too. Leaving these behind means initializeAuth restores the
  // session on the next launch and the sign-out silently did nothing.
  secStorage.removeItem('LICENSE_KEY')
  secStorage.removeItem('LICENSE_VALID')
  // DEVICE_ID deliberately survives: it identifies the install, not the
  // session, and minting a fresh one would read as a new device and cost the
  // user R150 to get back in.
}
