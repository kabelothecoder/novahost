<template>
  <div ref="shell" class="nova gate">

    <!-- ── The bed ──────────────────────────────────────────────────────────
         Everything the glass above has to work with. The pools are placed to
         straddle the card's top and bottom edges on purpose: the same blob
         reads sharp above the edge and melted below it, and that
         discontinuity is what tells the eye the card is transparent. The
         hairline grid does the same job at a finer scale — crisp on the
         canvas, gone entirely under 36px of blur. -->
    <div class="gate-bed" aria-hidden="true">
      <span class="gate-pool gate-pool-a"></span>
      <span class="gate-pool gate-pool-b"></span>
      <span class="gate-pool gate-pool-c"></span>
      <span class="gate-grid"></span>
    </div>

    <div class="gate-scroll">
      <div class="gate-stack">

        <!-- ── Hero lens ──────────────────────────────────────────────────
             The robot is the same masked mark the onboarding vault step
             ends on, so arriving here reads as the next beat of that screen.
             While validating it grows scan rings and, on success, blooms
             into a tick. -->
        <div class="gate-lens-wrap" :class="{ 'gate-lens-wrap-busy': phase === 'validating' }">
          <span class="gate-halo" aria-hidden="true"></span>
          <span class="gate-ring gate-ring-outer" aria-hidden="true"></span>
          <span class="gate-ring gate-ring-inner" aria-hidden="true"></span>

          <template v-if="phase === 'validating' && verifyState === 'pending'">
            <span class="gate-scan gate-scan-1" aria-hidden="true"></span>
            <span class="gate-scan gate-scan-2" aria-hidden="true"></span>
            <span class="gate-scan gate-scan-3" aria-hidden="true"></span>
          </template>

          <div
            class="gate-lens"
            :class="{ 'gate-lens-ok': verifyState === 'ok', 'gate-lens-bad': verifyState === 'bad' }"
          >
            <svg v-if="verifyState === 'ok'" class="gate-lens-check" width="64" height="64" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M5 12.5 L10 17.5 L19 7" stroke="#FFFFFF" stroke-width="2.4"
                    stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <NovaLogoMark v-else :size="LENS_MARK" alt="NovaHost" />
            <span class="gate-lens-gloss" aria-hidden="true"></span>
            <span class="gate-lens-sheen" aria-hidden="true"></span>
          </div>
        </div>

        <!-- ══ PHASE 1 — license key entry ══════════════════════════════════ -->
        <form v-if="phase === 'key'" class="gate-card" novalidate @submit.prevent="handleUnlock">
          <span class="gate-card-sheen" aria-hidden="true"></span>

          <div class="gate-copy nv-stagger">
            <p class="nv-step-label">The Vault</p>
            <h1 class="nv-title gate-title">Activate your node</h1>
            <p class="nv-body gate-body">
              Enter the license key your mentor issued you. It binds to this device once activated.
            </p>
          </div>

          <div class="gate-form">
            <div class="gate-field" :class="fieldClass">
              <svg class="gate-field-glyph" width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <circle cx="7" cy="10" r="3.4" stroke="currentColor" stroke-width="1.5" />
                <path d="M10.4 10 H17.4 M15 10 V12.6 M12.8 10 V12.2"
                      stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              </svg>

              <input
                v-model="licenseKey"
                class="gate-input gate-input-key"
                type="text"
                inputmode="text"
                placeholder="XXXX-XXXX-XXXX-XXXX"
                autocomplete="off"
                autocapitalize="characters"
                autocorrect="off"
                spellcheck="false"
                enterkeyhint="go"
                aria-label="License key"
                :aria-invalid="!!errorMessage"
                @input="onKeyInput"
              >

              <button
                v-if="!keyValid"
                type="button"
                class="gate-paste"
                aria-label="Paste from clipboard"
                @click="pasteKey"
              >
                <svg width="17" height="18" viewBox="0 0 17 18" fill="none" aria-hidden="true">
                  <rect x="3.4" y="3.2" width="10.2" height="13" rx="2.6" stroke="currentColor" stroke-width="1.6" />
                  <path d="M6.4 3.2 V2.4 C6.4 1.8, 6.9 1.3, 7.5 1.3 H9.5 C10.1 1.3, 10.6 1.8, 10.6 2.4 V3.2"
                        stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                </svg>
              </button>

              <svg v-else class="gate-tick" width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <circle cx="10" cy="10" r="9" fill="var(--nv-success)" />
                <path d="M5.6 10.2 L8.6 13 L14.4 6.9" stroke="#FFFFFF" stroke-width="2"
                      stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </div>

            <Transition name="gate-err">
              <p v-if="errorMessage" class="gate-error" role="alert">
                <svg width="16" height="16" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                  <circle cx="10" cy="10" r="9" fill="var(--nv-danger)" />
                  <path d="M10 5.4 V11" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" />
                  <circle cx="10" cy="14.3" r="1.2" fill="#FFFFFF" />
                </svg>
                <span>{{ errorMessage }}</span>
              </p>
            </Transition>

            <div class="gate-cta">
              <NovaCta :disabled="!keyValid" @click="handleUnlock">Activate</NovaCta>
              <span v-if="keyValid" class="gate-cta-gloss" aria-hidden="true"></span>
            </div>
          </div>
        </form>

        <!-- ══ PHASE 2 — validation ════════════════════════════════════════ -->
        <div v-else class="gate-card gate-verify nv-stagger" role="status" aria-live="polite">
          <span class="gate-card-sheen" aria-hidden="true"></span>

          <div class="gate-copy">
            <p class="nv-step-label">The Vault</p>
            <h1 class="nv-title gate-title">{{ verifyTitle }}</h1>
          </div>

          <ol class="gate-steps">
            <li
              v-for="(label, i) in VERIFY_STEPS"
              :key="label"
              class="gate-step"
              :class="{
                'gate-step-done': stepDone(i),
                'gate-step-live': stepLive(i),
              }"
            >
              <span class="gate-step-dot" aria-hidden="true">
                <svg v-if="stepDone(i)" width="12" height="12" viewBox="0 0 20 20" fill="none">
                  <path d="M4.6 10.2 L8.4 13.6 L15.4 6.2" stroke="#FFFFFF" stroke-width="2.6"
                        stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <span v-else-if="stepLive(i)" class="gate-step-spin"></span>
              </span>
              <span class="gate-step-label">{{ label }}</span>
            </li>
          </ol>

          <Transition name="gate-err">
            <p v-if="errorMessage" class="gate-error" role="alert">
              <svg width="16" height="16" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <circle cx="10" cy="10" r="9" fill="var(--nv-danger)" />
                <path d="M10 5.4 V11" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" />
                <circle cx="10" cy="14.3" r="1.2" fill="#FFFFFF" />
              </svg>
              <span>{{ errorMessage }}</span>
            </p>
          </Transition>

          <button v-if="verifyState === 'bad'" type="button" class="gate-retry" @click="resetToKey">
            Try a different key
          </button>
        </div>

        <!-- Third material, thinnest, sitting straight on the bed. -->
        <p v-if="phase === 'key'" class="gate-note">
          <svg width="14" height="16" viewBox="0 0 16 18" fill="none" aria-hidden="true">
            <path d="M4.4 8 V5.6 C4.4 3.6, 6 2, 8 2 C10 2, 11.6 3.6, 11.6 5.6 V8"
                  stroke="var(--nv-accent)" stroke-width="1.7" stroke-linecap="round" />
            <rect x="2.6" y="7.8" width="10.8" height="8.4" rx="2.6" fill="var(--nv-accent)" />
          </svg>
          <span>Your key activates once, on one device.</span>
        </p>

      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * @description UnlockView — the licence-key gate the router drops every
 * unverified session onto, straight out of the onboarding wizard.
 *
 * ONE door: a mentor-issued licence key. There is no email fallback — every
 * user validates a key here, and only then reaches the email + PayFast screen
 * (`payment`). The old "use my subscription email instead" path and its
 * check-subscription-status call are gone from this screen; entitlement is
 * proven by paying, on the next screen, not here.
 *
 * Two phases on one route:
 *   'key'         the input form
 *   'validating'  a full-screen confirmation animation over the real
 *                 activateLicenseKey() call. On success it routes to
 *                 `payment`; on refusal it drops back to 'key'.
 *
 * Material layers each sit directly on the bed so their backdrop-filter has
 * live colour to refract: the hero lens, the card and the footnote pill. The
 * field is a recess in the card's surface, not a fourth backdrop-filter — see
 * the git history of this file for why.
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { activateLicenseKey } from '../services/authService'
import { useKeyboardInset } from '../composables/useKeyboardInset.js'
import NovaLogoMark from '../components/nova/NovaLogoMark.vue'
import NovaCta from '../components/nova/NovaCta.vue'

