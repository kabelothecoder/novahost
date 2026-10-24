<template>
  <div class="nova plan">

    <div class="plan-bed" aria-hidden="true">
      <span class="plan-pool plan-pool-a"></span>
      <span class="plan-pool plan-pool-b"></span>
      <span class="plan-pool plan-pool-c"></span>
      <span class="plan-grid"></span>
    </div>

    <div class="plan-scroll">
      <div class="plan-stack">

        <div class="plan-dots" role="tablist" aria-label="About your plan">
          <button
            v-for="i in PAGES"
            :key="i"
            type="button"
            role="tab"
            class="plan-dot"
            :class="{ 'plan-dot-on': page === i - 1 }"
            :aria-selected="page === i - 1"
            :aria-label="`Page ${i} of ${PAGES}`"
            @click="page = i - 1"
          ></button>
        </div>

        <Transition :name="dir" mode="out-in">

          <!-- ══ 01 — What it does ════════════════════════════════════════ -->
          <section v-if="page === 0" key="what" class="plan-page">
            <div class="plan-head nv-stagger">
              <p class="nv-step-label">What you get</p>
              <h1 class="nv-title plan-title">Your robot, hosted.<br>Your phone, the remote.</h1>
              <p class="nv-body plan-body">
                NovaHost runs your robot provider's EA in the cloud, around the clock, on your own broker account.
                No VPS to rent. No PC left on.
              </p>
            </div>

            <ul class="plan-list">
              <li v-for="(f, i) in FEATURES" :key="f.title" class="plan-item" :style="{ animationDelay: `${120 + i * 70}ms` }">
                <span class="plan-icon" aria-hidden="true">
                  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" v-html="f.icon"></svg>
                </span>
                <span class="plan-item-copy">
                  <span class="plan-item-title">{{ f.title }}</span>
                  <span class="plan-item-body">{{ f.body }}</span>
                </span>
              </li>
            </ul>
          </section>

          <!-- ══ 02 — The plan ════════════════════════════════════════════ -->
          <section v-else key="plan" class="plan-page">
            <div class="plan-head nv-stagger">
              <p class="nv-step-label">The plan</p>
              <h1 class="nv-title plan-title">One price. No contract.</h1>
              <p class="nv-body plan-body">
                Pay for 30 days at a time. Nothing renews on its own and there's no card on file. When you want another month, you pay again.
              </p>
            </div>

            <div class="plan-price">
              <span class="plan-price-rim" aria-hidden="true"></span>
              <div class="plan-price-top">
                <span class="plan-price-amount">R349</span>
                <span class="plan-price-per">/ 30 days</span>
              </div>
              <ul class="plan-incl">
                <li v-for="line in INCLUDED" :key="line">
                  <svg width="14" height="14" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                    <path d="M4.6 10.2 L8.4 13.6 L15.4 6.2" stroke="var(--nv-accent)" stroke-width="2.4"
                          stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  <span>{{ line }}</span>
                </li>
              </ul>
            </div>

            <ol class="plan-steps" aria-label="What happens next">
              <li v-for="(s, i) in NEXT" :key="s" class="plan-step">
                <span class="plan-step-num">{{ i + 1 }}</span>
                <span class="plan-step-text" v-html="s"></span>
              </li>
            </ol>
          </section>
        </Transition>

        <div class="plan-cta">
          <NovaCta @click="next">{{ page === 0 ? 'Next' : 'Continue to payment' }}</NovaCta>
          <span class="plan-cta-gloss" aria-hidden="true"></span>
        </div>

        <button v-if="page > 0" type="button" class="plan-back" @click="back">Back</button>

      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * @description PlanView — the two screens that sell the plan, between the
 * onboarding wizard and the payment screen.
 *
 *   01  what NovaHost does for them
 *   02  the price, what it includes, and what happens after paying
 *
 * Framing follows the product's positioning: NovaHost HOSTS a robot (an EA)
 * from a robot provider — it is not "a mentor sends you trades". The price
 * shown is the marketing price; PaymentView shows the R350.00 actually
 * collected, so the two screens never disagree with the PayFast page.
 *
 * Holds no state the router cares about. The guard routes an unpaid first-time
 * buyer here; renewals and device moves skip it and land on /payment.
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import NovaCta from '../components/nova/NovaCta.vue'

const router = useRouter()

const PAGES = 2
const page = ref(0)
/** Which way the page transition slides. */
const dir = ref('plan-fwd')

