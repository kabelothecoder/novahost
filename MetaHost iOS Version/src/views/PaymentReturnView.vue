<template>
  <div class="nova ret">

    <div class="ret-bed" aria-hidden="true">
      <span class="ret-pool ret-pool-a"></span>
      <span class="ret-pool ret-pool-b"></span>
      <span class="ret-pool ret-pool-c"></span>
    </div>

    <div class="ret-scroll">
      <div class="ret-card">

        <div class="ret-seal" :class="{ 'ret-seal-quiet': cancelled }" aria-hidden="true">
          <svg v-if="!cancelled" width="28" height="28" viewBox="0 0 24 24" fill="none">
            <path d="M5 12.5 L10 17.5 L19 7" stroke="#FFFFFF" stroke-width="2.6"
                  stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <svg v-else width="26" height="26" viewBox="0 0 24 24" fill="none">
            <path d="M7 7 L17 17 M17 7 L7 17" stroke="var(--nv-ink-3)" stroke-width="2.4"
                  stroke-linecap="round" />
          </svg>
        </div>

        <h1 class="nv-title ret-title">{{ cancelled ? 'Payment cancelled.' : 'Payment received.' }}</h1>
        <p class="nv-body ret-body">{{ body }}</p>

        <p class="ret-step">{{ instruction }}</p>

        <!-- Only offered when this browsing context is the one that started
             the checkout. See `sameContext` below for why that matters. -->
        <div v-if="sameContext" class="ret-cta">
          <NovaCta @click="continueIn">Continue to NovaHost</NovaCta>
        </div>

        <p class="ret-secure">
          <svg width="13" height="15" viewBox="0 0 16 18" fill="none" aria-hidden="true">
            <path d="M4.4 8 V5.6 C4.4 3.6, 6 2, 8 2 C10 2, 11.6 3.6, 11.6 5.6 V8"
                  stroke="var(--nv-accent)" stroke-width="1.7" stroke-linecap="round" />
            <rect x="2.6" y="7.8" width="10.8" height="8.4" rx="2.6" fill="var(--nv-accent)" />
          </svg>
          <span>Payment is handled by PayFast. NovaHost never sees your card details.</span>
        </p>

      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * @description PaymentReturnView — where PayFast drops a web payer once the
 * card is done, via the `payment-redirect` function.
 *
 * This screen is deliberately inert. It checks nothing, verifies nothing and
 * writes nothing, and the router guard waves it through before either the
 * onboarding or the entitlement gate gets a say. That is the entire point:
 *
 * A standalone iOS PWA breaks out to Safari for a cross-origin navigation, so
 * the checkout — and this page after it — happen in Safari while the installed
 * app sits untouched on the Home Screen. Since iOS 16.4 a Home Screen web app
 * keeps its own storage bucket, so the copy Safari loads here holds a
 * DIFFERENT DEVICE_ID from the installed one the purchase was just bound to.
 * Anything on this screen that asked the server about entitlement would be
 * asking about the wrong device, and would answer someone who has just paid
 * R599 with "your licence is on another device, that'll be R150."
 *
 * So the screen states the outcome and points home. The installed app
 * re-checks itself when it comes back to the front — PaymentView listens on
 * `visibilitychange` for exactly that.
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { secStorage } from '../utils/secStorage'
import NovaCta from '../components/nova/NovaCta.vue'

const route = useRoute()
const router = useRouter()

const cancelled = computed(() => route.query.status === 'cancel')

/**
 * Whether this browsing context is the one that started the checkout.
 *
 * There are two ways to arrive here and they want opposite advice:
 *
 *   - Installed to the Home Screen. Safari's storage is a separate bucket, so
 *     it is empty — no DEVICE_ID. Continuing here would open a second copy of
 *     the app that the licence does not belong to. Send them back to the real
 *     one instead.
 *   - Running in an ordinary Safari tab. That tab navigated away to PayFast
 *     and has now landed here, so storage is shared and DEVICE_ID is the very
 *     one the purchase was bound to. Continuing is exactly right.
 *
 * A minted id would make the first case masquerade as the second, so this
 * reads the key directly rather than going through `getDeviceId()`, which
 * mints on miss.
 */
const sameContext = computed(() => !!secStorage.getItem('DEVICE_ID'))

const body = computed(() => {
  if (cancelled.value) {
    return 'Nothing was charged. You can start the purchase again whenever you are ready.'
  }
  return 'Thank you. Your purchase is being activated on the device you started from — it usually takes a few seconds.'
})

