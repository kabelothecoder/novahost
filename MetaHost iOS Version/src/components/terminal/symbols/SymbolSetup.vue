<template>
  <!--
    PER-ACCOUNT SYMBOL SETUP — the Broker Terminal's linked state.

    Three steps per account: match each symbol to the broker's own name, choose
    how much to risk, then test every symbol with the broker. Plus an overview
    of all accounts, a switch sheet and a copy sheet. Design source: Claude
    Design `SymbolsPhone2.dc.html`.

    The linked account is REAL (see services/symbolSetupService.js); accounts
    beyond it are mock until the multi-account backend exists.
  -->
  <div class="sx sxs">
    <div v-if="!st.booted" class="sxs-boot"><span class="sx-spin"></span>Reading your broker’s symbols…</div>

    <template v-else-if="A">
      <!-- ── Wizard chrome ───────────────────────────────────────── -->
      <div v-if="st.screen === 'wizard'" class="sxs-nav">
        <button class="sxs-back" aria-label="Back" @click="goBack">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <div class="sxs-segs" aria-hidden="true">
          <span v-for="i in 3" :key="i" :class="{ 'is-on': i <= st.step, 'is-cur': i === st.step }"></span>
        </div>
        <span v-if="state === 'normal'" class="sxs-steplabel">{{ st.step }} / 3</span>
      </div>
      <div v-else class="sxs-nav">
        <button class="sxs-back" aria-label="Back to setup" @click="st.screen = 'wizard'">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <span class="sx-caps">Accounts</span>
      </div>

      <!-- ── Overview ────────────────────────────────────────────── -->
      <SetupOverview v-if="st.screen === 'overview'" :ctl="ctl" @edit="onEdit" @copy="st.sheet = 'copy'" />

      <!-- ── Wizard body ─────────────────────────────────────────── -->
      <template v-else>
        <div v-if="st.busy && state !== 'normal'" class="sxs-loading"><span class="sx-spin"></span>{{ st.busy }}</div>

        <div v-else-if="state === 'disconnected'" class="sxs-state">
          <span class="sx-pill red">Disconnected</span>
          <h2 class="sx-h">{{ A.broker }} is disconnected</h2>
          <p class="sx-lead">Log in to {{ A.broker }} again so your robot can read this account’s symbols and balance. Nothing trades on it until then.</p>
          <div class="sx-card">
            <div class="sxs-kv"><span>Account</span><span class="sx-mono">{{ tag || '—' }}</span></div>
            <div class="sxs-kv"><span>Last balance</span><span class="sx-mono">{{ money(A, A.balance) }}</span></div>
            <div class="sxs-kv"><span>Saved settings</span><span>Kept</span></div>
          </div>
        </div>

        <div v-else-if="state === 'loadfail'" class="sxs-state">
          <span class="sx-pill amber">Symbol list not loaded</span>
          <h2 class="sx-h">Couldn’t load {{ A.broker }}’s symbols</h2>
          <p class="sx-lead">Your robot needs the broker’s symbol list to find names like Gold. Your saved settings for this account are safe.</p>
          <p v-if="loadMsg" class="sx-note sxs-why">{{ loadMsg }}</p>
        </div>

        <template v-else>
          <StepMatch v-if="st.step === 1" :ctl="ctl" />
          <StepRisk v-else-if="st.step === 2" :ctl="ctl" />
          <StepTest v-else :ctl="ctl" @fix="st.step = 1" @overview="st.screen = 'overview'" />
        </template>

        <!-- ── Commit ──────────────────────────────────────────────── -->
        <button class="sxs-cta" :class="{ 'is-accent': cta.accent }" :disabled="cta.disabled" @click="cta.go()">
          <span v-if="cta.spin" class="sx-spin"></span>{{ cta.label }}
        </button>
        <p v-if="st.saveError" class="sxs-err" role="alert">{{ st.saveError }}</p>

        <!-- ── Which account this is for ───────────────────────────── -->
        <component
          :is="multi ? 'button' : 'div'" class="sxs-bar"
          :class="{ 'is-good': C.ready && A.connected, 'is-static': !multi }" @click="multi && onBar()"
        >
          <span class="sx-id">
            <span class="sxs-bar-k">This configuration is for</span>
            <span class="sxs-bar-v">{{ A.broker }} <i>·</i> <span class="sx-mono">{{ tag }}</span> <i>·</i> {{ money(A, A.balance) }}</span>
          </span>
          <span class="sx-pill" :class="barStatus.tone">{{ barStatus.text }}</span>
          <span v-if="multi" class="sxs-bar-go">{{ single ? 'Add' : 'Switch' }}</span>
        </component>
      </template>
    </template>

    <SwitchAccountSheet
      v-if="st.sheet === 'switch'" :ctl="ctl"
      @close="st.sheet = null" @pick="ctl.switchTo" @overview="openOverview" @add="$router.push('/accounts')"
    />
    <CopySettingsSheet v-if="st.sheet === 'copy'" :ctl="ctl" @close="st.sheet = null" @copy="ctl.copy()" />
    <ReadyCelebration
      v-if="st.celebrate" :broker="A.broker" :tag="tag"
      :count="ctl.readyCount(A, ctl.R.value, C)" @close="st.celebrate = false"
    />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import './symbols.css'
