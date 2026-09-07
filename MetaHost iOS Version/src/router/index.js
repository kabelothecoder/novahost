import { createRouter, createWebHashHistory } from 'vue-router'
import { secStorage } from '../utils/secStorage.js'
import { authState, initializeAuth } from '../services/authService.js'

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
    path: '/parent-setup',
    name: 'parent-setup',
    component: () => import('../views/ParentSetupView.vue'),
  },
  {
    path: '/child-handoff',
    name: 'child-handoff',
    component: () => import('../views/ChildHandoffView.vue'),
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

/**
 * The gate and the purchase screen it hands off to.
 *
 * Both have to be reachable while unverified, or the gate is a dead end for
 * anyone who has not paid — which, until the entitlement check was fixed, was
 * everybody.
 */
const GATE_ROUTES = ['unlock', 'payment']

// ── Smart Shield Guard ────────────────────────────────────────────────────────
router.beforeEach(async (to, from, next) => {
  if (to.name === 'splash') return next()

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

  if (to.meta.public && !GATE_ROUTES.includes(to.name)) {
    return next()
  }

  // Ensure auth is initialized on first load
  if (authState.isVerifying) {
    await initializeAuth()
  }

  // Check Supabase subscription state
  if (!authState.isVerified) {
    if (!GATE_ROUTES.includes(to.name)) {
      return next({ name: 'unlock' })
    }
    return next()
  }
  if (to.name === 'unlock') return next({ name: 'home' })

  // Payment stays reachable after the gate opens so the setup steps below
  // cannot bounce a verified user out of a purchase already in flight. The
  // R349 scanner no longer routes here — it is sold inside the scanner screen
  // by ScannerPaywall.vue, which runs its own checkout in place.
  if (to.name === 'payment') return next()

  const parentSetupCompleted = localStorage.getItem('PARENT_SETUP_COMPLETED') === 'true'
  if (!parentSetupCompleted && to.name !== 'parent-setup') {
    return next({ name: 'parent-setup' })
  }
  if (to.name === 'parent-setup') return next()

  const childHandoffCompleted = localStorage.getItem('CHILD_HANDOFF_COMPLETED') === 'true'
  if (!childHandoffCompleted && to.name !== 'child-handoff') {
    return next({ name: 'child-handoff' })
  }
  if (to.name === 'child-handoff') return next()

  next()
})
