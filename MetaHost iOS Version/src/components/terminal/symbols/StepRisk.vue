<template>
  <!-- Step 2: how much this account risks. Presets for most people, per-symbol
       lot and trades-at-once under Advanced. -->
  <div>
    <h2 class="sx-h">How much to risk</h2>
    <p class="sx-lead">Only for {{ a.broker }} {{ tag }}. Your other accounts keep their own.</p>

    <div class="rk-hero">
      <span class="sx-caps">{{ hero.caps }}</span>
      <span class="rk-money">≈ {{ hero.money }}</span>
      <span class="rk-sub">{{ hero.sub }}</span>
    </div>

    <div class="rk-presets">
      <button
        v-for="p in presets"
        :key="p.id"
        class="rk-preset"
        :class="{ 'is-on': p.on }"
        @click="ctl.setPreset(p.id)"
      >
        <span class="rk-pn">{{ p.name }}</span>
        <span class="rk-pm sx-mono">{{ p.pct }}% · {{ p.money }}</span>
        <span class="rk-pt">up to {{ p.trades }} per symbol</span>
      </button>
    </div>

    <button class="rk-adv-head" :aria-expanded="c.adv" @click="c.adv = !c.adv">
      <span class="sx-id">
        <span class="sx-name">Advanced</span>
        <span class="rk-adv-sub">Lot size and trades at once, per symbol</span>
      </span>
      <span class="rk-adv-tog">{{ c.adv ? 'Hide' : 'Show' }}</span>
    </button>

    <div v-if="c.adv" class="sx-card rk-adv">
      <p v-if="limits" class="rk-lim">{{ limits }}</p>
      <div class="rk-cols sx-caps"><span></span><span>Lot size</span><span>Trades at once</span></div>
      <div v-for="l in lots" :key="l.id" class="sx-row">
        <div class="rk-grid">
          <div class="sx-id">
            <span class="sx-name" :class="{ 'is-muted': !l.t }">{{ l.name }}</span>
            <span class="sx-sub">{{ l.sub }}</span>
          </div>
          <div class="rk-step" :style="{ opacity: l.t ? 1 : 0.4 }">
            <button aria-label="Smaller lot" @click="ctl.setLot(l.row, l.lot - 0.01)">−</button>
            <input
              :value="edit[l.id] ?? l.lot.toFixed(2)"
              inputmode="decimal"
              :aria-label="`${l.name} lot size`"
              @focus="edit[l.id] = l.lot.toFixed(2)"
              @input="onLot(l, $event.target.value)"
              @blur="delete edit[l.id]"
            />
            <button aria-label="Bigger lot" :disabled="l.atMax" @click="ctl.setLot(l.row, l.lot + 0.01)">+</button>
          </div>
          <div class="rk-step">
            <button aria-label="Fewer trades" :disabled="!l.t" @click="ctl.setTrades(l.row, l.t - 1)">−</button>
            <span class="rk-tv">{{ l.t }}</span>
            <button aria-label="More trades" :disabled="l.t >= ctl.MAX_TRADES" @click="ctl.setTrades(l.row, l.t + 1)">+</button>
          </div>
        </div>
        <p class="sx-note" :class="{ 'is-warn': l.warn }">{{ l.line }}</p>
      </div>
    </div>

    <div class="rk-total" :class="`is-${tot.level}`">
      <span class="sx-caps rk-tl">{{ totLabel }}</span>
      <span class="rk-tt">If every trade hits its stop: ≈ {{ money(a, tot.total) }} ({{ Math.round(tot.pct * 100) }}% of your balance)</span>
      <span v-if="tot.level !== 'ok'" class="rk-tn">{{ tot.level === 'block' ? 'Above 25% of the balance. Save is blocked until it’s lower.' : 'Above 10% of the balance. You can still save.' }}</span>
      <span v-if="posNote" class="rk-tn">{{ posNote }}</span>
      <button v-if="tot.level === 'block'" class="sx-btn is-red rk-lower" @click="ctl.lowerIt()">Lower it for me</button>
    </div>
    <p class="sx-note rk-est">Estimates assume a 20-pip stop at $10 per lot per pip.</p>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { PRESETS, friendly } from '../../../services/symbolSetupService.js'
import { money, tradesWord, accountTag } from './fmt.js'

const props = defineProps({ ctl: { type: Object, required: true } })
const edit = reactive({})

const a = computed(() => props.ctl.A.value)
const c = computed(() => props.ctl.C.value)
const tag = computed(() => accountTag(a.value))
const tot = computed(() => props.ctl.riskTotals.value)
const custom = computed(() => props.ctl.isCustom(c.value))

const hero = computed(() => {
  const p = props.ctl.presetOf(c.value)
  if (custom.value) {
    const act = props.ctl.activeRows(a.value, props.ctl.R.value, c.value)
    const maxSingle = Math.max(0, ...act.map((r) => props.ctl.lotMoney(a.value, r, c.value)))
    return { caps: 'Custom · set in Advanced', money: money(a.value, maxSingle), sub: `largest single trade on your ${money(a.value, a.value.balance)} account` }
  }
  return {
    caps: `${p.name} · ${p.pct}% of balance`,
    money: money(a.value, (a.value.balance * p.pct) / 100),
    sub: `per trade on your ${money(a.value, a.value.balance)} account, up to ${tradesWord(p.trades)} per symbol`,
  }
})

const presets = computed(() => PRESETS.map((p) => ({
  ...p,
  trades: tradesWord(p.trades),
  money: money(a.value, (a.value.balance * p.pct) / 100),
  on: p.id === c.value.preset && !custom.value,
})))

