<template>
  <!--
    AI CHART SCANNER

    Ported from the "Nova Edge - Chart Scanner" design doc onto the command
    system. The doc lays it out as five artboards; on a handset it is one
    flow with two stages — INPUT, then the reading — because a scan result
    that lives on a different screen from the chart that produced it is a
    screen you have to remember your way back to.

    What the doc draws and this cannot do is called out in place rather than
    faked: there is no economic calendar on this platform, so Event Radar and
    two of the guardrails report as unrunnable instead of green.
  -->
  <div
    class="cmd scan"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <div class="scan-wash" aria-hidden="true"></div>

    <header class="scan-head">
      <button class="scan-icon" aria-label="Back" @click="$router.push('/home')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <div class="scan-title">
        <h1>Chart Scanner</h1>
        <span class="scan-sub">{{ reading ? readingLine : 'Confluence engine · scored on device' }}</span>
      </div>
      <button v-if="reading" class="scan-icon" aria-label="New scan" @click="reset">
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 12a9 9 0 1 1-2.64-6.36" /><polyline points="21 3 21 9 15 9" />
        </svg>
      </button>
      <span v-else class="scan-icon-spacer"></span>
    </header>

    <div class="scan-body">
      <ScannerPaywall v-if="!hasScanner" @unlocked="onUnlocked" />

      <template v-else>
        <!-- ═══ 1 · SCAN INPUT ══════════════════════════════════════ -->
        <template v-if="!reading">
          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Scan mode</h2>
              <span class="scan-sec-s">{{ activeMode.tf }}</span>
            </div>
            <div class="scan-modes">
              <button
                v-for="m in MODES" :key="m.id"
                class="scan-mode" :class="{ 'is-on': mode === m.id }"
                :aria-pressed="mode === m.id"
                @click="mode = m.id; haptic(20)"
              >
                <span class="scan-mode-n">{{ m.name }}</span>
                <span class="scan-mode-tf cc-mono">{{ m.tf }}</span>
              </button>
            </div>
          </section>

          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Position size</h2>
              <span class="scan-sec-s">{{ lotSource }}</span>
            </div>
            <div class="scan-lot cc-card">
              <button class="scan-step" aria-label="Decrease lot" @click="bumpLot(-0.01)">
                <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12" /></svg>
              </button>
              <div class="scan-lot-v">
                <span class="scan-lot-num cc-figure">{{ totalLot.toFixed(2) }}</span>
                <span class="scan-lot-note cc-caption">total lots</span>
              </div>
              <button class="scan-step is-accent" aria-label="Increase lot" @click="bumpLot(0.01)">
                <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
                  <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                </svg>
              </button>
            </div>
          </section>

          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Your chart</h2>
              <span class="scan-sec-s">JPG · PNG · WEBP</span>
            </div>

            <button class="scan-drop cc-card" :class="{ 'is-busy': scanning }" @click="pickFile" :disabled="scanning">
              <input ref="fileInput" type="file" hidden accept="image/*" @change="onFile" />

              <template v-if="!preview">
                <span class="scan-drop-ring">
                  <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect x="3" y="5" width="18" height="14" rx="2" />
                    <circle cx="8.5" cy="10" r="1.6" /><path d="m21 15-4.5-4.5L11 16" />
                  </svg>
                </span>
                <span class="scan-drop-t">Attach a chart screenshot</span>
                <span class="scan-drop-s cc-caption">Your drawings and levels are read too</span>
              </template>

              <template v-else>
                <img :src="preview" class="scan-preview" alt="Chart to analyse" />
                <span v-if="scanning" class="scan-laser" aria-hidden="true"></span>
                <span v-if="scanning" class="scan-busy cc-mono">Reading the chart…</span>
              </template>
            </button>

            <p v-if="error" class="scan-err cc-caption">{{ error }}</p>
          </section>
        </template>

        <!-- ═══ 2 · CONFLUENCE ══════════════════════════════════════ -->
        <template v-else>
          <section class="scan-sec">
            <div class="scan-verdict cc-card">
              <div class="scan-meter">
                <svg viewBox="0 0 100 100" class="scan-meter-svg" aria-hidden="true">
                  <circle class="scan-meter-track" cx="50" cy="50" r="42" />
                  <circle
                    v-if="score.score != null"
                    class="scan-meter-arc" :class="`is-${gradeTone}`"
                    cx="50" cy="50" r="42"
                    :stroke-dasharray="C"
                    :stroke-dashoffset="C - (C * score.score) / 100"
                  />
                </svg>
                <div class="scan-meter-mid">
                  <span v-if="score.score != null" class="scan-meter-n cc-figure">{{ score.score }}</span>
                  <span v-else class="scan-meter-n cc-figure">—</span>
                  <span class="scan-meter-d cc-caption">of 100</span>
                </div>
              </div>

              <div class="scan-verdict-copy">
                <span class="scan-dir" :class="dirTone">{{ score.direction || '—' }}</span>
                <span class="scan-grade" :class="`is-${gradeTone}`">{{ score.grade }}</span>
                <span class="scan-basis cc-caption">
                  {{ score.passed }} of {{ score.known }} checks passed
                  <template v-if="score.unknown.length"> · {{ score.unknown.length }} could not run</template>
                </span>
              </div>
            </div>
          </section>

          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Score breakdown</h2>
              <span class="scan-sec-s">scored on device</span>
            </div>
            <ul class="scan-checks">
              <li v-for="c in score.checks" :key="c.id" class="scan-check cc-card"
                  :class="c.ok === true ? 'is-pass' : c.ok === false ? 'is-fail' : 'is-unknown'">
                <span class="scan-check-mark">
                  <svg v-if="c.ok === true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12" /></svg>
                  <svg v-else-if="c.ok === false" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="3" stroke-linecap="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
                  <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="3" stroke-linecap="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12" /></svg>
                </span>
                <span class="scan-check-copy">
                  <span class="scan-check-l">{{ c.label }}</span>
                  <span class="scan-check-d cc-caption">{{ c.detail }}</span>
                </span>
              </li>
            </ul>
            <p class="scan-privacy cc-caption">
              Scored on your device from the chart reading. Nothing about your account left the phone.
            </p>
          </section>

          <!-- ═══ 3 · LADDER ════════════════════════════════════════ -->
          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Trade plan</h2>
              <span class="scan-sec-s cc-mono">{{ ladder.valid ? `1:${ladder.blendedRR.toFixed(2)} blended` : 'unusable' }}</span>
            </div>

            <div v-if="!ladder.valid" class="scan-feed cc-card">
              <p class="cc-caption">{{ ladder.reason }}</p>
            </div>

            <template v-else>
              <div class="scan-allocs">
                <button
                  v-for="a in ALLOCATIONS" :key="a.id"
                  class="scan-alloc" :class="{ 'is-on': allocation === a.id }"
                  :aria-pressed="allocation === a.id"
                  @click="allocation = a.id; haptic(20)"
                >
                  <span class="scan-alloc-n">{{ a.name }}</span>
                  <span class="scan-alloc-s cc-mono">{{ a.split.join('/') }}</span>
                </button>
              </div>

              <div class="scan-ladder cc-card">
                <div class="scan-row is-head">
                  <span class="cc-label">Level</span><span class="cc-label">R:R</span>
                  <span class="cc-label">Lots</span><span class="cc-label">Price</span>
                </div>
                <div v-for="l in ladder.legs" :key="l.level" class="scan-row"
                     :class="{ 'is-muted': mode2 === 'single' && l.level !== 'TP1' }">
                  <span class="scan-row-l">
                    {{ l.level }}
                    <span v-if="l.projected" class="scan-proj cc-mono">proj</span>
                  </span>
                  <span class="cc-figure">1:{{ l.rr.toFixed(2) }}</span>
                  <span class="cc-figure" :class="{ 'is-warn': l.undersized }">
                    {{ mode2 === 'single' && l.level === 'TP1' ? totalLot.toFixed(2) : l.lot.toFixed(2) }}
                  </span>
                  <span class="cc-figure">{{ fmt(l.price) }}</span>
                </div>
                <div class="scan-row is-foot">
                  <span class="scan-row-l">SL</span>
                  <span class="cc-figure">{{ ladder.slPips.toFixed(1) }}p</span>
                  <span class="cc-figure">—</span>
                  <span class="cc-figure">{{ fmt(ladder.sl) }}</span>
                </div>
              </div>

              <p class="scan-note cc-caption">
                TP1 is the level read off your chart. TP2 and TP3 are 2R and 3R projections of the same stop.
                Pip figures are estimated from the instrument family.
              </p>
            </template>
          </section>

          <!-- ═══ 4 · CONTEXT ═══════════════════════════════════════ -->
          <section v-if="timeframes.length" class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Timeframe alignment</h2>
              <span class="scan-sec-s">{{ timeframes.length }} read</span>
            </div>
            <div class="scan-mtf cc-card">
              <div v-for="t in timeframes" :key="t.timeframe" class="scan-tf">
                <span class="scan-tf-id cc-mono">{{ t.timeframe }}</span>
                <span class="scan-tf-bias" :class="biasTone(t.bias)">{{ t.bias }}</span>
                <span class="scan-tf-note cc-caption">{{ t.note }}</span>
              </div>
            </div>
          </section>

          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Guardrails</h2>
              <span class="scan-sec-s">{{ guards.summary }}</span>
            </div>
            <ul class="scan-checks">
              <li v-for="r in guards.rules" :key="r.id" class="scan-check cc-card"
                  :class="`is-${r.status.toLowerCase()}`">
                <span class="scan-check-mark">{{ r.status === 'PASS' ? '✓' : r.status === 'BLOCK' ? '✕' : r.status === 'WARN' ? '!' : '–' }}</span>
                <span class="scan-check-copy">
                  <span class="scan-check-l">{{ r.label }}</span>
                  <span class="scan-check-d cc-caption">{{ r.detail }}</span>
                </span>
                <span class="scan-badge" :class="`is-${r.status.toLowerCase()}`">{{ r.status }}</span>
              </li>
            </ul>
          </section>

          <!-- Narrative — the model's own argument, including against itself -->
          <section v-if="reading.narrative" class="scan-sec">
            <div class="scan-sec-head"><h2 class="scan-sec-t">What it saw</h2></div>
            <div class="scan-feed cc-card"><p class="cc-body scan-narr">{{ reading.narrative }}</p></div>
          </section>

          <!-- ═══ 5 · EXECUTE ═══════════════════════════════════════ -->
          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Send to broker</h2>
              <span class="scan-sec-s">{{ pair || 'no symbol' }}</span>
            </div>

            <!-- The choice is explicit and per-trade. One order and three are
                 different amounts of exposure and different failure modes;
                 neither is a default worth guessing at. -->
            <div class="scan-modes scan-modes-2">
              <button class="scan-mode" :class="{ 'is-on': mode2 === 'single' }"
                      :aria-pressed="mode2 === 'single'" @click="mode2 = 'single'; haptic(20)">
                <span class="scan-mode-n">Single order</span>
                <span class="scan-mode-tf cc-mono">{{ totalLot.toFixed(2) }} at TP1</span>
              </button>
              <button class="scan-mode" :class="{ 'is-on': mode2 === 'ladder' }"
                      :aria-pressed="mode2 === 'ladder'" @click="mode2 = 'ladder'; haptic(20)">
                <span class="scan-mode-n">Ladder</span>
                <span class="scan-mode-tf cc-mono">{{ fundedLegs }} orders</span>
              </button>
            </div>

            <p v-if="mode2 === 'ladder'" class="scan-note cc-caption">
              Each rung is a separate position at your broker, sent one at a time. If one is refused the
              earlier ones stay open and the rest are not sent — you will be told exactly which.
            </p>

            <button
              class="scan-exec"
              :class="{ 'is-blocked': guards.blocked }"
              :disabled="sending || guards.blocked || !pair || !ladder.valid"
              @click="execute"
            >
              <svg v-if="!sending" width="17" height="17" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M13 2L4.5 13H11l-1 9 8.5-11H12z" />
              </svg>
              <svg v-else class="scan-spin" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M21 12a9 9 0 1 1-6.36-8.6" /></svg>
              <span>{{ execLabel }}</span>
            </button>

            <p v-if="guards.blocked" class="scan-note cc-caption">
              Blocked by your own rule, not by us. Change it in Guardrails, or retune the plan.
            </p>
            <p v-if="result" class="scan-result cc-caption" :class="{ 'is-error': !resultOk }">{{ result }}</p>
          </section>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { theme, resolvedScheme } from '../stores/theme.js'
