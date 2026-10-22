<template>
  <!--
    Command Core — the robot in a reactor core (Claude Design, "NovaHost
    Command Core", component CommandCorePhone).

    Four glass panels report market, account, broker link and last action.
    Each is wired to the core by a pipeline that only carries particles while
    that link is really live: the broker line breaks red with a cross when the
    link is down, and a new position sends a pulse Market → Core → Broker →
    Account → Last action. Every number is read from the user's own broker;
    anything not known yet shows an em dash, never a sample figure.
  -->
  <div ref="root" class="cc" :class="{ 'cc-mini': mini }" :style="{ '--a': accent }">
    <div ref="probe" class="cc-probe" aria-hidden="true"></div>

    <div v-if="ready" ref="stage" class="cc-stage" :style="stageStyle">
      <img v-if="!lite" :src="art" alt="" class="cc-bgart" :style="{ width: (W + 120) + 'px', height: (H + 120) + 'px' }">
      <div class="cc-fill" style="background:linear-gradient(180deg, rgba(9,9,12,.80) 0%, rgba(9,9,12,.70) 38%, rgba(9,9,12,.86) 70%, #09090C 100%)"></div>
      <div class="cc-fill" :style="{ opacity: state === 'empty' ? .35 : 1, background: `radial-gradient(62% 32% at 50% ${((366 + hud) / H * 100).toFixed(1)}%, color-mix(in srgb, var(--a) 22%, transparent), transparent 72%)` }"></div>
      <div class="cc-fill cc-grid" :style="{ maskImage: gridMask, WebkitMaskImage: gridMask }"></div>

      <div class="cc-col" :style="{ left: dx + 'px', height: H + 'px' }">
        <!-- Header -->
        <div class="cc-head" :style="{ top: (50 + st) + 'px' }">
          <div>
            <div class="cc-robot">{{ rb.name }}</div>
            <div class="cc-by">{{ robot.mentor ? `Your robot · by ${robot.mentor}` : 'Your robot' }}</div>
          </div>
          <div role="button" aria-label="Home look and settings" class="cc-tune" @click="live && $emit('look')">
            <span class="ms" style="font-size:22px">tune</span>
          </div>
        </div>

        <!-- HUD: core, canvas, four panels — shifted together so the pipes line up -->
        <div class="cc-hud" :style="{ top: hud + 'px' }">
          <div class="cc-core" :style="{ filter: coreFilter }">
            <img :src="art" :alt="rb.name" class="cc-core-img" :style="{ animation: anim ? 'ccDrift 14s ease-in-out infinite' : 'none' }">
            <div class="cc-core-vig"></div>
          </div>
          <canvas ref="canvas" width="390" height="844" class="cc-canvas"></canvas>

          <!-- Market -->
          <div role="button" aria-label="Market feed details" class="cc-panel" :style="{ left: '16px', top: '108px', ...panelStyle('market', tone[links.market]) }" @click="open('/scanner')">
            <div class="cc-ph"><div class="cc-ph-l"><div class="cc-pdot" :style="{ background: mk.dot }"></div><div class="cc-pt">MARKET FEED</div></div><span class="ms cc-chev">chevron_right</span></div>
            <div class="cc-msym">{{ mk.symbol }}</div>
            <div class="cc-mprice" :style="{ color: mk.priceColor }">{{ mk.price }}</div>
            <div class="cc-pfoot"><div class="cc-small" :style="{ color: mk.subColor }">{{ mk.sub }}</div></div>
          </div>

          <!-- Account -->
          <div role="button" aria-label="Account details" class="cc-panel" :style="{ left: '203px', top: '108px', ...panelStyle('account', tone[links.account]) }" @click="open('/trades')">
            <div class="cc-ph"><div class="cc-ph-l"><div class="cc-pdot" :style="{ background: ac.dot }"></div><div class="cc-pt">ACCOUNT{{ cur ? ' · ' + cur : '' }}</div></div><span class="ms cc-chev">chevron_right</span></div>
            <template v-if="state === 'empty'">
              <div class="cc-empty-h">No account linked</div>
              <div class="cc-empty-b">Balance and equity show here once your broker is connected.</div>
            </template>
            <template v-else>
              <div class="cc-eql">{{ ac.eqLabel }}</div>
              <div class="cc-eq" :style="{ color: ac.valColor }">{{ ac.equity }}</div>
              <div class="cc-acrows">
                <div class="cc-acrow"><div class="cc-mute">Balance</div><div :style="{ color: ac.valColor, fontWeight: 500 }">{{ ac.balance }}</div></div>
                <div class="cc-acrow"><div class="cc-mute">Free margin</div><div :style="{ color: ac.freeColor, fontWeight: 600 }">{{ ac.free }}</div></div>
              </div>
            </template>
          </div>

          <!-- Broker -->
          <div role="button" aria-label="Broker link details" class="cc-panel" :style="{ left: '16px', top: '496px', ...panelStyle('broker', tone[links.broker]) }" @click="open('/terminal')">
            <div class="cc-ph"><div class="cc-ph-l"><div class="cc-pdot" :style="{ background: br.dot }"></div><div class="cc-pt">BROKER LINK</div></div><span class="ms cc-chev">chevron_right</span></div>
            <div class="cc-brh" :style="{ color: br.headColor }">{{ br.head }}</div>
            <div class="cc-brs">{{ br.server }}</div>
            <div class="cc-pfoot">
              <div v-if="br.fix" class="cc-fix"><span class="ms" style="font-size:15px;color:inherit">build</span><div>Tap to fix</div></div>
              <div v-else class="cc-small cc-mute">{{ br.meta }}</div>
            </div>
          </div>

          <!-- Last action -->
          <div role="button" aria-label="Last action details" class="cc-panel" :style="{ left: '203px', top: '496px', ...panelStyle('action', la.tone) }" @click="open('/trades')">
            <div class="cc-ph"><div class="cc-ph-l"><div class="cc-pdot" :style="{ background: la.dot }"></div><div class="cc-pt">LAST ACTION</div></div><span class="ms cc-chev">chevron_right</span></div>
            <template v-if="la.empty">
              <div class="cc-empty-h">No trades yet</div>
              <div class="cc-empty-b">{{ rb.name }}'s first trade shows here.</div>
            </template>
            <template v-else>
              <div class="cc-lat">{{ la.title }}</div>
              <div class="cc-lav" :style="{ color: la.valueColor }">{{ la.value }}</div>
              <div class="cc-pfoot"><div class="cc-small cc-mute">{{ la.meta }}</div></div>
            </template>
          </div>
        </div>

        <!-- Status line (tap for the status sheet, where start/stop lives) -->
        <div role="button" class="cc-status" :style="{ top: (642 + dy) + 'px' }" @click="live && (sheet = 'status')">
          <div class="cc-status-row"><div class="cc-sdot" :style="{ background: sl.dot }"></div><div>{{ sl.text }}</div></div>
          <div v-if="sl.sub" class="cc-status-sub">{{ sl.sub }}</div>
        </div>

        <!-- The one action -->
        <div class="cc-act" :style="{ top: (700 + dy) + 'px' }">
          <div v-if="act.mic" role="button" :aria-label="`Hold to talk to ${rb.name}`" class="cc-mic" :style="{ boxShadow: micShadow }"
               @pointerdown="pttDown" @pointerup="pttUp" @pointerleave="pttUp" @pointercancel="pttUp">
            <div class="cc-mic-ic"><span class="ms" style="font-size:24px">mic</span></div>
            <div style="display:flex;flex-direction:column;gap:1px">
              <div class="cc-mic-l">{{ listening ? 'Listening…' : answering ? `${rb.name} is answering` : `Talk to ${rb.name}` }}</div>
              <div class="cc-mic-h">{{ listening ? 'Release to send' : 'Hold to speak' }}</div>
            </div>
          </div>
          <div v-else role="button" class="cc-solid" @click="act.onClick">
            <span class="ms" style="font-size:22px;color:#0B0B0E">{{ act.icon }}</span>
            <div>{{ act.label }}</div>
          </div>
        </div>
      </div>

      <Transition name="cc-cap">
        <div v-if="caption" class="cc-caption" :style="{ left: (dx + 20) + 'px', top: (560 + dy) + 'px' }">
          <div class="cc-cap-who">{{ rb.name.toUpperCase() }}</div>
          <div>{{ caption }}</div>
        </div>
      </Transition>

      <HomeSheets
        :sheet="sheet"
        :kind="kind"
        :robot-name="rb.name"
        :rows="symbolRows"
        :issues="symbolIssues"
        :running="running"
        power
        @close="sheet = null"
        @go="p => { sheet = null; $emit('go', p) }"
        @power="togglePower"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { robot } from '../../../stores/robot.js'
import { session } from '../../../stores/session.js'
import { theme } from '../../../stores/theme.js'
import { trades, money, refreshPositions } from '../../../stores/trades.js'
import { loadQuote, loadAccount } from '../../../services/brokerService.js'
import { useSpeech } from '../../../composables/useSpeech.js'
import { useStage } from './useStage.js'
import HomeSheets from './HomeSheets.vue'
import {
  activeRobot, statusKind, symbolRows, symbolIssues, refreshPlan,
  startRobot, stopRobot, effectMode, GREEN, AMBER, RED,
} from './homeData.js'

const props = defineProps({ mini: { type: Boolean, default: false } })
const emit = defineEmits(['look', 'go', 'toast'])

const { root, probe, stage, ready, W, H, dx, dy, st, stageStyle } = useStage({ mini: props.mini })
const { speak } = useSpeech()
const live = computed(() => !props.mini)

/* ── Motion: full · lite (weak phones) · static (reduced motion) ─── */
const mode = ref(effectMode())
watch(() => theme.motion, () => { mode.value = effectMode(); restart() })
const lite = computed(() => mode.value === 'lite')
const anim = computed(() => mode.value === 'full')

/* ── Identity ────────────────────────────────────────────────────── */
const rb = computed(() => activeRobot.value || { name: robot.name, art: '', accent: theme.accent.hex })
const accent = computed(() => rb.value.accent || '#E0313F')
const art = computed(() => rb.value.art)
const running = computed(() => session.isRunning)
const kind = computed(() => statusKind.value)

/* The HUD moves as one block so pipelines stay attached to their panels. */
const hud = computed(() => Math.max(dy.value / 2, st.value))
const gridMask = computed(() => `radial-gradient(75% 48% at 50% ${((366 + hud.value) / H.value * 100).toFixed(1)}%, #000 20%, transparent 80%)`)

/* ── Live data ───────────────────────────────────────────────────── */
const linked = computed(() => Boolean(session.brokerServer) || session.brokerConnected)
const state = computed(() => {
  if (!navigator.onLine) return 'error'
  if (!linked.value) return 'empty'
  return 'running'
})
const brokerDown = computed(() => linked.value && !session.brokerConnected)
const thinking = ref(false)

const quote = ref(null)
const quotedAt = ref(null)
const account = ref(null)
const symbol = computed(() => (robot.allowedSymbols || [])[0] || 'XAUUSD')
const cur = computed(() => account.value?.currency || trades.currency || '')

async function pull() {
  if (props.mini || !linked.value) return
  try {
    const [q, a] = await Promise.all([loadQuote(symbol.value), loadAccount()])
    if (q.ok) { quote.value = q; quotedAt.value = Date.now() }
    if (a.ok) account.value = a
  } catch { /* panels keep their last known values */ }
  refreshPositions().catch(() => {})
}

function fmt(v, digits) {
  if (v == null || !Number.isFinite(Number(v))) return '—'
  return Number(v).toLocaleString('en-ZA', { minimumFractionDigits: digits ?? 2, maximumFractionDigits: digits ?? 2 })
}

const links = computed(() => {
  if (state.value === 'empty') return { market: 'idle', account: 'idle', broker: 'idle', action: 'idle' }
  if (state.value === 'error') return { market: 'down', account: 'down', broker: 'down', action: 'idle' }
  return {
    market: quote.value ? 'live' : 'connecting',
    account: brokerDown.value ? 'stale' : (account.value || trades.equity != null) ? 'live' : 'connecting',
    broker: brokerDown.value ? 'down' : 'live',
    action: 'tether',
  }
})
const tone = { live: 'live', tether: 'live', stale: 'amber', connecting: 'amber', down: 'red', idle: 'neutral' }

const mk = computed(() => {
  const q = quote.value
  const base = { symbol: symbol.value, price: q ? fmt(q.price, q.digits ?? 2) : '—', priceColor: '#F4F4F7', sub: q ? `Spread ${q.spread_pips != null ? q.spread_pips.toFixed(1) + ' pips' : '—'}` : 'Connecting to feed…', subColor: q ? GREEN : '#A3A3AE', dot: q ? GREEN : AMBER }
  if (state.value === 'empty') return { ...base, price: '—', priceColor: '#76767F', sub: 'Waiting for broker', subColor: '#A3A3AE', dot: '#6F6F7A' }
  if (state.value === 'error') return { ...base, priceColor: '#A3A3AE', sub: quotedAt.value ? 'as of ' + new Date(quotedAt.value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Offline', subColor: '#A3A3AE', dot: RED }
  return base
})

const ac = computed(() => {
  const a = account.value
  const c = cur.value
  const equity = a?.equity ?? trades.equity
  const balance = a?.balance ?? trades.balance
  const freePct = a && a.free_margin != null && equity ? Math.round(a.free_margin / equity * 100) : null
  const low = freePct != null && freePct < 20
  const out = {
    eqLabel: 'Equity', equity: equity != null ? money(equity, c).replace(/^\+/, '') : '—',
    balance: balance != null ? money(balance, c).replace(/^\+/, '') : '—',
    free: freePct != null ? freePct + '%' : '—', freeColor: low ? AMBER : GREEN, valColor: '#F4F4F7', dot: GREEN,
  }
  if (brokerDown.value) Object.assign(out, { eqLabel: 'Equity · last known', valColor: '#B9B9C2', dot: AMBER })
  if (state.value === 'error') Object.assign(out, { eqLabel: 'Equity · last known', valColor: '#A3A3AE', freeColor: '#A3A3AE', dot: RED })
  if (equity == null && state.value === 'running') out.dot = AMBER
  if (state.value === 'empty') out.dot = '#6F6F7A'
  return out
})

const br = computed(() => {
  const out = { head: 'Connected', headColor: GREEN, server: session.brokerServer || 'Your broker', meta: session.pingMs ? `${session.pingMs} ms` : 'Linked', fix: false, dot: GREEN }
  if (brokerDown.value) Object.assign(out, { head: 'Disconnected', headColor: RED, fix: true, dot: RED })
  if (state.value === 'empty') Object.assign(out, { head: 'Not connected', headColor: '#E6E6EA', server: 'MT4 or MT5 account', meta: 'Not linked yet', dot: '#6F6F7A' })
  if (state.value === 'error') Object.assign(out, { head: 'Unreachable', headColor: RED, meta: 'No internet', dot: RED })
  return out
})

const la = computed(() => {
  const p = trades.positions[0]
  if (state.value === 'empty' || (!p && !trades.openCount)) {
    return { empty: true, dot: '#6F6F7A', tone: 'neutral' }
  }
  const pl = Number(p?.net_profit)
  const good = !Number.isFinite(pl) || pl >= 0
  return {
    empty: false,
    title: p ? `${p.side || ''} ${p.symbol || ''} ${p.volume ?? p.lots ?? ''}`.trim() : `${trades.openCount} open`,
    value: Number.isFinite(pl) ? money(pl, cur.value) : 'Open',
    valueColor: good ? GREEN : RED,
    meta: `${trades.openCount} open trade${trades.openCount === 1 ? '' : 's'}`,
    dot: good ? GREEN : RED,
    tone: state.value === 'error' ? 'neutral' : 'live',
  }
})

const sl = computed(() => {
  const n = rb.value.name
  const ready = symbolRows.value.length - symbolIssues.value
  if (listening.value) return { dot: 'var(--a)', text: 'Listening…' }
  if (state.value === 'error') return { dot: RED, text: `${n} paused · no internet connection`, sub: 'Open trades stay at your broker with their stop-loss.' }
  if (state.value === 'empty') return { dot: '#6F6F7A', text: `${n} is ready · no broker account linked` }
  if (brokerDown.value) return { dot: RED, text: 'Trades on hold · broker disconnected', sub: `Your ${trades.openCount} open trades stay at your broker with their stop-loss.` }
  if (!running.value) return { dot: '#8E8E99', text: `${n} is stopped · tap to start` }
  return { dot: GREEN, text: `Running · ${ready} symbol${ready === 1 ? '' : 's'} ready · ${trades.openCount} open` }
})

const act = computed(() => {
  if (state.value === 'empty') return { icon: 'add_link', label: 'Connect broker account', onClick: () => open('/terminal') }
  if (state.value === 'error') return { icon: 'refresh', label: 'Retry', onClick: () => pull() }
  if (brokerDown.value) return { icon: 'link', label: 'Reconnect broker', onClick: () => open('/terminal') }
  return { mic: true }
})

const coreFilter = computed(() => state.value === 'error' ? 'grayscale(1) brightness(.55)' : state.value === 'empty' ? 'saturate(.3) brightness(.7)' : running.value ? 'none' : 'brightness(.7) saturate(.6)')

/* ── Panel edges light as the pulse passes ───────────────────────── */
const hit = ref({})
const EDGE_ACC = 'color-mix(in srgb, var(--a) 42%, transparent)'
function panelStyle(k, tn) {
  const on = hit.value[k]
  const edge = on ? 'var(--a)' : tn === 'live' ? EDGE_ACC : tn === 'amber' ? 'rgba(242,181,58,.65)' : tn === 'red' ? 'rgba(255,93,93,.75)' : 'rgba(255,255,255,.12)'
  let shadow
  if (lite.value) shadow = on ? '0 0 0 1px var(--a)' : 'inset 0 1px 0 rgba(255,255,255,.05)'
  else if (on) shadow = '0 0 30px -4px color-mix(in srgb, var(--a) 75%, transparent), inset 0 1px 0 rgba(255,255,255,.08)'
  else {
    const c = tn === 'amber' ? 'rgba(242,181,58,.32)' : tn === 'red' ? 'rgba(255,93,93,.4)' : tn === 'live' ? 'color-mix(in srgb, var(--a) 45%, transparent)' : 'transparent'
    shadow = `0 0 22px -10px ${c}, inset 0 1px 0 rgba(255,255,255,.06)`
  }
  const blur = lite.value ? 'none' : 'blur(16px) saturate(1.2)'
  return { border: `1px solid ${edge}`, boxShadow: shadow, background: lite.value ? 'rgba(15,15,20,.94)' : 'rgba(13,13,18,.78)', backdropFilter: blur, WebkitBackdropFilter: blur }
}

/* ── Navigation ──────────────────────────────────────────────────── */
const sheet = ref(null)
function open(path) { if (live.value) emit('go', path) }
function togglePower() {
  if (running.value) stopRobot(); else startRobot()
  sheet.value = null
}

/* ── Push-to-talk (placeholder answer until Fish Audio lands) ────── */
const listening = ref(false)
const answering = ref(false)
const caption = ref('')
let vT = 0
function pttDown() {
  if (!live.value || state.value !== 'running') return
  clearTimeout(vT); caption.value = ''; answering.value = false
  listening.value = true
}
function pttUp() {
  if (!listening.value) return
  listening.value = false
  answering.value = true
  const line = 'Voice chat is almost ready. Soon you can ask me about your trades, your account, or how to set anything up.'
  caption.value = line
  speak(line)
  vT = setTimeout(() => { answering.value = false; caption.value = '' }, 5200)
}
const micShadow = computed(() => lite.value ? 'none' : listening.value
  ? '0 0 44px -2px color-mix(in srgb, var(--a) 75%, transparent), inset 0 0 22px -4px color-mix(in srgb, var(--a) 45%, transparent)'
  : '0 0 32px -8px color-mix(in srgb, var(--a) 60%, transparent), inset 0 0 20px -8px color-mix(in srgb, var(--a) 30%, transparent)')

/* ══ Canvas: rings, pipelines, particles, trade pulse ═════════════ */
const canvas = ref(null)
const CW = 390, CH = 844, CX = 195, CY = 366
const PATHS = {
  market:  [[100, 236], [100, 271], [135.6, 306.6]],
  account: [[290, 236], [290, 271], [254.4, 306.6]],
  broker:  [[100, 496], [100, 461], [135.6, 425.4]],
  action:  [[290, 496], [290, 461], [254.4, 425.4]],
}
const A = [242, 181, 58], R = [255, 93, 93], GR = [150, 150, 162]
const rgba = (c, a) => `rgba(${c[0]},${c[1]},${c[2]},${a})`
function hexRgb(h) { h = String(h || '#E0313F').replace('#', ''); if (h.length === 3) h = h.split('').map(c => c + c).join(''); const n = parseInt(h, 16); return isNaN(n) ? [224, 49, 63] : [(n >> 16) & 255, (n >> 8) & 255, n & 255] }
const MEAS = {}
for (const k in PATHS) { const p = PATHS[k]; const segs = []; let tot = 0; for (let i = 1; i < p.length; i++) { const l = Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]); segs.push(l); tot += l } MEAS[k] = { segs, tot } }
function at(k, t) {
  const p = PATHS[k], m = MEAS[k]; let d = Math.max(0, Math.min(1, t)) * m.tot
  for (let i = 0; i < m.segs.length; i++) {
    if (d <= m.segs[i] || i === m.segs.length - 1) { const f = Math.min(1, d / m.segs[i]); return [p[i][0] + (p[i + 1][0] - p[i][0]) * f, p[i][1] + (p[i + 1][1] - p[i][1]) * f] }
    d -= m.segs[i]
  }
  return p[p.length - 1]
}
function seg(ctx, k, t0, t1) { const N = 40; ctx.beginPath(); for (let i = 0; i <= N; i++) { const q = at(k, t0 + (t1 - t0) * i / N); i ? ctx.lineTo(q[0], q[1]) : ctx.moveTo(q[0], q[1]) } ctx.stroke() }
function ring(ctx, r) { ctx.beginPath(); ctx.arc(CX, CY, r, 0, Math.PI * 2); ctx.stroke() }
function parts(ctx, k, dir, n, sp, c, t) {
  for (let i = 0; i < n; i++) {
    const ph = (((t * sp + i / n) % 1) + 1) % 1; const q = at(k, dir > 0 ? ph : 1 - ph); const a = Math.sin(Math.PI * ph)
    ctx.fillStyle = rgba(c, 0.18 * a); ctx.beginPath(); ctx.arc(q[0], q[1], 4.5, 0, 7); ctx.fill()
    ctx.fillStyle = rgba(c, 0.95 * a); ctx.beginPath(); ctx.arc(q[0], q[1], 1.8, 0, 7); ctx.fill()
  }
}
function legsFor(k) {
  const L = [{ k: 'market', a: 0, b: 1, d: 280 }]
  if (k === 'refuse') { L.push({ flash: 'amber', d: 420, hit: 'action' }); return L }
  L.push({ flash: 'acc', d: 160 })
  if (k === 'blocked') { L.push({ k: 'broker', a: 1, b: 0.62, d: 180 }); L.push({ burst: 1, d: 420, hit: 'action' }); return L }
  L.push({ k: 'broker', a: 1, b: 0, d: 280, hit: 'broker' })
  L.push({ ping: 1, d: 120 })
  L.push({ k: 'broker', a: 0, b: 1, d: 240 })
  L.push({ k: 'account', a: 1, b: 0, d: 260, hit: 'account' })
  L.push({ k: 'action', a: 1, b: 0, d: 200, hit: 'action' })
  return L
}

let ctx = null
let raf = 0
let pulse = null
let legIdx = -1
const timers = []
const later = (fn, ms) => timers.push(setTimeout(fn, ms))

function setupCanvas() {
  const c = canvas.value
  if (!c) return
  const dpr = Math.min(2, window.devicePixelRatio || 1) * (props.mini ? 0.5 : 1)
  c.width = CW * dpr; c.height = CH * dpr
  ctx = c.getContext('2d'); ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  restart()
}
function restart() {
  cancelAnimationFrame(raf)
  if (!ctx) return
  if (anim.value && !props.mini) { const tick = n => { draw(n); raf = requestAnimationFrame(tick) }; raf = requestAnimationFrame(tick) }
  else draw(performance.now())
}

function draw(now) {
  if (!ctx) return
  const fx = anim.value, s = state.value
  const acc = hexRgb(accent.value), t = now / 1000
  ctx.clearRect(0, 0, CW, CH); ctx.setLineDash([]); ctx.shadowBlur = 0
  const think = (thinking.value || listening.value) && s === 'running'
  const ringC = (s === 'empty' || s === 'error') ? GR : acc
  const Lk = links.value
  if (!lite.value && s !== 'error' && s !== 'empty') {
    const per = think ? 1.2 : 4; const b = fx ? 0.5 + 0.5 * Math.sin(t * 2 * Math.PI / per) : 0.5
    const g = ctx.createRadialGradient(CX, CY, 64, CX, CY, 170); g.addColorStop(0, rgba(acc, (running.value ? 0.2 : 0.08) + 0.16 * b)); g.addColorStop(1, rgba(acc, 0))
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(CX, CY, 170, 0, 7); ctx.fill()
  }
  const lit = (pulse && lite.value && legIdx >= 0) ? pulse.legs[legIdx] : null
  for (const k of ['market', 'account', 'broker', 'action']) drawLink(k, Lk[k], fx, acc, t, !!(lit && lit.k === k))
  drawRings(fx, ringC, acc, t, think, s, lit && lit.flash)
  if (fx) {
    if (Lk.market === 'live') parts(ctx, 'market', 1, 4, 0.55, acc, t)
    if (Lk.account === 'live') { parts(ctx, 'account', 1, 3, 0.45, acc, t); parts(ctx, 'account', -1, 3, 0.45, acc, t + 0.17) }
    if (Lk.broker === 'live') parts(ctx, 'broker', -1, 4, 0.55, acc, t)
  }
  if (fx && pulse) drawPulse(pulse.legs, now - pulse.t0, acc)
}
function drawLink(k, s, fx, acc, t, lit) {
  const col = (s === 'live' || s === 'tether') ? acc : (s === 'stale' || s === 'connecting') ? A : s === 'down' ? R : [255, 255, 255]
  ctx.save(); ctx.lineCap = 'round'
  if (s === 'down') {
    ctx.strokeStyle = rgba(R, 0.9); ctx.lineWidth = 1.5; ctx.setLineDash([4, 4]); seg(ctx, k, 0, 0.38); seg(ctx, k, 0.62, 1); ctx.setLineDash([])
    const q = at(k, 0.5); ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(q[0] - 4.5, q[1] - 4.5); ctx.lineTo(q[0] + 4.5, q[1] + 4.5); ctx.moveTo(q[0] + 4.5, q[1] - 4.5); ctx.lineTo(q[0] - 4.5, q[1] + 4.5); ctx.stroke()
  } else if (s === 'idle') {
    ctx.strokeStyle = 'rgba(255,255,255,.22)'; ctx.lineWidth = 1.25; ctx.setLineDash([2, 5]); seg(ctx, k, 0, 1)
  } else if (s === 'connecting') {
    ctx.strokeStyle = rgba(A, 0.85); ctx.lineWidth = 1.5; ctx.setLineDash([4, 5]); ctx.lineDashOffset = fx ? -t * 30 : 0; seg(ctx, k, 0, 1)
  } else if (s === 'tether') {
    ctx.strokeStyle = (lit || hit.value.action) ? rgba(acc, 0.95) : 'rgba(255,255,255,.2)'; ctx.lineWidth = 1.25; seg(ctx, k, 0, 1)
  } else {
    if (fx) { ctx.strokeStyle = rgba(col, 0.13); ctx.lineWidth = 6; seg(ctx, k, 0, 1) }
    ctx.strokeStyle = rgba(col, s === 'live' ? 0.6 : 0.85); ctx.lineWidth = 1.5; seg(ctx, k, 0, 1)
  }
  if (lit && s !== 'down') { ctx.setLineDash([]); ctx.strokeStyle = rgba(acc, 1); ctx.lineWidth = 3; seg(ctx, k, 0, 1) }
  ctx.setLineDash([])
  const e = at(k, 1), s0 = at(k, 0), pc = (s === 'idle' || s === 'tether') ? [130, 130, 140] : col
  ctx.fillStyle = rgba(pc, 1)
  ctx.beginPath(); ctx.moveTo(e[0], e[1] - 3.5); ctx.lineTo(e[0] + 3.5, e[1]); ctx.lineTo(e[0], e[1] + 3.5); ctx.lineTo(e[0] - 3.5, e[1]); ctx.closePath(); ctx.fill()
  ctx.beginPath(); ctx.arc(s0[0], s0[1], 3, 0, 7); ctx.fill()
  ctx.restore()
}
function drawRings(fx, c, acc, t, think, s, flashLit) {
  const sp = think ? 3 : 1, rot = fx ? t * 0.25 * sp : 0.4
  ctx.save()
  if (fx) { ctx.shadowColor = rgba(c, 0.8); ctx.shadowBlur = 10 }
  ctx.strokeStyle = rgba(c, 0.95); ctx.lineWidth = 2; ring(ctx, 69)
  ctx.shadowBlur = 0
  ctx.strokeStyle = rgba(c, 0.45); ctx.lineWidth = 1; ring(ctx, 77)
  ctx.save(); ctx.translate(CX, CY); ctx.rotate(-rot * 0.6); ctx.setLineDash([2, 5]); ctx.strokeStyle = rgba(c, 0.55); ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(0, 0, 88, 0, Math.PI * 2); ctx.stroke(); ctx.restore()
  ctx.save(); ctx.translate(CX, CY); ctx.rotate(rot); ctx.strokeStyle = rgba(c, 0.7); ctx.lineWidth = 2.5; ctx.lineCap = 'round'
  for (let i = 0; i < 3; i++) { ctx.beginPath(); ctx.arc(0, 0, 100, i * 2.094, i * 2.094 + 1.1); ctx.stroke() }
  ctx.restore()
  ctx.save(); ctx.translate(CX, CY); ctx.strokeStyle = rgba(c, 0.28); ctx.lineWidth = 1
  for (let i = 0; i < 72; i++) { const a = i / 72 * Math.PI * 2, r0 = i % 6 === 0 ? 109 : 113; ctx.beginPath(); ctx.moveTo(Math.cos(a) * r0, Math.sin(a) * r0); ctx.lineTo(Math.cos(a) * 118, Math.sin(a) * 118); ctx.stroke() }
  ctx.restore()
  if (think && fx) { const ph = (t % 1.2) / 1.2; ctx.strokeStyle = rgba(c, 0.6 * (1 - ph)); ctx.lineWidth = 1.5; ring(ctx, 70 + ph * 48) }
  if (flashLit) { ctx.fillStyle = rgba(flashLit === 'amber' ? A : c, 0.35); ctx.beginPath(); ctx.arc(CX, CY, 68, 0, 7); ctx.fill(); ctx.strokeStyle = rgba(flashLit === 'amber' ? A : c, 0.9); ctx.lineWidth = 2.5; ring(ctx, 72) }
  ctx.restore()
}
function drawPulse(legs, e, acc) {
  let el = e
  for (const L of legs) { if (el <= L.d) { drawLeg(L, Math.max(0, el / L.d), acc); return } el -= L.d }
}
function drawLeg(L, f, acc) {
  const e = 0.5 - 0.5 * Math.cos(Math.PI * Math.min(1, f))
  const hot = acc.map(v => Math.round(v + (255 - v) * 0.55))
  if (L.k) {
    for (let i = 7; i >= 1; i--) { const q = at(L.k, L.a + (L.b - L.a) * Math.max(0, e - i * 0.04)); ctx.fillStyle = rgba(acc, 0.55 * (1 - i / 8)); ctx.beginPath(); ctx.arc(q[0], q[1], 3.2 * (1 - i / 10), 0, 7); ctx.fill() }
    const q = at(L.k, L.a + (L.b - L.a) * e)
    ctx.fillStyle = rgba(acc, 0.28); ctx.beginPath(); ctx.arc(q[0], q[1], 10, 0, 7); ctx.fill()
    ctx.fillStyle = rgba(hot, 1); ctx.beginPath(); ctx.arc(q[0], q[1], 3.6, 0, 7); ctx.fill()
  } else if (L.flash) {
    const c = L.flash === 'amber' ? A : acc
    ctx.fillStyle = rgba(c, 0.5 * (1 - f)); ctx.beginPath(); ctx.arc(CX, CY, 68, 0, 7); ctx.fill()
    ctx.strokeStyle = rgba(c, 0.9 * (1 - f)); ctx.lineWidth = 2.5; ring(ctx, 72 + f * 46)
  } else if (L.burst) {
    const q = at('broker', 0.62); ctx.strokeStyle = rgba(R, 1 - f); ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(q[0], q[1], 4 + f * 16, 0, 7); ctx.stroke()
  } else if (L.ping) {
    const q = at('broker', 0); ctx.strokeStyle = rgba(acc, 1 - f); ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(q[0], q[1], 4 + f * 14, 0, 7); ctx.stroke()
  }
}
function flashHit(k) {
  hit.value = { ...hit.value, [k]: 1 }
  later(() => { const h = { ...hit.value }; delete h[k]; hit.value = h }, 380)
}
/** A trade happened: run the pulse along the pipes (design 1g). */
function fire(k) {
  if (state.value !== 'running') return
  if (k === 'fill' && brokerDown.value) k = 'blocked'
  if (mode.value === 'reduced') {
    hit.value = k === 'fill' ? { market: 1, broker: 1, account: 1, action: 1 } : { market: 1, action: 1 }
    later(() => { hit.value = {} }, 600)
    draw(performance.now())
    return
  }
  const legs = legsFor(k)
  pulse = { legs, t0: performance.now() }
  flashHit('market')
  let tt = 0
  legs.forEach((L, i) => {
    later(() => { legIdx = i; if (lite.value) draw(performance.now()) }, tt)
    tt += L.d
    later(() => { if (L.hit) flashHit(L.hit) }, tt)
  })
  later(() => { pulse = null; legIdx = -1; if (!anim.value) draw(performance.now()) }, tt + 10)
}

/* New open position = a fill reached the broker. */
watch(() => trades.openCount, (n, o) => { if (o != null && n > o) fire('fill') })
/* Static modes redraw on state change instead of every frame. */
watch([links, running, listening, accent], () => { if (!anim.value) draw(performance.now()) })

let poll = 0
onMounted(async () => {
  refreshPlan()
  await nextTick()
  setupCanvas()
  pull()
  if (!props.mini) poll = setInterval(() => { if (document.visibilityState === 'visible') pull() }, 20_000)
})
watch(ready, async v => { if (v) { await nextTick(); setupCanvas() } })
watch(linked, v => { if (v) pull() })
onUnmounted(() => {
  cancelAnimationFrame(raf)
  clearInterval(poll); clearTimeout(vT)
  timers.forEach(clearTimeout)
})
</script>

<style>
@keyframes ccDrift { 0%,100% { transform: scale(1.06) translate(0,0) } 50% { transform: scale(1.16) translate(-3%,2%) } }
</style>

<style scoped>
.cc { position: fixed; inset: 0; overflow: hidden; background: #09090C; }
.cc-mini { position: absolute; pointer-events: none; }
.cc-probe { position: absolute; visibility: hidden; pointer-events: none; padding-top: env(safe-area-inset-top, 0px); }
.cc-stage {
  position: absolute; left: 0; top: 0; transform-origin: 0 0; overflow: hidden;
  background: #09090C; color: #F4F4F7; font-family: Geist, 'SF Pro Text', system-ui, sans-serif;
  font-variant-numeric: tabular-nums; -webkit-font-smoothing: antialiased; isolation: isolate;
  user-select: none; -webkit-user-select: none;
}
.ms { font-family: 'Material Symbols Outlined'; font-weight: 400; font-style: normal; line-height: 1; letter-spacing: normal; font-feature-settings: 'liga'; color: #E6E6EA; }
.cc-fill { position: absolute; inset: 0; pointer-events: none; }
.cc-bgart { position: absolute; left: -60px; top: -60px; object-fit: cover; filter: blur(30px) brightness(.5) saturate(.85); opacity: .6; }
.cc-grid { background-image: linear-gradient(rgba(255,255,255,.04) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.04) 1px, transparent 1px); background-size: 24px 24px; }
.cc-col { position: absolute; top: 0; width: 390px; }
.cc-head { position: absolute; left: 20px; right: 16px; height: 48px; display: flex; align-items: center; justify-content: space-between; z-index: 3; }
.cc-robot { font-size: 22px; font-weight: 600; letter-spacing: -.01em; line-height: 26px; }
.cc-by { font-size: 12.5px; color: #A3A3AE; }
.cc-tune { width: 44px; height: 44px; border-radius: 14px; display: flex; align-items: center; justify-content: center; cursor: pointer; background: rgba(18,18,24,.72); border: 1px solid rgba(255,255,255,.1); }
.cc-hud { position: absolute; left: 0; width: 390px; height: 844px; }
.cc-core { position: absolute; left: 127px; top: 298px; width: 136px; height: 136px; border-radius: 50%; overflow: hidden; background: #141418; z-index: 1; transition: filter .3s; }
.cc-core-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.cc-core-vig { position: absolute; inset: 0; border-radius: 50%; background: radial-gradient(circle, transparent 58%, rgba(6,6,9,.6) 100%); }
.cc-canvas { position: absolute; left: 0; top: 0; width: 390px; height: 844px; pointer-events: none; z-index: 2; }
.cc-panel { position: absolute; width: 171px; height: 128px; box-sizing: border-box; padding: 12px 12px 12px 14px; border-radius: 16px; z-index: 3; cursor: pointer; display: flex; flex-direction: column; transition: border-color .2s ease, box-shadow .2s ease, transform .15s ease; }
.cc-panel:active { transform: scale(.98); }
.cc-ph { display: flex; align-items: center; justify-content: space-between; height: 16px; }
.cc-ph-l { display: flex; align-items: center; gap: 6px; }
.cc-pdot { width: 6px; height: 6px; border-radius: 50%; }
.cc-pt { font-size: 11px; font-weight: 600; letter-spacing: .12em; color: #9C9CA8; }
.cc-chev { font-size: 16px; color: #76767F; }
.cc-msym { margin-top: 9px; font-size: 13px; font-weight: 600; color: #D9D9DF; letter-spacing: .02em; }
.cc-mprice { margin-top: 1px; font-size: 23px; font-weight: 600; letter-spacing: -.01em; }
.cc-pfoot { margin-top: auto; display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.cc-small { font-size: 12px; font-weight: 500; }
.cc-mute { color: #A3A3AE; }
.cc-empty-h { margin-top: 12px; font-size: 15px; font-weight: 600; color: #E6E6EA; line-height: 20px; }
.cc-empty-b { margin-top: 4px; font-size: 12px; line-height: 17px; color: #A3A3AE; text-wrap: pretty; }
.cc-eql { margin-top: 8px; font-size: 11.5px; color: #A3A3AE; }
.cc-eq { margin-top: 1px; font-size: 21px; font-weight: 600; letter-spacing: -.01em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cc-acrows { margin-top: auto; display: flex; flex-direction: column; gap: 2px; font-size: 12px; }
.cc-acrow { display: flex; justify-content: space-between; gap: 6px; }
.cc-brh { margin-top: 10px; font-size: 16px; font-weight: 600; }
.cc-brs { margin-top: 3px; font-size: 12.5px; font-weight: 500; color: #D9D9DF; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cc-fix { display: inline-flex; align-items: center; gap: 4px; height: 24px; padding: 0 9px 0 7px; border-radius: 12px; border: 1px solid rgba(255,93,93,.7); background: rgba(255,93,93,.12); font-size: 12px; font-weight: 600; color: #FF7A7A; }
.cc-lat { margin-top: 9px; font-size: 13.5px; font-weight: 600; color: #E6E6EA; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cc-lav { margin-top: 3px; font-size: 19px; font-weight: 600; letter-spacing: -.01em; }
.cc-status { position: absolute; left: 20px; right: 20px; z-index: 3; display: flex; flex-direction: column; align-items: center; gap: 4px; text-align: center; cursor: pointer; }
.cc-status-row { display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 500; color: #E6E6EA; }
.cc-sdot { width: 7px; height: 7px; border-radius: 50%; flex: none; }
.cc-status-sub { font-size: 12px; line-height: 17px; color: #A3A3AE; max-width: 300px; text-wrap: pretty; }
.cc-act { position: absolute; left: 16px; right: 16px; height: 64px; z-index: 3; }
.cc-mic { height: 64px; border-radius: 32px; display: flex; align-items: center; gap: 14px; padding: 0 9px; cursor: pointer; touch-action: none; background: rgba(16,16,22,.84); border: 1px solid color-mix(in srgb, var(--a) 65%, transparent); transition: box-shadow .2s ease, transform .15s ease; }
.cc-mic:active { transform: scale(.985); }
.cc-mic-ic { width: 46px; height: 46px; border-radius: 50%; flex: none; display: flex; align-items: center; justify-content: center; background: color-mix(in srgb, var(--a) 24%, #101015); border: 1.5px solid var(--a); }
.cc-mic-ic .ms { color: #fff; }
.cc-mic-l { font-size: 17px; font-weight: 600; color: #F4F4F7; }
.cc-mic-h { font-size: 12px; color: #A3A3AE; }
.cc-solid { height: 64px; border-radius: 32px; display: flex; align-items: center; justify-content: center; gap: 10px; cursor: pointer; background: #F4F4F7; color: #0B0B0E; font-size: 17px; font-weight: 600; transition: transform .15s ease; }
.cc-solid:active { transform: scale(.985); }
.cc-caption { position: absolute; width: 350px; z-index: 11; padding: 12px 16px; border-radius: 20px; background: rgba(14,14,19,.82); border: 1px solid color-mix(in srgb, var(--a) 40%, transparent); backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px); font-size: 14px; line-height: 20px; color: #E6E6EA; pointer-events: none; }
.cc-cap-who { font-size: 10.5px; font-weight: 600; letter-spacing: .14em; color: #9C9CA8; margin-bottom: 3px; }
.cc-cap-enter-active, .cc-cap-leave-active { transition: opacity .25s ease, transform .25s ease; }
.cc-cap-enter-from, .cc-cap-leave-to { opacity: 0; transform: translateY(8px); }
</style>
