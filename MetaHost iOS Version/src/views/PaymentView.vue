<template>
  <div ref="shell" class="nova pay">

    <div class="pay-bed" aria-hidden="true">
      <span class="pay-pool pay-pool-a"></span>
      <span class="pay-pool pay-pool-b"></span>
      <span class="pay-pool pay-pool-c"></span>
      <span class="pay-grid"></span>
    </div>

    <div class="pay-scroll">
      <div class="pay-stack">

        <div class="pay-head">
          <div class="pay-lens">
            <NovaLogoMark :size="76" alt="NovaHost" />
            <span class="pay-lens-gloss" aria-hidden="true"></span>
          </div>
          <div class="pay-head-copy nv-stagger">
            <p class="nv-step-label">{{ isMove ? 'Device move' : 'The Vault' }}</p>
            <h1 class="nv-title pay-title">{{ title }}</h1>
            <p class="nv-body pay-body">{{ body }}</p>
          </div>
        </div>

        <p v-if="email" class="pay-for">
          <svg width="14" height="14" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <rect x="2.1" y="4.3" width="15.8" height="11.4" rx="3.2" stroke="currentColor" stroke-width="1.6" />
            <path d="M3.4 6.4 L10 11 L16.6 6.4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>{{ email }}</span>
        </p>

        <!-- ── Plans ──────────────────────────────────────────────────────-->
        <div class="pay-plans" role="radiogroup" :aria-label="isMove ? 'Device move' : 'Choose what to buy'">
          <button
            v-for="p in plans"
            :key="p.id"
            type="button"
            role="radio"
            :aria-checked="selected === p.id"
            class="pay-plan"
            :class="{ 'pay-plan-on': selected === p.id }"
            :disabled="busy"
            @click="selected = p.id"
          >
            <span class="pay-plan-rim" aria-hidden="true"></span>
            <span class="pay-plan-top">
              <span class="pay-plan-name">{{ p.name }}</span>
              <span class="pay-plan-price">{{ p.price }}</span>
            </span>
            <span class="pay-plan-blurb">{{ p.blurb }}</span>
            <span class="pay-plan-foot">
              <span class="pay-plan-tag">{{ p.note }}</span>
              <!-- The collected amount is a rand over the marketing price to
                   absorb the card fee. Showing only the round number would put
                   a different figure on this screen than on the PayFast page. -->
              <span class="pay-plan-charged">{{ p.charged }} at checkout</span>
            </span>
            <span class="pay-plan-tick" :class="{ 'pay-plan-tick-on': selected === p.id }" aria-hidden="true">
              <svg v-if="selected === p.id" width="13" height="13" viewBox="0 0 20 20" fill="none">
                <path d="M4.6 10.2 L8.4 13.6 L15.4 6.2" stroke="#FFFFFF" stroke-width="2.6"
                      stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
          </button>
        </div>

        <!-- ── The move needs proof of mailbox control before it will take
             R150 and evict whoever is on the other handset. ──────────────-->
        <div v-if="needsCode" class="pay-code">
          <label class="pay-code-label" for="pay-move-code">6-digit code</label>
          <div class="pay-code-row">
            <input
              id="pay-move-code"
              v-model="moveCode"
              class="pay-code-input"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              placeholder="000000"
              :disabled="busy"
              @input="notice = ''"
            >
            <button type="button" class="pay-code-resend" :disabled="busy || codeSending" @click="sendCode">
              {{ codeSending ? 'Sending…' : (codeSent ? 'Resend' : 'Send code') }}
            </button>
          </div>
          <p class="pay-code-help">{{ codeHelp }}</p>
        </div>

        <Transition name="pay-note">
          <p v-if="notice" class="pay-notice" :class="{ 'pay-notice-bad': noticeBad }" role="status">
            <span>{{ notice }}</span>
          </p>
        </Transition>

        <div class="pay-cta">
          <NovaCta :busy="busy" :disabled="!canPay" @click="startCheckout">
            <span v-if="!busy">{{ ctaLabel }}</span>
            <span v-else class="pay-cta-busy">
              <span class="pay-spinner"></span>
              <span>Opening checkout</span>
            </span>
          </NovaCta>
          <span v-if="!busy && canPay" class="pay-cta-gloss" aria-hidden="true"></span>
        </div>

        <p class="pay-secure">
          <svg width="13" height="15" viewBox="0 0 16 18" fill="none" aria-hidden="true">
            <path d="M4.4 8 V5.6 C4.4 3.6, 6 2, 8 2 C10 2, 11.6 3.6, 11.6 5.6 V8"
                  stroke="var(--nv-accent)" stroke-width="1.7" stroke-linecap="round" />
            <rect x="2.6" y="7.8" width="10.8" height="8.4" rx="2.6" fill="var(--nv-accent)" />
          </svg>
          <span>Payment is handled by PayFast. NovaHost never sees your card details.</span>
        </p>

        <button type="button" class="pay-back" @click="router.replace({ name: 'unlock' })">
          Back to sign in
        </button>

      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * @description PaymentView — what the gate hands off to when an email has no
 * active purchase, or holds one that lives on another device.
 *
 * Prices are decided server-side by `generate-payfast-checkout`; the labels
 * here exist only so the screen can be drawn before that call is made. The
 * function is also the authority on *whether* a purchase is allowed at all —
 * it refuses with a `route` rather than an error, and every one of those
 * refusals is a 200. Treating them as failures would collapse "you already own
 * this", "you moved it last week" and "the network died" into one message, so
 * they are switched on explicitly below.
 *
 * PayFast's return_url points at the `payment-redirect` function, not back
 * into this app, and iOS breaks a standalone PWA out to Safari for a
 * cross-origin navigation anyway. So there is no callback to wait on: the
 * screen re-checks entitlement whenever it becomes visible again, which is
 * what actually happens when the user switches back.
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { supabase } from '../utils/supabaseClient'
import { authState, verifySubscription } from '../services/authService'
import { getDeviceId } from '../utils/deviceId'
import { useKeyboardInset } from '../composables/useKeyboardInset.js'
import NovaLogoMark from '../components/nova/NovaLogoMark.vue'
import NovaCta from '../components/nova/NovaCta.vue'

