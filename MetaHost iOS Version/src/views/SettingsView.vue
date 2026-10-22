<template>
  <div class="s3" :style="{ '--a': accentHex }" :data-motion="theme.motion">
    <img class="s3-wash" :src="art" alt="" aria-hidden="true" />
    <div class="s3-veil"></div>
    <div class="s3-halo"></div>

    <div ref="scroller" class="s3-scroll">
      <!-- ── Hub ─────────────────────────────────────────────── -->
      <div v-if="isHub" key="hub" class="s3-stack s3-fade">
        <button class="s3-back" @click="leave">
          <span class="ms">chevron_left</span>Home
        </button>
        <h1 class="s3-h1">Settings</h1>

        <div v-if="f.expired" class="s3-expired">
          <div class="s3-expired-copy">
            <span class="ms" style="color:#FF7A7E">event_busy</span>
            <p>Your {{ prefs.plan }} plan has ended. {{ R }} stopped trading. Open trades are still at your broker.</p>
          </div>
          <button class="s3-pill-light" @click="go('plan')">Renew plan</button>
        </div>

        <section class="s3-card s3-hero">
          <div class="s3-hero-main">
            <div class="s3-ring"><div class="s3-ring-in"><img :src="art" :alt="R" :class="{ drift: prefs.loop !== 'Off (data saver)' }" /></div></div>
            <div class="s3-hero-copy">
              <div class="s3-robot">{{ R }}</div>
              <div class="s3-plan">{{ planLine }}</div>
              <div class="s3-lic" :style="{ color: licColor }"><i :style="{ background: licColor }"></i>{{ licText }}</div>
            </div>
          </div>
          <button v-if="f.broker" class="s3-hero-row is-warn" @click="openReconnect">
            <span class="ms">link_off</span>
            <span class="grow">Broker disconnected — tap to fix</span>
            <span class="ms">chevron_right</span>
          </button>
          <div v-else class="s3-hero-row">
            <i class="s3-dot" :style="{ background: healthDot, boxShadow: '0 0 8px ' + healthDot }"></i>
            <span class="grow">{{ healthText }}</span>
          </div>
        </section>

        <label class="s3-search">
          <span class="ms">search</span>
          <input v-model="q" type="search" placeholder="Search settings…" enterkeyhint="search" autocomplete="off" />
          <button v-if="q" class="ms s3-clear" aria-label="Clear search" @click="q = ''">cancel</button>
        </label>

        <template v-if="query">
          <div class="s3-group">
            <div class="s3-h">{{ results.length }} {{ results.length === 1 ? 'RESULT' : 'RESULTS' }}</div>
            <div v-if="results.length" class="s3-card">
              <button v-for="(r, i) in results" :key="i" class="s3-row s3-result" @click="jump(r.chain)">
                <span class="grow">
                  <span class="s3-label">{{ r.label }}</span>
                  <span class="s3-sub">{{ r.crumb }}</span>
                </span>
                <span class="s3-val">{{ r.value }}</span>
                <span class="ms s3-chev">chevron_right</span>
              </button>
            </div>
            <p v-else class="s3-empty">No settings match “{{ q }}”. Try “sound”, “margin” or “quiet”.</p>
          </div>
        </template>

        <template v-else>
          <div v-for="g in groups" :key="g.h" class="s3-group">
            <div class="s3-h">{{ g.h }}</div>
            <div class="s3-card">
              <button v-for="r in g.rows" :key="r.to" class="s3-row s3-hubrow" @click="go(r.to)">
                <span class="ms s3-icon">{{ r.icon }}</span>
                <span class="grow s3-label">{{ r.label }}</span>
                <span class="s3-val" :class="{ 'is-warn': r.warn }">{{ r.value }}</span>
                <span class="ms s3-chev">chevron_right</span>
              </button>
            </div>
          </div>
          <p class="s3-foot-mono">NovaHost {{ version }} · {{ R }}</p>
        </template>
      </div>

      <!-- ── A page ─────────────────────────────────────────── -->
      <div v-else-if="page" :key="top" class="s3-stack s3-push">
        <button class="s3-back" @click="pop"><span class="ms">chevron_left</span>{{ backLabel }}</button>
        <h1 class="s3-h1 s3-h1-page">{{ page.title }}</h1>
        <p v-if="page.sub" class="s3-lede">{{ page.sub }}</p>

        <div v-for="(sec, si) in page.sections" :key="si" class="s3-group s3-sec">
          <div v-if="sec.h" class="s3-h">{{ sec.h }}</div>
          <div class="s3-card">
            <template v-for="(r, i) in sec.rows" :key="i">
              <!-- line -->
              <component :is="r.tap && !r.dis ? 'button' : 'div'" v-if="r.t === 'line'" class="s3-row" :class="{ 'is-dis': r.dis }" @click="!r.dis && r.tap && r.tap()">
                <span class="grow">
                  <span class="s3-label">{{ r.label }}</span>
                  <span v-if="r.sub" class="s3-sub">{{ r.sub }}</span>
                </span>
                <span class="s3-val" :style="r.vc ? { color: r.vc } : null">{{ r.value }}</span>
                <span v-if="r.chev" class="ms s3-chev">{{ r.chevIcon || 'chevron_right' }}</span>
              </component>

              <!-- switch / category -->
              <div v-else-if="r.t === 'tog' || r.t === 'cat'" class="s3-row" :class="{ 'is-dis': r.dis }">
                <span class="grow">
                  <span class="s3-label">{{ r.label }}</span>
                  <span v-if="r.sub" class="s3-sub">{{ r.sub }}</span>
                </span>
                <button v-if="r.t === 'cat'" class="s3-snd" :class="{ on: prefs['s_' + r.key] }" :disabled="r.dis"
                        :aria-label="(prefs['s_' + r.key] ? 'Mute ' : 'Unmute ') + r.label" @click="flip('s_' + r.key)">
                  <span class="ms">{{ prefs['s_' + r.key] ? 'volume_up' : 'volume_off' }}</span>
                </button>
                <button class="s3-switch" role="switch" :aria-checked="isOn(r)" :aria-label="r.label" :disabled="r.dis"
                        :class="{ on: isOn(r) }" @click="toggle(r)"><i></i></button>
              </div>

              <button v-else-if="r.t === 'act'" class="s3-row s3-act" :class="'tone-' + (r.tone || 'plain')" @click="r.tap && r.tap()">
                <span class="ms">{{ r.icon }}</span>{{ r.label }}
              </button>

              <button v-else-if="r.t === 'warn'" class="s3-row s3-warnrow" @click="r.tap && r.tap()">
                <span class="ms">{{ r.icon }}</span>
                <span class="grow">{{ r.label }}</span>
                <span class="ms">chevron_right</span>
              </button>

              <div v-else-if="r.t === 'note'" class="s3-row s3-note">
                <span class="ms">{{ r.icon }}</span>
                <span class="grow">{{ r.label }}</span>
              </div>

              <div v-else-if="r.t === 'layouts'" class="s3-layouts">
                <button v-for="L in layoutTiles" :key="L.id" class="s3-tile" :class="{ on: theme.layout === L.id }" @click="setLayout(L.id)">
                  <span class="s3-mini">
                    <i v-for="(e, ei) in L.els" :key="ei" :style="e"></i>
                  </span>
                  <span class="s3-tile-name">{{ L.name }}</span>
                </button>
              </div>
            </template>
          </div>
          <p v-if="sec.foot" class="s3-foot">{{ sec.foot }}</p>
        </div>
      </div>
    </div>

    <div class="s3-topfade" aria-hidden="true"></div>

    <Teleport to="body">
    <div class="s3-layer" :style="{ '--a': accentHex }" :data-motion="theme.motion">
    <!-- ── Toast ─────────────────────────────────────────────── -->
    <Transition name="s3-drop">
      <div v-if="toastMsg" class="s3-toast" role="status" @click="toastMsg = null">
        <img src="/app_logo.png" alt="" />
        <div class="grow">
          <div class="s3-toast-meta"><span>NOVAHOST</span><span>now</span></div>
          <div class="s3-toast-title" :class="{ 'is-warn': toastMsg.warn }">{{ toastMsg.title }}</div>
          <div class="s3-toast-body">{{ toastMsg.body }}</div>
        </div>
      </div>
    </Transition>

    <!-- ── Bottom sheet ──────────────────────────────────────── -->
    <Transition name="s3-sheet">
      <div v-if="sheet" class="s3-sheet-wrap">
        <div class="s3-scrim" @click="sheet = null"></div>
        <div class="s3-sheet" role="dialog" :aria-label="sheet.title">
          <div class="s3-grab"></div>
          <div class="s3-sheet-head">
            <h2>{{ sheet.title }}</h2>
            <button class="s3-done" @click="sheet = null">Done</button>
          </div>

          <template v-if="sheet.type === 'pick'">
            <div class="s3-opts">
              <button v-for="o in sheet.opts" :key="o" class="s3-opt" :class="{ on: o === sheet.cur }" @click="choose(o)">
                <span>{{ o }}</span><span class="ms">check</span>
              </button>
            </div>
            <p v-if="sheet.note" class="s3-sheet-note">{{ sheet.note }}</p>
          </template>

          <template v-else-if="sheet.type === 'quiet'">
            <div class="s3-quiet">
              <div v-for="col in quietCols" :key="col.key">
                <div class="s3-h">{{ col.h }}</div>
                <div :ref="el => (quietRefs[col.key] = el)" class="s3-times">
                  <button v-for="t in TIMES" :key="t" :class="{ on: prefs[col.key] === t }" @click="prefs[col.key] = t">{{ t }}</button>
                </div>
              </div>
            </div>
            <p class="s3-sheet-copy">Silent from {{ prefs.qFrom }} to {{ prefs.qTo }}{{ prefs.quietAllow ? '. Still allowed: trade refused, broker disconnected.' : '. Everything waits until morning.' }}</p>
          </template>

          <template v-else-if="sheet.type === 'how'">
            <div class="s3-how">
              <div v-for="(s, i) in HOW" :key="i" class="s3-step"><b>{{ i + 1 }}</b><span>{{ s }}</span></div>
              <button class="s3-cta" @click="sheet = null">I’ve added it</button>
            </div>
          </template>

          <template v-else-if="sheet.type === 'reconnect'">
            <div class="s3-how">
              <p class="s3-sheet-copy" style="padding:0">{{ f.brokerName }} refused the password. New trades are on hold. Open trades still have their stop-loss.</p>
              <label class="s3-field">
                <span class="s3-h" style="padding:0">PASSWORD</span>
                <input v-model="pw" type="password" autocomplete="current-password" placeholder="Trading password" />
              </label>
              <button class="s3-cta" :disabled="!pw" @click="doReconnect">Reconnect</button>
              <button class="s3-link" @click="sheet = null; go('accounts')">Open My accounts</button>
            </div>
          </template>
        </div>
      </div>
    </Transition>

    <!-- ── Confirm ───────────────────────────────────────────── -->
    <Transition name="s3-fadeT">
      <div v-if="confirmBox" class="s3-confirm-wrap">
        <div class="s3-confirm" role="alertdialog" :aria-label="confirmBox.title">
          <div class="s3-confirm-copy">
            <strong>{{ confirmBox.title }}</strong>
            <p>{{ confirmBox.body }}</p>
          </div>
          <div class="s3-confirm-btns">
            <button @click="confirmBox = null">Cancel</button>
            <button :class="{ danger: confirmBox.danger }" @click="okConfirm">{{ confirmBox.btn }}</button>
          </div>
        </div>
      </div>
    </Transition>

    </div>
    </Teleport>

    <InterfaceStyleSheet v-if="styleOpen" :tab="styleTab" @close="styleOpen = false" />
  </div>
