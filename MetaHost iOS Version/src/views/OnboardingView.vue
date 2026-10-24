<template>
  <div ref="shell" class="nova ob">
    <NovaProgressRail :pct="pct" />

    <!-- Full-bleed art, so the sheet has live content to blur. -->
    <NovaArtPane :art-key="step" :reserve="reserve">
      <WelcomeArt          v-if="step === 0" />
      <VisionArt           v-else-if="step === 1" />
      <BrokerArt           v-else-if="step === 2" />
      <SilentPrecisionArt  v-else-if="step === 3" />
      <NotifPreviewArt     v-else-if="step === 4" :preview="notifPreview" />
      <CandleArt           v-else-if="step === 5" />
      <VaultArt            v-else-if="step === 6" :locked="locked" />
      <InstallArt          v-else />
    </NovaArtPane>

    <NovaSheet
      ref="sheetRef"
      :sheet-key="step"
      :label="'Step ' + String(step + 1).padStart(2, '0')"
      :title="title"
      :body="body"
      :tall="isTall"
    >
      <!-- 05 — display name -->
      <NovaNameField
        v-if="step === 4"
        v-model="name"
        :valid="nameValid"
        :show-error="showNameError"
        :helper="helper"
        @update:model-value="touched = true"
      />

      <!-- 06 — trading style -->
      <div v-else-if="step === 5" class="ob-styles" role="radiogroup" aria-label="Trading style">
        <NovaStyleOption
          v-for="s in STYLES"
          :key="s.id"
          :label="s.label"
          :note="s.note"
          :selected="style === s.id"
          @select="style = s.id"
        />
      </div>

      <!-- 08 — add to home screen -->
      <ol v-else-if="step === INSTALL_STEP" class="ob-howto">
        <li v-for="(line, i) in INSTALL_STEPS" :key="i" class="ob-howto-row">
          <span class="ob-howto-num">{{ i + 1 }}</span>
          <span class="ob-howto-text" v-html="line" />
        </li>
      </ol>

      <template #action>
        <NovaCta :disabled="!canAdvance" @click="advance">
          {{ ctaLabel }}
        </NovaCta>
      </template>
    </NovaSheet>
  </div>
</template>

<script setup>
/**
 * @description OnboardingView — the 7-step NovaHost onboarding, ported from
 * the Android build and the `NovaHost - Onboarding.dc.html` design, rendered
 * in the light Nova palette with Apple glass materials.
 *
 * Wiring is deliberately unchanged from the previous version: the same two
 * localStorage keys the router guard reads (DISPLAY_NAME, ONBOARDING_COMPLETED)
 * are written on finish. Two deviations, both flagged:
 *   - the old code pushed to a route named 'activate' that was never
 *     registered, so finishing threw. It now exits to 'home', which is where
 *     the design's handoff card says the flow lands and where the auth guard
 *     would redirect anyway.
 *   - TRADING_STYLE is persisted alongside, so step 06 isn't collecting a
 *     value that goes nowhere. Same mechanism, no new dependency.
 */
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboardInset } from '../composables/useKeyboardInset.js'
import { needsHomeScreenInstall } from '../utils/install.js'

import NovaProgressRail from '../components/nova/NovaProgressRail.vue'
import NovaArtPane      from '../components/nova/NovaArtPane.vue'
import NovaSheet        from '../components/nova/NovaSheet.vue'
import NovaCta          from '../components/nova/NovaCta.vue'
import NovaNameField    from '../components/nova/NovaNameField.vue'
import NovaStyleOption  from '../components/nova/NovaStyleOption.vue'

import WelcomeArt         from '../components/nova/art/WelcomeArt.vue'
import VisionArt          from '../components/nova/art/VisionArt.vue'
import BrokerArt          from '../components/nova/art/BrokerArt.vue'
import SilentPrecisionArt from '../components/nova/art/SilentPrecisionArt.vue'
import NotifPreviewArt    from '../components/nova/art/NotifPreviewArt.vue'
import CandleArt          from '../components/nova/art/CandleArt.vue'
import VaultArt           from '../components/nova/art/VaultArt.vue'
import InstallArt         from '../components/nova/art/InstallArt.vue'

const router = useRouter()

// Publishes --nv-kb so the sheet can lift clear of the keyboard on step 05.
const shell = ref(null)
useKeyboardInset(shell)

const MAX_NAME   = 18
const LOCK_MS    = 1800   // design prop `lockMs`, default 1800
const VAULT_STEP  = 6
const INSTALL_STEP = 7