const router = useRouter()

const shell = ref(null)
useKeyboardInset(shell)

const email = computed(() => authState.email || '')

/** The gate got a `device_mismatch`: they have paid, just not here. */
const isMove = computed(() => authState.reason === 'device_mismatch')

/**
 * Only app access is sold here.
 *
 * The R349 chart scanner used to sit alongside it and no longer does. It is an
 * add-on to a product the visitor has not bought yet, and asking someone at the
 * front door to price a feature they have never seen is a worse pitch than
 * showing it to them where it lives. It is sold inside the scanner screen
 * instead — see ScannerPaywall.vue, which runs this same checkout with
 * `product: 'SCANNER'`.
 */
const BUY_PLANS = [
  {
    id: 'APP',
    name: 'App access',
    price: 'R599',
    charged: 'R600.00',
    note: 'Once-off · lifetime',
    blurb: 'Unlocks NovaHost on this device — cloud execution, broker connect and live signals.',
  },
]

const MOVE_PLAN = [
  {
    id: 'REACTIVATE',
    name: 'Move to this device',
    price: 'R150',
    charged: 'R150.00',
    note: 'Once-off',
    blurb: 'Releases your licence from the device it is on now and binds it here instead.',
  },
]

const plans = computed(() => (isMove.value ? MOVE_PLAN : BUY_PLANS))
const selected = ref(isMove.value ? 'REACTIVATE' : 'APP')

const busy = ref(false)
const notice = ref('')
const noticeBad = ref(false)

const needsCode = ref(false)
const moveCode = ref('')
const codeSent = ref(false)
const codeSending = ref(false)

const title = computed(() => (isMove.value ? 'Move your licence.' : 'Get NovaHost.'))

const body = computed(() => {
  if (isMove.value) {
    return 'This purchase is active on another device. Moving it releases that one and binds NovaHost here.'
  }
  return 'One once-off payment, no subscription. Your purchase binds to this device and stays yours.'
})

const ctaLabel = computed(() => (needsCode.value ? 'Confirm & continue' : 'Continue to secure checkout'))