</template>

<script>
/* Where the user was, kept across a trip to /accounts or /symbols so the
   Back button there lands on the page they left, not the top of the hub. */
let lastStack = ['hub']
</script>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { loadLinkState, setAutoExecute } from '../services/brokerService.js'
import {
  theme, activeLayout, activeGradient, activeTone, activeNameFont, resolvedScheme,
  useMentorAccent, LAYOUTS, FX_STYLES, GLOW_MODES, CARD_STYLES, ART_MODES, SCHEMES, REORDERABLE, activeArtMode,
} from '../stores/theme.js'
import { robot, activeEa, artFor, ensureSeeded, refreshFleet, selectRobot } from '../stores/robot.js'
import { session } from '../stores/session.js'
import { accounts, accountList, extraCount, loadAccounts } from '../stores/accounts.js'
import { prefs, RENEWS, NOTIF_CATS } from '../stores/settingsPrefs.js'
import { push, refreshPushState, enablePush, disablePush, syncPush, testPush } from '../services/pushService.js'
import { secStorage } from '../utils/secStorage.js'
import { haptic } from '../composables/useSpeech.js'
import { buildPages, hubGroups } from '../components/settings/v3/pages.js'
import InterfaceStyleSheet from '../components/settings/InterfaceStyleSheet.vue'

