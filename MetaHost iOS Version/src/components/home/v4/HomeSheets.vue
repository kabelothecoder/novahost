<template>
  <!-- Design 2g (robots), 2h (symbols), 2i (status). Drawn inside the stage. -->
  <template v-if="sheet">
    <div class="hsh-scrim" @click="$emit('close')"></div>
    <div class="hsh" role="dialog" :aria-label="title">
      <div class="hsh-grab"></div>

      <!-- ── Status ─────────────────────────────────────────────── -->
      <template v-if="sheet === 'status'">
        <div class="hsh-pill" :style="pillStyle">
          <span v-if="kind === 'error'" class="ms" style="font-size:16px">link_off</span>
          <div v-else class="hsh-pill-dot" :style="{ background: st.pillColor }"></div>
          <div>{{ st.pill }}</div>
        </div>
        <div class="hsh-title">{{ st.title }}</div>
        <div class="hsh-body">{{ st.body }}</div>

        <div v-if="kind !== 'error'" class="hsh-list">
          <div class="hsh-row">
            <span class="ms" :style="{ color: session.brokerConnected ? GREEN : GREY }">{{ session.brokerConnected ? 'check_circle' : 'radio_button_unchecked' }}</span>
            <div class="hsh-row-l">Broker</div>
            <div class="hsh-row-r">{{ brokerLine }}</div>
          </div>
          <div class="hsh-row">
            <span class="ms" :style="{ color: issues ? AMBER : GREEN }">{{ issues ? 'error' : 'check_circle' }}</span>
            <div class="hsh-row-l">Trading symbols</div>
            <div class="hsh-row-r">{{ symbolsLine }}</div>
          </div>
          <div class="hsh-row">
            <span class="ms" :style="{ color: GREEN }">check_circle</span>
            <div class="hsh-row-l">Open trades</div>
            <div class="hsh-row-r">{{ trades.openCount }}</div>
          </div>
        </div>

        <!-- Command Core has no slide control, so power lives here. -->
        <div v-if="power" class="hsh-cta" role="button" @click="$emit('power')">
          <span class="ms" style="font-size:21px">power_settings_new</span>
          <div>{{ running ? `Stop ${robotName}` : `Start ${robotName}` }}</div>
        </div>

        <template v-else>
          <div class="hsh-cta" role="button" @click="$emit('go', '/terminal')">
            <span class="ms" style="font-size:21px">key</span>
            <div>Reconnect broker</div>
          </div>
          <div class="hsh-ghost" role="button" @click="$emit('close')">Not now</div>
        </template>
      </template>

      <!-- ── Robots ─────────────────────────────────────────────── -->
      <template v-else-if="sheet === 'robots'">
        <div class="hsh-h">Your robots</div>
        <div class="hsh-sub">{{ list.length > 1 ? 'Or swipe the video left and right to switch.' : 'Add another licence key in Settings to run a second robot.' }}</div>
        <div class="hsh-grid">
          <div
            v-for="(r, j) in list"
            :key="r.id"
            role="button"
            class="hsh-bot"
            :style="{ '--c': r.accent, boxShadow: j === current ? `0 0 0 2px ${r.accent}, 0 0 22px -4px ${r.accent}` : '0 0 0 1px rgba(255,255,255,.1)' }"
            @click="$emit('pick', r.id)"
          >
            <div class="hsh-bot-art" :style="{ backgroundImage: `url(${r.art})`, filter: j === current && running ? 'none' : 'brightness(.6)' }"></div>
            <div class="hsh-bot-fade"></div>
            <div class="hsh-bot-meta">
              <div class="hsh-bot-name">{{ r.name.toUpperCase() }}</div>
              <div class="hsh-bot-state">
                <div class="hsh-bot-dot" :style="{ background: j === current && running ? GREEN : GREY }"></div>
                <div>{{ r.busy ? 'Switching…' : j === current ? (running ? 'Running' : 'Stopped') : 'Tap to switch' }}</div>
              </div>
            </div>
            <div v-if="j === current" class="hsh-bot-check"><span class="ms" style="font-size:14px;font-weight:500">check</span></div>
          </div>
        </div>
      </template>

      <!-- ── Symbols ────────────────────────────────────────────── -->
      <template v-else-if="sheet === 'symbols'">
        <div class="hsh-h-row">
          <div class="hsh-h">Trading symbols</div>
          <div v-if="issues" class="hsh-warn">{{ issues }} {{ issues === 1 ? 'needs' : 'need' }} setting up</div>
        </div>
        <div class="hsh-sub">{{ rows.length ? `${robotName} trades ${rows.length === 1 ? 'this symbol' : 'these ' + rows.length}.` : `${robotName}'s symbols load once your licence is checked.` }}</div>
        <div v-if="rows.length" class="hsh-list hsh-list-sym">
          <div v-for="(y, n) in rows" :key="y.sym" class="hsh-row hsh-row-top" :class="{ 'hsh-row-b': n }">
            <span class="ms" :style="{ color: y.ready ? GREEN : AMBER }">{{ y.ready ? 'check_circle' : 'error' }}</span>
            <div style="flex:1;min-width:0">
              <div class="hsh-sym">{{ y.sym }}</div>
              <div class="hsh-sym-note" :style="{ color: y.ready ? '#A3A3AE' : '#E8C47A' }">{{ y.note }}</div>
            </div>
          </div>
        </div>
        <div class="hsh-cta" role="button" @click="$emit('go', '/symbols')">
          <span class="ms" style="font-size:21px">tune</span>
          <div>Open symbol wizard</div>
        </div>
      </template>
    </div>
  </template>
