<template>
  <BottomSheet label="Add another account" @close="$emit('close')">
    <div class="up-art" aria-hidden="true">
      <span class="up-back"></span>
      <span class="up-front"><span class="up-halo"></span><span class="up-plus">+</span></span>
    </div>

    <h2 class="sh-title sh-title-c">
      Link another account for {{ price }}/month <span>— {{ robotName }} trades it too</span>
    </h2>

    <ul class="up-perks">
      <li v-for="p in perks" :key="p.text">
        <span class="up-ico" v-html="p.icon"></span>
        <span>{{ p.text }}</span>
      </li>
    </ul>

    <div class="up-bill">
      <div class="up-row">
        <div><span class="up-l">Today</span><span class="up-s">{{ pr.restLabel }}</span></div>
        <span class="up-v">{{ pr.today }}</span>
      </div>
      <div class="up-row">
        <div><span class="up-l">{{ pr.nextLabel }}</span><span class="up-s">On your plan’s billing date</span></div>
        <span class="up-v">{{ price }}/mo</span>
      </div>
    </div>

    <div class="sh-actions">
      <button class="sh-primary" @click="$emit('continue')">
        Continue to PayFast
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
      </button>
      <p class="up-foot">Billed with your plan, cancel anytime</p>
      <p v-if="placeholder" class="up-mock">Preview checkout · nothing is charged</p>
      <button class="sh-ghost" @click="$emit('close')">Not now</button>
    </div>
  </BottomSheet>
</template>

<script setup>
import BottomSheet from './BottomSheet.vue'
import { ACCOUNT_ADDON, proration } from '../../../services/accountsService.js'

defineProps({ robotName: { type: String, default: 'Your robot' } })
defineEmits(['close', 'continue'])

const price = `${ACCOUNT_ADDON.currencySymbol}${ACCOUNT_ADDON.priceMonthly}`
const placeholder = ACCOUNT_ADDON.PLACEHOLDER
const pr = proration()

const svg = (d) => `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`
const perks = [
  { icon: svg('<rect x="3" y="8" width="18" height="12" rx="2"/><path d="M12 8V4M8 14h.01M16 14h.01"/>'), text: 'Trades it with the same strategy' },
  { icon: svg('<polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/>'), text: 'Make it the active account in one tap' },
  { icon: svg('<path d="M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M16 12h2"/>'), text: 'Balance and status next to your other accounts' },
]
</script>

<style scoped>
.up-art { position: relative; width: 200px; height: 100px; align-self: center; flex: none; }
.up-back, .up-front { position: absolute; width: 150px; height: 86px; border-radius: 16px; }
.up-back { left: 8px; top: 14px; transform: rotate(-7deg); background: linear-gradient(140deg, rgba(255, 255, 255, 0.16), rgba(28, 28, 34, 0.8) 60%); border: 1px solid rgba(255, 255, 255, 0.14); }
.up-front {
  left: 44px; top: 4px; display: grid; place-items: center;
  border: 1.5px dashed rgba(var(--cc-accent-rgb), 0.7);
  background: rgba(var(--cc-accent-rgb), 0.14);
}
.up-plus {
  position: relative; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%;
  background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink); font-size: 26px; font-weight: 500; line-height: 1;
}
.up-halo { position: absolute; width: 40px; height: 40px; border-radius: 50%; background: var(--cc-accent); opacity: 0.5; animation: up-glow 2.6s ease-in-out infinite; }
@keyframes up-glow { 0%, 100% { transform: scale(1); opacity: 0.6 } 50% { transform: scale(1.45); opacity: 0 } }

.up-perks { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
.up-perks li { display: flex; align-items: center; gap: 12px; font-size: 14px; line-height: 19px; color: var(--cc-ink-2); }
.up-ico { flex: none; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 11px; background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.09); color: var(--cc-ink-2); }

.up-bill { border-radius: 18px; background: rgba(255, 255, 255, 0.045); border: 1px solid rgba(255, 255, 255, 0.09); padding: 4px 16px; }
.up-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 0; }
.up-row + .up-row { border-top: 1px solid rgba(255, 255, 255, 0.07); }
.up-row > div { display: flex; flex-direction: column; gap: 2px; }
.up-l { font-size: 14px; font-weight: 500; color: var(--cc-ink); }
.up-s { font-size: 12px; color: var(--cc-ink-4); }
.up-v { font-family: var(--cc-mono); font-size: 16px; font-weight: 600; color: var(--cc-ink); white-space: nowrap; }
.up-foot, .up-mock { margin: 0; text-align: center; font-size: 12.5px; color: var(--cc-ink-4); }
.up-mock { color: var(--cc-warn); }
@media (prefers-reduced-motion: reduce) { .up-halo { animation: none } }
</style>