import { useSymbolSetup } from './useSymbolSetup.js'
import { money, accountTag } from './fmt.js'
import { friendly } from '../../../services/symbolSetupService.js'
import { haptic } from '../../../composables/useSpeech.js'
import StepMatch from './StepMatch.vue'
import StepRisk from './StepRisk.vue'
import StepTest from './StepTest.vue'
import SetupOverview from './SetupOverview.vue'
import SwitchAccountSheet from './SwitchAccountSheet.vue'
import CopySettingsSheet from './CopySettingsSheet.vue'
import ReadyCelebration from './ReadyCelebration.vue'

const props = defineProps({ mock: Boolean })
/** The linked account needs its broker login again; the Terminal owns that form. */
const emit = defineEmits(['relink'])

const router = useRouter()
const ctl = useSymbolSetup()
const st = ctl.st
const A = ctl.A
const C = ctl.C

const tag = computed(() => accountTag(A.value))
const single = computed(() => st.order.length === 1)
const multi = ctl.multi
const loadMsg = computed(() => st.loadError[st.acc] || '')

const state = computed(() => {
  if (!A.value) return 'none'
  if (!A.value.connected) return 'disconnected'
  if (st.loadError[st.acc]) return 'loadfail'
  return 'normal'
})

const barStatus = computed(() => {
  if (!A.value.connected) return { text: 'Disconnected', tone: 'red' }
  return C.value.ready ? { text: 'Configured ✓', tone: 'green' } : { text: 'Needs setup', tone: 'amber' }
})

function goBack() {
  if (st.step > 1 && state.value === 'normal') st.step--
  else if (multi.value) st.screen = 'overview'
  else router.push('/home')
}

function openOverview() { st.sheet = null; st.screen = 'overview' }

function onBar() {
  if (single.value) router.push('/accounts')
  else st.sheet = 'switch'
}

function onEdit(id) {
  const a = st.accounts[id]
  ctl.switchTo(id)
  if (!a.connected && !a.real) ctl.reconnect()
}

/** The one button at the bottom. Every state says what it is waiting for. */
const cta = computed(() => {
  const a = A.value, c = C.value
  if (st.busy) return { label: st.busy, disabled: true, spin: true, go: () => {} }
  if (state.value === 'disconnected') {
    return a.real
      ? { label: 'Log in again', accent: true, go: () => emit('relink') }
      : { label: 'Reconnect to continue', accent: true, go: () => ctl.reconnect() }
  }
  if (state.value === 'loadfail') return { label: 'Try again', go: () => ctl.retryLoad() }
  if (st.step === 1) {
    const need = ctl.pickNeeded.value
    if (need) return { label: `Pick a match for ${friendly(need.id)}`, disabled: true, go: () => {} }
    if (!ctl.R.value.some((r) => ctl.usable(r, c))) return { label: 'Turn on at least one symbol', disabled: true, go: () => {} }
    return { label: 'Looks good', go: () => { haptic(15); st.step = 2 } }
  }
  if (st.step === 2) {
    if (ctl.riskTotals.value.level === 'block') return { label: 'Save is blocked', disabled: true, go: () => {} }
    return { label: 'Save and continue', go: async () => { haptic(20); if (await ctl.save()) st.step = 3 } }
  }
  if (c.phase === 'checking') return { label: `Checking with ${a.broker}…`, disabled: true, spin: true, go: () => {} }
  if (c.phase === 'done' && c.ready) {
    if (!multi.value) return { label: 'Done', accent: true, go: () => router.push('/home') }
    const next = ctl.nextUnready.value
    return next
      ? { label: 'Set up your next account', accent: true, go: () => ctl.switchTo(next) }
      : { label: 'All accounts ready', accent: true, go: () => { st.screen = 'overview' } }
  }
  return { label: c.phase === 'done' ? 'Test again' : 'Test with broker', go: () => { haptic(20); ctl.runTest() } }
})

