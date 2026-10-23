<template>
  <!--
    Payment confirmed: the shackle lifts and swings open, the lock fills with
    accent, rings pulse outward. Tap the lock to replay it.
  -->
  <Teleport to="body">
    <div
      class="cmd unl"
      :data-scheme="resolvedScheme"
      :data-motion="theme.motion"
      role="dialog"
      aria-modal="true"
      aria-label="Slot unlocked"
    >
      <div class="unl-glow" aria-hidden="true"></div>

      <button :key="replay" class="unl-lock" aria-label="Replay animation" @click="replay++">
        <i class="unl-ring" style="--d: 1s"></i>
        <i class="unl-ring" style="--d: 1.35s"></i>
        <i class="unl-ring thin" style="--d: 1.7s"></i>
        <i v-for="(r, n) in sparks" :key="n" class="unl-spark" :class="{ w: n % 2 }" :style="{ '--r': r + 'deg', '--sd': 0.95 + (n % 4) * 0.03 + 's' }"></i>
        <svg viewBox="0 0 120 120" width="120" height="120">
          <circle cx="60" cy="60" r="58" fill="rgba(255,255,255,.04)" stroke="rgba(255,255,255,.12)" stroke-width="1" />
          <path class="unl-shackle" d="M44 56 V44 a16 16 0 0 1 32 0 V56" fill="none" stroke="#F4F4F7" stroke-width="7" stroke-linecap="round" />
          <rect class="unl-body" x="34" y="54" width="52" height="40" rx="11" stroke="#F4F4F7" stroke-width="2.5" />
          <circle cx="60" cy="72" r="4.5" fill="#F4F4F7" />
          <rect x="58" y="72" width="4" height="9" rx="2" fill="#F4F4F7" />
        </svg>
      </button>

      <div class="unl-copy">
        <span class="unl-k">PAYMENT CONFIRMED</span>
        <h2>Slot unlocked</h2>
        <p>Your extra account slot is ready. It’s billed with your plan from {{ nextDay }} at {{ price }}/mo.</p>
      </div>

      <div class="unl-actions">
        <button class="sh-primary" @click="$emit('link')">
          Link account
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
        </button>
        <button class="sh-ghost" @click="$emit('later')">Later</button>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'
import { theme, resolvedScheme } from '../../../stores/theme.js'
import { ACCOUNT_ADDON, proration } from '../../../services/accountsService.js'

defineEmits(['link', 'later'])

const replay = ref(0)
const sparks = [0, 45, 90, 135, 180, 225, 270, 315]
const price = `${ACCOUNT_ADDON.currencySymbol}${ACCOUNT_ADDON.priceMonthly}`
const nextDay = proration().nextLabel.replace('From ', '')
</script>

<style scoped>
.unl { position: fixed; inset: 0; z-index: 420; display: flex; flex-direction: column; align-items: center; background: var(--cc-canvas); animation: unl-in 400ms ease both; }
.unl-glow { position: absolute; left: -60px; right: -60px; top: 14%; height: 460px; background: radial-gradient(50% 50% at 50% 50%, rgba(var(--cc-accent-rgb), 0.34), transparent 70%); animation: unl-in 1.6s ease 0.6s both; pointer-events: none; }
.unl-lock { position: relative; margin-top: 26dvh; width: 120px; height: 120px; padding: 0; border: 0; background: none; cursor: pointer; flex: none; }
.unl-lock svg { position: absolute; inset: 0; overflow: visible; }
.unl-ring { position: absolute; inset: 0; border-radius: 50%; border: 2px solid var(--cc-accent); animation: unl-ring 2.2s cubic-bezier(.2, .7, .3, 1) var(--d) infinite both; }
.unl-ring.thin { border-width: 1.5px; }
.unl-spark { position: absolute; left: 57px; top: 57px; width: 6px; height: 6px; border-radius: 3px; background: var(--cc-accent); animation: unl-spark 1.1s ease-out var(--sd) both; }
.unl-spark.w { background: #fff; }
.unl-shackle { transform-box: view-box; transform-origin: 76px 56px; animation: unl-shackle 1.1s cubic-bezier(.3, .7, .2, 1) both; }
.unl-body { fill: #26262E; animation: unl-body 1.1s ease both; }
.unl-copy { position: relative; margin-top: 34px; padding: 0 32px; display: flex; flex-direction: column; align-items: center; gap: 10px; text-align: center; animation: unl-fade 600ms cubic-bezier(.2, .8, .2, 1) 1.25s both; }
.unl-k { font-size: 11px; font-weight: 600; letter-spacing: 0.18em; color: var(--cc-ink-3); }
.unl-copy h2 { margin: 0; font-size: 30px; line-height: 36px; font-weight: 600; letter-spacing: -0.02em; color: var(--cc-ink); }
.unl-copy p { margin: 0; font-size: 14.5px; line-height: 21px; color: var(--cc-ink-3); text-wrap: pretty; }
.unl-actions { position: absolute; left: 24px; right: 24px; bottom: calc(env(safe-area-inset-bottom, 0px) + 28px); display: flex; flex-direction: column; gap: 6px; animation: unl-fade 600ms cubic-bezier(.2, .8, .2, 1) 1.5s both; }

@keyframes unl-in { from { opacity: 0 } to { opacity: 1 } }
@keyframes unl-fade { from { opacity: 0; transform: translateY(10px) } to { opacity: 1; transform: none } }
@keyframes unl-ring { 0% { transform: scale(.7); opacity: .9 } 100% { transform: scale(3.1); opacity: 0 } }
@keyframes unl-spark { 0% { transform: rotate(var(--r)) translateY(-34px) scale(1); opacity: 0 } 12% { opacity: 1 } 100% { transform: rotate(var(--r)) translateY(-150px) scale(.2); opacity: 0 } }
@keyframes unl-shackle { 0%, 35% { transform: none } 62% { transform: translateY(-11px) } 100% { transform: translateY(-11px) rotate(-38deg) } }
@keyframes unl-body { 0%, 58% { fill: #26262E } 100% { fill: var(--cc-accent) } }
/* Reduced motion keeps the end state: opened lock, filled body, copy visible. */
@media (prefers-reduced-motion: reduce) {
  .unl, .unl-glow, .unl-copy, .unl-actions { animation: none }
  .unl-ring, .unl-spark { display: none }
  .unl-shackle { animation: none; transform: translateY(-11px) rotate(-38deg) }
  .unl-body { animation: none; fill: var(--cc-accent) }
}
</style>