/**
 * iPhone/iPad in a Safari tab: welcome, then straight to the install steps,
 * and nothing after them. Not skippable — the tab cannot pay or run the app
 * (see utils/install.js), and the Home Screen app keeps separate storage, so
 * any name or style typed here would be lost on install anyway. The full
 * wizard runs once they open it from the Home Screen, where this is false.
 */
const needsInstallStep = needsHomeScreenInstall()

/** The steps actually walked, in order. */
const SEQUENCE = needsInstallStep
  ? [0, INSTALL_STEP]
  : [0, 1, 2, 3, 4, 5, VAULT_STEP]

const TOTAL = SEQUENCE.length
const LAST_STEP = SEQUENCE[SEQUENCE.length - 1]

/** Set once they tap "I've added it" in the tab — the cue to go and open it. */
const installNudged = ref(false)

const INSTALL_STEPS = [
  'Tap the <b>Share</b> icon in Safari’s toolbar',
  'Scroll down and pick <b>Add to Home Screen</b>',
  'Tap <b>Add</b> — then open NovaHost from your home screen',
]

const STYLES = [
  { id: 'Scalp', label: 'Scalp', note: 'Minutes in, minutes out' },
  { id: 'Day',   label: 'Day',   note: 'Opened and closed same session' },
  { id: 'Swing', label: 'Swing', note: 'Held for days at a time' },
]

const COPY = [
  {
    title: 'Welcome to NovaHost.',
    body: 'Institutional-grade trading technology, built for your pocket. Let’s get your engine started.',
    cta: 'Start Setup',
  },
  {
    title: 'AI-Powered Vision.',
    body: 'Your mentor hosts a bot with AI-powered vision. Send it a screenshot of any chart and it reads the setup back to you — entry, Stop Loss and Take Profit.',
    cta: 'Next',
  },
  {
    title: 'Connect Any Broker.',
    body: 'NovaHost doesn’t lock you in. Bring the MT4 or MT5 account you already trade with, and keep it.',
    cta: 'Next',
  },
  {
    title: 'Silent Precision.',
    body: 'Close the app, let the screen go dark, put the phone in your pocket. Your bot keeps reading the chart without it.',
    cta: 'Next',
  },
  {
    title: 'What should it call you?',
    body: 'Your display name is how the bot addresses you in every notification it sends.',
    cta: 'Continue',
  },
  {
    title: 'How do you trade?',
    body: 'We’ll pre-configure your chart scanner based on your style.',
    cta: 'Continue',
  },
  // 07 is dual-state; title/body resolved below.
  { title: '', body: '', cta: 'Enter NovaHost' },
  {
    title: 'Add NovaHost to your Home Screen.',
    body: 'NovaHost only runs as an app — full screen, alerts on, and your licence locked to this phone. Add it, then open it from your Home Screen to finish setup.',
    cta: 'I’ve added it',
  },
]

// ── State ─────────────────────────────────────────────────────
const step    = ref(0)
const name    = ref('')
const touched = ref(false)
const style   = ref(null)
const locked  = ref(false)

// ── Display-name validation (design's `validity()`) ───────────
const validity = computed(() => {
  const v = name.value.trim()
  if (!v) return 'empty'
  if (!/^[A-Za-z0-9][A-Za-z0-9 .'-]*$/.test(v)) return 'chars'
  if (v.length < 2) return 'short'
  return 'ok'
})

const nameValid = computed(() => validity.value === 'ok')

const showNameError = computed(() =>
  touched.value && (validity.value === 'chars' || validity.value === 'short')
)

const helper = computed(() => {
  if (!showNameError.value) return 'Shown in notifications, never to anyone else.'
  return validity.value === 'chars'
    ? 'Letters and numbers only — no symbols.'
    : 'Use at least 2 characters.'
})

const notifPreview = computed(() => {
  const shown = name.value.trim() || 'trader'
  return nameValid.value
    ? `${shown}, your Day setup on EURUSD just filled.`
    : 'Your setup on EURUSD just filled.'
})

// ── Step chrome ───────────────────────────────────────────────
const pct    = computed(() => Math.round(((SEQUENCE.indexOf(step.value) + 1) / TOTAL) * 100))
const isTall = computed(() =>
  step.value === 4 || step.value === 5 || step.value === INSTALL_STEP
)

/**
 * How much of the shell the sheet actually covers.
 *
 * This is measured rather than assumed. A fixed percentage was wrong the
 * moment content grew past it — on an SE the step-08 sheet needs 76% while
 * `isTall` claimed 62%, so the artwork centred itself 55px underneath the
 * sheet and got cropped. Measuring also covers Dynamic Type and long
 * validation messages for free.
 */
const sheetRef = ref(null)
const measured = ref(null)
let ro = null

function measure () {
  const el = sheetRef.value?.$el
  const host = shell.value
  if (!el || !host) return
  const h = el.getBoundingClientRect().height
  const vh = host.clientHeight || window.innerHeight
  if (!vh) return
  // +2 so the art never sits flush against the sheet's top edge.
  measured.value = Math.min(92, Math.ceil((h / vh) * 100) + 2)
}

function rebindObserver () {
  const el = sheetRef.value?.$el
  if (!ro || !el) return
  ro.disconnect()
  ro.observe(el)
  measure()
}

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined') {
    ro = new ResizeObserver(measure)
  }
  nextTick(rebindObserver)
  window.addEventListener('resize', measure)
})

