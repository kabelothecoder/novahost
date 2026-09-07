<template>
  <div class="sp">

    <div class="sp-zone">
      <span class="sp-grid" aria-hidden="true"></span>

      <div class="sp-lock">
        <div class="sp-lock-ring">
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <rect x="4" y="10.5" width="16" height="10.5" rx="2.6" />
            <path d="M8 10.5 V7.2 A4 4 0 0 1 16 7.2 V10.5" />
          </svg>
        </div>
        <span class="sp-lock-main">SCANNER LOCKED</span>
        <span class="sp-lock-sub">ONCE-OFF UNLOCK</span>
      </div>
    </div>

    <div class="sp-card">
      <div class="sp-head">
        <span class="sp-name">AI CHART SCANNER</span>
        <span class="sp-price">R349</span>
      </div>

      <p class="sp-blurb">
        Send a screenshot of any chart and get the setup read back — direction,
        confidence, entry, stop loss and take profit.
      </p>

      <ul class="sp-list">
        <li v-for="line in FEATURES" :key="line">
          <svg width="13" height="13" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path d="M4.6 10.2 L8.4 13.6 L15.4 6.2" stroke="currentColor" stroke-width="2.4"
                  stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>{{ line }}</span>
        </li>
      </ul>

      <div class="sp-foot">
        <span class="sp-tag">ONCE-OFF · ADD-ON</span>
        <!-- The collected amount is a rand over the marketing price to absorb
             the card fee. Showing only the round number would put a different
             figure here than on the PayFast page. -->
        <span class="sp-charged">R350.00 at checkout</span>
      </div>
    </div>

    <Transition name="sp-note">
      <p v-if="notice" class="sp-notice" :class="{ 'sp-notice-bad': noticeBad }" role="status">
        {{ notice }}
      </p>
    </Transition>

    <button type="button" class="sp-cta" :disabled="busy || !email" @click="startCheckout">
      <template v-if="!busy">
        <span>UNLOCK SCANNER</span>
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2.5" stroke-linecap="round"><path d="m13 2 9 9-9 9" /><path d="M22 11H3" /></svg>
      </template>
      <template v-else>
        <span class="sp-spinner" aria-hidden="true"></span>
        <span>OPENING CHECKOUT</span>
      </template>
    </button>

    <p class="sp-secure">
      Payment is handled by PayFast. NovaHost never sees your card details.
    </p>

  </div>
</template>