const router = useRouter()
/* A literal, not a build define: package.json is still 0.0.0. */
const version = 'V6'

const TIMES = Array.from({ length: 48 }, (_, i) => String(Math.floor(i / 2)).padStart(2, '0') + ':' + (i % 2 ? '30' : '00'))
const HOW = ['Tap the Share button in Safari.', 'Choose “Add to Home Screen”.', 'Open NovaHost from the new icon and allow notifications.']

/* ── Navigation ───────────────────────────────────────────────────── */
const stack = ref(lastStack)
watch(stack, v => { lastStack = v })
const scroller = ref(null)
const top = computed(() => stack.value[stack.value.length - 1])
const isHub = computed(() => top.value === 'hub')

function go(to) {
  haptic(15)
  if (to.startsWith('route:')) return router.push(to.slice(6))
  if (to === 'accounts' || to === 'symbols') return router.push('/' + to)
  stack.value = stack.value.concat(to)
  q.value = ''
}
function pop() { if (stack.value.length > 1) stack.value = stack.value.slice(0, -1) }
function jump(chain) { stack.value = chain; q.value = '' }
function leave() { lastStack = ['hub']; router.push('/home') }
watch(top, () => nextTick(() => { if (scroller.value) scroller.value.scrollTop = 0 }))

/* ── Overlays ─────────────────────────────────────────────────────── */
const sheet = ref(null)
const confirmBox = ref(null)
const toastMsg = ref(null)
const styleOpen = ref(false)
const styleTab = ref('colours')
const pw = ref('')
let toastTimer = 0

function toast(title, body, warn = false) {
  clearTimeout(toastTimer)
  toastMsg.value = { title, body, warn }
  toastTimer = setTimeout(() => { toastMsg.value = null }, 3400)
}
function pick(title, opts, cur, onPick, note) {
  sheet.value = { type: 'pick', title, opts, cur, onPick, note }
}
function choose(o) {
  const s = sheet.value
  sheet.value = null
  haptic(10)
  s.onPick(o)
}
function ask(title, body, btn, danger, ok) { confirmBox.value = { title, body, btn, danger, ok } }
function okConfirm() { const c = confirmBox.value; confirmBox.value = null; c?.ok?.() }
function openStyle(tab) { styleTab.value = tab; styleOpen.value = true; haptic(20) }
function openReconnect() { pw.value = ''; sheet.value = { type: 'reconnect', title: 'Reconnect broker' } }
function doReconnect() {
  if (!pw.value) return
  sheet.value = null
  pw.value = ''
  toast('Reconnecting', f.value.brokerName + ' is checking the new password.')
}

const quietRefs = {}
const quietCols = [{ h: 'FROM', key: 'qFrom' }, { h: 'TO', key: 'qTo' }]
watch(() => sheet.value?.type, t => {
  if (t !== 'quiet') return
  nextTick(() => quietCols.forEach(({ key }) => {
    const el = quietRefs[key]
    if (el) el.scrollTop = Math.max(0, TIMES.indexOf(prefs[key]) * 44 - 96)
  }))
})

onBeforeUnmount(() => clearTimeout(toastTimer))

/* ── Switches ─────────────────────────────────────────────────────── */
function isOn(r) {
  if (r.get) return r.get()
  return !!prefs[r.key] && !(r.dis && r.key === 'nOn')
}
function flip(k) { prefs[k] = !prefs[k]; haptic(10) }
function toggle(r) {
  if (r.dis) return
  if (r.set) r.set()
  else prefs[r.key] = !prefs[r.key]
  haptic(10)
}

/* ── Auto-execute ─────────────────────────────────────────────────────
   The one switch here the server already honours (`licenses.auto_execute`).
   `null` means the licence has not answered — drawing that as "off" would
   tell someone their robot is idle when it may be trading. */
const autoExec = ref(null)
const autoExecBusy = ref(false)
const autoExecCaption = computed(() => autoExec.value === null
  ? 'Checking your licence…'
  : autoExec.value ? 'The server places your mentor’s trades' : 'Trades arrive only while this app is open')

async function refreshAutoExec() {
  try {
    const state = await loadLinkState()
    autoExec.value = state.linked === null ? null : Boolean(state.autoExecute)
  } catch {
    autoExec.value = null
  }
}
async function toggleAutoExec() {
  if (autoExecBusy.value || autoExec.value === null) return
  autoExecBusy.value = true
  try {
    const res = await setAutoExecute(!autoExec.value)
    if (res.ok) {
      autoExec.value = res.autoExecute
      if (res.warning) toast('Auto-execute', res.warning, true)
    } else {
      toast('Could not change that', res.message || 'Try again in a moment.', true)
    }
  } catch {
    toast('Could not change that', 'Check your connection and try again.', true)
  } finally {
    autoExecBusy.value = false
  }
}

/* ── Device facts ─────────────────────────────────────────────────── */
const ua = navigator.userAgent
const iosVer = (ua.match(/OS (\d+)[_.](\d+)/) || [])
const deviceShort = /iPad/.test(ua) ? 'iPad' : /iPhone/.test(ua) ? 'iPhone' : /Android/.test(ua) ? 'Android' : 'This browser'
const deviceLine = deviceShort + (iosVer[1] ? ' · iOS ' + iosVer[1] + '.' + iosVer[2] : '')
const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true
const isIOS = /iPhone|iPad/.test(ua)
const perm = ref(typeof Notification !== 'undefined' ? Notification.permission : 'unsupported')
/* What the phone can actually do, not what a switch says. The old row read
   "on" from a local default while no notification could reach the phone. */
onMounted(async () => {
  await refreshPushState()
  if (typeof Notification !== 'undefined') perm.value = Notification.permission
  syncPush()
})

async function togglePush() {
  const res = push.state === 'on' ? await disablePush() : await enablePush()
  if (typeof Notification !== 'undefined') perm.value = Notification.permission
  if (!res.ok) toast('Notifications', res.message, true)
  else if (push.state === 'on') toast(R.value + ' can reach you now', 'You’ll get an alert when I spot or place a trade.')
}

/* Category and quiet-hours switches travel with the subscription; the server
   reads them before every alert. Debounced so a run of taps is one call. */
