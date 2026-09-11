<template>
  <!--
    TRADING SYMBOLS

    Ported from the "Nova Edge - Trading Symbols" design doc onto the
    command-center system. The doc's own palette is neon cyan/magenta on
    #07070B; this uses `--cc-*` instead, so the screen inherits the user's
    accent, their card style and their scheme rather than shipping a second
    identity. The information architecture is the doc's, unchanged:
    allowance → selection → sizing → context.
  -->
  <div
    class="cmd sym"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <div class="sym-wash" aria-hidden="true"></div>

    <header class="sym-head">
      <button class="sym-icon" aria-label="Back" @click="$router.push('/home')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <div class="sym-title">
        <h1>Trading Symbols</h1>
        <span class="sym-sub">{{ headerMeta }}</span>
      </div>
      <button
        class="sym-icon"
        :class="{ 'is-busy': syncing }"
        :aria-label="syncing ? 'Saving' : 'Save plan'"
        :disabled="syncing || !dirty"
        @click="push"
      >
        <svg v-if="!syncing" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
          <polyline points="17 21 17 13 7 13 7 21" /><polyline points="7 3 7 8 15 8" />
        </svg>
        <svg v-else class="sym-spin" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
          <path d="M21 12a9 9 0 1 1-6.36-8.6" />
        </svg>
      </button>
    </header>

    <div class="sym-body">
      <!-- ═══ 1 · ALLOWED BY THE ROBOT ═══════════════════════════════ -->
      <section class="sym-sec">
        <div class="sym-sec-head">
          <div>
            <h2 class="sym-sec-t">Allowed by the robot</h2>
            <p class="sym-sec-s">{{ allowanceLine }}</p>
          </div>
          <span v-if="allowed.length" class="cc-chip sym-hint">Tap to use</span>
        </div>

        <p v-if="!allowed.length" class="sym-empty cc-caption">
          Your licence lists no symbols yet. They arrive with the robot's allowance —
          pull to refresh on Home, or ask your mentor.
        </p>

        <div v-else class="sym-grid">
          <button
            v-for="s in allowed"
            :key="s.symbol"
            class="sym-card cc-card"
            :class="{ 'is-on': plan[s.symbol]?.enabled }"
            :aria-pressed="!!plan[s.symbol]?.enabled"
            @click="toggle(s.symbol)"
          >
            <span class="sym-card-top">
              <span class="sym-cat cc-mono">{{ s.category }}</span>
              <span class="sym-tick" :class="{ 'is-on': plan[s.symbol]?.enabled }">
                <svg v-if="plan[s.symbol]?.enabled" width="12" height="12" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2.6" stroke-linecap="round" aria-hidden="true">
                  <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                </svg>
              </span>
            </span>
            <span class="sym-card-name">{{ s.symbol }}</span>
            <span class="sym-card-meta cc-mono">{{ planMeta(s.symbol) }}</span>
          </button>
        </div>
      </section>

      <!-- ═══ 2 · YOUR SELECTION ═════════════════════════════════════ -->
      <section v-if="allowed.length" class="sym-sec">
        <div class="sym-sec-head">
          <div>
            <h2 class="sym-sec-t">Your selection</h2>
            <p class="sym-sec-s">Pick one to size it</p>
          </div>
          <span class="sym-count cc-mono">{{ selected.length }} of {{ allowed.length }}</span>
        </div>

        <p v-if="!selected.length" class="sym-empty cc-caption">
          Nothing selected. The robot will not trade a symbol you have not turned on.
        </p>

        <div v-else class="sym-cfg">
          <div class="sym-rail">
            <button
              v-for="sym in selected"
              :key="sym"
              class="sym-rail-row"
              :class="{ 'is-active': sym === active }"
              @click="active = sym"
            >
              <span class="sym-rail-bar" :class="{ 'is-active': sym === active }"></span>
              <span class="sym-rail-copy">
                <span class="sym-rail-name">{{ sym }}</span>
                <span class="sym-rail-meta cc-mono">{{ planMeta(sym) }}</span>
              </span>
            </button>
          </div>

          <div v-if="cfg" class="sym-panel cc-card">
            <div class="sym-panel-head">
              <div class="sym-panel-id">
                <span class="sym-panel-name">{{ active }}</span>
                <span class="sym-panel-sub cc-caption">{{ cfg.smart_lot ? 'Smart lot' : 'Manual lot' }}</span>
              </div>
              <span class="cc-chip cc-chip-accent sym-armed">
                <span class="cc-dot cc-dot-live"></span>Armed
              </span>
            </div>

            <!-- Lot -->
            <div class="sym-field cc-recess">
              <div class="sym-field-head">
                <span class="cc-label">Lot size</span>
                <span class="cc-caption">per trade</span>
              </div>
              <div class="sym-stepper">
                <button class="sym-step" aria-label="Decrease lot" @click="bumpLot(-0.01)">
                  <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12" /></svg>
                </button>
                <div class="sym-step-val">
                  <span class="sym-step-num cc-figure">{{ lotShown.toFixed(2) }}</span>
                  <span class="sym-step-note cc-caption">{{ cfg.smart_lot ? 'from calculator' : '0.01 steps' }}</span>
                </div>
                <button class="sym-step is-accent" aria-label="Increase lot" @click="bumpLot(0.01)">
                  <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
                    <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- Max trades -->
            <div class="sym-field cc-recess">
              <div class="sym-field-head">
                <span class="cc-label">How many trades</span>
                <span class="cc-caption">open at once</span>
              </div>
              <div class="sym-stepper">
                <button class="sym-step" aria-label="Fewer trades" @click="bumpTrades(-1)">
                  <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12" /></svg>
                </button>
                <div class="sym-step-val sym-pips">
                  <span v-for="i in 5" :key="i" class="sym-pip" :class="{ 'is-on': i <= cfg.max_trades }"
                        :style="{ height: `${9 + i * 2}px` }"></span>
                  <span class="sym-step-num cc-figure sym-pip-num">{{ cfg.max_trades }}</span>
                </div>
                <button class="sym-step is-accent" aria-label="More trades" @click="bumpTrades(1)">
                  <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
                    <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                  </svg>
                </button>
              </div>
            </div>

            <!--
              The one field that decides whether an order fills.

              Brokers do not list canonical names: the same instrument is
              `.US30.`, `US30.cash`, `Gold` or `XAUUSD.m` depending on the
              server. This string goes into the order verbatim, so it is the
              only value on this screen that is NOT normalised. Left blank
              means "I have not said" — the executor then falls back to its own
              suffix and alias discovery, which is usually right and is a
              better default than a guess typed here.
            -->
            <div class="sym-field cc-recess">
              <div class="sym-field-head">
                <span class="cc-label">Broker symbol</span>
                <span class="cc-caption">optional</span>
              </div>
              <input
                class="sym-input"
                type="text"
                inputmode="text"
                autocapitalize="characters"
                autocomplete="off"
                spellcheck="false"
                :placeholder="active"
                :value="cfg.broker_symbol || ''"
                @input="setBrokerSymbol($event.target.value)"
              />
              <p class="sym-field-help cc-caption">
                Exactly what your broker calls it — <span class="cc-mono">.US30.</span>,
                <span class="cc-mono">XAUUSD.m</span>. Leave blank to let the server match it.
              </p>
            </div>

            <div class="sym-panel-foot">
              <div class="sym-exposure">
                <span class="cc-label">Exposure</span>
                <span class="sym-exposure-v cc-figure">{{ exposure }}</span>
              </div>
              <button
                class="sym-smart"
                :class="{ 'is-on': cfg.smart_lot }"
                :aria-pressed="cfg.smart_lot"
                @click="toggleSmart"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M12 2l2.1 5.4L19.5 9l-4.2 3.6 1.3 5.6L12 15.4 7.4 18.2l1.3-5.6L4.5 9l5.4-1.6z" />
                </svg>
                <span>Smart lot</span>
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- ═══ 3 · TRADE CALCULATOR ═══════════════════════════════════ -->
      <section class="sym-sec">
        <div class="sym-sec-head">
          <div>
            <h2 class="sym-sec-t">Trade calculator</h2>
            <p class="sym-sec-s">Splits one risk budget across your trades</p>
          </div>
        </div>

        <div class="sym-calc cc-card">
          <div class="sym-field cc-recess">
            <div class="sym-field-head">
              <span class="cc-label">Account balance</span>
              <span class="cc-caption">{{ balanceNote }}</span>
            </div>
            <div class="sym-money">
              <span class="sym-cur cc-figure">{{ risk.currency }}</span>
              <input class="sym-input sym-input-lg" type="text" inputmode="decimal"
                     :value="risk.balance" @input="onNum('balance', $event.target.value)" />
            </div>
          </div>

          <div class="sym-calc-2">
            <div class="sym-field cc-recess">
              <div class="sym-field-head"><span class="cc-label">Total risk</span></div>
              <div class="sym-money">
                <input class="sym-input sym-input-md" type="text" inputmode="decimal"
                       :value="risk.riskPercent" @input="onNum('riskPercent', $event.target.value)" />
                <span class="sym-cur cc-figure">%</span>
              </div>
              <p class="sym-field-help cc-caption">across all trades</p>
            </div>
            <div class="sym-field cc-recess">
              <div class="sym-field-head"><span class="cc-label">How many</span></div>
              <input class="sym-input sym-input-md" type="text" inputmode="numeric"
                     :value="risk.riskTrades" @input="onNum('riskTrades', $event.target.value)" />
              <p class="sym-field-help cc-caption">splits the budget</p>
            </div>
          </div>

          <div class="sym-calc-out">
            <div class="sym-out is-accent">
              <span class="cc-label">Risk / trade</span>
              <span class="sym-out-v cc-figure">{{ calc.perPct }}</span>
              <span class="sym-out-n cc-caption">{{ calc.perAmt }}</span>
            </div>
            <div class="sym-out">
              <span class="cc-label">Total at risk</span>
              <span class="sym-out-v cc-figure">{{ calc.totalAmt }}</span>
              <span class="sym-out-n cc-caption">{{ calc.totalNote }}</span>
            </div>
            <div class="sym-out">
              <span class="cc-label">Suggested lot</span>
              <span class="sym-out-v cc-figure">{{ calc.lot }}</span>
              <span class="sym-out-n cc-caption">{{ calc.lotNote }}</span>
            </div>
          </div>

          <button v-if="cfg" class="sym-apply" @click="applySuggested">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M13 2L4.5 13H11l-1 9 8.5-11H12z" />
            </svg>
            <span>Apply {{ calc.lot }} to {{ active }}</span>
          </button>
        </div>
      </section>

      <!-- ═══ 4 · SESSIONS ═══════════════════════════════════════════ -->
      <section class="sym-sec">
        <div class="sym-sec-head">
          <div>
            <h2 class="sym-sec-t">Sessions</h2>
            <p class="sym-sec-s">Liquidity windows in your local time</p>
          </div>
          <span class="sym-count cc-mono">{{ openSessions }}</span>
        </div>

        <div class="sym-sessions cc-card">
          <div class="sym-sess-grid">
            <div v-for="s in sessions" :key="s.id" class="sym-sess" :class="{ 'is-open': s.open }">
              <span class="sym-sess-top">
                <span class="cc-dot" :class="{ 'cc-dot-live': s.open }"></span>
                <span class="sym-sess-id">{{ s.id }}</span>
              </span>
              <span class="sym-sess-h cc-mono">{{ s.label }}</span>
            </div>
          </div>

          <!-- 24h track. `now` ticks it, so the marker actually moves.
               A window that crosses midnight is drawn as TWO segments —
               one bar with left+width summing past 100% ran off the end of
               the track (Sydney measured 129%) and was only invisible
               because the container clips. -->
          <div class="sym-track">
            <template v-for="s in sessions" :key="s.id">
              <div v-for="(seg, i) in s.segments" :key="`${s.id}-${i}`"
                   class="sym-track-bar" :class="{ 'is-open': s.open }" :style="seg"></div>
            </template>
            <div class="sym-track-now" :style="{ left: nowPct + '%' }"></div>
          </div>
          <div class="sym-track-ticks cc-mono">
            <span>00</span><span>06</span><span>12</span><span>18</span><span>24</span>
          </div>
        </div>
      </section>

      <!-- ═══ 5 · NEWS · 6 · EVENTS ══════════════════════════════════
           Both are drawn by the doc with live copy. Neither has a source:
           there is no calendar or news table, and no edge function that
           returns either. They render as explicitly NOT CONNECTED rather
           than with sample headlines, because a plausible-looking headline
           on a trading screen is the one kind of placeholder that can move
           real money. The layouts are ready the day a provider lands. -->
      <section v-for="f in feeds" :key="f.id" class="sym-sec">
        <div class="sym-sec-head">
          <div>
            <h2 class="sym-sec-t">{{ f.title }}</h2>
            <p class="sym-sec-s">{{ f.sub }}</p>
          </div>
          <span class="cc-chip sym-off">Not connected</span>
        </div>
        <div class="sym-feed cc-card">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M4 11a9 9 0 0 1 9 9" /><path d="M4 4a16 16 0 0 1 16 16" />
            <circle cx="5" cy="19" r="1.5" fill="currentColor" stroke="none" />
          </svg>
          <p class="cc-caption">{{ f.blurb }}</p>
        </div>
      </section>

      <p v-if="syncMsg" class="sym-syncmsg cc-caption" :class="{ 'is-error': !syncOk }">{{ syncMsg }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { theme, resolvedScheme } from '../stores/theme.js'
import { robot } from '../stores/robot.js'
import { session } from '../stores/session.js'
import { haptic } from '../composables/useSpeech.js'
import {
  loadPlan, savePlan, syncPlan, normaliseSymbol, cleanBrokerSymbol, DEFAULT_ROW,
} from '../services/symbolPlanService.js'

/* ── Allowance ───────────────────────────────────────────────────────
   The robot's permitted symbols, straight from the licence. Category is
   derived for grouping only — it never changes what is sent. */
function categoryOf(sym) {
  const s = sym.toUpperCase()
  if (/^XAU|^XAG|GOLD|SILVER/.test(s)) return 'METALS'
  if (/BTC|ETH|SOL|XRP|DOGE/.test(s)) return 'CRYPTO'
  if (/US30|NAS|SPX|US500|GER|UK100|USTECH|DAX/.test(s)) return 'INDEX'
  if (/OIL|WTI|BRENT|NGAS/.test(s)) return 'ENERGY'
  return 'FOREX'
}

const allowed = computed(() =>
  (robot.allowedSymbols || [])
    .map(s => normaliseSymbol(s))
    .filter(Boolean)
    .map(symbol => ({ symbol, category: categoryOf(symbol) })),
)

/* ── Plan ────────────────────────────────────────────────────────── */
const plan = reactive(loadPlan())
const active = ref(null)
const dirty = ref(false)

/** Every allowed symbol gets a row so the UI never reads undefined; rows
 *  arrive disabled, because turning a symbol on is the user's decision. */
function ensureRows() {
  for (const { symbol } of allowed.value) {
    if (!plan[symbol]) plan[symbol] = { ...DEFAULT_ROW }
  }
  if (!active.value || !plan[active.value]?.enabled) {
    active.value = selected.value[0] || null
  }
}

const selected = computed(() =>
  allowed.value.map(a => a.symbol).filter(s => plan[s]?.enabled),
)

const cfg = computed(() => (active.value ? plan[active.value] : null))

function toggle(sym) {
  const row = plan[sym] || (plan[sym] = { ...DEFAULT_ROW })
  row.enabled = !row.enabled
  haptic(row.enabled ? 40 : 20)
  if (row.enabled) active.value = sym
  else if (active.value === sym) active.value = selected.value[0] || null
  dirty.value = true
}

function bumpLot(d) {
  if (!cfg.value) return
  const base = cfg.value.smart_lot ? calc.value.lotNum : cfg.value.lot
  cfg.value.lot = Math.max(0.01, Math.round((base + d) * 100) / 100)
  cfg.value.smart_lot = false   // a manual nudge is a manual lot
  haptic(15)
  dirty.value = true
}

function bumpTrades(d) {
  if (!cfg.value) return
  cfg.value.max_trades = Math.min(5, Math.max(1, cfg.value.max_trades + d))
  haptic(15)
  dirty.value = true
}

function toggleSmart() {
  if (!cfg.value) return
  cfg.value.smart_lot = !cfg.value.smart_lot
  haptic(30)
  dirty.value = true
}

function setBrokerSymbol(v) {
  if (!cfg.value) return
  cfg.value.broker_symbol = cleanBrokerSymbol(v)
  dirty.value = true
}

/**
 * The lot the panel shows: the calculator's figure while Smart is on, the
 * stored one otherwise.
 *
 * Falls back to the stored lot when the calculator has nothing to work
 * from. Smart is the default and a fresh install has no balance yet, so
 * without this the panel opened reading a flat `0.00` — which is not a
 * size, it is a missing input, and it looked like the screen was broken.
 */
const lotShown = computed(() => {
  if (!cfg.value) return 0
  if (!cfg.value.smart_lot) return cfg.value.lot
  return calc.value.lotNum > 0 ? calc.value.lotNum : cfg.value.lot
})

function planMeta(sym) {
  const r = plan[sym]
  if (!r?.enabled) return 'off'
  const lot = r.smart_lot && calc.value.lotNum > 0 ? calc.value.lotNum : r.lot
  return `${lot.toFixed(2)} x ${r.max_trades}`
}

const exposure = computed(() => {
  if (!cfg.value) return '—'
  const total = lotShown.value * cfg.value.max_trades
  return `${total.toFixed(2)} lots`
})

/* ── Risk profile ────────────────────────────────────────────────────
   Balance is seeded from the live broker figure when there is one. It
   stays editable: a user planning a trade on an account they have not
   linked yet still needs the calculator. */
const risk = reactive({
  balance: '',
  currency: session.brokerCurrency || 'USD',
  riskPercent: '1',
  riskTrades: '3',
})

const balanceNote = computed(() =>
  session.brokerBalance != null && String(risk.balance) === String(Math.round(session.brokerBalance))
    ? 'from your linked terminal'
    : 'edit to plan ahead',
)

function onNum(field, v) {
  const clean = field === 'riskTrades'
    ? v.replace(/[^0-9]/g, '').slice(0, 2)
    : v.replace(/[^0-9.]/g, '')
  risk[field] = clean
  dirty.value = true
}

/**
 * The doc's calculator. One risk budget split across N trades.
 *
 * The suggested lot uses a fixed 20-pip stop and a $10/lot/pip value —
 * the same simplification the doc labels "20-pip stop" under the figure.
 * It is a STARTING size, not a broker-accurate one: pip value varies by
 * instrument and account currency, and this screen has neither. Labelled
 * as an estimate rather than presented as a computed truth.
 */
const calc = computed(() => {
  const bal = parseFloat(risk.balance) || 0
  const pct = Math.min(100, Math.max(0, parseFloat(risk.riskPercent) || 0))
  const n = Math.min(20, Math.max(1, parseInt(risk.riskTrades, 10) || 1))
  const totalAmt = (bal * pct) / 100
  const perAmt = totalAmt / n
  const perPct = pct / n
  const lotNum = perAmt > 0 ? Math.max(0.01, Math.round((perAmt / 200) * 100) / 100) : 0
  const money = v => `${risk.currency} ${Math.round(v).toLocaleString('en-US')}`
  return {
    lotNum,
    lot: lotNum.toFixed(2),
    lotNote: 'est · 20-pip stop',
    perPct: `${perPct.toFixed(2)}%`,
    perAmt: bal ? money(perAmt) : '—',
    totalAmt: bal ? money(totalAmt) : '—',
    totalNote: bal ? `${pct.toFixed(2)}% of ${Math.round(bal).toLocaleString('en-US')}` : 'set a balance',
  }
})

function applySuggested() {
  if (!cfg.value) return
  cfg.value.lot = calc.value.lotNum
  cfg.value.smart_lot = true
  haptic(40)
  dirty.value = true
}

/* ── Sessions ────────────────────────────────────────────────────────
   Real windows in UTC, rendered in the device's local time. `now` is a
   ticking ref, not `Date.now()` inside a computed — that caches on first
   read and the marker never moves. */
const SESSIONS = [
  { id: 'SYD', from: 22, to: 7 },
  { id: 'TKY', from: 0,  to: 9 },
  { id: 'LDN', from: 8,  to: 17 },
  { id: 'NY',  from: 13, to: 22 },
]

const now = ref(new Date())
let clock = null

function localHour(utcHour) {
  const d = new Date(now.value)
  d.setUTCHours(utcHour, 0, 0, 0)
  return d.getHours()
}

const sessions = computed(() => {
  const h = now.value.getUTCHours() + now.value.getUTCMinutes() / 60
  return SESSIONS.map(s => {
    const wraps = s.to <= s.from
    const open = wraps ? (h >= s.from || h < s.to) : (h >= s.from && h < s.to)
    const a = localHour(s.from)
    const b = localHour(s.to)
    const pad = n => String(n).padStart(2, '0')
    const pct = n => `${(n / 24) * 100}%`
    // Two segments when the window crosses midnight, one otherwise.
    const segments = wraps
      ? [
          { left: pct(s.from), width: pct(24 - s.from) },
          { left: '0%', width: pct(s.to) },
        ]
      : [{ left: pct(s.from), width: pct(s.to - s.from) }]

    return { ...s, open, label: `${pad(a)}–${pad(b)}`, segments }
  })
})

const openSessions = computed(() => {
  const on = sessions.value.filter(s => s.open).map(s => s.id)
  return on.length ? `${on.join(' + ')} open` : 'All closed'
})

const nowPct = computed(() => {
  const h = now.value.getUTCHours() + now.value.getUTCMinutes() / 60
  return (h / 24) * 100
})

/* ── Feeds with no source ────────────────────────────────────────── */
const feeds = [
  { id: 'news', title: 'News', sub: 'Wire on your selected symbols',
    blurb: 'No news provider is connected to this build. Headlines appear here once one is.' },
  { id: 'events', title: 'Events', sub: 'High-impact economic calendar',
    blurb: 'No calendar provider is connected. Until one is, the robot does not pause for news — size accordingly.' },
]

/* ── Sync ────────────────────────────────────────────────────────── */
const syncing = ref(false)
const syncMsg = ref('')
const syncOk = ref(true)

async function push() {
  if (syncing.value) return
  syncing.value = true
  syncMsg.value = ''
  haptic(30)
  /* `smart_lot_size` is the calculator's figure, sent alongside the plan —
     it is what the executor sizes a `smart_lot` row from. Passing `risk`
     alone left it undefined and the server stored 0, which would have made
     every smart row size to nothing. */
  const res = await syncPlan(plan, { ...risk, smartLot: calc.value.lotNum })
  syncing.value = false
  syncOk.value = res.ok
  if (res.ok) {
    dirty.value = false
    syncMsg.value = res.synced
      ? `Saved · ${res.synced} symbol${res.synced === 1 ? '' : 's'} enforced by the server.`
      : 'Risk profile saved. No symbols enabled, so nothing is tradeable.'
  } else {
    syncMsg.value = `Not saved — ${res.error} Your changes are still here; try again.`
  }
}

/* Persist locally on every change, immediately. The server copy is the
   ceiling; this is so a reload does not lose a half-built plan. */
watch(plan, () => savePlan(plan), { deep: true })
watch(allowed, ensureRows, { immediate: true })

onMounted(() => {
  ensureRows()
  if (session.brokerBalance != null && !risk.balance) {
    risk.balance = String(Math.round(session.brokerBalance))
    risk.currency = session.brokerCurrency || 'USD'
  }
  clock = setInterval(() => { now.value = new Date() }, 30_000)
})
onUnmounted(() => clearInterval(clock))

const headerMeta = computed(() =>
  `${robot.name} · ${selected.value.length} of ${allowed.value.length} in use`,
)
const allowanceLine = computed(() =>
  allowed.value.length
    ? `${robot.name} permits ${allowed.value.length} symbol${allowed.value.length === 1 ? '' : 's'}`
    : 'Nothing permitted yet',
)
</script>

<style scoped>
.sym {
  position: relative;
  /* height, NOT min-height. `.app-shell` is `height: 100dvh; overflow:
     hidden`, so it clips this view; with min-height this element grows
     past the shell rather than scrolling inside it and everything below
     the fold becomes unreachable. Installed to the Home screen there is
     no collapsing URL bar to jostle it into view, so the page just
     freezes — which is the bug this fixes.

     overflow-y: auto is what makes this element the scroll container. */
  height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}

/* A single quiet accent wash. The doc stacks three neon radials; one is
   enough to stop the canvas reading as flat black, and glass needs
   something behind it to refract. */
.sym-wash {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(120% 42% at 50% 0%, rgba(var(--cc-accent-rgb), 0.14), transparent 62%),
    radial-gradient(100% 38% at 12% 100%, rgba(var(--cc-accent-rgb), 0.07), transparent 60%);
}

/* ── Header ──────────────────────────────────────────────────────── */
.sym-head {
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

.sym-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: none;
  background: none;
  border-radius: var(--cc-r-chip);
  color: var(--cc-ink-3);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.sym-icon:active { transform: scale(0.9) }
.sym-icon:disabled { opacity: 0.34; cursor: default }
.sym-icon.is-busy { color: var(--cc-accent-text) }
.sym-spin { animation: ccSpin 900ms linear infinite }
@keyframes ccSpin { to { transform: rotate(360deg) } }

.sym-title { flex: 1; min-width: 0 }
.sym-title h1 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--cc-ink);
}
.sym-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--cc-ink-3);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ── Body ────────────────────────────────────────────────────────── */
.sym-body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 26px;
  padding: 20px 18px calc(var(--cc-safe-bottom) + 118px);
}

