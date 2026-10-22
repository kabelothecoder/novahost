<template>
  <BottomSheet label="Switch active account" @close="$emit('close')">
    <div>
      <h2 class="sh-title">Switch active account?</h2>
      <p class="sh-lead">{{ robotName }} trades one account at a time, with the same strategy on each.</p>
    </div>

    <div class="cs-flow">
      <div class="cs-row is-from">
        <span class="cs-tile" :style="{ background: from.tint }">{{ from.broker.charAt(0) }}</span>
        <span class="cs-t"><b>{{ from.broker }} {{ maskLogin(from) }}</b><i>{{ robotName }} stops trading here</i></span>
        <span class="cs-tag">PAUSES</span>
      </div>
      <div class="cs-arrow" aria-hidden="true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg>
      </div>
      <div class="cs-row">
        <span class="cs-tile is-to" :style="{ background: to.tint }">{{ to.broker.charAt(0) }}</span>
        <span class="cs-t"><b>{{ to.broker }} {{ maskLogin(to) }}</b><i>{{ robotName }} starts here, same strategy</i></span>
        <span class="cs-tag is-on">ACTIVE</span>
      </div>
    </div>

    <p class="sh-note">Open trades on {{ from.broker }} stay open at the broker. {{ robotName }} won’t manage them while that account is paused.</p>

    <div class="sh-actions">
      <button class="sh-primary" :disabled="busy" @click="$emit('confirm')">{{ busy ? 'Switching…' : 'Switch and keep trading' }}</button>
      <button class="sh-ghost" @click="$emit('close')">Cancel</button>
    </div>
  </BottomSheet>
</template>

<script setup>
import BottomSheet from './BottomSheet.vue'
import { maskLogin } from '../../../services/accountsService.js'

defineProps({
  from: { type: Object, required: true },
  to: { type: Object, required: true },
  robotName: { type: String, default: 'Your robot' },
  busy: Boolean,
})
defineEmits(['close', 'confirm'])
</script>

<style scoped>
.cs-flow { border-radius: 20px; background: rgba(255, 255, 255, 0.045); border: 1px solid rgba(255, 255, 255, 0.09); padding: 6px 14px; }
.cs-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; }
.cs-row.is-from { opacity: 0.75; }
.cs-tile { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 11px; color: #0B0B0D; font-weight: 800; }
.cs-tile.is-to { box-shadow: 0 0 0 1.5px var(--cc-accent); }
.cs-t { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cs-t b { font-size: 15px; font-weight: 600; color: var(--cc-ink); }
.cs-t i { font-style: normal; font-size: 12.5px; color: var(--cc-ink-3); }
.cs-tag { font-size: 10px; font-weight: 700; letter-spacing: 0.1em; color: var(--cc-ink-4); }
.cs-tag.is-on { height: 19px; padding: 0 7px; display: inline-flex; align-items: center; border-radius: 6px; background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink); }
.cs-arrow { display: flex; padding-left: 10px; color: var(--cc-ink-4); }
</style>
