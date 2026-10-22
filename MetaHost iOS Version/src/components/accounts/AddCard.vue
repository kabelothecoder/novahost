<template>
  <!--
    The "+" slot. Three honest states: first account (included, no payment),
    add another (priced), and full (quiet, offers nothing).
  -->
  <button class="add-card" :class="{ 'is-full': full }" :disabled="full" @click="$emit('add')">
    <span class="add-plus">
      <span v-if="!full" class="add-halo" aria-hidden="true"></span>
      <svg v-if="!full" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
      <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
    </span>
    <span class="add-text">
      <span class="add-title-row">
        <span class="add-title">{{ title }}</span>
        <span v-if="first" class="add-incl">Included</span>
      </span>
      <span class="add-sub">{{ sub }}</span>
    </span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { ACCOUNT_ADDON } from '../../services/accountsService.js'

const props = defineProps({ count: { type: Number, default: 0 } })
defineEmits(['add'])

const first = computed(() => props.count === 0)
const full = computed(() => props.count >= ACCOUNT_ADDON.maxAccounts)
const title = computed(() =>
  first.value ? 'Link your first account'
    : full.value ? `${ACCOUNT_ADDON.maxAccounts} of ${ACCOUNT_ADDON.maxAccounts} accounts linked`
    : 'Add another account')
const sub = computed(() =>
  first.value ? 'Your plan covers one broker account. Your robot trades it.'
    : full.value ? 'That’s the most one plan can hold.'
    : `+${ACCOUNT_ADDON.currencySymbol}${ACCOUNT_ADDON.priceMonthly}/mo · your robot trades it too`)
</script>

<style scoped>
.add-card {
  width: 100%; height: 100%;
  display: flex; align-items: center; gap: 16px; padding: 0 20px;
  border-radius: 26px; text-align: left; cursor: pointer; font: inherit; color: var(--cc-ink);
  border: 1.5px dashed rgba(var(--cc-accent-rgb), 0.6);
  background: rgba(var(--cc-accent-rgb), 0.07);
  -webkit-backdrop-filter: blur(16px); backdrop-filter: blur(16px);
}
.add-card:active:not(:disabled) { transform: scale(0.985); }
.add-card.is-full { border-color: rgba(255, 255, 255, 0.14); background: rgba(255, 255, 255, 0.02); cursor: default; }

.add-plus {
  position: relative; flex: none; display: grid; place-items: center; width: 48px; height: 48px; border-radius: 50%;
  background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.3);
}
.is-full .add-plus { background: rgba(255, 255, 255, 0.08); color: var(--cc-ink-3); }
.add-halo {
  position: absolute; inset: -10px; border-radius: 50%; pointer-events: none;
  background: radial-gradient(circle, rgba(var(--cc-accent-rgb), 0.45), transparent 70%);
  animation: add-glow 2.6s ease-in-out infinite;
}
@keyframes add-glow { 0%, 100% { transform: scale(1); opacity: 0.8 } 50% { transform: scale(1.3); opacity: 0.1 } }

.add-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.add-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.add-title { font-size: 15.5px; font-weight: 600; }
.add-incl {
  height: 19px; padding: 0 7px; display: inline-flex; align-items: center; border-radius: 6px;
  background: rgba(var(--cc-accent-rgb), 0.22); font-size: 10.5px; font-weight: 600;
}
.add-sub { font-size: 12.5px; line-height: 17px; color: var(--cc-ink-3); text-wrap: pretty; }
@media (prefers-reduced-motion: reduce) { .add-halo { animation: none } }
</style>