.sym-sec { display: flex; flex-direction: column; gap: 11px }
.sym-sec-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}
.sym-sec-t {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--cc-ink);
}
.sym-sec-s { margin: 3px 0 0; font-size: 11.5px; color: var(--cc-ink-3) }
.sym-hint, .sym-off { height: 24px; padding: 0 10px; font-size: 10px; flex: none }
.sym-count { font-size: 10px; letter-spacing: 0.08em; color: var(--cc-ink-3); flex: none }
.sym-empty { margin: 0 }

/* ── 1 · Allowance grid ──────────────────────────────────────────── */
.sym-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
}

.sym-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 12px;
  min-height: 96px;
  padding: 11px;
  cursor: pointer;
  text-align: left;
  transition: transform 200ms var(--cc-spring), border-color 220ms var(--cc-ease);
}
.sym-card:active { transform: scale(0.96) }
.sym-card.is-on { border-color: var(--cc-accent) !important }

.sym-card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 4px }
.sym-cat { font-size: 8px; letter-spacing: 0.10em; color: var(--cc-ink-4) }

.sym-tick {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 7px;
  border: 1px solid var(--cc-border-strong);
  color: var(--cc-ink-4);
  transition: background 200ms var(--cc-ease);
}
.sym-tick.is-on {
  background: var(--cc-accent);
  border-color: var(--cc-accent);
  color: var(--cc-accent-ink);
}