import { session } from '../stores/session.js'
import { authState } from '../services/authService'
import { haptic } from '../composables/useSpeech.js'
import ScannerPaywall from '../components/ScannerPaywall.vue'
import { scanChart, sendPlan, preflight } from '../services/scannerService.js'
import { buildLadder, scoreConfluence, checkGuardrails, ALLOCATIONS } from '../services/confluence.js'
import { loadPlan, normaliseSymbol } from '../services/symbolPlanService.js'

/* The add-on gate. `analyze-chart` is the real authority — this only decides
   what to draw, and a client that lied here would still be refused. */
const hasScanner = computed(() => authState.hasScanner)

const MODES = [
  { id: 'scalp', name: 'Scalp', tf: 'M5–M15' },
  { id: 'day',   name: 'Day',   tf: 'H1–H4' },
  { id: 'swing', name: 'Swing', tf: 'H4–D1' },
]
const mode = ref('day')
const activeMode = computed(() => MODES.find(m => m.id === mode.value) || MODES[1])

const C = 2 * Math.PI * 42

const fileInput = ref(null)
const preview = ref(null)
const scanning = ref(false)
const error = ref('')
const reading = ref(null)

const allocation = ref('balanced')
/** 'single' | 'ladder'. Starts on single: one order is the smaller,
 *  more reversible action, and the ladder is opted into. */