const canPay = computed(() => {
  if (!email.value) return false
  if (needsCode.value) return /^\d{6}$/.test(moveCode.value.trim())
  return !!selected.value
})

const codeHelp = computed(() => {
  if (!codeSent.value) return `We'll email a code to ${email.value} to confirm the licence is yours.`
  return `Sent to ${email.value}. It expires shortly.`
})

// ── Checkout ───────────────────────────────────────────────────────────────
let redirecting = false

async function startCheckout () {
  if (busy.value || !canPay.value) return
  redirecting = false
  busy.value = true
  notice.value = ''
  noticeBad.value = false

  try {
    const { data, error } = await supabase.functions.invoke('generate-payfast-checkout', {
      body: {
        email: email.value,
        deviceId: getDeviceId(),
        product: selected.value,
        // Decides what PayFast sends the browser back to. Android has a
        // `metahost://` scheme to deep link into; we do not, and being sent to
        // one is the "invalid address" page every iOS payer used to land on.
        // Saying `ios` gets `payment-redirect` to serve a real page instead.
        platform: 'ios',
        ...(needsCode.value ? { move_code: moveCode.value.trim() } : {}),
      },
    })

    if (error || !data) {
      return say('That purchase could not be started. Try again in a moment.', true)
    }

    if (data.error) return say(data.error, true)

    // A checkout_url is the only success. Everything else is a refusal the
    // function chose to explain, so it is shown as written rather than
    // reworded — the copy already knows about cooldown dates and try counts.
    if (data.checkout_url) {
      // Assigned rather than window.open: this runs after an await, and iOS
      // blocks a popup that is no longer attributable to the tap.
      redirecting = true
      window.location.href = data.checkout_url
      return
    }

    switch (data.route) {
      case 'ACTIVE_SAME_DEVICE':
        // Already entitled here — nothing to sell, so let them in.
        return finishIfEntitled('App access is already active on this device.')

      // SCANNER_OWNED / ACTIVE_NEW_DEVICE_BLOCKED are not handled here: this
      // screen only ever asks for APP or REACTIVATE, so the function cannot
      // answer with either. ScannerPaywall.vue owns those two.

      case 'MOVE_CODE_REQUIRED':
        needsCode.value = true
        // A first pass has no code yet; a later one means it expired or was
        // exhausted, and the function says which.
        if (!codeSent.value) await sendCode()
        else say(data.message || 'Enter the code we emailed you.', true)
        return

      case 'MOVE_CODE_INVALID':
        needsCode.value = true
        return say(data.message || 'That code is not right.', true)

      default:
        return say(data.message || 'That purchase could not be started right now.', true)
    }
  } catch (err) {
    console.error('Checkout error:', err)
    say('That purchase could not be started. Check your connection and try again.', true)
  } finally {
    // Held on the redirect path: navigation is already under way, and
    // snapping the button back to ready before the page unloads reads as the
    // tap having failed.
    if (!redirecting) busy.value = false
  }
}

// ── The move code ──────────────────────────────────────────────────────────
async function sendCode () {
  if (codeSending.value) return
  codeSending.value = true
  try {
    const { data, error } = await supabase.functions.invoke('request-device-move', {
      body: { email: email.value, deviceId: getDeviceId() },
    })

    if (error || !data || data.success === false) {
      return say((data && data.error) || 'Could not send the code. Try again.', true)
    }

    codeSent.value = true
    say(`Code sent to ${email.value}.`, false)
  } catch (err) {
    console.error('request-device-move failed:', err)
    say('Could not send the code. Check your connection.', true)
  } finally {
    codeSending.value = false
  }
}

function say (message, bad) {
  notice.value = message
  noticeBad.value = !!bad
}

/**
 * Re-checks entitlement and leaves if it now passes.
 *
 * This is the whole return path. PayFast hands off to `payment-redirect`, so
 * the app never gets a callback — the user just comes back to it, and that is
 * the moment worth re-asking on.
 */
async function finishIfEntitled (fallbackMessage) {
  if (!email.value) return
  const result = await verifySubscription(email.value)
  if (result.success) {
    router.replace({ name: 'home' })
    return true
  }
  if (fallbackMessage) say(fallbackMessage, false)
  return false
}