<script setup>
/**
 * @description ScannerPaywall — the R349 chart-scanner add-on, sold where the
 * scanner lives rather than at the front door.
 *
 * It used to be a second plan card on PaymentView, the screen the sign-in gate
 * hands an unpaid visitor. That put a price on a feature the reader had never
 * seen, next to the app purchase they were actually there for. This screen has
 * the pitch the other one could not make: it is standing where the scanner
 * would be.
 *
 * The checkout is the same one PaymentView runs — `generate-payfast-checkout`
 * with `product: 'SCANNER'` — and the same rules apply to reading its answer:
 * a refusal is a 200 carrying a `route`, never an error, so the routes are
 * switched on explicitly rather than collapsed into one failure message.
 *
 * There is no callback to wait on. PayFast's return_url points at the
 * `payment-redirect` function and iOS breaks a standalone PWA out to Safari
 * for a cross-origin navigation anyway, so entitlement is re-checked whenever
 * this component becomes visible again — which is what actually happens when
 * the user switches back.
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { supabase } from '../utils/supabaseClient'
import { authState, verifySubscription } from '../services/authService'
import { getDeviceId } from '../utils/deviceId'

const emit = defineEmits(['unlocked'])

const FEATURES = [
  'Direction and confidence read off the chart',
  'Entry, stop loss and take profit levels',
  'Send the result straight to your terminal',
]

const email = computed(() => authState.email || '')

const busy = ref(false)
const notice = ref('')
const noticeBad = ref(false)

function say (message, bad) {
  notice.value = message
  noticeBad.value = !!bad
}

let redirecting = false

async function startCheckout () {
  if (busy.value || !email.value) return
  redirecting = false
  busy.value = true
  say('', false)

  try {
    const { data, error } = await supabase.functions.invoke('generate-payfast-checkout', {
      body: {
        email: email.value,
        deviceId: getDeviceId(),
        product: 'SCANNER',
        // Sends the payer to a page rather than the android `metahost://`
        // deep link, which in Safari is an invalid-address error.
        platform: 'ios',
      },
    })

    if (error || !data) {
      return say('That purchase could not be started. Try again in a moment.', true)
    }

    if (data.error) return say(data.error, true)

    if (data.checkout_url) {
      // Assigned rather than window.open: this runs after an await, and iOS
      // blocks a popup that is no longer attributable to the tap.
      redirecting = true
      window.location.href = data.checkout_url
      return
    }

    switch (data.route) {
      case 'SCANNER_OWNED':
        // Already paid for — nothing to sell, so open it.
        return refresh('The scanner is already unlocked for this email.')

      case 'ACTIVE_NEW_DEVICE_BLOCKED':
        // Buying here would leave them holding a scanner behind an app lock.
        // The function's own wording names the move, so it is shown as written.
        return say(data.message || 'This email is active on another device. Move it across first.', true)

      default:
        return say(data.message || 'That purchase could not be started right now.', true)
    }
  } catch (err) {
    console.error('Scanner checkout error:', err)
    say('That purchase could not be started. Check your connection and try again.', true)
  } finally {
    // Held on the redirect path: navigation is already under way, and snapping
    // the button back to ready before the page unloads reads as a failed tap.
    if (!redirecting) busy.value = false
  }
}

/**
 * Re-asks the server and lifts the lock if the scanner is now owned.
 *
 * `verifySubscription` reports on app access, not the add-on, so its return
 * value is not the answer here — `hasScanner` is what it refreshes on the way
 * through, and that is what this reads.
 */
async function refresh (fallbackMessage) {
  if (!email.value) return
  await verifySubscription(email.value)
  if (authState.hasScanner) {
    emit('unlocked')
    return true
  }
  if (fallbackMessage) say(fallbackMessage, false)
  return false
}

function onVisible () {
  // The ITN can lag the redirect by a few seconds, so a miss here is not
  // final. Nothing is said on failure, to avoid shouting "not paid" at someone
  // whose payment is still settling.
  if (document.visibilityState === 'visible' && !busy.value) refresh(null)
}

onMounted(() => document.addEventListener('visibilitychange', onVisible))
onUnmounted(() => document.removeEventListener('visibilitychange', onVisible))
</script>

<style scoped>
/* The HUD's vocabulary, not Nova's glass: this sits inside the Vision HUD
   screen, where the ground is dark and the accent is cyan. */
.sp { display: flex; flex-direction: column; gap: 14px; }

/* ── The locked scan zone, standing in for the real one ───────────────── */
.sp-zone {
  position: relative;
  width: 100%;
  aspect-ratio: 1.35;
  border-radius: 18px;
  overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  border: 1px dashed rgba(var(--accent-rgb), 0.28);
  background: rgba(var(--accent-rgb), 0.03);
}