let syncTimer = null
const NOTIF_KEYS = ['quietOn', 'qFrom', 'qTo', 'quietAllow', ...NOTIF_CATS.flatMap(([, l]) => l.map(([k]) => k))]
watch(() => NOTIF_KEYS.map(k => prefs[k]).join('|'), () => {
  clearTimeout(syncTimer)
  syncTimer = setTimeout(syncPush, 800)
})
const mic = ref('Ask when needed')
onMounted(async () => {
  try {
    const s = await navigator.permissions?.query({ name: 'microphone' })
    if (s) mic.value = s.state === 'granted' ? 'Allowed' : s.state === 'denied' ? 'Blocked' : 'Ask when needed'
  } catch { /* Safari does not expose microphone permission state */ }
})

const licenceKey = secStorage.getItem('LICENSE_KEY') || ''
const maskedKey = licenceKey ? licenceKey.slice(0, 4) + '-••••-••••-' + licenceKey.slice(-4) : 'Not set'
const email = secStorage.getItem('VERIFIED_EMAIL') || secStorage.getItem('PENDING_EMAIL') || ''

async function copyKey() {
  try {
    await navigator.clipboard.writeText(licenceKey)
    toast('Licence key copied', maskedKey)
  } catch {
    toast('Could not copy', 'Your browser blocked the clipboard.', true)
  }
}

/* ── State the hub reads ──────────────────────────────────────────── */
const R = computed(() => robot.name)
const accentHex = computed(() => theme.accent.hex)
const art = computed(() => artFor(activeEa.value))

const f = computed(() => {
  const exp = activeEa.value?.expiresAt
  const down = accountList.value.find(a => a.state === 'disconnected' || a.needsPassword)
  return {
    expired: Boolean(exp && new Date(exp).getTime() < Date.now()),
    broker: Boolean(down),
    brokerName: down ? (down.broker + (down.server ? ' · ' + down.server : '')) : 'Your broker',
    blocked: perm.value === 'denied' || push.state === 'denied',
    ios: isIOS && !standalone,
  }
})

const nAcc = computed(() => accountList.value.length)
const nSym = computed(() => robot.allowedSymbols.length)
const planLine = computed(() => f.value.expired ? prefs.plan + ' · ended' : prefs.plan === 'Annual' ? 'Annual · renews 22 Oct 2027' : RENEWS)
const licText = computed(() => f.value.expired ? 'LICENCE PAUSED' : licenceKey ? 'LICENCE ACTIVE' : 'NO LICENCE')
const licColor = computed(() => f.value.expired || !licenceKey ? '#FF7A7E' : '#30D158')
const healthDot = computed(() => f.value.expired ? '#FF7A7E' : session.isRunning ? '#30D158' : '#8E8E99')
const healthText = computed(() => {
  if (f.value.expired) return 'Robot stopped · open trades are at your broker'
  const a = nAcc.value + (nAcc.value === 1 ? ' account' : ' accounts')
  return (session.isRunning ? a + ' trading' : 'Robot paused · ' + a) + ' · ' + nSym.value + ' symbols ready'
})

/* ── Look & feel bridges into theme.js ────────────────────────────── */
/* Miniature of each home layout: boxes in % of a 56×104 phone. */
const E = (l, t, w, h, r, bg, sh) => ({ left: l + '%', top: t + '%', width: w + '%', height: h + '%', borderRadius: r || '3px', background: bg || 'rgba(255,255,255,.16)', boxShadow: sh || 'none' })
const ACC = 'var(--a)', ACCM = 'color-mix(in srgb, var(--a) 38%, transparent)'
const MINI = {
  core:    [E(8, 12, 38, 20), E(54, 12, 38, 20), E(8, 66, 38, 20), E(54, 66, 38, 20), E(32, 39, 36, 19.4, '50%', ACC, '0 0 10px var(--a)')],
  classic: [E(22, 22, 56, 30, '50%', ACC, '0 0 14px var(--a)'), E(12, 64, 76, 8), E(12, 76, 76, 8)],
  focus:   [E(36, 12, 28, 15, '50%', ACC, '0 0 8px var(--a)'), E(8, 38, 84, 48, '5px')],
  bleed:   [E(0, 0, 100, 100, '0', ACCM), E(8, 10, 44, 5, '2px'), E(8, 80, 84, 10, '4px')],
}
const ORDER = ['core', 'classic', 'focus', 'bleed']
const layoutTiles = ORDER.map(id => LAYOUTS.find(l => l.id === id)).filter(Boolean).map(l => ({ id: l.id, name: l.name, els: MINI[l.id] }))
function setLayout(id) { theme.layout = id; haptic(15) }

const fxName = computed(() => FX_STYLES.find(x => x.id === theme.fx)?.name || 'Off')
function pickFx() {
  const cur = FX_STYLES.find(x => x.id === theme.fx)?.name
  pick('Falling FX', FX_STYLES.map(x => x.name), cur, n => { theme.fx = FX_STYLES.find(x => x.name === n).id })
}
function pickReduce() {
  pick('Reduce effects', ['Auto', 'On', 'Off'], prefs.reduceMode, v => {
    prefs.reduceMode = v
    theme.motion = v === 'On' ? 'reduced' : 'full'
  }, 'Auto follows iPhone Reduce Motion and Low Power Mode.')
}
const accentModeLabel = computed(() => theme.accentOverridden ? 'Custom' : 'Robot’s colour')
function pickAccentMode() {
  pick('Accent', ['Robot’s colour', 'Custom'], accentModeLabel.value, v => {
    if (v === 'Custom') return openStyle('colours')
    if (theme.mentorAccent) useMentorAccent()
    else toast('No robot colour yet', 'This licence did not set one, so your own accent stays.', true)
  })
}

const styleRows = computed(() => {
  const glow = GLOW_MODES.find(g => g.id === theme.glowMode)?.name
  const card = CARD_STYLES.find(c => c.id === theme.cardStyle)?.name
  const artMode = ART_MODES.find(a => a.id === activeArtMode.value)?.name
  const cfg = theme.widgets[theme.layout]
  const scheme = theme.scheme === 'system'
    ? 'System · ' + (resolvedScheme.value === 'dark' ? 'Dark' : 'Light')
    : SCHEMES.find(s => s.id === theme.scheme)?.name
  const row = (label, value, tap) => ({ t: 'line', label, value, chev: true, tap })
  return [
    row('Accent colour', theme.accent.custom ? 'Custom' : theme.accent.name, () => openStyle('colours')),
    row('Gradient accent', activeGradient.value.name, () => openStyle('colours')),
    row('Background', activeTone.value.name, () => openStyle('colours')),
    row('Glow', glow, () => openStyle('colours')),
    row('Card style', card, () => openStyle('colours')),
    row('Robot art', artMode, () => openStyle('layouts')),
    row('Widgets', cfg ? (REORDERABLE.length - cfg.hidden.length) + ' of ' + REORDERABLE.length : '', () => go('route:/arrange-widgets')),
    row('Bot name & type', activeNameFont.value.name, () => openStyle('type')),
    row('Colour scheme', scheme, () => openStyle('modes')),
  ]
})