function onVisible () {
  // The ITN can lag the redirect by a few seconds, so a miss here is not
  // final — the gate will ask again. Nothing is said on failure to avoid
  // shouting "not paid" at someone whose payment is still settling.
  if (document.visibilityState === 'visible' && !busy.value) finishIfEntitled(null)
}

onMounted(() => {
  // Arriving with no email at all means the gate was skipped; it owns that
  // input, so send them back rather than growing a second copy of it here.
  if (!email.value) router.replace({ name: 'unlock' })
  document.addEventListener('visibilitychange', onVisible)
})

onUnmounted(() => document.removeEventListener('visibilitychange', onVisible))
</script>

<style scoped>
/* Same material vocabulary as the gate: a living bed with structure, then
   glass over it. See UnlockView for why the pools straddle the card edges. */
.pay {
  position: relative;
  width: 100%;
  height: calc(100dvh - var(--nv-kb, 0px));
  overflow: hidden;
  background: linear-gradient(180deg, var(--nv-art-top), var(--nv-art-bottom));
  transition: height 220ms var(--nv-ease-out);
}

.pay-bed { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }

.pay-pool { position: absolute; display: block; border-radius: 999px; will-change: transform; }

.pay-pool-a {
  left: -16%; top: 6%; width: 76%; height: 30%;
  background: radial-gradient(closest-side, rgba(162, 136, 227, 0.56), rgba(162, 136, 227, 0) 76%);
  animation: nhDriftA 16s ease-in-out infinite;
}
.pay-pool-b {
  right: -22%; top: 36%; width: 84%; height: 36%;
  background: radial-gradient(closest-side, rgba(92, 156, 230, 0.56), rgba(92, 156, 230, 0) 74%);
  animation: nhDriftB 21s ease-in-out infinite;
}
.pay-pool-c {
  left: 2%; bottom: -10%; width: 86%; height: 32%;
  background: radial-gradient(closest-side, rgba(129, 146, 232, 0.42), rgba(129, 146, 232, 0) 76%);
  animation: nhDriftA 25s ease-in-out infinite reverse;
}

