<template>
  <!-- Step 3: ask the broker about every symbol, under the name that was saved. -->
  <div>
    <h2 class="sx-h">Ready</h2>
    <p class="sx-lead">Check each symbol with {{ a.broker }} before your robot uses it.</p>

    <div v-if="done" class="ts-ready">
      <span class="sx-caps ts-rk">Account ready</span>
      <span class="ts-rh">{{ ctl.readyCount(a, ctl.R.value, c) }} of {{ ctl.R.value.length }} symbols ready on {{ a.broker }} — your robot can trade these</span>
    </div>

    <div class="sx-card">
      <div v-for="t in tests" :key="t.id" class="sx-row" :class="{ 'is-red': t.bad, 'is-dim': t.skip }">
        <div class="sx-line">
          <div class="sx-id">
            <span class="sx-name">{{ t.name }}</span>
            <span class="sx-sub">{{ t.sub }}</span>
          </div>
          <span class="sx-pill" :class="[t.tone, { plain: t.checking }]">
            <span v-if="t.checking" class="sx-spin"></span>{{ t.pill }}
          </span>
        </div>
        <div v-if="t.bad" class="ts-prob">
          <p class="sx-note">{{ t.problem }}</p>
          <button class="sx-btn" @click="$emit('fix')">Fix</button>
        </div>
      </div>
    </div>

    <button v-if="done && ctl.multi.value" class="ts-all" @click="$emit('overview')">View all accounts</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { friendly } from '../../../services/symbolSetupService.js'
import { tradesWord } from './fmt.js'

const props = defineProps({ ctl: { type: Object, required: true } })
defineEmits(['fix', 'overview'])

const a = computed(() => props.ctl.A.value)
const c = computed(() => props.ctl.C.value)
const done = computed(() => c.value.phase === 'done' && c.value.ready)

const tests = computed(() => {
  const { R, nameFor, tradesFor, lotFor, usable } = props.ctl
  return R.value.map((row) => {
    const name = friendly(row.id), resolved = nameFor(row, c.value), on = c.value.on[row.id] !== false
    const t = tradesFor(row, c.value), v = c.value.res[row.id]
    const skip = !usable(row, c.value) || t === 0
    let sub, pill = 'Not tested', tone = 'grey'
    if (row.kind === 'none') { sub = `Not on ${a.value.broker}`; pill = 'Not offered' }
    else if (!on) { sub = 'Off'; pill = 'Skipped' }
    else if (!resolved) { sub = 'Not matched'; pill = 'Skipped' }
    else if (t === 0) { sub = `${resolved} · paused`; pill = 'Paused' }
    else {
      sub = `${resolved} · ${lotFor(a.value, row, c.value).toFixed(2)} lots · up to ${tradesWord(t)}`
      if (v === 'chk') pill = 'Checking…'
      else if (v === 'ok') { pill = 'Ready'; tone = 'green' }
      else if (v === 'bad') { pill = 'Problem'; tone = 'red' }
    }
    const p = c.value.problems[row.id]
    const problem = p
      ? `${a.value.broker} rejected ${resolved}. ${p.message && !p.message.includes('not accepted') ? p.message + ' ' : ''}${p.works ? `Try ${p.works}.` : 'Pick another name.'}`
      : ''
    return { id: row.id, name, sub, pill, tone, skip, checking: v === 'chk', bad: v === 'bad', problem }
  })
})
</script>

<style scoped>
.ts-ready {
  display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; padding: 16px; border-radius: 20px;
  background: linear-gradient(160deg, rgba(48, 209, 88, 0.14), rgba(48, 209, 88, 0.03)); border: 1px solid rgba(48, 209, 88, 0.35);
}
.ts-rk { color: var(--sx-green-ink); }
.ts-rh { font-size: 15px; line-height: 21px; font-weight: 600; color: var(--cc-ink); }
.ts-prob { display: flex; align-items: center; gap: 12px; }
.ts-prob .sx-note { flex: 1; color: #FFD5D6; }
.ts-all { margin-top: 14px; width: 100%; height: 40px; border: 0; background: none; cursor: pointer; font: inherit; font-size: 14px; font-weight: 500; color: var(--cc-ink-3); }
</style>
