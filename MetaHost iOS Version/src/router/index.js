import { createRouter, createWebHashHistory } from 'vue-router'
import { secStorage } from '../utils/secStorage.js'
import { authState, initializeAuth } from '../services/authService.js'
// TEMPORARY dev-only gate bypass. The `import.meta.env.DEV &&` guard at
// its call site below makes Rollup drop this import from any production
// build. Delete this line and the marked block together.
import { devGateBypassActive } from '../utils/devGate.js'
import { needsHomeScreenInstall } from '../utils/install.js'

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
    /* The two value screens that sell the plan, right before checkout. */
    path: '/plan',
    name: 'plan',
    component: () => import('../views/PlanView.vue'),
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
    component: () => import('../views/ConnectView.vue'),
  },
  {
    /* Step two after linking: per-account symbol matching and risk. */
    path: '/symbols',
    name: 'symbols',
    component: () => import('../views/SymbolsView.vue'),
  },
  {
    path: '/accounts',
    name: 'accounts',
    component: () => import('../views/AccountsView.vue'),
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
    /* The doc's danger screen. A route rather than a row in Settings so
       the list of what gets erased can be read before anything is armed. */
    path: '/sign-out',
    name: 'sign-out',
    component: () => import('../views/SignOutView.vue'),
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
    /* Open positions, the chart, and what the account has actually done.
       Everything on it comes from the broker — it is the only place that
       knows a position exists. */
    path: '/trades',
    name: 'trades',
    component: () => import('../views/TradesView.vue'),
  },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// ── Smart Shield Guard ────────────────────────────────────────────────────────
//
// Home Screen install → onboarding wizard → value screens (/plan) → email +
// PayFast (/payment) → licence key, typed or claimed by code (/unlock) → home.
//
// Pay first, key second. The key is claimed AFTER paying (request-claim-code),
// so no mentor key is ever issued to someone who has not paid. Two sequential
// locks after the wizard:
//
//   isVerified    a paid, in-date, device-matched purchase (check-subscription-status)
//   keyValidated  a licence key has cleared validate-license
//
// Neither is skippable: unpaid ⇒ /plan (or straight to /payment for a renewal
// or a device move), paid but no key ⇒ /unlock, both ⇒ the app.
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

  // ── Lock −1: the Home Screen ──────────────────────────────────────────────
  // An iPhone in a Safari tab gets the install steps and nothing else — above
  // all, no checkout, which would bind the purchase to a device id the
  // installed app never presents. See utils/install.js.
  if (needsHomeScreenInstall()) {
    return to.name === 'onboarding' ? next() : next({ name: 'onboarding' })
  }

  // ── Lock 0: the wizard ────────────────────────────────────────────────────
  //
  // This used to end in `if (to.name === 'onboarding') return next()` — an
  // UNCONDITIONAL pass, taken before any of the locks below could run. Every
  // cold start lands here, because SplashView pushes to /onboarding and lets
  // the guard sort it out, so a user who had finished the wizard, cleared the
  // licence gate AND paid was dropped back into step one of the setup wizard
  // on every launch. That is the "it takes you back to onboarding after
  // closing it" report, and it was never storage being lost: ONBOARDING_COMPLETED
  // was written, read, found to be true, and then ignored.
  //
  // The flag now decides in both directions. Not completed ⇒ the wizard is the
  // only destination. Completed ⇒ the wizard is not a destination at all, and
  // the request falls through to the licence and payment locks, which forward
  // it to whichever step is genuinely outstanding.
  const onboardingCompleted = localStorage.getItem('ONBOARDING_COMPLETED') === 'true'
  if (!onboardingCompleted) {
    return to.name === 'onboarding' ? next() : next({ name: 'onboarding' })
  }

  // Any other public route (none today beyond the ones handled above) is let
  // through. `unlock` is handled explicitly once auth is known, and
  // `onboarding` is public ONLY in the sense that it needs no licence — with
  // the wizard behind them it is a step to be forwarded past, not a screen to
  // render, so it must not short-circuit here either.
  if (to.meta.public && to.name !== 'unlock' && to.name !== 'onboarding') {
    return next()
  }

  // Ensure auth is initialized on first load
  if (authState.isVerifying) {
    await initializeAuth()
  }

  // ── Lock 1: payment ───────────────────────────────────────────────────────
  // No active purchase (none yet, 30 days run out, or one that lives on
  // another device — PaymentView reads authState.reason to show buy, renew or
  // move). A first-time buyer sees the value screens first; someone renewing
  // or moving has already seen them and goes straight to checkout.
  if (!authState.isVerified) {
    if (to.name === 'plan' || to.name === 'payment') return next()
    const returning = authState.reason === 'expired' || authState.reason === 'device_mismatch'
    return next({ name: returning ? 'payment' : 'plan' })
  }

  // ── Lock 2: the licence key ───────────────────────────────────────────────
  // Paid. Now the key — typed, or claimed with a code from a mentor's site.
  if (!authState.keyValidated) {
    return to.name === 'unlock' ? next() : next({ name: 'unlock' })
  }

  // Fully in. None of the gate screens is a destination any more — falling
  // through to next() would render one over the top of an activated app. The
  // scanner's scan packs are sold in place by ScannerPaywall.vue.
  if (['unlock', 'plan', 'payment', 'onboarding'].includes(to.name)) {
    return next({ name: 'home' })
  }

  next()
})