const instruction = computed(() => {
  if (sameContext.value) {
    return cancelled.value
      ? 'Head back in to pick up where you left off.'
      : 'Head back in and NovaHost will unlock itself.'
  }
  return cancelled.value
    ? 'Switch back to the NovaHost app to pick up where you left off.'
    : 'Switch back to the NovaHost app — it checks again the moment it comes to the front, and unlocks itself.'
})

/**
 * Hands off to the gate, which owns re-verification. Not `home`: the guard
 * would bounce an unverified session straight back out, and the gate can
 * prefill the stored email and say something useful if the ITN is still
 * settling.
 */
function continueIn () {
  router.replace({ name: 'unlock' })
}
</script>

<style scoped>
/* Same material vocabulary as the gate and the purchase screen. */
.ret {
  position: relative;
  width: 100%;
  /* The drifting pools are absolute against this element, so it stays the
     fixed-height frame and .ret-scroll inside it does the scrolling. */
  height: 100dvh;
  overflow: hidden;
  background: linear-gradient(180deg, var(--nv-art-top), var(--nv-art-bottom));
}

.ret-bed { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }
.ret-pool { position: absolute; display: block; border-radius: 999px; }

.ret-pool-a {
  left: -16%; top: 4%; width: 76%; height: 30%;
  background: radial-gradient(closest-side, rgba(162, 136, 227, 0.5), rgba(162, 136, 227, 0) 76%);
  animation: nhDriftA 16s ease-in-out infinite;
}
.ret-pool-b {
  right: -22%; top: 34%; width: 84%; height: 36%;
  background: radial-gradient(closest-side, rgba(92, 156, 230, 0.5), rgba(92, 156, 230, 0) 74%);
  animation: nhDriftB 21s ease-in-out infinite;
}
.ret-pool-c {
  left: 2%; bottom: -10%; width: 86%; height: 32%;
  background: radial-gradient(closest-side, rgba(129, 146, 232, 0.38), rgba(129, 146, 232, 0) 76%);
  animation: nhDriftA 25s ease-in-out infinite reverse;
}

.ret-scroll {
  position: relative;
  z-index: 1;
  height: 100%;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  display: flex;
  /* flex-start plus `margin: auto 0` on the card, NOT align-items: center.
     A centred flex item that outgrows its container overflows in BOTH
     directions, and the top overflow of a scroll container is
     unreachable — so a long refusal message from PayFast would put the
     heading above the scroll origin with no way to reach it. Auto margins
     centre when there is room and collapse to zero when there is not. */
  align-items: flex-start;
  justify-content: center;
  padding: calc(28px + var(--nv-safe-top)) 20px calc(28px + var(--nv-safe-bottom));
}

.ret-card {
  width: 100%;
  max-width: 380px;
  margin: auto 0;
  padding: 30px 24px 26px;
  border-radius: 26px;
  text-align: center;
  background: rgba(255, 255, 255, 0.62);
  -webkit-backdrop-filter: blur(30px) saturate(210%);
          backdrop-filter: blur(30px) saturate(210%);
  border: 0.5px solid var(--nv-glass-rim);
  box-shadow:
    inset 0  1px 0 var(--nv-glass-hairline),
    inset 0 -1px 0 var(--nv-glass-seat),
    var(--nv-shadow-raised);
  animation: nhUp 460ms var(--nv-ease) both;
}

.ret-seal {
  width: 62px; height: 62px;
  margin: 0 auto 20px;
  border-radius: 999px;
  display: flex; align-items: center; justify-content: center;
  background: var(--nv-accent-grad);
  box-shadow: 0 8px 20px rgba(92, 156, 230, 0.32);
  animation: nhPop 420ms var(--nv-spring) 120ms both;
}
.ret-seal-quiet { background: rgba(138, 148, 166, 0.18); box-shadow: none; }

.ret-title { margin: 0 0 10px; font-size: 24px; }
.ret-body { margin: 0 0 16px; }

.ret-step {
  margin: 0;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(92, 156, 230, 0.1);
  font-size: 14px;
  line-height: 20px;
  color: #33619B;
  text-wrap: pretty;
}

.ret-cta { margin-top: 18px; }

.ret-secure {
  display: flex; align-items: flex-start; justify-content: center; gap: 8px;
  margin: 18px 0 0;
  font-size: 11.5px;
  line-height: 16px;
  color: var(--nv-ink-3);
  text-wrap: pretty;
}
.ret-secure svg { flex: none; margin-top: 1px; }

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .ret-card { background: var(--nv-surface); }
}
</style>
