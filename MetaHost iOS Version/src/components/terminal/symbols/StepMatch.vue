<template>
  <!-- Step 1: each symbol the robot trades, matched to this broker's own name.
       Two layouts of the same rows: List (default) and Bridge, which draws the
       robot's name and the broker's name as two ends of a connector. -->
  <div>
    <h2 class="sx-h">Your robot trades these</h2>
    <p class="sx-lead">Each one is matched to the name {{ ctl.A.value.broker }} uses for it.</p>

    <div v-if="rows.length" class="sm-seg" role="tablist" aria-label="Layout">
      <button
        v-for="s in STYLES" :key="s.id" role="tab" class="sm-seg-btn"
        :class="{ 'is-on': style === s.id }" :aria-selected="style === s.id" @click="setStyle(s.id)"
      >{{ s.label }}</button>
    </div>

    <div v-if="!rows.length" class="sx-card"><div class="sx-row"><p class="sx-note">Your licence doesn’t list any symbols yet. Ask your robot provider to add some.</p></div></div>

    <div v-else-if="style === 'list'" class="sx-card">
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

    <div v-else class="sx-card sm-br">
      <div class="sm-br-head"><span>Your robot</span><span>{{ brokerTag }}</span></div>
      <div v-for="(r, i) in view" :key="r.id" class="sm-br-row">
        <div class="sm-br-line">
          <div class="sm-br-left" :class="['b-' + r.tone, { 'is-dim': r.dim }]">
            <span class="sm-br-name">{{ r.name }}</span>
            <button
              v-if="r.canToggle"
              class="sx-switch sm-sw"
              :class="{ 'is-on': r.on }"
              role="switch"
              :aria-checked="r.on"
              :aria-label="`Trade ${r.name}`"
              @click="ctl.toggle(r.row)"
            ></button>
          </div>
          <div class="sm-br-wire" :class="{ 'is-live': r.live, 'is-amb': r.amb }">
            <span v-if="r.live" class="sm-br-dot" :style="{ animationDelay: i * 0.35 + 's' }"></span>
          </div>
          <div class="sm-br-right" :class="['b-' + r.tone, { 'is-live': r.live, 'is-amb': r.amb }]">{{ r.rightText }}</div>
        </div>

        <div v-if="r.showPick" class="sm-br-pick">
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
        </div>

        <p v-if="r.note" class="sx-note sm-br-note">{{ r.note }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { friendly } from '../../../services/symbolSetupService.js'
import { accountTag } from './fmt.js'

const props = defineProps({ ctl: { type: Object, required: true } })

/* The layout is a per-device preference, not part of the account's setup.
   Storage can be unavailable (private mode), so reads and writes are guarded. */
const STYLE_KEY = 'nh.symbols.step1Style'
const STYLES = [{ id: 'list', label: 'List' }, { id: 'bridge', label: 'Bridge' }]
function readStyle() {
  try { return localStorage.getItem(STYLE_KEY) === 'bridge' ? 'bridge' : 'list' } catch { return 'list' }
}
const style = ref(readStyle())
function setStyle(id) {
  style.value = id
  try { localStorage.setItem(STYLE_KEY, id) } catch { /* preference only */ }
}

const rows = computed(() => props.ctl.R.value)
const brokerTag = computed(() => {
  const a = props.ctl.A.value
  const tag = accountTag(a)
  return tag ? `${a.broker} ${tag}` : a.broker
})

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
      live: on && !paused && !!resolved,
      rightText: amb ? 'Pick one' : row.kind === 'none' ? 'Not offered' : (resolved || 'Not matched'),
      canToggle: row.kind !== 'none',
      showPick: row.kind === 'pick' && on,
      pickQ: resolved ? 'Picked by you · tap to change' : `Which one is ${name} on ${a.broker}?`,
    }
  })
})
</script>

