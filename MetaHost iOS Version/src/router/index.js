import { createRouter, createWebHashHistory } from 'vue-router'
import { secStorage } from '../utils/secStorage.js'
import { authState, initializeAuth } from '../services/authService.js'
// TEMPORARY dev-only gate bypass. The `import.meta.env.DEV &&` guard at
// its call site below makes Rollup drop this import from any production
// build. Delete this line and the marked block together.
import { devGateBypassActive } from '../utils/devGate.js'

const routes = [
  {
    path: '/',
    name: 'splash',
    component: () => import('../views/SplashView.vue'),
    meta: { transition: 'fade', public: true }
  },
  {
    path: '/unlock',
    name: 'unlock',
    component: () => import('../views/UnlockView.vue'),
    meta: { public: true }
  },
  {
    path: '/payment',
    name: 'payment',
    component: () => import('../views/PaymentView.vue'),
  },
  {
    path: '/payment-return',
    name: 'payment-return',
    component: () => import('../views/PaymentReturnView.vue'),
    meta: { public: true }
  },
  {
    path: '/onboarding',
    name: 'onboarding',
    component: () => import('../views/OnboardingView.vue'),
    meta: { public: true }
  },
  {
    path: '/home',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
  },
  {
    path: '/pairs',
    name: 'pairs',
    component: () => import('../views/PairsView.vue'),
  },
  {
    path: '/terminal',
    name: 'terminal',
    component: () => import('../views/TerminalView.vue'),
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('../views/SettingsView.vue'),
  },
  {
    path: '/arrange-widgets',
    name: 'arrange-widgets',
    component: () => import('../views/ArrangeWidgetsView.vue'),
  },
  {
    path: '/support',
    name: 'support',
    component: () => import('../views/SupportView.vue'),
  },
  {
    path: '/scanner',
    name: 'scanner',
    component: () => import('../views/ScannerView.vue'),
  },
  {
    path: '/voice',
    name: 'voice',
    component: () => import('../views/VoiceView.vue'),
  },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// ── Smart Shield Guard ────────────────────────────────────────────────────────
//
// Onboarding wizard → licence-key gate (/unlock) → email + PayFast (/payment)
// → home. Two sequential locks after the wizard:
//
//   keyValidated  the mentor-issued key has cleared validate-license
//   isVerified    a paid, device-matched purchase (check-subscription-status)
//
// Neither is skippable and the order is fixed: no key ⇒ /unlock, key but
// unpaid ⇒ /payment, both ⇒ the app. `/payment` is NOT reachable without a
// validated key — the checkout itself refuses one that does not carry a key.
router.beforeEach(async (to, from, next) => {
  if (to.name === 'splash') return next()

  // ── TEMPORARY dev-only gate bypass ─────────────────────────────────
  // `?devgate=1` (once, from the address bar) waves everything past
  // onboarding and the licence gate so the redesign can be reviewed on
  // a device. `import.meta.env.DEV &&` short-circuits to `false` in a
  // production build, which deletes devGate.js from the bundle. Remove
  // this whole block and the import above before deploying.
  if (import.meta.env.DEV && devGateBypassActive(to)) {
    if (to.name === 'onboarding' || to.name === 'unlock') return next({ name: 'home' })
    return next()
  }
  // ──────────────────────────────────────────────────────────────────

  // PayFast's landing page, and the one route nothing may redirect away from
  // — not even onboarding.
  //
  // It is reached in a browser that has usually never seen this app: a
  // standalone PWA breaks out to Safari for the cross-origin checkout, and
  // Safari keeps its own storage bucket, so ONBOARDING_COMPLETED is missing
  // and the check below would sit a paying customer down in front of the
  // setup wizard. The screen verifies nothing, so there is nothing to guard.
  if (to.name === 'payment-return') return next()

  // Check if onboarding is completed
  const onboardingCompleted = localStorage.getItem('ONBOARDING_COMPLETED') === 'true'
  if (!onboardingCompleted && to.name !== 'onboarding') {
    return next({ name: 'onboarding' })
  }
  if (to.name === 'onboarding') return next()

  // Any other public route (none today beyond the ones handled above) is let
  // through; `unlock` is handled explicitly once auth is known.
  if (to.meta.public && to.name !== 'unlock') {
    return next()
  }

  // Ensure auth is initialized on first load
  if (authState.isVerifying) {
    await initializeAuth()
  }

  // ── Lock 1: the licence key ───────────────────────────────────────────────
  // Nothing past the gate is reachable without one — `payment` included, since
  // generate-payfast-checkout refuses an ios checkout that carries no key.
  if (!authState.keyValidated) {
    return to.name === 'unlock' ? next() : next({ name: 'unlock' })
  }
  // Key is in — the gate is behind them. Send them on to whichever lock is next.
  if (to.name === 'unlock') {
    return next({ name: authState.isVerified ? 'home' : 'payment' })
  }

  // ── Lock 2: payment ───────────────────────────────────────────────────────
  // Key validated, no active purchase (or one that lives on another device —
  // PaymentView reads authState.reason to show buy vs move). Checkout is the
  // only place to go.
  if (!authState.isVerified) {
    return to.name === 'payment' ? next() : next({ name: 'payment' })
  }
  // Fully in. Don't strand a verified user on the purchase screen — the
  // scanner add-on is sold in place by ScannerPaywall.vue, nothing routes here.
  if (to.name === 'payment') {
    return next({ name: 'home' })
  }

  next()
})