/** Feathers out at 54% of its box: 138.2px across, just inside the 140px
 *  lens. Any larger and the mark's pale source background spills past the
 *  rim as a smudge. */
const LENS_MARK = 128

const router = useRouter()

// Publishes --nv-kb so the shell can shrink out from under the keyboard
// instead of letting iOS cover the CTA with it.
const shell = ref(null)
useKeyboardInset(shell)

// ── Phase ─────────────────────────────────────────────────────────────────
const phase = ref('key')            // 'key' | 'validating'
const verifyState = ref('pending')  // 'pending' | 'ok' | 'bad'
const stepIndex = ref(0)
const errorMessage = ref('')

const licenseKey = ref('')

/**
 * Four groups of four, sixteen characters — the shape `generate-license`
 * actually emits (a two-letter plan code and a two-digit year, then three
 * base-36 segments).
 */
const KEY_LENGTH = 16

function formatKey (raw) {
  const bare = String(raw || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, KEY_LENGTH)
  return bare.replace(/(.{4})(?=.)/g, '$1-')
}

const keyValid = computed(() =>
  licenseKey.value.replace(/[^A-Z0-9]/gi, '').length === KEY_LENGTH
)

const fieldClass = computed(() => {
  if (errorMessage.value) return 'gate-field-error'
  return keyValid.value ? 'gate-field-valid' : ''
})