</template>

<script setup>
import { computed } from 'vue'
import { session } from '../../../stores/session.js'
import { trades } from '../../../stores/trades.js'
import { GREEN, GREY, AMBER } from './homeData.js'

const props = defineProps({
  sheet:     { type: String, default: null },
  kind:      { type: String, default: 'stopped' }, // running | stopped | error
  robotName: { type: String, default: '' },
  list:      { type: Array, default: () => [] },
  current:   { type: Number, default: 0 },
  running:   { type: Boolean, default: false },
  rows:      { type: Array, default: () => [] },
  issues:    { type: Number, default: 0 },
  power:     { type: Boolean, default: false },
})
defineEmits(['close', 'pick', 'go', 'power'])

const title = computed(() =>
  props.sheet === 'robots' ? 'Your robots' : props.sheet === 'symbols' ? 'Trading symbols' : 'Robot status')

const brokerLine = computed(() =>
  session.brokerConnected ? `${session.brokerServer || 'Broker'} · connected` : 'Not connected')

const symbolsLine = computed(() => {
  const total = props.rows.length
  if (!total) return '—'
  return `${total - props.issues} of ${total} ready`
})

const st = computed(() => {
  const n = props.robotName
  if (props.kind === 'error') return {
    pill: 'Trades on hold', pillColor: '#FF7A7A', pillBg: 'rgba(255,93,93,.12)', pillBorder: 'rgba(255,93,93,.7)',
    title: `${n} can't reach your broker`,
    body: `The broker link is down, so no new trades are being placed. Open trades stay at your broker with their stop-loss.`,
  }
  if (props.kind === 'running') return {
    pill: 'Running', pillColor: GREEN, pillBg: 'rgba(76,215,135,.1)', pillBorder: 'rgba(76,215,135,.5)',
    title: `${n} is running`,
    body: `Watching ${props.rows.length || 'your'} symbol${props.rows.length === 1 ? '' : 's'} · ${trades.openCount} trade${trades.openCount === 1 ? '' : 's'} open`,
  }
  return {
    pill: 'Stopped', pillColor: '#B9B9C2', pillBg: 'rgba(255,255,255,.06)', pillBorder: 'rgba(255,255,255,.18)',
    title: `${n} is stopped`,
    body: "It won't open new trades. Open trades keep their stop-loss at your broker. Slide the power button to start.",
  }
})

const pillStyle = computed(() => ({
  border: `1px solid ${st.value.pillBorder}`,
  background: st.value.pillBg,
  color: st.value.pillColor,
}))
</script>

