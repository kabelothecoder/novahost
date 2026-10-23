<template>
  <BottomSheet label="Delink account" @close="$emit('close')">
    <span class="dl-ico" aria-hidden="true">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 17H7a5 5 0 0 1 0-10h2M15 7h2a5 5 0 0 1 0 10h-2M8 12h2M2 2l20 20"/></svg>
    </span>

    <div>
      <h2 class="sh-title">Delink {{ account.broker }} {{ maskLogin(account) }}?</h2>
      <p class="sh-lead">{{ body }}</p>
    </div>

    <div v-if="!included" class="dl-end">
      <span>Last billed period ends</span>
      <b>{{ endLabel }}</b>
    </div>

    <div class="sh-actions">
      <button class="sh-primary is-danger" :disabled="busy" @click="$emit('confirm')">{{ busy ? 'Delinking…' : 'Delink account' }}</button>
      <button class="sh-ghost" @click="$emit('close')">Keep it</button>
    </div>
  </BottomSheet>
</template>

<script setup>
import { computed } from 'vue'
import BottomSheet from './BottomSheet.vue'
import { maskLogin, proration } from '../../../services/accountsService.js'

const props = defineProps({
  account: { type: Object, required: true },
  included: Boolean,
  robotName: { type: String, default: 'Your robot' },
  busy: Boolean,
})
defineEmits(['close', 'confirm'])

const endLabel = proration().endLabel
const body = computed(() =>
  props.included
    ? `This is the account included in your plan. Open trades stay at ${props.account.broker}; ${props.robotName} stops managing them.`
    : `Billing for this account stops at the end of this period. Open trades stay at ${props.account.broker}; ${props.robotName} stops managing them.`)
</script>

<style scoped>
.dl-ico {
  width: 52px; height: 52px; border-radius: 16px; display: grid; place-items: center; flex: none;
  background: rgba(224, 84, 78, 0.14); border: 1px solid rgba(224, 84, 78, 0.35); color: var(--cc-sell-text);
}
.dl-end { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-radius: 16px; background: rgba(255, 255, 255, 0.045); border: 1px solid rgba(255, 255, 255, 0.09); font-size: 13.5px; color: var(--cc-ink-2); }
.dl-end b { font-family: var(--cc-mono); font-weight: 600; }
</style>