function onKeyInput (e) {
  licenseKey.value = formatKey(e.target.value)
  // Keep the DOM in step: Vue will not re-render a value it believes unchanged,
  // which leaves the separators the user typed over sitting in the field.
  e.target.value = licenseKey.value
  errorMessage.value = ''
}

async function pasteKey () {
  try {
    const text = await navigator.clipboard.readText()
    if (!text || !text.trim()) {
      errorMessage.value = 'Clipboard is empty.'
      return
    }
    licenseKey.value = formatKey(text)
    errorMessage.value = ''
  } catch {
    // Safari refuses clipboard reads outside a trusted gesture in some
    // configurations, and there is nothing to do about it but say so.
    errorMessage.value = 'Clipboard access denied — type the key instead.'
  }
}

// ── Validation animation ──────────────────────────────────────────────────
const VERIFY_STEPS = ['Reading your key', 'Checking with the Vault', 'Binding to this device']

/** Floor on how long the animation is shown, so a fast network does not make
 *  it flash past unread. The real request runs underneath it in parallel. */
const MIN_VERIFY_MS = 2200
const STEP_MS = Math.round(MIN_VERIFY_MS / VERIFY_STEPS.length)
/** Beat held on the tick before handing off to the payment screen. */
const CONFIRM_HOLD_MS = 850

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const verifyTitle = computed(() => {
  if (verifyState.value === 'ok') return 'Node activated.'
  if (verifyState.value === 'bad') return 'That key was not accepted.'
  return 'Confirming your license…'
})

const stepDone = (i) => verifyState.value === 'ok' || i < stepIndex.value
const stepLive = (i) => verifyState.value === 'pending' && i === stepIndex.value