/* ── Robot switch ─────────────────────────────────────────────────── */
function switchRobot() {
  const list = robot.eaList
  if (list.length < 2) return toast('One robot on this phone', 'Add another licence from Home › Robots to switch between them.')
  const cur = activeEa.value?.name
  pick('Switch robot', list.map(e => e.name), cur, async name => {
    const ea = list.find(e => e.name === name)
    const res = await selectRobot(ea.id)
    if (res.ok) toast('Switched robot', name + ' is now active.')
    else toast('Could not switch', res.message, true)
  })
}

async function testNotif() {
  if (f.value.ios) return toast('Can’t send a test', 'Add NovaHost to your Home Screen first.', true)
  if (f.value.blocked) return toast('Can’t send a test', 'Notifications are blocked for NovaHost on this iPhone.', true)
  if (push.state !== 'on') return toast('Can’t send a test', 'Turn on Allow notifications first.', true)
  const res = await testPush()
  if (res.ok) toast('Test sent', 'It should arrive in a few seconds. Lock your phone to see it on the Lock Screen.')
  else toast('Test failed', res.message, true)
}

/* ── Context the page tree reads ──────────────────────────────────── */
const ctx = computed(() => ({
  p: prefs, R: R.value, f: f.value, theme, version,
  go, pop, pick, ask, toast, sheet: s => { sheet.value = s }, openReconnect,
  open: url => window.open(url, '_blank', 'noopener'),
  accountsLine: nAcc.value ? nAcc.value + ' linked' : 'None linked',
  symbolsLine: nSym.value + (nSym.value === 1 ? ' symbol' : ' symbols'),
  symbolNames: robot.allowedSymbols.slice(0, 4).join(', ') || 'Your robot’s symbols',
  extrasLine: extraCount.value ? extraCount.value + ' × R149/mo' : 'None',
  licencesLine: robot.eaList.length + (robot.eaList.length === 1 ? ' licence' : ' licences') + ' on this phone',
  autoExec: autoExec.value, autoExecCaption: autoExecCaption.value, toggleAutoExec,
  micLabel: mic.value,
  permLabel: perm.value === 'granted' ? 'Allowed' : perm.value === 'unsupported' ? 'Not supported' : 'Not asked yet',
  testNotif, switchRobot,
  pushOn: push.state === 'on', pushBusy: push.busy, togglePush,
  pushSub: push.busy ? 'Connecting…' : push.state === 'on' ? 'Alerts reach this iPhone, even when the app is closed' : null,
  email, licenceKey, maskedKey, copyKey, deviceLine, deviceShort,
  layoutName: activeLayout.value.name, styleRows: styleRows.value,
  fxName: fxName.value, pickFx, accentModeLabel: accentModeLabel.value, pickAccentMode, pickReduce,
}))

const pages = computed(() => buildPages(ctx.value))
const groups = computed(() => hubGroups(ctx.value))
const page = computed(() => pages.value[top.value] || null)
const backLabel = computed(() => {
  const prev = stack.value[stack.value.length - 2]
  return prev === 'hub' ? 'Settings' : pages.value[prev]?.title || 'Back'
})

/* A page id that no longer exists (a prop account removed elsewhere) falls
   back to the hub rather than drawing an empty screen. */
watch(page, v => { if (!v && !isHub.value) stack.value = ['hub'] }, { immediate: true })

/* ── Search ───────────────────────────────────────────────────────── */
const q = ref('')
const query = computed(() => q.value.trim().toLowerCase())

function chainOf(id) {
  const c = []
  let x = id
  while (x && x !== 'hub') { c.unshift(x); x = pages.value[x]?.parent }
  return ['hub', ...c]
}

const results = computed(() => {
  const s = query.value
  if (!s) return []
  const out = []
  for (const [id, pg] of Object.entries(pages.value)) {
    const chain = chainOf(id)
    const crumb = chain.slice(1).map(x => pages.value[x].title).join(' › ')
    if (pg.title.toLowerCase().includes(s)) {
      out.push({ label: pg.title, crumb: pages.value[pg.parent]?.title || 'Settings', value: '', chain })
    }
    for (const sec of pg.sections) for (const r of sec.rows) {
      if (!r.label || r.t === 'note' || !r.label.toLowerCase().includes(s)) continue
      const value = r.t === 'tog' || r.t === 'cat' ? (isOn(r) ? 'On' : 'Off') : (r.value || '')
      out.push({ label: r.label, crumb, value, chain })
    }
  }
  return out.slice(0, 30)
})

/* ── Boot ─────────────────────────────────────────────────────────── */
onMounted(() => {
  ensureSeeded()
  refreshFleet().catch(() => {})
  if (!accounts.loaded) loadAccounts().catch(() => {})
  refreshAutoExec()
})

/* Legacy credential purge: the old API-credentials inputs are gone, but a
   key typed into them would otherwise sit in localStorage forever. */
for (const k of ['METAAPI_TOKEN', 'METAAPI_ACCOUNT_ID']) localStorage.removeItem(k)
</script>

<style scoped>
.s3, .s3-layer {
  --ink: #F4F4F7; --ink-2: #B4B4BE; --ink-3: #9C9CA8; --ink-4: #8E8E99; --ink-5: #6E6E78;
  --glass: rgba(22, 22, 28, .62);
  --hair: rgba(255, 255, 255, .07);
  --amber: #FFC24D;
  --safe-t: env(safe-area-inset-top, 0px);
  --safe-b: env(safe-area-inset-bottom, 0px);
  color: var(--ink);
  font-family: Geist, 'SF Pro Text', system-ui, sans-serif;
  font-variant-numeric: tabular-nums;
  -webkit-font-smoothing: antialiased;
}
/* Overlays live on body so they sit above App.vue's Screens button (z 9001). */
.s3-layer { position: fixed; inset: 0; z-index: 9100; pointer-events: none; }
.s3-layer > * { pointer-events: auto; }
.s3 {
  position: relative;
  height: 100dvh;
  /* clip, not just hidden: the blurred wash is wider than the screen, and a
     hidden box can still be scrolled sideways when the search field focuses. */
  overflow: hidden;
  overflow: clip;
  isolation: isolate;
  background: #09090C;
}
.s3 .ms, .s3-layer .ms {
  font-family: 'Material Symbols Outlined';
  font-variation-settings: 'wght' 300;
  font-size: 20px; line-height: 1;
  font-weight: normal; font-style: normal;
  letter-spacing: normal; text-transform: none; white-space: nowrap;
  flex: none;
}
:where(.s3, .s3-layer) :where(button) { font: inherit; color: inherit; background: none; border: 0; padding: 0; text-align: left; cursor: pointer; -webkit-tap-highlight-color: transparent; }
:where(.s3, .s3-layer) :where(button:disabled) { cursor: default; }
:where(.s3, .s3-layer) :where(.grow) { flex: 1; min-width: 0; }

