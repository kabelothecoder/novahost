<template>
  <!--
    A paid-for place to put an account, which is a different object from an
    account. It is pending until PayFast confirms, failed if it was refused
    (nothing is charged and the slot stays locked), and ready once paid but
    not yet linked.
  -->
  <div class="slot-card cc-card" :style="{ '--tint': meta.tint }">
    <div class="slot-head">
      <span class="slot-ico" aria-hidden="true">
        <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-html="meta.icon"></svg>
      </span>
      <div class="slot-id">
        <div class="slot-name-row">
          <span class="slot-name">Extra slot</span>
          <span class="slot-pill">{{ meta.pill }}</span>
        </div>
        <span class="slot-price">{{ price }}</span>
      </div>
      <span v-if="item.state === 'pending'" class="slot-spin" aria-label="Waiting for payment"></span>
    </div>
    <div class="slot-rule"></div>
    <p class="slot-msg">{{ meta.msg }}</p>
    <button class="slot-btn" :style="{ background: meta.btnBg }" @click.stop="$emit('act', item)">
      {{ meta.btn }}
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ACCOUNT_ADDON } from '../../services/accountsService.js'

const props = defineProps({ item: { type: Object, required: true } })
defineEmits(['act'])

const price = `+${ACCOUNT_ADDON.currencySymbol}${ACCOUNT_ADDON.priceMonthly}/mo · billed with plan`

const META = {
  pending: {
    tint: 'var(--cc-warn)', pill: 'PENDING',
    icon: '<path d="M5 22h14M5 2h14M17 22v-4.17a2 2 0 0 0-.59-1.42L12 12l-4.41 4.41A2 2 0 0 0 7 17.83V22M7 2v4.17a2 2 0 0 0 .59 1.42L12 12l4.41-4.41A2 2 0 0 0 17 6.17V2"/>',
    msg: 'Waiting for PayFast to confirm your payment. This usually takes under a minute.',
    btn: 'Check again', btnBg: 'rgba(255,255,255,0.1)',
  },
  failed: {
    tint: 'var(--cc-sell)', pill: 'PAYMENT FAILED',
    icon: '<circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>',
    msg: 'PayFast couldn’t take the payment, so you haven’t been charged. Your slot stays locked.',
    btn: 'Try again', btnBg: 'var(--cc-sell)',
  },
  ready: {
    tint: 'var(--cc-accent)', pill: 'READY',
    icon: '<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/>',
    msg: 'Your extra slot is ready — link an account.',
    btn: 'Link account', btnBg: 'var(--cc-accent-fill)',
  },
}
const meta = computed(() => META[props.item.state] || META.ready)
</script>

<style scoped>
.slot-card {
  position: relative; width: 100%; height: 100%; padding: 0;
  border-radius: 26px; overflow: hidden;
  background:
    radial-gradient(220px 200px at 100% 0%, color-mix(in srgb, var(--tint) 20%, transparent), transparent 70%),
    var(--cc-card);
  box-shadow: var(--cc-shadow-raised), inset 0 1px 0 rgba(255, 255, 255, 0.12);
}
.slot-head { position: absolute; left: 18px; right: 18px; top: 12px; height: 44px; display: flex; align-items: center; gap: 12px; }
.slot-ico {
  flex: none; display: grid; place-items: center; width: 42px; height: 42px; border-radius: 12px;
  color: var(--tint);
  background: color-mix(in srgb, var(--tint) 22%, transparent);
  border: 1px solid color-mix(in srgb, var(--tint) 45%, transparent);
}
.slot-id { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.slot-name-row { display: flex; align-items: center; gap: 7px; }
.slot-name { font-size: 16px; font-weight: 600; color: var(--cc-ink); }
.slot-pill {
  display: inline-flex; align-items: center; height: 19px; padding: 0 7px; border-radius: 6px;
  background: color-mix(in srgb, var(--tint) 20%, transparent); color: var(--tint);
  font-size: 10px; font-weight: 700; letter-spacing: 0.1em; white-space: nowrap;
}
.slot-price { font-family: var(--cc-mono); font-size: 12px; color: var(--cc-ink-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.slot-spin {
  flex: none; width: 20px; height: 20px; border-radius: 50%;
  border: 2px solid rgba(232, 163, 61, 0.25); border-top-color: var(--cc-warn);
  animation: slot-spin 0.9s linear infinite;
}
@keyframes slot-spin { to { transform: rotate(360deg) } }
.slot-rule { position: absolute; left: 18px; right: 18px; top: 72px; height: 1px; background: rgba(255, 255, 255, 0.08); }
.slot-msg { position: absolute; left: 18px; right: 18px; top: 84px; margin: 0; font-size: 13.5px; line-height: 20px; color: var(--cc-ink-2); text-wrap: pretty; }
.slot-btn {
  position: absolute; left: 18px; top: 144px; height: 36px; padding: 0 16px;
  display: inline-flex; align-items: center; gap: 6px;
  border: 0; border-radius: 12px; color: #fff; font: inherit; font-size: 13.5px; font-weight: 600; cursor: pointer;
}
.slot-btn:active { transform: scale(0.97); }
@media (prefers-reduced-motion: reduce) { .slot-spin { animation-duration: 2.4s } }
</style>