let stepTimer = null
function advanceSteps () {
  stepIndex.value = 0
  clearInterval(stepTimer)
  stepTimer = setInterval(() => {
    if (stepIndex.value < VERIFY_STEPS.length - 1) stepIndex.value += 1
    else clearInterval(stepTimer)
  }, STEP_MS)
}

async function handleUnlock () {
  if (phase.value === 'validating') return
  errorMessage.value = ''

  if (!keyValid.value) {
    errorMessage.value = 'A license key is 16 characters, in four groups of four.'
    return
  }

  // Into the animation. activateLicenseKey() runs underneath it.
  phase.value = 'validating'
  verifyState.value = 'pending'
  advanceSteps()

  const [result] = await Promise.all([
    activateLicenseKey(licenseKey.value),
    sleep(MIN_VERIFY_MS),
  ])

  clearInterval(stepTimer)

  if (result.success) {
    stepIndex.value = VERIFY_STEPS.length
    verifyState.value = 'ok'
    // A key activation is a first run by definition; the greeting HomeView
    // plays keys off this flag, which survives through to the far side of
    // payment. Guarded — a private-mode sessionStorage throw must not strand
    // the user on a finished animation.
    try { sessionStorage.setItem('MH_FRESH_ACTIVATION', '1') } catch { /* noop */ }
    await sleep(CONFIRM_HOLD_MS)
    await goToPayment()
    return
  }

  verifyState.value = 'bad'
  errorMessage.value = result.message || 'That license key was not accepted.'
}

/**
 * Hand off to the payment screen, and never leave the user parked on the
 * "Node activated" tick if the hand-off does not take. vue-router rejects a
 * push that the guard redirected or that duplicated the current route, so the
 * rejection itself is not the signal — `currentRoute` is.
 */
async function goToPayment () {
  try {
    await router.push({ name: 'payment' })
  } catch { /* redirect / redundant-navigation rejections are expected */ }

  if (router.currentRoute.value.name === 'payment') return

  // Still on the gate. One retry through the router, then a hard hash set as
  // the last resort so a stale bundle or a wedged guard cannot dead-end here.
  try {
    await router.replace({ name: 'payment' })
  } catch { /* as above */ }

  if (router.currentRoute.value.name !== 'payment') {
    window.location.hash = '#/payment'
  }
}

function resetToKey () {
  phase.value = 'key'
  verifyState.value = 'pending'
  stepIndex.value = 0
  errorMessage.value = ''
}

// A same-route re-navigation (…/#/unlock while already here) does not remount
// this view, so a phase left at 'validating' from a previous visit would
// linger. Reset it whenever the view is (re)shown.
onMounted(() => {
  if (phase.value !== 'key') resetToKey()
})

onUnmounted(() => clearInterval(stepTimer))
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════════════════════
   SHELL
   ═══════════════════════════════════════════════════════════════════════ */
.gate {
  position: relative;
  width: 100%;
  /* Shrinks to the space the keyboard leaves, so the CTA stays on screen.
     --nv-kb is 0 while it is closed. */
  height: calc(100dvh - var(--nv-kb, 0px));
  overflow: hidden;
  background: linear-gradient(180deg, var(--nv-art-top), var(--nv-art-bottom));
  transition: height 220ms var(--nv-ease-out);
}

