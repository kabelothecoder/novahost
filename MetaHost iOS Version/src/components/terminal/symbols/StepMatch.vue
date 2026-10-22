<template>
  <!-- Step 1: each symbol the robot trades, matched to this broker's own name. -->
  <div>
    <h2 class="sx-h">Your robot trades these</h2>
    <p class="sx-lead">Each one is matched to the name {{ ctl.A.value.broker }} uses for it.</p>

    <div v-if="!rows.length" class="sx-card"><div class="sx-row"><p class="sx-note">Your licence doesn’t list any symbols yet. Ask your robot provider to add some.</p></div></div>

    <div v-else class="sx-card">
      <div v-for="r in view" :key="r.id" class="sx-row" :class="{ 'is-amber': r.amb, 'is-dim': r.dim }">
        <div class="sx-line">
          <div class="sx-id">
            <span class="sx-name">{{ r.name }}</span>
            <span class="sx-sub">{{ r.sub }}</span>
          </div>
          <span class="sx-pill" :class="r.tone">{{ r.pill }}</span>
          <button
            v-if="r.canToggle"
            class="sx-switch"
            :class="{ 'is-on': r.on }"
            role="switch"
            :aria-checked="r.on"
            :aria-label="`Trade ${r.name}`"
            @click="ctl.toggle(r.row)"
          ></button>
        </div>

        <template v-if="r.showPick">
          <span class="sx-note">{{ r.pickQ }}</span>
          <div class="sx-chips">
            <button
              v-for="c in r.row.candidates"
              :key="c"
              class="sx-chip"
              :class="{ 'is-on': ctl.C.value.picks[r.id] === c }"
              @click="ctl.pick(r.row, c)"
            >{{ c }}</button>
          </div>
        </template>

        <p v-if="r.note" class="sx-note">{{ r.note }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { friendly } from '../../../services/symbolSetupService.js'

const props = defineProps({ ctl: { type: Object, required: true } })

const rows = computed(() => props.ctl.R.value)

const view = computed(() => {
  const { C, A, nameFor, tradesFor } = props.ctl
  const c = C.value, a = A.value
  return rows.value.map((row) => {
    const on = c.on[row.id] !== false
    const name = friendly(row.id)
    const resolved = nameFor(row, c)
    const amb = row.kind === 'pick' && on && !resolved
    const paused = on && !!resolved && tradesFor(row, c) === 0
    let sub = resolved || '', pill = 'Matched', tone = 'green', note = ''
    if (row.kind === 'none') { sub = `Not on ${a.broker}`; pill = 'Not offered'; tone = 'grey'; note = row.note || `${a.broker} doesn’t list ${name}, so your robot skips it here.` }
    else if (!on) { sub = `${resolved || 'Not matched'} · off`; pill = 'Off'; tone = 'grey' }
    else if (amb) { sub = `${row.candidates.length} possible names`; pill = 'Pick one'; tone = 'amber' }
    else if (paused) { pill = 'Paused'; tone = 'grey'; note = `Paused — robot won’t trade ${name} on this account` }
    return {
      id: row.id, row, name, sub, pill, tone, note, on, amb,
      dim: !on || row.kind === 'none',
      canToggle: row.kind !== 'none',
      showPick: row.kind === 'pick' && on,
      pickQ: resolved ? 'Picked by you · tap to change' : `Which one is ${name} on ${a.broker}?`,
    }
  })
})
</script>