const FEATURES = [
  {
    title: 'Runs while your phone sleeps',
    body: 'The robot trades from the cloud. Lock the screen, close the app — it keeps going.',
    icon: '<path d="M10 2.5 A7.5 7.5 0 1 0 17.5 10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M10 6 V10 L13 12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>',
  },
  {
    title: 'Your broker, your account',
    body: 'Connect the MT4 or MT5 account you already trade. Your money never leaves it.',
    icon: '<rect x="2.5" y="5" width="15" height="10.5" rx="2.6" stroke="currentColor" stroke-width="1.6"/><path d="M2.5 8.6 H17.5" stroke="currentColor" stroke-width="1.6"/>',
  },
  {
    title: 'You stay in control',
    body: 'Start and stop the robot, pick its symbols, set lot sizes and limits — from your phone.',
    icon: '<path d="M4 6 H16 M4 14 H16" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><circle cx="8" cy="6" r="2" fill="var(--nv-surface)" stroke="currentColor" stroke-width="1.6"/><circle cx="12.5" cy="14" r="2" fill="var(--nv-surface)" stroke="currentColor" stroke-width="1.6"/>',
  },
  {
    title: 'Alerts the moment it trades',
    body: 'A notification for every order placed or refused, with the reason.',
    icon: '<path d="M5 13.5 V9 A5 5 0 0 1 15 9 V13.5 L16.5 15 H3.5 Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M8.3 17.2 A1.9 1.9 0 0 0 11.7 17.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>',
  },
]

const INCLUDED = [
  'Cloud hosting for your robot, 24/7',
  'Broker connect, symbol plan and risk limits',
  'Trade alerts and live activity',
  '1 AI chart scan a month — more in packs of 20',
]

const NEXT = [
  'Pay <b>R349</b> securely through PayFast.',
  'Get your <b>licence key</b> — enter one you have, or request a code to claim it on your robot provider’s site.',
  'Connect your broker and switch the robot on.',
]

function next () {
  if (page.value < PAGES - 1) {
    dir.value = 'plan-fwd'
    page.value += 1
    return
  }
  router.push({ name: 'payment' })
}

function back () {
  dir.value = 'plan-back'
  page.value = Math.max(0, page.value - 1)
}
</script>

<style scoped>
/* Same material vocabulary as the payment screen it hands off to: a living
   bed with structure, then glass over it. */
.plan {
  position: relative;
  width: 100%;
  height: 100dvh;
  overflow: hidden;
  background: linear-gradient(180deg, var(--nv-art-top), var(--nv-art-bottom));
}

.plan-bed { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }
.plan-pool { position: absolute; display: block; border-radius: 999px; will-change: transform; }
.plan-pool-a {
  left: -18%; top: 4%; width: 78%; height: 30%;
  background: radial-gradient(closest-side, rgba(162, 136, 227, 0.56), rgba(162, 136, 227, 0) 76%);
  animation: nhDriftA 17s ease-in-out infinite;
}
.plan-pool-b {
  right: -22%; top: 34%; width: 84%; height: 36%;
  background: radial-gradient(closest-side, rgba(92, 156, 230, 0.56), rgba(92, 156, 230, 0) 74%);
  animation: nhDriftB 22s ease-in-out infinite;
}
.plan-pool-c {
  left: 0; bottom: -10%; width: 88%; height: 32%;
  background: radial-gradient(closest-side, rgba(129, 146, 232, 0.42), rgba(129, 146, 232, 0) 76%);
  animation: nhDriftA 26s ease-in-out infinite reverse;
}
.plan-grid {
  position: absolute; inset: 0; display: block;
  background-image:
    repeating-linear-gradient(0deg,  rgba(20, 22, 26, 0.05) 0 1px, transparent 1px 28px),
    repeating-linear-gradient(90deg, rgba(20, 22, 26, 0.05) 0 1px, transparent 1px 28px);
  -webkit-mask: radial-gradient(120% 78% at 50% 42%, #000 30%, transparent 82%);
          mask: radial-gradient(120% 78% at 50% 42%, #000 30%, transparent 82%);
}

/* Scroll container, never min-height: the installed shell clips and only a
   view that scrolls itself survives a short handset. */
.plan-scroll {
  position: relative;
  z-index: 1;
  height: 100%;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  display: flex;
  justify-content: center;
  padding: calc(22px + var(--nv-safe-top)) 20px calc(26px + var(--nv-safe-bottom));
}

.plan-stack {
  width: 100%;
  max-width: 380px;
  margin: auto;
  display: flex;
  flex-direction: column;
}

/* ── Page dots ───────────────────────────────────────────────────────── */
.plan-dots { display: flex; justify-content: center; gap: 7px; margin-bottom: 22px; }
.plan-dot {
  width: 7px; height: 7px; padding: 0;
  border: 0; border-radius: 999px;
  background: var(--nv-dot-border);
  cursor: pointer;
  transition: width 260ms var(--nv-ease-out), background-color 200ms linear;
}
.plan-dot-on { width: 22px; background: var(--nv-accent); }

/* ── Copy ────────────────────────────────────────────────────────────── */
.plan-head { text-align: center; }
.plan-head > .nv-step-label { margin: 0 0 9px; }
.plan-title { margin: 0 0 10px; font-size: 26px; text-wrap: balance; }
.plan-body { margin: 0; text-wrap: pretty; }

/* ── Page 01 — features ──────────────────────────────────────────────── */
.plan-list { list-style: none; margin: 22px 0 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }

.plan-item {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.55);
  -webkit-backdrop-filter: blur(30px) saturate(210%);
          backdrop-filter: blur(30px) saturate(210%);
  border: 0.5px solid var(--nv-glass-rim);
  box-shadow: inset 0 1px 0 var(--nv-glass-hairline), var(--nv-shadow-card);
  animation: nhUp 460ms var(--nv-ease) both;
}

.plan-icon {
  flex: none;
  width: 36px; height: 36px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  background: var(--nv-accent-dim);
  color: var(--nv-accent);
}

.plan-item-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.plan-item-title { font-size: 15px; font-weight: 600; color: var(--nv-ink); letter-spacing: -0.2px; }
.plan-item-body { font-size: 13px; line-height: 18px; color: var(--nv-ink-2); text-wrap: pretty; }

/* ── Page 02 — price card ────────────────────────────────────────────── */
.plan-price {
  position: relative;
  margin-top: 22px;
  padding: 20px 20px 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.62);
  -webkit-backdrop-filter: blur(34px) saturate(210%);
          backdrop-filter: blur(34px) saturate(210%);
  box-shadow:
    inset 0 1px 0 var(--nv-glass-hairline),
    0 0 0 1.5px rgba(92, 156, 230, 0.45),
    var(--nv-shadow-raised);
  animation: nhUp 460ms var(--nv-ease) 100ms both;
}
.plan-price-rim {
  position: absolute; inset: 0; border-radius: 24px; padding: 1px; pointer-events: none;
  background: linear-gradient(145deg,
    rgba(255, 255, 255, 0.92) 0%, rgba(255, 255, 255, 0.14) 40%,
    rgba(255, 255, 255, 0.06) 62%, rgba(255, 255, 255, 0.7) 100%);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
          mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
          mask-composite: exclude;
}
.plan-price-top { display: flex; align-items: baseline; gap: 7px; }
.plan-price-amount { font-size: 38px; font-weight: 700; letter-spacing: -1px; color: var(--nv-ink); }
.plan-price-per { font-size: 15px; font-weight: 500; color: var(--nv-ink-3); }