/* ── The bed ─────────────────────────────────────────────────────────── */
.gate-bed {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.gate-pool {
  position: absolute;
  display: block;
  border-radius: 999px;
  will-change: transform;
}

/* Crosses the card's top-left corner. */
.gate-pool-a {
  left: -14%;
  top: 12%;
  width: 74%;
  height: 34%;
  background: radial-gradient(closest-side, rgba(162, 136, 227, 0.58), rgba(162, 136, 227, 0) 76%);
  animation: nhDriftA 15s ease-in-out infinite;
}

/* Sits behind the card and spills off the right edge. */
.gate-pool-b {
  right: -20%;
  top: 40%;
  width: 82%;
  height: 40%;
  background: radial-gradient(closest-side, rgba(92, 156, 230, 0.58), rgba(92, 156, 230, 0) 74%);
  animation: nhDriftB 19s ease-in-out infinite;
}

/* Crosses the card's bottom edge and the footnote pill. */
.gate-pool-c {
  left: 4%;
  bottom: -8%;
  width: 84%;
  height: 34%;
  background: radial-gradient(closest-side, rgba(129, 146, 232, 0.42), rgba(129, 146, 232, 0) 76%);
  animation: nhDriftA 23s ease-in-out infinite reverse;
}

/* Fine structure. A blur over a smooth gradient is invisible — the material
   needs edges to smear, and these are the cheapest ones going. Faded out top
   and bottom so it never reads as graph paper. */
.gate-grid {
  position: absolute;
  inset: 0;
  display: block;
  background-image:
    repeating-linear-gradient(0deg,  rgba(20, 22, 26, 0.05) 0 1px, transparent 1px 28px),
    repeating-linear-gradient(90deg, rgba(20, 22, 26, 0.05) 0 1px, transparent 1px 28px);
  -webkit-mask: radial-gradient(120% 78% at 50% 46%, #000 30%, transparent 82%);
  mask: radial-gradient(120% 78% at 50% 46%, #000 30%, transparent 82%);
}

/* ── Layout ──────────────────────────────────────────────────────────── */
.gate-scroll {
  position: relative;
  z-index: 1;
  height: 100%;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(28px + var(--nv-safe-top)) 20px calc(28px + var(--nv-safe-bottom));
}

.gate-stack {
  width: 100%;
  max-width: 360px;
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ═══════════════════════════════════════════════════════════════════════
   HERO LENS
   ═══════════════════════════════════════════════════════════════════════ */
.gate-lens-wrap {
  position: relative;
  flex: none;
  width: 196px;
  height: 196px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  animation: nhUp 560ms var(--nv-ease) both;
}

.gate-halo {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 999px;
  background: radial-gradient(closest-side,
    rgba(92, 156, 230, 0.34),
    rgba(162, 136, 227, 0.16) 58%,
    transparent 76%);
  animation: nhGlow 5200ms ease-in-out infinite;
}

.gate-ring {
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
}

.gate-ring-outer { inset: 0;     border: 1px solid rgba(92, 156, 230, 0.26); }
.gate-ring-inner { inset: 20px;  border: 1px solid rgba(162, 136, 227, 0.24); }

/* The material itself. No overflow:hidden on it anywhere — the gloss and the
   sheen are backgrounds on absolutely positioned children carrying the same
   radius, which the browser clips for free and repaints cleanly under a
   backdrop-filter. */
.gate-lens {
  position: relative;
  width: 140px;
  height: 140px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;

  background: var(--nv-glass);
  -webkit-backdrop-filter: var(--nv-glass-blur);
  backdrop-filter: var(--nv-glass-blur);
  border: 0.5px solid var(--nv-glass-rim);
  box-shadow:
    inset 0  1px 0 var(--nv-glass-hairline),
    inset 0 -1px 0 var(--nv-glass-seat),
    var(--nv-shadow-float);

  animation: nhFloat 6400ms ease-in-out infinite;
}

.gate-lens::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 999px;
  padding: 1.2px;
  background: linear-gradient(145deg,
    rgba(255, 255, 255, 0.95) 0%,
    rgba(255, 255, 255, 0.12) 40%,
    rgba(255, 255, 255, 0.06) 60%,
    rgba(255, 255, 255, 0.78) 100%);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
          mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
          mask-composite: exclude;
  pointer-events: none;
}

/* Fixed light source, top-left, the way a real dome catches a window. */
.gate-lens-gloss {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  pointer-events: none;
  background: radial-gradient(48% 36% at 26% 16%,
    rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0) 74%);
}

.gate-lens-sheen {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  pointer-events: none;
  background: linear-gradient(104deg,
    transparent 44%,
    rgba(255, 255, 255, 0.42) 50%,
    transparent 56%);
  background-size: 300% 100%;
  animation: nhSheen 6600ms ease-in-out 900ms infinite;
}

/* ═══════════════════════════════════════════════════════════════════════
   CARD
   ═══════════════════════════════════════════════════════════════════════ */
.gate-card {
  position: relative;
  width: 100%;
  border-radius: 30px;
  padding: 28px 24px 24px;

  background: rgba(255, 255, 255, 0.55);
  -webkit-backdrop-filter: blur(34px) saturate(210%);
  backdrop-filter: blur(34px) saturate(210%);
  box-shadow:
    inset 0  1px 0 var(--nv-glass-hairline),
    inset 0 -1px 0 var(--nv-glass-seat),
    var(--nv-shadow-float),
    var(--nv-glass-bleed);

  animation: nhSheet 620ms var(--nv-ease) 80ms both;
}

/* The rim, not a border: bright where the light hits the top-left, dark
   through the middle, bright again where it exits bottom-right. */
.gate-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 30px;
  padding: 1px;
  background: linear-gradient(145deg,
    rgba(255, 255, 255, 0.95) 0%,
    rgba(255, 255, 255, 0.14) 38%,
    rgba(255, 255, 255, 0.06) 62%,
    rgba(255, 255, 255, 0.72) 100%);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
          mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
          mask-composite: exclude;
  pointer-events: none;
}

.gate-card-sheen {
  position: absolute;
  inset: 0;
  border-radius: 30px;
  pointer-events: none;
  background: linear-gradient(100deg,
    transparent 45%,
    rgba(255, 255, 255, 0.34) 50%,
    transparent 55%);
  background-size: 300% 100%;
  animation: nhSheen 9000ms ease-in-out 1400ms infinite;
}

/* Both stack above the sheen, so the highlight never washes out the copy. */
.gate-copy,
.gate-form { position: relative; }

.gate-copy { text-align: center; }
.gate-copy > .nv-step-label { margin: 0 0 10px; }
.gate-title { margin: 0 0 10px; font-size: 27px; }
.gate-body  { margin: 0; }

.gate-form {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── Field ───────────────────────────────────────────────────────────── */
/* A recess cut into the card, not a second sheet of glass: darker fill, dark
   inner shadow along the top, specular lip along the bottom. */
.gate-field {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 56px;
  padding: 0 15px;
  border-radius: 17px;
  border: 1px solid rgba(255, 255, 255, 0.55);
  background: rgba(116, 134, 160, 0.13);
  box-shadow:
    inset 0  2px 5px rgba(20, 22, 26, 0.07),
    inset 0 -1px 0 rgba(255, 255, 255, 0.66);
  transition:
    border-color 180ms var(--nv-ease-out),
    background-color 180ms linear,
    box-shadow 180ms var(--nv-ease-out);
}

/* Focus lifts the recess back up flush with the card and lights the rim. */
.gate-field:focus-within {
  border-color: rgba(92, 156, 230, 0.72);
  background: rgba(255, 255, 255, 0.62);
  box-shadow:
    inset 0 1px 2px rgba(20, 22, 26, 0.03),
    0 0 0 4px rgba(92, 156, 230, 0.15);
}

.gate-field-valid { border-color: rgba(92, 156, 230, 0.5); }

.gate-field-error,
.gate-field-error:focus-within {
  border-color: rgba(214, 69, 93, 0.6);
  background: rgba(214, 69, 93, 0.07);
}

.gate-field-error { animation: nhShake 380ms var(--nv-ease); }

.gate-field-error:focus-within {
  box-shadow:
    inset 0 1px 2px rgba(20, 22, 26, 0.03),
    0 0 0 4px rgba(214, 69, 93, 0.13);
}

.gate-field-glyph {
  flex: none;
  color: var(--nv-ink-3);
  transition: color 180ms linear;
}
.gate-field:focus-within .gate-field-glyph { color: var(--nv-accent); }
.gate-field-error .gate-field-glyph,
.gate-field-error:focus-within .gate-field-glyph { color: var(--nv-danger); }

.gate-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  padding: 16px 0;
  font-family: inherit;
  /* 16px floor, or iOS Safari zooms the viewport on focus. */
  font-size: 16px;
  font-weight: 500;
  color: var(--nv-ink);
}
.gate-input::placeholder { color: var(--nv-ink-disabled); }
.gate-input:disabled { opacity: 0.6; }

.gate-tick {
  flex: none;
  animation: nhPop 320ms var(--nv-spring) both;
}

/* ── Error ───────────────────────────────────────────────────────────── */
.gate-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 11px 13px;
  border-radius: 14px;
  background: rgba(214, 69, 93, 0.09);
  font-size: 13px;
  line-height: 18px;
  color: var(--nv-danger-text);
  text-wrap: pretty;
}
.gate-error svg { flex: none; margin-top: 1px; }