.pay-grid {
  position: absolute; inset: 0; display: block;
  background-image:
    repeating-linear-gradient(0deg,  rgba(20, 22, 26, 0.05) 0 1px, transparent 1px 28px),
    repeating-linear-gradient(90deg, rgba(20, 22, 26, 0.05) 0 1px, transparent 1px 28px);
  -webkit-mask: radial-gradient(120% 78% at 50% 42%, #000 30%, transparent 82%);
          mask: radial-gradient(120% 78% at 50% 42%, #000 30%, transparent 82%);
}

.pay-scroll {
  position: relative;
  z-index: 1;
  height: 100%;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  display: flex;
  justify-content: center;
  padding: calc(30px + var(--nv-safe-top)) 20px calc(30px + var(--nv-safe-bottom));
}

.pay-stack {
  width: 100%;
  max-width: 380px;
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

/* ── Header ──────────────────────────────────────────────────────────── */
.pay-head { display: flex; flex-direction: column; align-items: center; text-align: center; }

.pay-lens {
  position: relative;
  width: 86px; height: 86px;
  border-radius: 999px;
  display: flex; align-items: center; justify-content: center;
  background: var(--nv-glass);
  -webkit-backdrop-filter: var(--nv-glass-blur);
          backdrop-filter: var(--nv-glass-blur);
  border: 0.5px solid var(--nv-glass-rim);
  box-shadow:
    inset 0  1px 0 var(--nv-glass-hairline),
    inset 0 -1px 0 var(--nv-glass-seat),
    var(--nv-shadow-raised);
  animation: nhFloat 6400ms ease-in-out infinite;
}

.pay-lens-gloss {
  position: absolute; inset: 0; border-radius: 999px; pointer-events: none;
  background: radial-gradient(48% 36% at 26% 16%, rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0) 74%);
}

.pay-head-copy { margin-top: 18px; }
.pay-head-copy > .nv-step-label { margin: 0 0 9px; }
.pay-title { margin: 0 0 10px; font-size: 26px; }
.pay-body { margin: 0; }

.pay-for {
  display: flex; align-items: center; justify-content: center; gap: 7px;
  margin: 16px auto 0;
  padding: 7px 14px;
  border-radius: 999px;
  background: var(--nv-glass-thin);
  -webkit-backdrop-filter: var(--nv-glass-thin-blur);
          backdrop-filter: var(--nv-glass-thin-blur);
  border: 0.5px solid var(--nv-glass-rim);
  box-shadow: inset 0 1px 0 var(--nv-glass-hairline), var(--nv-shadow-card);
  font-size: 12.5px;
  color: var(--nv-ink-2);
  max-width: 100%;
}
.pay-for svg { color: var(--nv-ink-3); }
.pay-for span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ── Plan cards ──────────────────────────────────────────────────────── */
.pay-plans { display: flex; flex-direction: column; gap: 12px; margin-top: 22px; }

.pay-plan {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 17px 46px 15px 18px;
  border: 0;
  border-radius: 22px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;

  background: rgba(255, 255, 255, 0.55);
  -webkit-backdrop-filter: blur(30px) saturate(210%);
          backdrop-filter: blur(30px) saturate(210%);
  box-shadow:
    inset 0  1px 0 var(--nv-glass-hairline),
    inset 0 -1px 0 var(--nv-glass-seat),
    var(--nv-shadow-card);
  transition: transform 160ms var(--nv-ease-out), box-shadow 180ms var(--nv-ease-out);
  animation: nhUp 460ms var(--nv-ease) both;
}
.pay-plan:nth-child(2) { animation-delay: 80ms; }
.pay-plan:active { transform: scale(0.985); }
.pay-plan:disabled { cursor: default; }

/* Rim, not a border — bright where the light lands, dark through the middle. */
.pay-plan-rim {
  position: absolute; inset: 0; border-radius: 22px; padding: 1px; pointer-events: none;
  background: linear-gradient(145deg,
    rgba(255, 255, 255, 0.92) 0%,
    rgba(255, 255, 255, 0.14) 40%,
    rgba(255, 255, 255, 0.06) 62%,
    rgba(255, 255, 255, 0.7) 100%);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
          mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
          mask-composite: exclude;
}

.pay-plan-on {
  background: rgba(255, 255, 255, 0.68);
  box-shadow:
    inset 0 1px 0 var(--nv-glass-hairline),
    0 0 0 1.5px rgba(92, 156, 230, 0.55),
    var(--nv-shadow-raised);
}

.pay-plan-top { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.pay-plan-name { font-size: 16px; font-weight: 600; color: var(--nv-ink); letter-spacing: -0.2px; }
.pay-plan-price { font-size: 19px; font-weight: 700; color: var(--nv-ink); letter-spacing: -0.4px; }
.pay-plan-blurb { font-size: 13.5px; line-height: 19px; color: var(--nv-ink-2); text-wrap: pretty; }

.pay-plan-foot { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.pay-plan-tag {
  font-family: var(--nv-mono);
  font-size: 10.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--nv-accent);
}
.pay-plan-charged { font-size: 11.5px; color: var(--nv-ink-3); }

.pay-plan-tick {
  position: absolute;
  top: 17px; right: 16px;
  width: 22px; height: 22px;
  border-radius: 999px;
  border: 1.5px solid var(--nv-dot-border);
  display: flex; align-items: center; justify-content: center;
  transition: background-color 160ms linear, border-color 160ms linear;
}
.pay-plan-tick-on {
  background: var(--nv-accent);
  border-color: var(--nv-accent);
  animation: nhPop 280ms var(--nv-spring) both;
}

/* ── Move code ───────────────────────────────────────────────────────── */
.pay-code {
  margin-top: 14px;
  padding: 15px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.55);
  -webkit-backdrop-filter: blur(30px) saturate(210%);
          backdrop-filter: blur(30px) saturate(210%);
  border: 0.5px solid var(--nv-glass-rim);
  box-shadow: inset 0 1px 0 var(--nv-glass-hairline), var(--nv-shadow-card);
  animation: nhUp 420ms var(--nv-ease) both;
}

.pay-code-label {
  display: block;
  font-family: var(--nv-mono);
  font-size: 10.5px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--nv-ink-3);
  margin-bottom: 9px;
}

.pay-code-row { display: flex; align-items: center; gap: 9px; }

.pay-code-input {
  flex: 1;
  min-width: 0;
  min-height: 50px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.55);
  background: rgba(116, 134, 160, 0.13);
  box-shadow: inset 0 2px 5px rgba(20, 22, 26, 0.07);
  outline: none;
  font-family: var(--nv-mono);
  /* 16px floor, or iOS Safari zooms the viewport on focus. */
  font-size: 17px;
  letter-spacing: 0.32em;
  color: var(--nv-ink);
}
.pay-code-input::placeholder { color: var(--nv-ink-disabled); letter-spacing: 0.32em; }
.pay-code-input:focus {
  border-color: rgba(92, 156, 230, 0.72);
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 0 0 4px rgba(92, 156, 230, 0.15);
}