onUnmounted(() => {
  ro?.disconnect()
  window.removeEventListener('resize', measure)
})

// The sheet is keyed by step, so each step mounts a fresh element to watch.
watch(step, () => { measured.value = null; nextTick(rebindObserver) })

const reserve = computed(() => measured.value ?? (isTall.value ? 62 : 44))

const title = computed(() =>
  step.value === VAULT_STEP
    ? (locked.value ? 'Vault secured.' : 'Securing Your Vault.')
    : COPY[step.value].title
)

const body = computed(() => {
  if (step.value === INSTALL_STEP && installNudged.value) {
    return 'Now close this Safari tab and open NovaHost from your Home Screen. Setup continues there.'
  }
  if (step.value !== VAULT_STEP) return COPY[step.value].body
  return locked.value
    ? 'NovaHost is now locked to this device. Your access and your licence will not run anywhere else.'
    : 'For enterprise-grade security, NovaHost is locking exclusively to this physical device.'
})

const ctaLabel = computed(() => COPY[step.value].cta)

const canAdvance = computed(() => {
  if (step.value === 4) return nameValid.value
  if (step.value === 5) return !!style.value
  if (step.value === VAULT_STEP) return locked.value
  return true
})

// ── The vault bind ────────────────────────────────────────────
let lockTimer = null

watch(step, (s) => {
  clearTimeout(lockTimer)
  if (s === VAULT_STEP) {
    locked.value = false
    lockTimer = setTimeout(() => { locked.value = true }, LOCK_MS)
  }
})

onUnmounted(() => clearTimeout(lockTimer))

// ── Navigation ────────────────────────────────────────────────
function advance () {
  if (!canAdvance.value) return

  if (step.value !== LAST_STEP) {
    step.value = SEQUENCE[SEQUENCE.indexOf(step.value) + 1]
    return
  }

  // The tab never finishes: there is nowhere for it to go. It only points
  // the user at the Home Screen icon they just made.
  if (needsInstallStep) {
    installNudged.value = true
    return
  }

  finish()
}

/**
 * @description Persists onboarding output and hands off to Home.
 * Keys match what the router guard in router/index.js reads.
 */
function finish () {
  localStorage.setItem('DISPLAY_NAME', name.value.trim() || 'ALPHA TRADER')
  if (style.value) localStorage.setItem('TRADING_STYLE', style.value)
  localStorage.setItem('ONBOARDING_COMPLETED', 'true')

  router.push({ name: 'home' })
}
</script>

<style scoped>
.ob {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  /* Shrink to the space the keyboard leaves, so the sheet and its CTA stay
     on screen instead of being covered. --nv-kb is 0 when it is closed. */
  height: calc(100dvh - var(--nv-kb, 0px));
  overflow: hidden;
  background: var(--nv-canvas);
  transition: height 220ms var(--nv-ease-out);
}

.ob-styles {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* -- Step 08 how-to list -------------------------------------------- */
.ob-howto {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ob-howto-row {
  display: flex;
  align-items: flex-start;
  gap: 13px;
}

.ob-howto-num {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--nv-accent-grad);
  color: var(--nv-on-accent);
  font-size: 13px;
  font-weight: 600;
  line-height: 24px;
  text-align: center;
}

.ob-howto-text {
  font-size: 15px;
  line-height: 24px;
  color: var(--nv-ink-2);
}

.ob-howto-text :deep(b) {
  font-weight: 600;
  color: var(--nv-ink);
}
</style>