.gate-err-enter-active,
.gate-err-leave-active { transition: opacity 220ms linear, transform 220ms var(--nv-ease-out); }
.gate-err-enter-from,
.gate-err-leave-to { opacity: 0; transform: translateY(-5px); }

/* ═══════════════════════════════════════════════════════════════════════
   PHASE 2 — VALIDATION
   ═══════════════════════════════════════════════════════════════════════ */
/* Same card material as phase 1, so the swap between them reads as the same
   surface changing its contents rather than a new screen. */
.gate-verify {
  display: flex;
  flex-direction: column;
  gap: 20px;
  text-align: center;
}
.gate-verify .gate-copy { margin: 0; }

/* ── The checklist ───────────────────────────────────────────────────── */
.gate-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 13px;
  text-align: left;
}

.gate-step {
  display: flex;
  align-items: center;
  gap: 12px;
  opacity: 0.4;
  transition: opacity 260ms var(--nv-ease-out);
}
.gate-step-live,
.gate-step-done { opacity: 1; }

.gate-step-dot {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid var(--nv-dot-border);
  background: rgba(116, 134, 160, 0.1);
  transition: background-color 220ms linear, border-color 220ms linear;
}
.gate-step-live .gate-step-dot { border-color: var(--nv-accent); background: rgba(92, 156, 230, 0.12); }
.gate-step-done .gate-step-dot {
  border-color: var(--nv-accent);
  background: var(--nv-accent);
  animation: nhPop 300ms var(--nv-spring) both;
}

