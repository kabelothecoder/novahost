<template>
  <!-- A paused account in the list layout: one compact row, tap to switch to it. -->
  <button class="row cc-card" :style="{ '--tint': account.tint }">
    <span class="row-tile" aria-hidden="true">{{ account.broker.charAt(0) }}</span>
    <span class="row-id">
      <span class="row-name">{{ account.broker }}</span>
      <span class="row-sub">
        <span class="row-mask">{{ mask }}</span>
        <span class="row-tag">{{ included ? 'Included' : extraText }}</span>
      </span>
    </span>
    <span class="row-bal">
      <span class="row-fig">{{ money }}</span>
      <span class="row-go">Switch
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="9 18 15 12 9 6"/></svg>
      </span>
    </span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { maskLogin, formatMoney, ACCOUNT_ADDON } from '../../services/accountsService.js'

const props = defineProps({ account: { type: Object, required: true }, included: Boolean })
const mask = computed(() => maskLogin(props.account))
const money = computed(() => formatMoney(props.account.balance, props.account.currency))
const extraText = `+${ACCOUNT_ADDON.currencySymbol}${ACCOUNT_ADDON.priceMonthly}/mo`
</script>

<style scoped>
.row {
  width: 100%; height: 66px; padding: 0 16px;
  display: flex; align-items: center; gap: 12px;
  border-radius: 20px; text-align: left; cursor: pointer; font: inherit; color: var(--cc-ink);
  background: linear-gradient(140deg, color-mix(in srgb, var(--tint) 12%, var(--cc-card)), var(--cc-card) 70%);
  box-shadow: var(--cc-shadow-card), inset 0 1px 0 rgba(255, 255, 255, 0.1);
  transition: transform 180ms var(--cc-spring);
}
.row:active { transform: scale(0.985); }
.row-tile {
  flex: none; display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px;
  background: var(--tint); color: #0B0B0D; font-size: 16px; font-weight: 800;
}
.row-id { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.row-name { font-size: 15px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.row-sub { display: flex; align-items: center; gap: 7px; }
.row-mask { font-family: var(--cc-mono); font-size: 11.5px; color: var(--cc-ink-3); }
.row-tag { font-family: var(--cc-mono); font-size: 10.5px; font-weight: 600; color: var(--cc-ink-3); }
.row-bal { flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 3px; }
.row-fig { font-family: var(--cc-mono); font-size: 13px; font-weight: 600; white-space: nowrap; }
.row-go { display: inline-flex; align-items: center; gap: 2px; font-size: 11.5px; font-weight: 600; color: var(--cc-accent-text); }
</style>