.sp-grid {
  position: absolute; inset: 0; display: block; pointer-events: none;
  background-image:
    repeating-linear-gradient(0deg,  rgba(var(--accent-rgb), 0.07) 0 1px, transparent 1px 26px),
    repeating-linear-gradient(90deg, rgba(var(--accent-rgb), 0.07) 0 1px, transparent 1px 26px);
  -webkit-mask: radial-gradient(120% 80% at 50% 45%, #000 25%, transparent 78%);
          mask: radial-gradient(120% 80% at 50% 45%, #000 25%, transparent 78%);
}

.sp-lock {
  position: relative;
  display: flex; flex-direction: column; align-items: center; gap: 12px;
}

.sp-lock-ring {
  width: 68px; height: 68px;
  border-radius: 50%;
  border: 1.5px solid rgba(var(--accent-rgb), 0.3);
  background: rgba(var(--accent-rgb), 0.06);
  display: flex; align-items: center; justify-content: center;
  color: rgba(var(--accent-rgb), 0.6);
}

.sp-lock-main {
  font-family: 'Courier New', monospace;
  font-weight: 800; letter-spacing: 0.1em;
  color: rgba(255, 255, 255, 0.45);
}
.sp-lock-sub {
  font-family: 'Courier New', monospace;
  font-size: 0.65rem; letter-spacing: 0.12em;
  color: rgba(var(--accent-rgb), 0.5);
}

/* ── The offer ───────────────────────────────────────────────────────── */
.sp-card {
  padding: 18px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.sp-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }

.sp-name {
  font-family: 'Courier New', monospace;
  font-size: 0.82rem; font-weight: 900; letter-spacing: 0.11em;
  color: #E8F0FF;
}
.sp-price { font-size: 1.35rem; font-weight: 800; color: var(--accent); letter-spacing: -0.02em; }

.sp-blurb {
  margin: 11px 0 0;
  font-size: 0.85rem; line-height: 1.5;
  color: rgba(255, 255, 255, 0.62);
  text-wrap: pretty;
}

.sp-list { list-style: none; margin: 14px 0 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.sp-list li {
  display: flex; align-items: flex-start; gap: 9px;
  font-size: 0.8rem; line-height: 1.45;
  color: rgba(255, 255, 255, 0.55);
}
.sp-list svg { flex: none; margin-top: 3px; color: var(--accent); }

.sp-foot {
  display: flex; align-items: baseline; justify-content: space-between; gap: 10px;
  flex-wrap: wrap;
  margin-top: 15px;
  padding-top: 13px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}
.sp-tag {
  font-family: 'Courier New', monospace;
  font-size: 0.62rem; letter-spacing: 0.12em;
  color: rgba(var(--accent-rgb), 0.6);
}
.sp-charged { font-size: 0.72rem; color: rgba(255, 255, 255, 0.38); }

/* ── Notice ──────────────────────────────────────────────────────────── */
.sp-notice {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(var(--accent-rgb), 0.09);
  border: 1px solid rgba(var(--accent-rgb), 0.18);
  font-size: 0.8rem; line-height: 1.45;
  color: rgba(255, 255, 255, 0.72);
  text-wrap: pretty;
}
.sp-notice-bad {
  background: rgba(255, 0, 60, 0.08);
  border-color: rgba(255, 0, 60, 0.22);
  color: #FF7A96;
}

.sp-note-enter-active,
.sp-note-leave-active { transition: opacity 220ms linear, transform 220ms ease-out; }
.sp-note-enter-from,
.sp-note-leave-to { opacity: 0; transform: translateY(-5px); }

/* ── CTA, matching .execute-btn so the screen has one button language ─── */
.sp-cta {
  width: 100%; padding: 16px;
  border-radius: 14px;
  background: rgba(var(--accent-rgb), 0.12);
  border: 1.5px solid var(--accent);
  color: var(--accent);
  font-family: 'Courier New', monospace;
  font-size: 0.9rem; font-weight: 900; letter-spacing: 0.12em;
  display: flex; align-items: center; justify-content: center; gap: 12px;
  cursor: pointer;
  box-shadow: 0 0 28px rgba(var(--accent-rgb), 0.2);
  transition: all 0.3s ease;
}
.sp-cta:active { transform: scale(0.97); box-shadow: 0 0 40px rgba(var(--accent-rgb), 0.4); }
.sp-cta:disabled {
  opacity: 0.55;
  cursor: default;
  box-shadow: none;
  transform: none;
}

.sp-spinner {
  width: 15px; height: 15px;
  border-radius: 50%;
  border: 2px solid rgba(var(--accent-rgb), 0.3);
  border-top-color: var(--accent);
  animation: spSpin 800ms linear infinite;
}
@keyframes spSpin { to { transform: rotate(360deg); } }

.sp-secure {
  margin: 0;
  text-align: center;
  font-size: 0.68rem; line-height: 1.4;
  color: rgba(255, 255, 255, 0.3);
  text-wrap: pretty;
}
</style>