.pay-code-resend {
  flex: none;
  min-height: 50px;
  padding: 0 15px;
  border-radius: 999px;
  border: 1px solid rgba(92, 156, 230, 0.4);
  background: rgba(92, 156, 230, 0.1);
  color: var(--nv-accent);
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
}
.pay-code-resend:disabled { opacity: 0.5; cursor: default; }

.pay-code-help { margin: 9px 0 0; font-size: 12px; line-height: 17px; color: var(--nv-ink-3); text-wrap: pretty; }

/* ── Notice ──────────────────────────────────────────────────────────── */
.pay-notice {
  margin: 14px 0 0;
  padding: 12px 14px;
  border-radius: 15px;
  background: rgba(92, 156, 230, 0.11);
  font-size: 13px;
  line-height: 18.5px;
  color: #33619B;
  text-wrap: pretty;
}
.pay-notice-bad { background: rgba(214, 69, 93, 0.09); color: var(--nv-danger-text); }

.pay-note-enter-active,
.pay-note-leave-active { transition: opacity 220ms linear, transform 220ms var(--nv-ease-out); }
.pay-note-enter-from,
.pay-note-leave-to { opacity: 0; transform: translateY(-5px); }

/* ── CTA ─────────────────────────────────────────────────────────────── */
.pay-cta { position: relative; margin-top: 20px; }

.pay-cta-gloss {
  position: absolute; inset: 0; border-radius: 999px; pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0) 54%),
    linear-gradient(100deg, transparent 44%, rgba(255, 255, 255, 0.32) 50%, transparent 56%);
  background-size: 100% 100%, 300% 100%;
  animation: nhSheen 5200ms ease-in-out 2000ms infinite;
}

.pay-cta-busy { display: inline-flex; align-items: center; justify-content: center; gap: 9px; }

.pay-spinner {
  width: 16px; height: 16px;
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.34);
  border-top-color: #FFFFFF;
  animation: nhSpin 800ms linear infinite;
}

/* ── Footer ──────────────────────────────────────────────────────────── */
.pay-secure {
  display: flex; align-items: flex-start; justify-content: center; gap: 8px;
  margin: 14px 0 0;
  font-size: 11.5px;
  line-height: 16px;
  color: var(--nv-ink-3);
  text-wrap: pretty;
}
.pay-secure svg { flex: none; margin-top: 1px; }

.pay-back {
  margin: 6px auto 0;
  min-height: 44px;
  padding: 0 16px;
  border: 0;
  background: transparent;
  color: var(--nv-ink-3);
  font-family: inherit;
  font-size: 14.5px;
  font-weight: 500;
  cursor: pointer;
}
.pay-back:active { opacity: 0.6; }

/* Without backdrop-filter, fall back to the design's opaque white so nothing
   renders unreadable, and drop the structure the blur was there for. */
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .pay-lens,
  .pay-for,
  .pay-plan,
  .pay-code { background: var(--nv-surface); }
  .pay-grid { display: none; }
}

@media (max-height: 700px) {
  .pay-lens { width: 68px; height: 68px; }
  .pay-head-copy { margin-top: 14px; }
  .pay-title { font-size: 23px; }
  .pay-plans { margin-top: 18px; }
}
</style>