const mode2 = ref('single')

const totalLot = ref(0.05)
const lotSource = ref('manual')

const sending = ref(false)
const result = ref('')
const resultOk = ref(true)

/* ── Derived ─────────────────────────────────────────────────────── */
const pair = computed(() => normaliseSymbol(reading.value?.symbol_on_chart || ''))

const ladder = computed(() =>
  reading.value
    ? buildLadder(reading.value, { allocation: allocation.value, totalLot: totalLot.value })
    : { valid: false, legs: [], reason: '' },
)
const score = computed(() =>
  reading.value ? scoreConfluence(reading.value, effectiveLadder.value)
                : { checks: [], passed: 0, known: 0, unknown: [], score: null, grade: 'UNKNOWN', direction: '' },
)
/**
 * The plan actually being sent — which is what the guardrails must judge.
 *
 * In 'single' mode only TP1 goes to the broker, at the full lot. Scoring
 * that against the LADDER's blended R:R was a real hole: TP2 and TP3 are
 * fixed 2R and 3R projections, so a balanced split floors blended at
 * 0.25*2 + 0.25*3 = 1.25 before TP1 contributes anything. A 0.50R first
 * target therefore blended to 1.50 and cleared a 1.5 minimum — passing a
 * single 0.50R trade on the strength of two rungs that were never placed.
 * Measured on a GBP/JPY setup: TP1 at 0.50R, execute enabled.
 */