.plan-incl { list-style: none; margin: 14px 0 0; padding: 0; display: flex; flex-direction: column; gap: 9px; }
.plan-incl li { display: flex; align-items: flex-start; gap: 9px; font-size: 14px; line-height: 19px; color: var(--nv-ink-2); }
.plan-incl svg { flex: none; margin-top: 2.5px; }

/* ── Page 02 — next steps ────────────────────────────────────────────── */
.plan-steps { list-style: none; margin: 20px 0 0; padding: 0 4px; display: flex; flex-direction: column; gap: 12px; }
.plan-step { display: flex; align-items: flex-start; gap: 12px; animation: nhUp 460ms var(--nv-ease) 200ms both; }
.plan-step-num {
  flex: none;
  width: 24px; height: 24px;
  border-radius: 999px;
  background: var(--nv-accent-grad);
  color: var(--nv-on-accent);
  font-size: 13px; font-weight: 600;
  line-height: 24px; text-align: center;
}
.plan-step-text { font-size: 14px; line-height: 21px; color: var(--nv-ink-2); padding-top: 1px; text-wrap: pretty; }
.plan-step-text :deep(b) { font-weight: 600; color: var(--nv-ink); }

/* ── CTA ─────────────────────────────────────────────────────────────── */
.plan-cta { position: relative; margin-top: 24px; }
.plan-cta-gloss {
  position: absolute; inset: 0; border-radius: 999px; pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0) 54%),
    linear-gradient(100deg, transparent 44%, rgba(255, 255, 255, 0.32) 50%, transparent 56%);
  background-size: 100% 100%, 300% 100%;
  animation: nhSheen 5200ms ease-in-out 2000ms infinite;
}

.plan-back {
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
.plan-back:active { opacity: 0.6; }

/* ── Page transitions ────────────────────────────────────────────────── */
.plan-fwd-enter-active, .plan-fwd-leave-active,
.plan-back-enter-active, .plan-back-leave-active {
  transition: opacity 220ms linear, transform 260ms var(--nv-ease-out);
}
.plan-fwd-enter-from  { opacity: 0; transform: translateX(24px); }
.plan-fwd-leave-to    { opacity: 0; transform: translateX(-24px); }
.plan-back-enter-from { opacity: 0; transform: translateX(-24px); }
.plan-back-leave-to   { opacity: 0; transform: translateX(24px); }

@media (prefers-reduced-motion: reduce) {
  .plan-fwd-enter-from, .plan-fwd-leave-to,
  .plan-back-enter-from, .plan-back-leave-to { transform: none; }
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .plan-item, .plan-price { background: var(--nv-surface); }
  .plan-grid { display: none; }
}

@media (max-height: 700px) {
  .plan-title { font-size: 23px; }
  .plan-list { margin-top: 16px; gap: 8px; }
  .plan-item { padding: 12px 14px; }
}
</style>
