/**
 * ⚠️ TEMPORARY — dev-only gate bypass.  DELETE BEFORE DEPLOY.
 *
 * Lets the redesigned Home + Settings be reviewed on a real phone over
 * the local HTTPS dev server without holding a licence key or a paid
 * subscription email. It has no place in a shipped build.
 *
 * Three locks, all of which must be open for it to do anything:
 *
 *   1. `import.meta.env.DEV` — checked at the CALL SITE in
 *      router/index.js as `import.meta.env.DEV && …`. Vite replaces that
 *      with the literal `false` in `vite build`, the branch dies, and
 *      Rollup then drops THIS MODULE from the bundle entirely (nothing
 *      else imports it). Verified absent from dist/ after build.
 *   2. A stored flag — a dev running the app the normal way still meets
 *      the real gate, so the gate itself stays testable.
 *   3. The flag only turns on from an explicit URL param, never a
 *      default, so enabling it is a deliberate act.
 *
 *   Enable :  https://<host>/?devgate=1
 *   Disable:  https://<host>/?devgate=0     (or clear the site's data)
 *
 * To remove: delete this file and the marked block in router/index.js.
 */

import { secStorage } from './secStorage.js'
import { accent } from '../stores/accent.js'

const FLAG = 'DEV_GATE_BYPASS'
let initialised = false

/**
 * Give the layouts something representative to render — without this a
 * fresh device shows "MetaBot" and an empty symbol line. Only fills
 * blanks; never overwrites a real licence's identity.
 */
function seedDemoIdentity () {
  localStorage.setItem('ONBOARDING_COMPLETED', 'true')

  if (!secStorage.getItem('ROBOT_NAME')) {
    accent.robotName = 'Algo Wave EA'          // setter persists via accent.js' own watch
  }

  const symbols = secStorage.getItem('ALLOWED_SYMBOLS', true)
  if (!Array.isArray(symbols) || symbols.length === 0) {
    accent.allowedSymbols = ['XAUUSD', 'US30', 'EURUSD']
  }
}

/**
 * @param {import('vue-router').RouteLocationNormalized} [to]
 * @returns {boolean} true when the router guard should wave everything through
 */
export function devGateBypassActive (to) {
  // Once per app load: read ?devgate= from the real query string
  // (https://host/?devgate=1 — what is actually typeable on a phone) or
  // from the hash-router query (…/#/home?devgate=1).
  if (!initialised) {
    initialised = true
    const fromSearch = new URLSearchParams(window.location.search).get('devgate')
    const fromHash = to && to.query ? to.query.devgate : undefined
    const param = fromSearch ?? fromHash

    if (param === '1') {
      localStorage.setItem(FLAG, 'true')
      seedDemoIdentity()
      console.warn(
        '%c[dev] GATE BYPASS ON — no licence required. Load ?devgate=0 to restore the real gate.',
        'color:#E8A33D;font-weight:bold',
      )
    } else if (param === '0') {
      localStorage.removeItem(FLAG)
      console.warn('[dev] gate bypass OFF — real gate restored.')
    }
  }

  return localStorage.getItem(FLAG) === 'true'
}

/**
 * Whether the Chart Scanner's paywall should be waved through.
 *
 * Same three locks as the gate above — `import.meta.env.DEV` at the CALL
 * SITE, a stored flag, and the flag only set from `?devgate=1` — so this
 * dies with the rest of the module in a production build.
 *
 * ## This only unlocks the SCREEN
 *
 * `analyze-chart` enforces entitlement server-side and is entirely
 * unaffected by anything here, deliberately: its own note is that "the only
 * credential the client holds is the anon key, which ships inside the APK,
 * so a gate enforced only in the UI is a gate anyone can walk around."
 *
 * So a scan run behind this bypass still needs a real entitled identity, and
 * will answer:
 *
 *   NO_IDENTITY     — no email stored on the device; sign in on /payment.
 *   NOT_ENTITLED    — that email has not bought the R349 scanner.
 *   DEVICE_MISMATCH — the email HAS it, bound to a different handset. Clear
 *                     the binding with the `support-reset-device` function.
 *
 * That is the correct behaviour and is not something a dev flag should be
 * able to defeat. The bypass exists so the scan flow, the reading, the
 * ladder and the guardrails can be exercised without a paywall in the way.
 */
export function devScannerUnlocked () {
  return localStorage.getItem(FLAG) === 'true'
}