<style scoped>
.sm-seg {
  display: inline-grid; grid-template-columns: 1fr 1fr; gap: 2px; padding: 3px; margin: -4px 0 14px;
  border-radius: 12px; background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.08);
}
.sm-seg-btn {
  height: 30px; padding: 0 14px; border: 0; border-radius: 9px; cursor: pointer; background: none;
  font: inherit; font-size: 13px; font-weight: 600; color: var(--cc-ink-3); transition: background 200ms ease, color 200ms ease;
}
.sm-seg-btn.is-on { background: rgba(255, 255, 255, 0.12); color: var(--cc-ink); }

/* ── Bridge ───────────────────────────────────────────────── */
.sm-br { padding: 2px 14px 4px; }
.sm-br-head {
  display: flex; justify-content: space-between; gap: 12px; padding: 12px 2px 10px;
  font-family: var(--cc-mono); font-size: 10.5px; letter-spacing: 0.14em; color: var(--cc-ink-4); text-transform: uppercase;
}
.sm-br-head span:last-child { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sm-br-row { display: flex; flex-direction: column; gap: 10px; padding: 12px 0; border-top: 1px solid rgba(255, 255, 255, 0.07); }
.sm-br-line { display: grid; grid-template-columns: minmax(0, 132px) minmax(14px, 1fr) auto; align-items: center; min-height: 44px; }

.sm-br-left {
  display: flex; align-items: center; gap: 8px; height: 44px; padding: 0 6px 0 13px; border-radius: 22px; min-width: 0;
  background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.12);
}
.sm-br-left.is-dim { opacity: 0.55; }
.sm-br-name { flex: 1; min-width: 0; font-size: 14.5px; font-weight: 600; color: var(--cc-ink); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sm-sw { width: 36px; height: 22px; border-radius: 11px; }
.sm-sw::after { top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 9px; }
.sm-sw.is-on::after { transform: translateX(14px); }

.sm-br-wire { position: relative; height: 2px; margin: 0 6px; background: rgba(255, 255, 255, 0.07); }
.sm-br-wire.is-live { background: rgba(48, 209, 88, 0.5); box-shadow: 0 0 10px rgba(48, 209, 88, 0.5); }
.sm-br-wire.is-amb { background: repeating-linear-gradient(90deg, rgba(255, 176, 32, 0.6) 0 4px, transparent 4px 8px); }
.sm-br-dot {
  position: absolute; top: -2px; left: 0; width: 6px; height: 6px; border-radius: 3px;
  background: var(--sx-green-ink); box-shadow: 0 0 10px var(--sx-green); animation: sm-flow 2.4s ease-in-out infinite;
}
@keyframes sm-flow { 0% { left: 0; opacity: 0 } 15% { opacity: 1 } 85% { opacity: 1 } 100% { left: calc(100% - 6px); opacity: 0 } }

.sm-br-right {
  max-width: 150px; height: 38px; padding: 0 12px; border-radius: 19px; display: flex; align-items: center;
  font-family: var(--cc-mono); font-size: 12.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; border: 1px solid;
}
.b-green { border-color: rgba(48, 209, 88, 0.4); }
.b-amber { border-color: rgba(255, 176, 32, 0.42); }
.b-grey { border-color: rgba(255, 255, 255, 0.12); }
.sm-br-right.b-green { background: rgba(48, 209, 88, 0.12); color: var(--sx-green-ink); }
.sm-br-right.b-amber { background: rgba(255, 176, 32, 0.12); color: var(--sx-amber); }
.sm-br-right.b-grey { background: rgba(255, 255, 255, 0.05); color: var(--cc-ink-3); }
.sm-br-right.is-live { box-shadow: 0 0 18px -6px rgba(48, 209, 88, 0.6); }
.sm-br-right.is-amb { box-shadow: 0 0 18px -6px rgba(255, 176, 32, 0.6); }

.sm-br-pick { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; text-align: right; }
.sm-br-pick .sx-chips { justify-content: flex-end; }
.sm-br-note { text-align: right; padding-left: 40px; }

@media (prefers-reduced-motion: reduce) { .sm-br-dot { animation: none; left: 50%; } }
</style>
