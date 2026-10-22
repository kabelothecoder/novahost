<template>
  <BottomSheet label="Configure which account" @close="$emit('close')">
    <div>
      <h2 class="sh-title">Configure which account?</h2>
      <p class="sh-lead">Each one keeps its own symbols, risk, lot sizes and trades at once.</p>
    </div>
    <div class="sx-card">
      <button v-for="o in rows" :key="o.id" class="sw-row" :class="{ 'is-cur': o.cur }" @click="$emit('pick', o.id)">
        <span class="sx-id">
          <span class="sx-name">{{ o.broker }} <span class="sx-mono sw-tag">{{ o.tag }}</span></span>
          <span class="sw-sub">{{ o.sub }}</span>
        </span>
        <span class="sx-pill" :class="o.tone">{{ o.status }}</span>
      </button>
    </div>
    <div class="sh-actions">
      <button class="sh-primary" @click="$emit('overview')">All accounts</button>
      <button class="sh-ghost" @click="$emit('add')">Add another account</button>
    </div>
  </BottomSheet>
</template>

<script setup>
import { computed } from 'vue'
import BottomSheet from '../../accounts/sheets/BottomSheet.vue'
import { money, accountTag } from './fmt.js'

const props = defineProps({ ctl: { type: Object, required: true } })
defineEmits(['close', 'pick', 'overview', 'add'])

const rows = computed(() => props.ctl.st.order.map((id) => {
  const a = props.ctl.st.accounts[id], s = props.ctl.statusOf(id), cur = id === props.ctl.st.acc
  return { id, cur, broker: a.broker, tag: accountTag(a), sub: `${money(a, a.balance)} · ${a.currency}${cur ? ' · editing now' : ''}`, status: s.text === 'Trading' ? 'Configured' : s.text, tone: s.tone }
}))
</script>

<style scoped>
.sw-row { width: 100%; display: flex; align-items: center; gap: 12px; padding: 14px 16px; border: 0; background: transparent; cursor: pointer; text-align: left; font: inherit; }
.sw-row + .sw-row { border-top: 1px solid rgba(255, 255, 255, 0.07); }
.sw-row.is-cur { background: rgba(255, 255, 255, 0.07); }
.sw-tag { font-size: 12px; font-weight: 500; color: var(--cc-ink-3); }
.sw-sub { font-size: 12.5px; color: var(--cc-ink-3); }
</style>