onMounted(() => ctl.boot({ mock: props.mock }))
</script>

<style scoped>
.sxs { display: flex; flex-direction: column; }
.sxs-boot, .sxs-loading { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 60px 0; font-size: 14px; color: var(--cc-ink-3); }

.sxs-nav { display: flex; align-items: center; gap: 12px; margin: 8px 0 22px; }
.sxs-back { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 20px; border: 1px solid rgba(255, 255, 255, 0.1); background: rgba(255, 255, 255, 0.06); color: var(--cc-ink); cursor: pointer; }
.sxs-segs { flex: 1; display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.sxs-segs span { height: 4px; border-radius: 2px; background: rgba(255, 255, 255, 0.1); transition: background 300ms ease; }
.sxs-segs span.is-on { background: var(--cc-accent); }
.sxs-segs span.is-cur { box-shadow: 0 0 12px var(--cc-accent); }
.sxs-steplabel { flex: none; font-family: var(--cc-mono); font-size: 12px; color: var(--cc-ink-3); }

.sxs-state { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; }
.sxs-state .sx-lead { margin: 0 0 6px; }
.sxs-state .sx-card { width: 100%; }
.sxs-kv { display: flex; justify-content: space-between; padding: 12px 16px; font-size: 13.5px; color: var(--cc-ink-2); }
.sxs-kv + .sxs-kv { border-top: 1px solid rgba(255, 255, 255, 0.07); }
.sxs-why { font-family: var(--cc-mono); font-size: 12px; }

.sxs-cta {
  margin-top: 22px; height: 56px; border: 0; border-radius: 18px; cursor: pointer; width: 100%;
  display: flex; align-items: center; justify-content: center; gap: 10px; font: inherit; font-size: 16px; font-weight: 600;
  background: #F4F4F7; color: #0B0B0E; box-shadow: 0 14px 36px -12px rgba(255, 255, 255, 0.35);
  transition: transform 180ms var(--cc-spring), opacity 200ms ease;
}
.sxs-cta.is-accent { background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink); box-shadow: 0 14px 40px -10px var(--cc-accent); }
.sxs-cta:active:not(:disabled) { transform: scale(0.985); }
.sxs-cta:disabled { opacity: 0.45; cursor: default; box-shadow: none; }
.sxs-err { margin: 10px 4px 0; font-size: 13px; color: var(--cc-sell-text); }

.sxs-bar {
  margin-top: 12px; width: 100%; display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-radius: 18px; cursor: pointer;
  text-align: left; font: inherit; background: rgba(255, 255, 255, 0.035); border: 1px solid rgba(255, 255, 255, 0.1);
}
.sxs-bar.is-good { border-color: rgba(48, 209, 88, 0.3); }
.sxs-bar.is-static { cursor: default; }
.sxs-bar-k { font-size: 11px; color: var(--cc-ink-4); }
.sxs-bar-v { font-size: 13.5px; font-weight: 600; color: var(--cc-ink); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sxs-bar-v i { font-style: normal; color: var(--cc-ink-4); }
.sxs-bar-v .sx-mono { font-size: 12px; font-weight: 500; color: var(--cc-ink-3); }
.sxs-bar-go { flex: none; font-size: 13px; font-weight: 600; color: var(--cc-accent-text); }
</style>