.gate-step-spin {
  width: 13px;
  height: 13px;
  border-radius: 999px;
  border: 2px solid rgba(92, 156, 230, 0.28);
  border-top-color: var(--nv-accent);
  animation: nhSpin 720ms linear infinite;
}

.gate-step-label {
  font-size: 14.5px;
  font-weight: 500;
  color: var(--nv-ink-2);
}
.gate-step-live .gate-step-label,
.gate-step-done .gate-step-label { color: var(--nv-ink); }

/* ── Retry ───────────────────────────────────────────────────────────── */
.gate-retry {
  align-self: center;
  min-height: 44px;
  padding: 0 18px;
  border: 0;
  border-radius: 999px;
  background: rgba(92, 156, 230, 0.12);
  color: var(--nv-accent);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 140ms var(--nv-ease-out), background-color 140ms linear;
}
.gate-retry:active { transform: scale(0.97); background: rgba(92, 156, 230, 0.2); }

/* ── Lens states ─────────────────────────────────────────────────────── */
/* The hero disc, borrowed from onboarding, reacts to the outcome. */
.gate-lens-wrap-busy .gate-halo { animation-duration: 2600ms; }

.gate-lens-ok {
  background: var(--nv-accent-grad);
  border-color: transparent;
}
.gate-lens-bad { animation: nhShake 420ms var(--nv-ease); }

.gate-lens-check { animation: nhPop 360ms var(--nv-spring) both; }