/* ── Backdrop ── */
.s3-wash {
  position: absolute; left: -80px; top: -80px; width: calc(100% + 160px); height: 620px;
  object-fit: cover; filter: blur(44px) brightness(.45) saturate(.9); opacity: .55; pointer-events: none;
}
.s3-veil { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(9,9,12,.55) 0%, rgba(9,9,12,.86) 40%, #09090C 75%); pointer-events: none; }
.s3-halo { position: absolute; inset: 0; background: radial-gradient(70% 28% at 50% 12%, color-mix(in srgb, var(--a) 20%, transparent), transparent 70%); pointer-events: none; }
.s3-topfade {
  position: absolute; left: 0; right: 0; top: 0; height: calc(var(--safe-t) + 14px); z-index: 30; pointer-events: none;
  background: linear-gradient(180deg, rgba(9,9,12,.92) 60%, rgba(9,9,12,0));
}

/* ── Scroller ── */
.s3-scroll {
  position: absolute; inset: 0; z-index: 2;
  overflow-x: hidden; overflow-y: auto; -webkit-overflow-scrolling: touch; overscroll-behavior: contain;
  /* The bottom clears App.vue's floating Screens button. */
  padding: calc(var(--safe-t) + 14px) 16px calc(var(--safe-b) + 96px);
  scrollbar-width: none;
}
.s3-scroll::-webkit-scrollbar { display: none; }
.s3-stack { display: flex; flex-direction: column; gap: 14px; max-width: 560px; margin: 0 auto; }
.s3-fade { animation: s3Fade .25s ease both; }
.s3-push { gap: 8px; animation: s3Push .26s ease both; }

.s3-back {
  align-self: flex-start; display: flex; align-items: center; gap: 2px; height: 36px; padding: 0 12px 0 6px;
  border-radius: 999px; background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.1);
  -webkit-backdrop-filter: blur(18px); backdrop-filter: blur(18px);
  font-size: 14px; font-weight: 500; color: #E4E4EA; transition: transform .15s;
}
.s3-back:active { transform: scale(.97); }
.s3-h1 { margin: 0; padding: 6px 4px 2px; font-size: 34px; font-weight: 700; letter-spacing: -.025em; }
.s3-h1-page { padding: 10px 4px 4px; font-size: 32px; line-height: 1.1; text-wrap: balance; }
.s3-lede { margin: 0; padding: 0 4px 4px; font-size: 15px; line-height: 21px; color: #A3A3AE; text-wrap: pretty; }

/* ── Glass card ── */
.s3-card {
  border-radius: 20px; overflow: hidden;
  background: var(--glass);
  -webkit-backdrop-filter: blur(24px) saturate(140%); backdrop-filter: blur(24px) saturate(140%);
  border: 1px solid rgba(255,255,255,.08);
}

/* ── Expired banner ── */
.s3-expired { display: flex; flex-direction: column; gap: 12px; padding: 16px; border-radius: 20px; background: rgba(255,90,95,.12); border: 1px solid rgba(255,90,95,.35); }
.s3-expired-copy { display: flex; gap: 10px; align-items: flex-start; }
.s3-expired-copy p { margin: 0; font-size: 14px; line-height: 20px; color: #FFD9DA; text-wrap: pretty; }
.s3-pill-light { height: 42px; border-radius: 999px; background: var(--ink); color: #09090C; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 600; }
.s3-pill-light:active { transform: scale(.98); }

/* ── Hero ── */
.s3-hero { border-radius: 22px; border-color: rgba(255,255,255,.09); }
.s3-hero-main { display: flex; align-items: center; gap: 14px; padding: 16px; }
.s3-ring {
  flex: none; width: 68px; height: 68px; border-radius: 50%; padding: 2px;
  background: conic-gradient(from 200deg, var(--a), transparent 60%, var(--a));
  box-shadow: 0 0 24px color-mix(in srgb, var(--a) 45%, transparent);
}
.s3-ring-in { width: 100%; height: 100%; border-radius: 50%; overflow: hidden; background: #141418; }
.s3-ring-in img { width: 100%; height: 100%; object-fit: cover; object-position: 50% 22%; display: block; }
.s3-ring-in img.drift { animation: s3Drift 9s ease-in-out infinite; }
.s3-hero-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.s3-robot { font-size: 24px; font-weight: 700; letter-spacing: -.02em; line-height: 1.1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.s3-plan { font-size: 14px; color: var(--ink-2); }
.s3-lic { display: flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; letter-spacing: .08em; }
.s3-lic i { width: 6px; height: 6px; border-radius: 50%; }
.s3-hero-row { width: 100%; display: flex; align-items: center; gap: 10px; padding: 13px 16px; border-top: 1px solid var(--hair); font-size: 14px; color: #D4D4DB; }
.s3-hero-row.is-warn { background: rgba(255,176,32,.10); color: var(--amber); font-weight: 600; }
.s3-hero-row.is-warn .ms:first-child { color: #FFB020; }
.s3-dot { width: 7px; height: 7px; border-radius: 50%; flex: none; }

/* ── Search ── */
.s3-search { display: flex; align-items: center; gap: 8px; height: 42px; padding: 0 12px; border-radius: 14px; background: rgba(118,118,128,.18); border: 1px solid rgba(255,255,255,.05); color: var(--ink-4); }
.s3-search input { flex: 1; min-width: 0; height: 100%; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; font-size: 16px; -webkit-appearance: none; appearance: none; }
.s3-search input::-webkit-search-cancel-button { display: none; }
.s3-search input::placeholder { color: var(--ink-4); }
.s3-clear { color: var(--ink-4); }
.s3-empty { margin: 0; padding: 28px 12px; text-align: center; font-size: 15px; color: var(--ink-3); text-wrap: pretty; }

/* ── Groups & rows ── */
.s3-group { display: flex; flex-direction: column; gap: 8px; margin-top: 6px; }
.s3-sec { margin-top: 12px; }
.s3-h { padding: 0 4px; font-size: 12px; font-weight: 600; letter-spacing: .12em; color: var(--ink-3); }
.s3-row {
  width: 100%; display: flex; align-items: center; gap: 12px; min-height: 54px; padding: 10px 14px 10px 16px;
  border-top: 1px solid var(--hair); transition: background .15s;
}
.s3-card > .s3-row:first-child, .s3-card > :first-child { border-top: 0; }
button.s3-row:active { background: rgba(255,255,255,.07); }
@media (hover: hover) { button.s3-row:hover { background: rgba(255,255,255,.04); } }
.s3-row.is-dis { opacity: .4; }
.s3-hubrow { height: 54px; min-height: 0; padding: 0 14px 0 16px; gap: 13px; }
.s3-result { min-height: 58px; padding: 9px 16px; }
.s3-icon { font-size: 22px; color: #C9C9D1; }
.s3-label { display: block; font-size: 16px; font-weight: 500; line-height: 21px; }
.s3-sub { display: block; margin-top: 2px; font-size: 12.5px; line-height: 17px; color: var(--ink-4); text-wrap: pretty; }
.s3-val { font-size: 15px; color: var(--ink-3); white-space: nowrap; max-width: 46%; overflow: hidden; text-overflow: ellipsis; }
.s3-hubrow .s3-label { white-space: nowrap; }
.s3-hubrow .s3-val { font-size: 14px; max-width: 44%; }
.s3-val.is-warn { color: var(--amber); }
.s3-row.is-dis .s3-val { color: #55555E; }
.s3-chev { color: var(--ink-5); }
.s3-foot { margin: 0; padding: 0 6px; font-size: 13px; line-height: 18px; color: var(--ink-4); text-wrap: pretty; }
.s3-foot-mono { margin: 0; padding: 18px 0 6px; text-align: center; font-family: 'Geist Mono', monospace; font-size: 11px; color: var(--ink-5); }

.s3-act { gap: 10px; padding: 10px 16px; font-size: 16px; font-weight: 600; color: #E4E4EA; }
.s3-act:active { opacity: .7; }
.s3-act.tone-danger { color: #FF6B6F; }
.s3-act.tone-accent { color: color-mix(in srgb, var(--a) 55%, #FFFFFF); }
.s3-act.tone-muted { color: var(--ink-5); }
.s3-warnrow { gap: 10px; background: rgba(255,176,32,.10); font-size: 15px; font-weight: 600; line-height: 20px; color: var(--amber); }
.s3-warnrow .ms:first-child { color: #FFB020; }
.s3-note { align-items: flex-start; gap: 10px; padding: 14px 16px; font-size: 14px; line-height: 20px; color: #C9C9D1; text-wrap: pretty; }
.s3-note .ms { color: var(--a); }

/* ── Switch (51 × 31, iOS) ── */
.s3-switch { flex: none; width: 51px; height: 31px; border-radius: 999px; padding: 2px; background: rgba(120,120,128,.32); transition: background .2s; }
.s3-switch i { display: block; width: 27px; height: 27px; border-radius: 50%; background: #FFF; box-shadow: 0 2px 6px rgba(0,0,0,.35); transition: transform .22s cubic-bezier(.3,.9,.4,1.15); }
.s3-switch.on { background: var(--a); }
.s3-switch.on i { transform: translateX(20px); }
.s3-snd { flex: none; width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.08); color: var(--ink-5); }
.s3-snd.on { color: #E4E4EA; }
.s3-snd .ms { font-size: 18px; }

/* ── Layout tiles ── */
.s3-layouts { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); gap: 10px; padding: 12px; }
.s3-tile { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 12px 8px 10px; border-radius: 16px; background: rgba(255,255,255,.035); border: 1px solid var(--hair); transition: border-color .2s, transform .15s; }
.s3-tile:active { transform: scale(.97); }
.s3-tile.on { border-color: var(--a); }
.s3-mini { position: relative; width: 56px; height: 104px; border-radius: 12px; overflow: hidden; background: #0E0E12; border: 1px solid rgba(255,255,255,.14); }
.s3-mini i { position: absolute; }
.s3-tile-name { font-size: 13px; font-weight: 600; color: var(--ink-3); }
.s3-tile.on .s3-tile-name { color: #FFF; }

/* ── Toast ── */
.s3-toast {
  position: absolute; left: 10px; right: 10px; top: calc(var(--safe-t) + 8px); z-index: 40; max-width: 540px; margin: 0 auto;
  display: flex; gap: 12px; align-items: flex-start; padding: 13px 14px; border-radius: 24px;
  background: rgba(32,32,38,.82); -webkit-backdrop-filter: blur(30px) saturate(160%); backdrop-filter: blur(30px) saturate(160%);
  border: 1px solid rgba(255,255,255,.12); box-shadow: 0 18px 40px rgba(0,0,0,.55); cursor: pointer;
}
.s3-toast img { flex: none; width: 38px; height: 38px; border-radius: 9px; object-fit: cover; background: #1A1A20; }
.s3-toast-meta { display: flex; justify-content: space-between; gap: 8px; font-size: 12px; font-weight: 600; letter-spacing: .06em; color: var(--ink-3); }
.s3-toast-title { margin-top: 3px; font-size: 15px; font-weight: 600; }
.s3-toast-title.is-warn { color: var(--amber); }
.s3-toast-body { margin-top: 2px; font-size: 14px; line-height: 19px; color: #C9C9D1; text-wrap: pretty; }
.s3-drop-enter-active { animation: s3Drop .34s cubic-bezier(.2,.9,.3,1.2) both; }
.s3-drop-leave-active { transition: opacity .2s, transform .2s; }
.s3-drop-leave-to { opacity: 0; transform: translateY(-12px); }

/* ── Sheet ── */
.s3-sheet-wrap { position: absolute; inset: 0; z-index: 35; }
.s3-scrim { position: absolute; inset: 0; background: rgba(0,0,0,.5); }
.s3-sheet {
  position: absolute; left: 0; right: 0; bottom: 0; max-height: 86dvh; overflow-y: auto; max-width: 600px; margin: 0 auto;
  padding: 10px 16px calc(var(--safe-b) + 24px); border-radius: 30px 30px 0 0;
  background: rgba(26,26,32,.92); -webkit-backdrop-filter: blur(30px) saturate(150%); backdrop-filter: blur(30px) saturate(150%);
  border-top: 1px solid rgba(255,255,255,.12);
}
.s3-sheet-enter-active .s3-scrim, .s3-sheet-leave-active .s3-scrim { transition: opacity .22s ease; }
.s3-sheet-enter-active .s3-sheet { transition: transform .3s cubic-bezier(.2,.8,.2,1); }
.s3-sheet-leave-active .s3-sheet { transition: transform .22s ease-in; }
.s3-sheet-enter-from .s3-scrim, .s3-sheet-leave-to .s3-scrim { opacity: 0; }
.s3-sheet-enter-from .s3-sheet, .s3-sheet-leave-to .s3-sheet { transform: translateY(100%); }
.s3-sheet-enter-active, .s3-sheet-leave-active { transition: opacity .3s; }
.s3-grab { width: 38px; height: 5px; margin: 0 auto 14px; border-radius: 999px; background: rgba(255,255,255,.25); }
.s3-sheet-head { display: flex; align-items: center; justify-content: space-between; padding: 0 4px 12px; }
.s3-sheet-head h2 { margin: 0; font-size: 20px; font-weight: 700; letter-spacing: -.01em; }
.s3-done { font-size: 15px; font-weight: 600; color: color-mix(in srgb, var(--a) 60%, #FFFFFF); }
.s3-opts { border-radius: 18px; overflow: hidden; background: rgba(255,255,255,.05); border: 1px solid var(--hair); }
.s3-opt { width: 100%; display: flex; align-items: center; justify-content: space-between; height: 52px; padding: 0 16px; border-top: 1px solid var(--hair); font-size: 16px; }
.s3-opt:first-child { border-top: 0; }
.s3-opt:active { background: rgba(255,255,255,.06); }
.s3-opt .ms { font-variation-settings: 'wght' 400; color: color-mix(in srgb, var(--a) 60%, #FFFFFF); opacity: 0; }
.s3-opt.on { font-weight: 600; }
.s3-opt.on .ms { opacity: 1; }
.s3-sheet-note { margin: 0; padding: 10px 6px 0; font-size: 13px; line-height: 18px; color: var(--ink-4); }
.s3-sheet-copy { margin: 0; padding: 12px 6px 0; font-size: 14px; line-height: 19px; color: #C9C9D1; text-wrap: pretty; }

.s3-quiet { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); gap: 10px; }
.s3-quiet > div { display: flex; flex-direction: column; gap: 8px; }
.s3-times { height: 236px; overflow-y: auto; border-radius: 18px; background: rgba(255,255,255,.05); border: 1px solid var(--hair); padding: 6px; scrollbar-width: none; }
.s3-times::-webkit-scrollbar { display: none; }
.s3-times button { width: 100%; height: 40px; margin-bottom: 4px; border-radius: 12px; text-align: center; font-size: 17px; color: var(--ink-2); }
.s3-times button.on { font-weight: 700; color: #FFF; background: color-mix(in srgb, var(--a) 40%, transparent); }

.s3-how { display: flex; flex-direction: column; gap: 10px; }
.s3-step { display: flex; gap: 12px; align-items: center; padding: 12px 14px; border-radius: 16px; background: rgba(255,255,255,.05); border: 1px solid var(--hair); font-size: 15px; line-height: 20px; }
.s3-step b { flex: none; width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; background: color-mix(in srgb, var(--a) 22%, transparent); font-size: 14px; }
.s3-cta { margin-top: 6px; height: 50px; border-radius: 999px; background: var(--a); color: #FFF; text-align: center; font-size: 16px; font-weight: 600; transition: transform .15s, opacity .2s; }
.s3-cta:active { transform: scale(.98); }
.s3-cta:disabled { opacity: .45; }
.s3-link { text-align: center; font-size: 14px; font-weight: 500; color: var(--ink-2); padding: 6px; }
.s3-field { display: flex; flex-direction: column; gap: 6px; padding: 12px 14px; border-radius: 16px; background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.1); }
.s3-field input { border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; font-size: 17px; height: 26px; }

/* ── Confirm ── */
.s3-confirm-wrap { position: absolute; inset: 0; z-index: 45; display: flex; align-items: center; justify-content: center; padding: 0 40px; background: rgba(0,0,0,.55); }
.s3-confirm { width: 100%; max-width: 320px; border-radius: 22px; overflow: hidden; background: rgba(36,36,42,.94); -webkit-backdrop-filter: blur(30px); backdrop-filter: blur(30px); border: 1px solid rgba(255,255,255,.1); }
.s3-confirm-copy { padding: 20px 18px 16px; text-align: center; }
.s3-confirm-copy strong { font-size: 17px; }
.s3-confirm-copy p { margin: 6px 0 0; font-size: 14px; line-height: 19px; color: #C9C9D1; text-wrap: pretty; }
.s3-confirm-btns { display: grid; grid-template-columns: 1fr 1fr; border-top: 1px solid rgba(255,255,255,.1); }
.s3-confirm-btns button { height: 48px; text-align: center; font-size: 16px; font-weight: 500; color: #E4E4EA; }
.s3-confirm-btns button + button { border-left: 1px solid rgba(255,255,255,.1); font-weight: 700; }
.s3-confirm-btns button.danger { color: #FF6B6F; }
.s3-fadeT-enter-active, .s3-fadeT-leave-active { transition: opacity .18s ease; }
.s3-fadeT-enter-from, .s3-fadeT-leave-to { opacity: 0; }

@keyframes s3Fade { from { opacity: 0 } to { opacity: 1 } }
@keyframes s3Push { from { opacity: 0; transform: translateX(16px) } to { opacity: 1; transform: none } }
@keyframes s3Drop { from { opacity: 0; transform: translateY(-24px) scale(.96) } to { opacity: 1; transform: none } }
@keyframes s3Drift { 0%, 100% { transform: scale(1.06) translate(0, 0) } 50% { transform: scale(1.18) translate(-3%, 2%) } }

.s3[data-motion='reduced'] *, .s3-layer[data-motion='reduced'] * { animation: none !important; transition-duration: 0s !important; }
@media (prefers-reduced-motion: reduce) {
  .s3 *, .s3-layer * { animation: none !important; transition-duration: 0s !important; }
}
</style>
