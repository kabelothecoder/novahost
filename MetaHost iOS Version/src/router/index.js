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

// ── Smart Shield Guard ────────────────────────────────────────────────────────
router.beforeEach(async (to, from, next) => {
  if (to.name === 'splash') return next()

  // Check if onboarding is completed
  const onboardingCompleted = localStorage.getItem('ONBOARDING_COMPLETED') === 'true'
  if (!onboardingCompleted && to.name !== 'onboarding') {
    return next({ name: 'onboarding' })
  }
  if (to.name === 'onboarding') return next()

  if (to.meta.public && to.name !== 'unlock') {
    return next()
  }

  // Ensure auth is initialized on first load
  if (authState.isVerifying) {
    await initializeAuth()
  }

  // Check Supabase subscription state
  if (!authState.isVerified) {
    if (to.name !== 'unlock') {
      return next({ name: 'unlock' })
    }
    return next()
  }
  if (to.name === 'unlock') return next({ name: 'home' })

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
