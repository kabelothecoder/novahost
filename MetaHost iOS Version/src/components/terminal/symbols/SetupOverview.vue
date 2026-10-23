<template>
  <!-- Every account and whether the robot is trading it. Each keeps its own
       symbols and risk. -->
  <div>
    <h2 class="sx-h">{{ headline }}</h2>
    <p class="sx-lead">Each account has its own symbols and risk.</p>

    <div class="ov-list">
      <div v-for="o in rows" :key="o.id" class="ov-card" :class="{ 'is-live': o.live }">
        <div class="sx-line">
          <div class="sx-id">
            <span class="sx-name">{{ o.broker }}</span>
            <span class="sx-sub">{{ o.meta }}</span>
          </div>
          <button
            class="sx-switch"
            :class="{ 'is-on': o.live }"
            role="switch"
            :aria-checked="o.live"
            :aria-label="`Trade ${o.broker}`"
            :disabled="!o.canToggle"
            :title="o.real ? 'Start or stop the robot from Home' : ''"
            @click="toggle(o)"
          ></button>
        </div>
        <span class="ov-sum">{{ o.summary }}</span>
        <span v-if="o.ready" class="ov-ready">{{ o.ready }}</span>
        <span v-if="o.why" class="ov-why">{{ o.why }}</span>
        <div class="ov-foot">
          <span class="sx-pill" :class="o.tone">{{ o.status }}</span>
          <button class="sx-btn" :class="o.btnClass" @click="$emit('edit', o.id)">{{ o.btn }}</button>
        </div>
      </div>
    </div>

    <button v-if="ctl.st.order.length > 1" class="ov-row" @click="$emit('copy')">
      <span class="sx-id"><span class="sx-name">Copy settings from another account</span><span class="ov-rs">Re-matches symbols and converts risk</span></span>
      <span class="ov-go">Copy</span>
    </button>
    <button class="ov-row" @click="$router.push('/accounts')">
      <span class="sx-id"><span class="sx-name">Add another account</span><span class="ov-rs">Manage your linked accounts</span></span>
      <span class="ov-go">Open</span>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { session } from '../../../stores/session.js'
import { money, accountTag } from './fmt.js'

const props = defineProps({ ctl: { type: Object, required: true } })
defineEmits(['edit', 'copy'])

/* The linked (real) account is "trading" when the robot is running; it is
   started and stopped from Home, so its switch here only reports. */
const isLive = (a, c) => a.connected && c.ready && (a.real ? session.isRunning : c.trading)

const rows = computed(() => props.ctl.st.order.map((id) => {
  const { st, presetOf, isCustom, activeRows, sumTrades, readyCount, statusOf } = props.ctl
  const a = st.accounts[id], c = st.cfg[id], rws = st.rows[id] || []
  const live = isLive(a, c)
  const n = activeRows(a, rws, c).length
  let btn = 'Edit', btnClass = '', why = ''
  if (!a.connected) { btn = 'Reconnect'; btnClass = 'is-red'; why = 'Not trading · broker disconnected' }
  else if (!c.ready) { btn = 'Set up'; btnClass = 'is-accent'; why = 'Not trading · needs setup' }
  else if (!live) why = a.real ? 'Not trading · robot stopped' : 'Not trading · switched off'
  const s = statusOf(id)
  return {
    id, real: a.real, broker: a.broker, live, why, btn, btnClass,
    meta: `${accountTag(a)} · ${money(a, a.balance)} · ${a.currency}`,
    summary: c.ready ? `${isCustom(c) ? 'Custom' : presetOf(c).name} risk · ${n} symbol${n === 1 ? '' : 's'} · max ${sumTrades(a, rws, c)} trades` : 'Symbols and risk not set',
    ready: c.ready && c.phase === 'done' ? `${readyCount(a, rws, c)} of ${rws.length} symbols ready` : '',
    status: live ? 'Trading' : s.text === 'Trading' ? 'Off' : s.text, tone: live ? 'green' : s.tone === 'green' ? 'grey' : s.tone,
    canToggle: !a.real && a.connected && c.ready,
  }
}))

const headline = computed(() => {
  const n = rows.value.filter((r) => r.live).length
  return `Your robot trades ${n} account${n === 1 ? '' : 's'}`
})

function toggle(o) {
  if (!o.canToggle) return
  const c = props.ctl.st.cfg[o.id]
  c.trading = !c.trading
}
</script>

<style scoped>
.ov-list { display: flex; flex-direction: column; gap: 10px; }
.ov-card {
  display: flex; flex-direction: column; gap: 6px; padding: 16px; border-radius: 22px;
  background: rgba(255, 255, 255, 0.035); border: 1px solid rgba(255, 255, 255, 0.08);
}
.ov-card.is-live {
  background: linear-gradient(160deg, rgba(48, 209, 88, 0.1), rgba(255, 255, 255, 0.03) 60%);
  border-color: rgba(48, 209, 88, 0.3); box-shadow: 0 20px 44px -26px rgba(48, 209, 88, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
.ov-sum { margin-top: 4px; font-size: 13.5px; color: var(--cc-ink-2); }
.ov-ready { font-size: 12.5px; color: var(--sx-green-ink); }
.ov-why { font-size: 12.5px; color: var(--cc-ink-3); }
.ov-foot { margin-top: 8px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.ov-row {
  margin-top: 10px; width: 100%; display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: 18px; cursor: pointer;
  text-align: left; font: inherit; background: rgba(255, 255, 255, 0.025); border: 1px dashed rgba(255, 255, 255, 0.12);
}
.ov-rs { font-size: 12.5px; color: var(--cc-ink-3); }
.ov-go { font-size: 13px; font-weight: 600; color: var(--cc-accent-text); }
</style>