.sym-card-name {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--cc-ink-3);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sym-card.is-on .sym-card-name { color: var(--cc-ink) }
.sym-card-meta { font-size: 9px; color: var(--cc-ink-4) }

/* ── 2 · Selection ───────────────────────────────────────────────── */
.sym-cfg { display: flex; gap: 10px; align-items: flex-start }

.sym-rail { flex: 0 0 92px; display: flex; flex-direction: column; gap: 7px }
.sym-rail-row {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 50px;
  padding: 8px 8px 8px 12px;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-r-tile);
  background: var(--cc-card-2);
  cursor: pointer;
  text-align: left;
  transition: border-color 200ms var(--cc-ease);
}
.sym-rail-row.is-active { border-color: var(--cc-accent-line); background: var(--cc-accent-dim) }
.sym-rail-bar {
  position: absolute;
  left: 0; top: 10px; bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: var(--cc-border-strong);
}
.sym-rail-bar.is-active { background: var(--cc-accent) }
.sym-rail-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0 }
.sym-rail-name {
  font-size: 12px; font-weight: 700; color: var(--cc-ink-3);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sym-rail-row.is-active .sym-rail-name { color: var(--cc-ink) }
.sym-rail-meta { font-size: 9px; color: var(--cc-ink-4) }

.sym-panel { flex: 1; min-width: 0; padding: 14px; display: flex; flex-direction: column; gap: 11px }
.sym-panel-head { display: flex; align-items: center; justify-content: space-between; gap: 8px }
.sym-panel-id { min-width: 0 }
.sym-panel-name {
  display: block;
  font-size: 19px; font-weight: 700; letter-spacing: -0.01em; color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sym-panel-sub { display: block; margin-top: 1px }
.sym-armed { height: 22px; padding: 0 8px; font-size: 9px; flex: none }

/* ── Fields ──────────────────────────────────────────────────────── */
.sym-field { padding: 11px 12px }
.sym-field-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px }
.sym-field-help { margin: 6px 0 0; font-size: 10px; line-height: 15px }

.sym-stepper { display: flex; align-items: center; gap: 10px; margin-top: 9px }
.sym-step {
  flex: none;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  border: 1px solid var(--cc-border-strong);
  background: var(--cc-card-2);
  color: var(--cc-ink-2);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.sym-step:active { transform: scale(0.92) }
.sym-step.is-accent {
  border-color: var(--cc-accent-line);
  background: var(--cc-accent-dim);
  color: var(--cc-accent-text);
}

.sym-step-val { flex: 1; min-width: 0; text-align: center }
.sym-step-num { display: block; font-size: 24px; font-weight: 600; line-height: 1; color: var(--cc-accent-text) }
.sym-step-note { display: block; margin-top: 4px; font-size: 10px }

.sym-pips { display: flex; align-items: flex-end; justify-content: center; gap: 4px }
.sym-pip { width: 5px; border-radius: 2px; background: var(--cc-border-strong) }
.sym-pip.is-on { background: var(--cc-accent) }
.sym-pip-num { margin-left: 7px; font-size: 20px; color: var(--cc-ink) }

/* 16px floor is mandatory: Safari zooms the viewport on focus below it
   and never zooms back. */
.sym-input {
  width: 100%;
  margin-top: 6px;
  border: none;
  background: none;
  padding: 0;
  font-family: var(--cc-mono);
  font-size: 16px;
  font-weight: 600;
  color: var(--cc-ink);
  outline: none;
}
.sym-input-lg { font-size: 20px }
.sym-input-md { font-size: 18px }
.sym-money { display: flex; align-items: baseline; gap: 7px }
.sym-cur { font-size: 12px; color: var(--cc-ink-3); flex: none }

.sym-panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-top: 1px solid var(--cc-border-soft);
  padding-top: 11px;
}
.sym-exposure { display: flex; flex-direction: column; gap: 3px; min-width: 0 }
.sym-exposure-v { font-size: 13px; color: var(--cc-ink-2) }

.sym-smart {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  border-radius: var(--cc-r-chip);
  border: 1px solid var(--cc-border-strong);
  background: var(--cc-card-2);
  font-family: var(--cc-font);
  font-size: 11px;
  font-weight: 600;
  color: var(--cc-ink-3);
  cursor: pointer;
  flex: none;
}
.sym-smart.is-on {
  border-color: var(--cc-accent-line);
  background: var(--cc-accent-dim);
  color: var(--cc-accent-text);
}

/* ── 3 · Calculator ──────────────────────────────────────────────── */
.sym-calc { padding: 14px; display: flex; flex-direction: column; gap: 11px }
.sym-calc-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px }
.sym-calc-out { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }

.sym-out {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 10px;
  border-radius: var(--cc-r-tile);
  border: 1px solid var(--cc-border);
  background: var(--cc-card-2);
}
.sym-out.is-accent { border-color: var(--cc-accent-line); background: var(--cc-accent-dim) }
.sym-out-v {
  font-size: 15px; font-weight: 600; color: var(--cc-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sym-out.is-accent .sym-out-v { color: var(--cc-accent-text) }
.sym-out-n { font-size: 10px }

.sym-apply {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 46px;
  border: none;
  border-radius: 14px;
  background: var(--cc-accent);
  color: var(--cc-accent-ink);
  font-family: var(--cc-font);
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: 0.04em;
  cursor: pointer;
  box-shadow: var(--cc-glow-edge);
  transition: transform 200ms var(--cc-spring);
}
.sym-apply:active { transform: scale(0.98) }

/* ── 4 · Sessions ────────────────────────────────────────────────── */
.sym-sessions { padding: 14px; display: flex; flex-direction: column; gap: 13px }
.sym-sess-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px }
.sym-sess {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 9px 8px;
  border-radius: 13px;
  border: 1px solid var(--cc-border);
  background: var(--cc-card-2);
}
.sym-sess.is-open { border-color: var(--cc-accent-line); background: var(--cc-accent-dim) }
.sym-sess-top { display: flex; align-items: center; gap: 5px }
.sym-sess-id { font-size: 10px; font-weight: 700; color: var(--cc-ink-3) }
.sym-sess.is-open .sym-sess-id { color: var(--cc-ink) }
.sym-sess-h { font-size: 9px; color: var(--cc-ink-4) }
.sym-sess.is-open .sym-sess-h { color: var(--cc-accent-text) }

.sym-track {
  position: relative;
  height: 26px;
  border-radius: 9px;
  border: 1px solid var(--cc-border);
  background: var(--cc-recess);
  overflow: hidden;
}
.sym-track-bar {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  height: 6px;
  border-radius: 999px;
  background: var(--cc-ink-5);
  opacity: 0.42;
}
.sym-track-bar.is-open { background: var(--cc-accent); opacity: 0.85 }
.sym-track-now { position: absolute; top: 0; bottom: 0; width: 1.5px; background: var(--cc-live) }
.sym-track-ticks {
  display: flex;
  justify-content: space-between;
  margin-top: 5px;
  font-size: 8px;
  color: var(--cc-ink-4);
}

/* ── 5/6 · Feeds ─────────────────────────────────────────────────── */
.sym-feed {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  color: var(--cc-ink-4);
}
.sym-feed p { margin: 0; flex: 1 }
.sym-feed svg { flex: none; color: var(--cc-ink-4) }

.sym-syncmsg { margin: 0; text-align: center }
.sym-syncmsg.is-error { color: var(--cc-warn) }
</style>