/* Concentric pulses that read as a scan, only while the check is in flight. */
.gate-scan {
  position: absolute;
  inset: 0;
  margin: auto;
  width: 140px;
  height: 140px;
  border-radius: 999px;
  border: 1.5px solid rgba(92, 156, 230, 0.5);
  pointer-events: none;
  animation: gateScan 1800ms var(--nv-ease-out) infinite;
}
.gate-scan-2 { animation-delay: 600ms; }
.gate-scan-3 { animation-delay: 1200ms; }

@keyframes gateScan {
  0%   { transform: scale(0.72); opacity: 0; }
  30%  { opacity: 0.7; }
  100% { transform: scale(1.65); opacity: 0; }
}

/* ── Key door ────────────────────────────────────────────────────────── */
/* Mono and tracked out, because a licence key is read character by character
   and proportional digits invite transcription errors. 16px floor still
   applies, or iOS Safari zooms the viewport on focus. */
.gate-input-key {
  font-family: var(--nv-mono);
  font-size: 16px;
  letter-spacing: 0.08em;
}

.gate-paste {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  border: 1px solid rgba(92, 156, 230, 0.34);
  background: rgba(92, 156, 230, 0.1);
  color: var(--nv-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 140ms var(--nv-ease-out), background-color 140ms linear;
}
.gate-paste:active { transform: scale(0.92); background: rgba(92, 156, 230, 0.18); }
.gate-paste:disabled { opacity: 0.45; cursor: default; }

/* ── CTA ─────────────────────────────────────────────────────────────── */
/* The gloss is a sibling overlay rather than something pushed into NovaCta,
   so the shared button keeps one definition across onboarding and here. */
.gate-cta {
  position: relative;
  margin-top: 4px;
}

.gate-cta-gloss {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0) 54%),
    linear-gradient(100deg, transparent 44%, rgba(255, 255, 255, 0.32) 50%, transparent 56%);
  background-size: 100% 100%, 300% 100%;
  animation: nhSheen 5200ms ease-in-out 2000ms infinite;
}

/* ── Footnote ────────────────────────────────────────────────────────── */
.gate-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  margin: 14px 0 0;
  padding: 11px 16px;
  border-radius: 999px;

  background: var(--nv-glass-thin);
  -webkit-backdrop-filter: var(--nv-glass-thin-blur);
  backdrop-filter: var(--nv-glass-thin-blur);
  border: 0.5px solid var(--nv-glass-rim);
  box-shadow:
    inset 0  1px 0 var(--nv-glass-hairline),
    inset 0 -1px 0 var(--nv-glass-seat),
    var(--nv-shadow-card);

  font-size: 12px;
  line-height: 17px;
  color: var(--nv-ink-2);
  text-wrap: pretty;
  animation: nhUp 500ms var(--nv-ease) 380ms both;
}
.gate-note svg { flex: none; }

/* ═══════════════════════════════════════════════════════════════════════
   FALLBACKS & SIZING
   ═══════════════════════════════════════════════════════════════════════ */
/* No backdrop-filter — fall back to the design's opaque white so nothing
   ever renders unreadable, and drop the structure the blur was there for. */
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .gate-lens:not(.gate-lens-ok),
  .gate-card,
  .gate-note { background: var(--nv-surface); }
  .gate-grid { display: none; }
}

/* Short handsets. The lens itself holds its size — NovaLogoMark is sized by
   an inline style from its `size` prop, so shrinking the disc under it would
   crop the mark. The halo and rings give the ground instead. */
@media (max-height: 740px) {
  .gate-lens-wrap { width: 178px; height: 178px; margin-bottom: 18px; }
  .gate-ring-inner { inset: 14px; }
}
@media (max-height: 660px) {
  .gate-lens-wrap { width: 158px; height: 158px; margin-bottom: 12px; }
  /* At this size the inner ring would land inside the disc. */
  .gate-ring-inner { display: none; }
  .gate-card { padding: 22px 20px 20px; }
  .gate-title { font-size: 24px; }
  .gate-form { margin-top: 20px; }
}
</style>