const effectiveLadder = computed(() => {
  const l = ladder.value
  if (!l.valid || mode2.value === 'ladder') return l
  const first = l.legs[0]
  return {
    ...l,
    legs: [{ ...first, alloc: 100, lot: totalLot.value }],
    blendedRR: first.rr,
  }
})

const guards = computed(() => checkGuardrails(effectiveLadder.value))

const gradeTone = computed(() =>
  ({ STRONG: 'ok', MODERATE: 'warn', WEAK: 'bad', UNKNOWN: 'idle' })[score.value.grade] || 'idle',
)
const dirTone = computed(() =>
  score.value.direction === 'BUY' ? 'is-buy' : score.value.direction === 'SELL' ? 'is-sell' : '',
)
const timeframes = computed(() =>
  Array.isArray(reading.value?.timeframes) ? reading.value.timeframes : [],
)
const fundedLegs = computed(() => ladder.value.legs?.filter(l => l.lot > 0).length || 0)

const readingLine = computed(() => {
  const r = reading.value
  if (!r) return ''
  return [pair.value, r.timeframe_on_chart, r.entry_type].filter(Boolean).join(' · ')
})

const execLabel = computed(() => {
  if (sending.value) return 'Sending…'
  if (guards.value.blocked) return 'Blocked by guardrails'
  if (!pair.value) return 'No symbol on the chart'
  if (mode2.value === 'single') return `Send 1 order · ${totalLot.value.toFixed(2)}`
  return `Send ${fundedLegs.value} orders · ${totalLot.value.toFixed(2)}`
})