const lots = computed(() => {
  const { R, usable, nameFor, lotFor, tradesFor, comb } = props.ctl
  return R.value.filter((r) => usable(r, c.value)).map((row) => {
    const lot = lotFor(a.value, row, c.value), t = tradesFor(row, c.value), risk = comb(a.value, row, c.value)
    const name = friendly(row.id)
    return {
      id: row.id, row, name, sub: nameFor(row, c.value), lot, t,
      atMax: !!a.value.maxLot && lot >= a.value.maxLot,
      warn: t > 0 && risk > a.value.balance * props.ctl.WARN,
      line: t ? `${lot.toFixed(2)} lots × ${tradesWord(t)} ≈ ${money(a.value, risk)} at risk on ${name}` : `Paused — robot won’t trade ${name} on this account`,
    }
  })
})

const limits = computed(() => {
  const bits = []
  if (a.value.maxPos) bits.push(`max ${a.value.maxPos} positions`)
  if (a.value.maxLot) bits.push(`max ${a.value.maxLot.toFixed(2)} lots per trade`)
  return bits.length ? `Your broker allows ${bits.join(' and ')}` : ''
})

const totLabel = computed(() => tot.value.level === 'block' ? 'Too much for this account' : tot.value.level === 'warn' ? 'High for this account' : 'Account total')
const posNote = computed(() => a.value.maxPos && tot.value.trades > a.value.maxPos
  ? `Your settings allow ${tot.value.trades} trades at once. Your broker allows max ${a.value.maxPos} positions, so the robot stops at ${a.value.maxPos}.`
  : '')

function onLot(l, raw) {
  const v = raw.replace(',', '.')
  edit[l.id] = v
  const n = parseFloat(v)
  if (n > 0) props.ctl.setLot(l.row, n)
}
</script>

<style scoped>
.rk-hero {
  display: flex; flex-direction: column; gap: 4px; padding: 18px 18px 16px; border-radius: 22px; margin-bottom: 12px;
  background: radial-gradient(240px 160px at 0% 0%, rgba(var(--cc-accent-rgb), 0.18), transparent 70%), rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.rk-money { font-family: var(--cc-mono); font-size: 34px; font-weight: 600; letter-spacing: -0.02em; color: var(--cc-ink); }
.rk-sub { font-size: 13px; line-height: 18px; color: var(--cc-ink-3); }

.rk-presets { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-bottom: 12px; }
.rk-preset {
  display: flex; flex-direction: column; align-items: flex-start; gap: 4px; padding: 12px; border-radius: 16px; cursor: pointer; text-align: left;
  background: rgba(255, 255, 255, 0.04); border: 1px solid rgba(255, 255, 255, 0.09); font: inherit; color: var(--cc-ink);
}
.rk-preset.is-on { background: rgba(var(--cc-accent-rgb), 0.18); border-color: var(--cc-accent); box-shadow: 0 0 26px -8px var(--cc-accent); }
.rk-pn { font-size: 15px; font-weight: 600; }
.rk-pm { font-size: 12px; color: var(--cc-ink-2); }
.rk-pt { font-size: 11px; line-height: 14px; color: var(--cc-ink-4); }

.rk-adv-head {
  width: 100%; display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: 18px; cursor: pointer; text-align: left;
  background: rgba(255, 255, 255, 0.035); border: 1px solid rgba(255, 255, 255, 0.08); font: inherit;
}
.rk-adv-sub { font-size: 12.5px; color: var(--cc-ink-3); }
.rk-adv-tog { font-size: 13px; font-weight: 600; color: var(--cc-accent-text); }
.rk-adv { margin-top: 8px; }
.rk-lim { margin: 0; padding: 10px 16px; font-size: 12.5px; color: var(--sx-amber); background: rgba(255, 176, 32, 0.08); }
.rk-cols, .rk-grid { display: grid; grid-template-columns: minmax(0, 1fr) 112px 96px; gap: 8px; align-items: center; }
.rk-cols { padding: 10px 16px 0; font-size: 10px; }
.rk-step { display: flex; align-items: center; justify-content: space-between; height: 36px; border-radius: 11px; background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.1); }
.rk-step button { width: 30px; height: 34px; border: 0; background: none; color: var(--cc-ink); font-size: 18px; cursor: pointer; }
.rk-step button:disabled { opacity: 0.3; cursor: default; }
.rk-step input { width: 48px; border: 0; background: none; text-align: center; color: var(--cc-ink); font-family: var(--cc-mono); font-size: 14px; font-weight: 600; outline: none; }
.rk-tv { font-family: var(--cc-mono); font-size: 14px; font-weight: 600; color: var(--cc-ink); }
.is-muted { color: var(--cc-ink-4); }
.sx-note.is-warn { color: var(--sx-amber); }

.rk-total { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; padding: 14px 16px; border-radius: 18px; background: rgba(255, 255, 255, 0.02); border: 1px solid rgba(255, 255, 255, 0.07); }
.rk-total.is-warn { background: linear-gradient(160deg, rgba(255, 176, 32, 0.12), rgba(255, 176, 32, 0.03)); border-color: rgba(255, 176, 32, 0.35); }
.rk-total.is-block { background: linear-gradient(160deg, rgba(255, 90, 95, 0.16), rgba(255, 90, 95, 0.05)); border-color: rgba(255, 90, 95, 0.45); }
.rk-total.is-warn .rk-tl { color: var(--sx-amber); }
.rk-total.is-block .rk-tl { color: var(--sx-red); }
.rk-tt { font-size: 14px; line-height: 20px; color: var(--cc-ink); }
.rk-tn { font-size: 12.5px; line-height: 18px; color: var(--cc-ink-3); }
.rk-lower { align-self: flex-start; margin-top: 4px; }
.rk-est { margin-top: 8px; }
</style>
