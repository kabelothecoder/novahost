<template>
  <div class="det">
    <div class="det-grid cc-card">
      <div class="det-full">
        <span class="cc-label">Robot</span>
        <span class="det-val">{{ robotLine }}</span>
      </div>
      <div>
        <span class="cc-label">Billing</span>
        <span class="det-val">{{ billing }}</span>
      </div>
      <div>
        <span class="cc-label">Login</span>
        <span class="det-val det-mono">{{ mask }}</span>
      </div>
    </div>

    <div v-if="active" class="det-banner">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm-1.2 14.6-4-4 1.4-1.4 2.6 2.6 5-5 1.4 1.4-6.4 6.4Z"/></svg>
      Active · Home and robot controls apply here
    </div>
    <button v-else class="det-make" :disabled="busy" @click="$emit('make')">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
      {{ busy ? 'Switching…' : 'Make active' }}
    </button>

    <button class="det-delink" :class="{ 'is-off': active }" :disabled="active || busy" @click="$emit('delink')">
      {{ active ? 'To delink, make another account active first' : 'Delink account' }}
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { maskLogin, ACCOUNT_ADDON } from '../../services/accountsService.js'

const props = defineProps({
  account: { type: Object, required: true },
  active: Boolean,
  included: Boolean,
  running: Boolean,
  busy: Boolean,
  robotName: { type: String, default: 'Your robot' },
})
defineEmits(['make', 'delink'])

const mask = computed(() => maskLogin(props.account))
const robotLine = computed(() => {
  if (props.active) return props.running ? `${props.robotName} is trading this account` : `${props.robotName} is stopped`
  return `Paused. ${props.robotName} trades your active account only.`
})
const billing = computed(() =>
  props.included ? 'Included in your plan'
    : `${ACCOUNT_ADDON.currencySymbol}${ACCOUNT_ADDON.priceMonthly}/mo, billed with your plan`)
</script>

<style scoped>
.det { display: flex; flex-direction: column; gap: 10px; }
.det-grid {
  display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px;
  padding: 14px 16px; border-radius: 22px;
}
.det-grid > div { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.det-full { grid-column: 1 / -1; }
.det-val { font-size: 14px; line-height: 19px; color: var(--cc-ink-2); text-wrap: pretty; }
.det-mono { font-family: var(--cc-mono); font-size: 12.5px; }

.det-banner, .det-make {
  height: 52px; border-radius: 16px;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  font: inherit; font-size: 14px; font-weight: 600;
}
.det-banner {
  color: var(--cc-ink);
  background: rgba(var(--cc-accent-rgb), 0.14);
  border: 1px solid rgba(var(--cc-accent-rgb), 0.45);
}
.det-make {
  border: 0; cursor: pointer; font-size: 15px;
  background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink);
  box-shadow: 0 14px 30px -14px var(--cc-accent);
  transition: transform 180ms var(--cc-spring);
}
.det-make:active:not(:disabled) { transform: scale(0.98); }
.det-make:disabled { opacity: 0.6; }
.det-delink {
  height: 36px; border: 0; background: none; cursor: pointer;
  font: inherit; font-size: 13.5px; font-weight: 500; color: var(--cc-sell-text);
}
.det-delink.is-off { color: var(--cc-ink-5); cursor: default; }
</style>
