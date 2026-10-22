<template>
  <BottomSheet label="Copy settings" @close="$emit('close')">
    <div>
      <h2 class="sh-title">Copy settings</h2>
      <p class="sh-lead">Symbols are re-matched to the new broker’s names. Risk is converted to its balance and currency.</p>
    </div>

    <div class="cp-block">
      <span class="sx-caps">From</span>
      <div class="sx-chips">
        <button v-for="o in froms" :key="o" class="sx-chip cp-chip" :class="{ 'is-sel': o === st.copyFrom }" @click="setFrom(o)">{{ label(o) }}</button>
      </div>
      <span class="sx-caps">To</span>
      <div class="sx-chips">
        <button v-for="o in tos" :key="o" class="sx-chip cp-chip" :class="{ 'is-sel': o === st.copyTo }" @click="st.copyTo = o">{{ label(o) }}</button>
      </div>
    </div>

    <div v-if="F && T" class="sx-card">
      <div v-for="r in rows" :key="r.id" class="cp-row">
        <span class="cp-n">{{ r.name }}</span>
        <span class="sx-mono cp-from">{{ r.from }}</span>
        <span class="cp-arrow" aria-hidden="true">→</span>
        <span class="sx-mono cp-to" :class="{ 'is-none': r.none }">{{ r.to }}</span>
      </div>
      <div class="cp-row cp-risk">
        <span class="cp-n">Risk</span>
        <span class="sx-mono cp-from">{{ riskFrom }}</span>
        <span class="cp-arrow" aria-hidden="true">→</span>
        <span class="sx-mono cp-to">{{ riskTo }}</span>
      </div>
    </div>

    <div class="sh-actions">
      <button class="sh-primary" :disabled="!F || !T" @click="$emit('copy')">Copy to {{ T ? T.broker : '…' }}</button>
      <button class="sh-ghost" @click="$emit('close')">Cancel</button>
    </div>
  </BottomSheet>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import BottomSheet from '../../accounts/sheets/BottomSheet.vue'
import { friendly } from '../../../services/symbolSetupService.js'
import { money, accountTag } from './fmt.js'

const props = defineProps({ ctl: { type: Object, required: true } })
defineEmits(['close', 'copy'])

const st = props.ctl.st
const label = (id) => `${st.accounts[id].broker} ${accountTag(st.accounts[id])}`
const froms = computed(() => st.order.filter((o) => st.cfg[o]?.ready))
const tos = computed(() => st.order.filter((o) => o !== st.copyFrom))
const F = computed(() => st.accounts[st.copyFrom])
const T = computed(() => st.accounts[st.copyTo])

function setFrom(o) {
  st.copyFrom = o
  if (st.copyTo === o) st.copyTo = st.order.find((q) => q !== o) || null
}

onMounted(() => {
  if (!st.copyFrom || !st.cfg[st.copyFrom]?.ready) st.copyFrom = froms.value[0] || null
  if (!st.copyTo || st.copyTo === st.copyFrom) st.copyTo = st.order.find((q) => q !== st.copyFrom) || null
})

const rows = computed(() => {
  const fc = st.cfg[st.copyFrom]
  const toRows = st.rows[st.copyTo] || []
  return (st.rows[st.copyFrom] || []).map((r) => {
    const tr = toRows.find((x) => x.id === r.id)
    const toName = !tr || tr.kind === 'none' ? 'not offered' : tr.match || tr.ok || 'you pick'
    return { id: r.id, name: friendly(r.id), from: props.ctl.nameFor(r, fc) || '—', to: toName, none: !tr || tr.kind === 'none' }
  })
})
const preset = computed(() => props.ctl.presetOf(st.cfg[st.copyFrom] || {}))
const riskFrom = computed(() => F.value ? `${preset.value.name} · ${money(F.value, (F.value.balance * preset.value.pct) / 100)}` : '')
const riskTo = computed(() => T.value ? `${preset.value.name} · ${money(T.value, (T.value.balance * preset.value.pct) / 100)}` : '')
</script>

<style scoped>
.cp-block { display: flex; flex-direction: column; gap: 8px; }
.cp-chip { font-family: inherit; font-weight: 600; }
.cp-chip.is-sel { background: rgba(255, 255, 255, 0.12); border-color: rgba(255, 255, 255, 0.32); color: var(--cc-ink); }
.cp-row { display: grid; grid-template-columns: 74px minmax(0, 1fr) 14px minmax(0, 1fr); gap: 8px; align-items: center; padding: 11px 16px; font-size: 12.5px; }
.cp-row + .cp-row { border-top: 1px solid rgba(255, 255, 255, 0.07); }
.cp-n { font-weight: 600; color: var(--cc-ink); font-size: 13.5px; }
.cp-from { color: var(--cc-ink-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cp-arrow { color: var(--cc-ink-4); }
.cp-to { color: var(--sx-green-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cp-to.is-none { color: var(--cc-ink-4); }
</style>