<style scoped>
.ms {
  font-family: 'Material Symbols Outlined';
  font-weight: 400; font-style: normal; line-height: 1; letter-spacing: normal;
  font-feature-settings: 'liga'; font-size: 20px;
}
.hsh-scrim { position: absolute; inset: 0; z-index: 20; background: rgba(4,4,6,.55); }
.hsh {
  position: absolute; left: 8px; right: 8px; bottom: 8px; z-index: 21;
  border-radius: 32px; background: #131318; border: 1px solid rgba(255,255,255,.08);
  box-shadow: 0 -24px 60px rgba(0,0,0,.55); padding: 10px 20px 26px;
  display: flex; flex-direction: column;
  animation: hshUp .3s cubic-bezier(.2,.8,.2,1);
}
@keyframes hshUp { from { transform: translateY(40px); opacity: 0 } to { transform: none; opacity: 1 } }
@media (prefers-reduced-motion: reduce) { .hsh { animation: none } }
.hsh-grab { align-self: center; width: 36px; height: 4px; border-radius: 2px; background: rgba(255,255,255,.22); }
.hsh-pill { margin-top: 18px; align-self: flex-start; display: flex; align-items: center; gap: 7px; height: 26px; padding: 0 11px 0 9px; border-radius: 13px; font-size: 12.5px; font-weight: 600; }
.hsh-pill-dot { width: 7px; height: 7px; border-radius: 50%; }
.hsh-title { margin-top: 12px; font-size: 24px; font-weight: 600; letter-spacing: -.01em; line-height: 30px; text-wrap: pretty; }
.hsh-body { margin-top: 6px; font-size: 14.5px; line-height: 21px; color: #B9B9C2; text-wrap: pretty; }
.hsh-list { margin-top: 18px; border-radius: 20px; background: rgba(255,255,255,.035); border: 1px solid rgba(255,255,255,.08); overflow: hidden; }
.hsh-list-sym { margin-top: 16px; }
.hsh-row { display: flex; align-items: center; gap: 12px; padding: 14px 16px; }
.hsh-row + .hsh-row { border-top: 1px solid rgba(255,255,255,.07); }
.hsh-row-top { align-items: flex-start; }
.hsh-row-l { flex: 1; min-width: 0; font-size: 15px; }
.hsh-row-r { flex: none; white-space: nowrap; font-size: 13.5px; color: #C9C9D1; max-width: 190px; overflow: hidden; text-overflow: ellipsis; }
.hsh-cta { margin-top: 22px; height: 56px; border-radius: 28px; display: flex; align-items: center; justify-content: center; gap: 10px; cursor: pointer; background: #F4F4F7; color: #0B0B0E; font-size: 16px; font-weight: 600; }
.hsh-cta:active { transform: scale(.985); }
.hsh-ghost { margin-top: 4px; height: 44px; display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 14.5px; font-weight: 500; color: #C9C9D1; }
.hsh-h { font-size: 22px; font-weight: 600; letter-spacing: -.01em; }
.hsh-h-row { margin-top: 18px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.hsh > .hsh-h { margin-top: 18px; }
.hsh-sub { margin-top: 4px; font-size: 14px; color: #A3A3AE; }
.hsh-warn { flex: none; height: 26px; padding: 0 10px; border-radius: 13px; display: flex; align-items: center; border: 1px solid rgba(242,181,58,.6); background: rgba(242,181,58,.12); font-size: 12.5px; font-weight: 600; color: #F2B53A; }
.hsh-sym { font-family: 'Geist Mono', ui-monospace, monospace; font-size: 14.5px; font-weight: 600; }
.hsh-sym-note { margin-top: 2px; font-size: 12.5px; line-height: 18px; }
.hsh-grid { margin-top: 18px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.hsh-bot { position: relative; height: 156px; border-radius: 18px; overflow: hidden; cursor: pointer; background: #0D0D11; }
.hsh-bot-art { position: absolute; inset: 0; background-size: cover; background-position: 50% 35%; }
.hsh-bot-fade { position: absolute; inset: 0; background: linear-gradient(180deg, transparent 35%, rgba(6,6,9,.92)); }
.hsh-bot-meta { position: absolute; left: 10px; right: 8px; bottom: 10px; display: flex; flex-direction: column; gap: 5px; }
.hsh-bot-name { font-family: Unbounded, Geist, sans-serif; font-weight: 700; font-size: 15px; letter-spacing: .06em; color: transparent; -webkit-text-stroke: 1px color-mix(in srgb, var(--c) 55%, #FFFFFF); filter: drop-shadow(0 0 5px var(--c)); white-space: nowrap; overflow: hidden; }
.hsh-bot-state { display: flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 500; color: #C9C9D1; }
.hsh-bot-dot { width: 6px; height: 6px; border-radius: 50%; }
.hsh-bot-check { position: absolute; right: 8px; top: 8px; width: 22px; height: 22px; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: var(--c); border: 2px solid #131318; color: #fff; }
</style>