/** Prices carry the instrument's own decimals — a 5-decimal FX quote and a
 *  1-decimal index must not be forced to a shared format. */
function fmt(v) {
  const n = Number(v)
  if (!isFinite(n)) return '—'
  return n >= 1000 ? n.toFixed(2) : n.toFixed(n < 10 ? 5 : 3)
}

function biasTone(b) {
  const s = String(b).toUpperCase()
  return s === 'BULLISH' ? 'is-buy' : s === 'BEARISH' ? 'is-sell' : ''
}

/* ── Actions ─────────────────────────────────────────────────────── */
function bumpLot(d) {
  totalLot.value = Math.max(0.01, Math.round((totalLot.value + d) * 100) / 100)
  lotSource.value = 'manual'
  haptic(15)
}

function pickFile() { if (!scanning.value) fileInput.value?.click() }

function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const fr = new FileReader()
  fr.onload = ev => { preview.value = ev.target.result; run() }
  fr.readAsDataURL(file)
}

async function run() {
  scanning.value = true
  error.value = ''
  reading.value = null
  try {
    const data = await scanChart(preview.value, mode.value)
    reading.value = data
    seedLot(data)
    haptic(40)
  } catch (err) {
    /* The model's own "I could not read this" is more useful than a generic,
       so it is shown verbatim. */
    error.value = err?.message || 'The scan failed. Try a clearer screenshot.'
    preview.value = null
  } finally {
    scanning.value = false
  }
}

/**
 * Seeds the lot from the Trading Symbols plan when the scanned instrument is
 * one the user has already sized. Their configured size for a symbol is a
 * better starting point than a constant, and it keeps the two screens
 * telling the same story.
 */
function seedLot(data) {
  const sym = normaliseSymbol(data?.symbol_on_chart || '')
  const row = sym ? loadPlan()[sym] : null
  if (row && row.enabled && row.lot > 0) {
    totalLot.value = row.lot
    lotSource.value = 'from Trading Symbols'
  } else {
    lotSource.value = 'manual'
  }
}

async function execute() {
  if (sending.value || guards.value.blocked) return
  sending.value = true
  result.value = ''
  haptic(60)

  const plan = {
    pair: pair.value,
    side: score.value.direction,
    sl: ladder.value.sl,
    entry: ladder.value.entry,
    legs: ladder.value.legs,
    totalLot: totalLot.value,
    orderType: reading.value?.entry_type || 'MARKET',
  }

  /* Validate server-side before anything real goes out — licence, allowance,
     position cap, stop sanity and symbol resolution all answered by the
     executor while `dry_run` keeps it from placing. */
  const pre = await preflight(plan)
  if (!pre.ok) {
    sending.value = false
    resultOk.value = false
    result.value = `Not sent — ${pre.message}`
    return
  }

  const res = await sendPlan(plan, mode2.value)
  sending.value = false
  resultOk.value = res.ok
  result.value = res.message
  if (res.ok) haptic([40, 60, 40])
}

function reset() {
  reading.value = null
  preview.value = null
  result.value = ''
  error.value = ''
  haptic(20)
}

function onUnlocked() { reset() }
</script>

<style scoped>
.scan {
  position: relative;
  min-height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}

.scan-wash {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(120% 42% at 50% 0%, rgba(var(--cc-accent-rgb), 0.14), transparent 62%),
    radial-gradient(100% 38% at 88% 100%, rgba(var(--cc-accent-rgb), 0.06), transparent 60%);
}

/* ── Header ──────────────────────────────────────────────────────── */
.scan-head {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: calc(var(--cc-safe-top) + 10px) 18px 12px;
  background: var(--cc-glass-thin);
  -webkit-backdrop-filter: var(--cc-glass-thin-blur);
  backdrop-filter: var(--cc-glass-thin-blur);
  border-bottom: 0.5px solid var(--cc-glass-rim);
}
.scan-icon {
  flex: none;
  display: grid; place-items: center;
  width: 44px; height: 44px;
  border: none; background: none;
  border-radius: var(--cc-r-chip);
  color: var(--cc-ink-3);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.scan-icon:active { transform: scale(0.9) }
.scan-icon-spacer { width: 44px; height: 44px; flex: none }

.scan-title { flex: 1; min-width: 0 }
.scan-title h1 {
  margin: 0; font-size: 19px; font-weight: 700;
  letter-spacing: -0.02em; color: var(--cc-ink);
}
.scan-sub {
  display: block; margin-top: 2px; font-size: 11px; color: var(--cc-ink-3);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ── Body ────────────────────────────────────────────────────────── */
.scan-body {
  position: relative; z-index: 1;
  display: flex; flex-direction: column; gap: 24px;
  padding: 20px 18px calc(var(--cc-safe-bottom) + 118px);
}
.scan-sec { display: flex; flex-direction: column; gap: 11px }
.scan-sec-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px }
.scan-sec-t { margin: 0; font-size: 15px; font-weight: 600; letter-spacing: -0.01em; color: var(--cc-ink) }
.scan-sec-s { font-size: 11px; color: var(--cc-ink-3); flex: none }

/* ── Mode / allocation pickers ───────────────────────────────────── */
.scan-modes { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
.scan-modes-2 { grid-template-columns: repeat(2, 1fr) }
.scan-mode {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
  min-height: 56px; padding: 9px 6px;
  border: 1px solid var(--cc-border); border-radius: var(--cc-r-tile);
  background: var(--cc-card-2);
  cursor: pointer;
  transition: border-color 200ms var(--cc-ease), transform 180ms var(--cc-spring);
}
.scan-mode:active { transform: scale(0.96) }
.scan-mode.is-on { border-color: var(--cc-accent); background: var(--cc-accent-dim) }
.scan-mode-n { font-size: 12.5px; font-weight: 600; color: var(--cc-ink-3) }
.scan-mode.is-on .scan-mode-n { color: var(--cc-ink) }
.scan-mode-tf { font-size: 9px; color: var(--cc-ink-4) }
.scan-mode.is-on .scan-mode-tf { color: var(--cc-accent-text) }

.scan-allocs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
.scan-alloc {
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  min-height: 46px; padding: 8px 6px;
  border: 1px solid var(--cc-border); border-radius: var(--cc-r-tile);
  background: var(--cc-card-2); cursor: pointer;
}
.scan-alloc.is-on { border-color: var(--cc-accent); background: var(--cc-accent-dim) }
.scan-alloc-n { font-size: 11.5px; font-weight: 600; color: var(--cc-ink-3) }
.scan-alloc.is-on .scan-alloc-n { color: var(--cc-ink) }
.scan-alloc-s { font-size: 9px; color: var(--cc-ink-4) }

/* ── Lot stepper ─────────────────────────────────────────────────── */
.scan-lot { display: flex; align-items: center; gap: 12px; padding: 12px 14px }
.scan-step {
  flex: none; display: grid; place-items: center;
  width: 44px; height: 44px; border-radius: 13px;
  border: 1px solid var(--cc-border-strong); background: var(--cc-card-2);
  color: var(--cc-ink-2); cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.scan-step:active { transform: scale(0.92) }
.scan-step.is-accent { border-color: var(--cc-accent-line); background: var(--cc-accent-dim); color: var(--cc-accent-text) }
.scan-lot-v { flex: 1; text-align: center; min-width: 0 }
.scan-lot-num { display: block; font-size: 24px; font-weight: 600; line-height: 1; color: var(--cc-accent-text) }
.scan-lot-note { display: block; margin-top: 4px; font-size: 10px }

/* ── Drop zone ───────────────────────────────────────────────────── */
.scan-drop {
  position: relative;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px;
  min-height: 190px; padding: 20px;
  cursor: pointer; overflow: hidden;
  border-style: dashed !important;
}
.scan-drop:disabled { cursor: default }
.scan-drop-ring {
  display: grid; place-items: center;
  width: 54px; height: 54px; border-radius: 50%;
  border: 1px solid var(--cc-accent-line); background: var(--cc-accent-dim);
  color: var(--cc-accent-text);
}
.scan-drop-t { font-size: 13.5px; font-weight: 600; color: var(--cc-ink-2) }
.scan-drop-s { text-align: center }
.scan-preview { width: 100%; border-radius: var(--cc-r-tile); display: block }

/* The laser is decoration, and decoration must not be the thing that tells
   you a scan is running — the mono caption under it does that. */
.scan-laser {
  position: absolute; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, transparent, var(--cc-accent), transparent);
  box-shadow: var(--cc-glow-edge);
  animation: scanSweep 1.6s var(--cc-ease) infinite;
}
@keyframes scanSweep { 0% { top: 4% } 50% { top: 92% } 100% { top: 4% } }
.scan-busy {
  position: absolute; bottom: 10px;
  font-size: 10px; letter-spacing: 0.14em; text-transform: uppercase;
  color: var(--cc-ink-2);
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.7);
}

.scan-err { margin: 0; color: var(--cc-warn) }

/* ── Verdict meter ───────────────────────────────────────────────── */
.scan-verdict { display: flex; align-items: center; gap: 16px; padding: 16px }
.scan-meter { position: relative; flex: none; width: 96px; height: 96px }
.scan-meter-svg { width: 100%; height: 100%; transform: rotate(-90deg) }
.scan-meter-track { fill: none; stroke: var(--cc-track); stroke-width: 8 }
.scan-meter-arc {
  fill: none; stroke-width: 8; stroke-linecap: round; stroke: var(--cc-idle);
  transition: stroke-dashoffset 560ms var(--cc-ease), stroke 240ms var(--cc-ease);
}
.scan-meter-arc.is-ok { stroke: var(--cc-live) }
.scan-meter-arc.is-warn { stroke: var(--cc-warn) }
.scan-meter-arc.is-bad { stroke: var(--cc-sell) }
.scan-meter-mid { position: absolute; inset: 0; display: grid; place-content: center; text-align: center }
.scan-meter-n { font-size: 26px; font-weight: 700; line-height: 1; color: var(--cc-ink) }
.scan-meter-d { display: block; margin-top: 2px; font-size: 9px }

.scan-verdict-copy { display: flex; flex-direction: column; gap: 4px; min-width: 0 }
.scan-dir {
  font-size: 22px; font-weight: 700; letter-spacing: -0.01em; color: var(--cc-ink-3);
}
.scan-dir.is-buy { color: var(--cc-buy) }
.scan-dir.is-sell { color: var(--cc-sell) }
.scan-grade {
  font-family: var(--cc-mono); font-size: 11px; font-weight: 700; letter-spacing: 0.14em;
  color: var(--cc-ink-3);
}
.scan-grade.is-ok { color: var(--cc-live) }
.scan-grade.is-warn { color: var(--cc-warn) }
.scan-grade.is-bad { color: var(--cc-sell) }

/* ── Checks ──────────────────────────────────────────────────────── */
.scan-checks { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px }
.scan-check { display: flex; align-items: center; gap: 11px; padding: 11px 13px }
.scan-check-mark {
  flex: none; display: grid; place-items: center;
  width: 22px; height: 22px; border-radius: 7px;
  font-family: var(--cc-mono); font-size: 12px; font-weight: 700;
  background: var(--cc-card-2); color: var(--cc-ink-4);
}
.scan-check.is-pass .scan-check-mark { background: var(--cc-buy-dim); color: var(--cc-buy) }
.scan-check.is-fail .scan-check-mark,
.scan-check.is-block .scan-check-mark { background: var(--cc-sell-dim); color: var(--cc-sell) }
.scan-check.is-warn .scan-check-mark { background: rgba(232, 163, 61, 0.14); color: var(--cc-warn) }
.scan-check-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.scan-check-l { font-size: 12.5px; font-weight: 600; color: var(--cc-ink-2) }
.scan-check-d { font-size: 11px; line-height: 16px }

.scan-badge {
  flex: none; padding: 3px 7px; border-radius: 5px;
  font-family: var(--cc-mono); font-size: 8.5px; font-weight: 700; letter-spacing: 0.10em;
  background: var(--cc-card-2); color: var(--cc-ink-4);
}
.scan-badge.is-pass { background: var(--cc-buy-dim); color: var(--cc-buy) }
.scan-badge.is-block { background: var(--cc-sell-dim); color: var(--cc-sell) }
.scan-badge.is-warn { background: rgba(232, 163, 61, 0.14); color: var(--cc-warn) }

.scan-privacy, .scan-note { margin: 0; line-height: 16px }

/* ── Ladder ──────────────────────────────────────────────────────── */
.scan-ladder { padding: 6px 13px 10px }
.scan-row {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr 0.8fr 1.2fr;
  gap: 8px; align-items: center;
  padding: 9px 0;
  border-bottom: 1px solid var(--cc-border-soft);
}
.scan-row:last-child { border-bottom: none }
.scan-row.is-head { padding: 8px 0 6px; border-bottom: 1px solid var(--cc-border) }
.scan-row.is-foot { border-top: 1px solid var(--cc-border); border-bottom: none; margin-top: 2px }
.scan-row.is-muted { opacity: 0.38 }
.scan-row > span { font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.scan-row-l { font-size: 12.5px; font-weight: 700; color: var(--cc-ink); display: flex; align-items: baseline; gap: 5px }
.scan-proj { font-size: 8px; letter-spacing: 0.08em; color: var(--cc-ink-4) }
.scan-row .is-warn { color: var(--cc-warn) }

/* ── MTF ─────────────────────────────────────────────────────────── */
.scan-mtf { padding: 6px 13px }
.scan-tf {
  display: grid; grid-template-columns: 44px 72px 1fr; gap: 9px; align-items: baseline;
  padding: 10px 0; border-bottom: 1px solid var(--cc-border-soft);
}
.scan-tf:last-child { border-bottom: none }
.scan-tf-id { font-size: 11px; font-weight: 700; color: var(--cc-ink) }
.scan-tf-bias { font-size: 11px; font-weight: 700; color: var(--cc-ink-3) }
.scan-tf-bias.is-buy { color: var(--cc-buy) }
.scan-tf-bias.is-sell { color: var(--cc-sell) }
.scan-tf-note { font-size: 11px; line-height: 16px }

.scan-feed { padding: 15px }
.scan-feed p { margin: 0 }
.scan-narr { font-size: 13px; line-height: 20px; color: var(--cc-ink-2) }

/* ── Execute ─────────────────────────────────────────────────────── */
.scan-exec {
  display: flex; align-items: center; justify-content: center; gap: 9px;
  min-height: 52px;
  border: none; border-radius: var(--cc-r-chip);
  background: var(--cc-accent); color: var(--cc-accent-ink);
  font-family: var(--cc-font); font-size: 13.5px; font-weight: 700; letter-spacing: 0.06em;
  cursor: pointer;
  box-shadow: var(--cc-glow-halo);
  transition: transform 200ms var(--cc-spring), background 220ms var(--cc-ease);
}
.scan-exec:active { transform: scale(0.98) }
.scan-exec:disabled { cursor: default; box-shadow: none; opacity: 0.5 }
.scan-exec.is-blocked {
  background: var(--cc-card-2); color: var(--cc-ink-3);
  border: 1px solid var(--cc-sell-dim); opacity: 1;
}
.scan-spin { animation: scanSpin 900ms linear infinite }
@keyframes scanSpin { to { transform: rotate(360deg) } }

.scan-result { margin: 0; text-align: center }
.scan-result.is-error { color: var(--cc-warn) }
</style>
