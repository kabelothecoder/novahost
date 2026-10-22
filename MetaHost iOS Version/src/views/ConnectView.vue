<template>
  <!--
    CONNECT A BROKER — replaces the old Broker Terminal form.

    Design: Claude Design "NovaHost Multiple Accounts", Part 1 (brief in
    design-briefs/2026-10-claude-design-prompts.md, Prompt 8). A numbered strip,
    Broker → Server → Login → Connected, so the user never has to guess a
    server name: they search their broker, tap its server, then log in.

    Text only, no broker logos or lettered badges (the 2026-10-22 rule that
    overrides the brief's logo lines). Every broker and server shown comes from
    MetaCopier's own catalogue through `listServers`; nothing is hardcoded but
    the five popular shortcuts, which are search terms, not a list of support.

    The backend still holds ONE account per licence, so connecting replaces the
    linked account. The banner on step 1 says so before anything is typed.
  -->
  <div
    class="cmd cnv"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <div class="cnv-wash" aria-hidden="true"></div>

    <div class="cnv-inner">
      <!-- ── Chrome ─────────────────────────────────────────────── -->
      <div class="cnv-top">
        <button class="cnv-back" :aria-label="step > 1 && step < 4 ? 'Previous step' : 'Back'" @click="goBack">
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <span class="cc-label">Connect account</span>
        <div class="cnv-plat" role="tablist" aria-label="Platform">
          <button
            v-for="p in PLATFORMS" :key="p"
            role="tab" :aria-selected="platform === p"
            :class="{ on: platform === p }"
            :disabled="step >= 3 && busy"
            @click="pickPlatform(p)"
          >{{ p }}</button>
        </div>
      </div>

      <!-- ── Step strip ─────────────────────────────────────────── -->
      <ol class="cnv-steps" aria-label="Steps">
        <li v-for="(s, i) in STEPS" :key="s" :class="{ done: step > i + 1, cur: step === i + 1 }" :aria-current="step === i + 1 ? 'step' : undefined">
          <span class="cnv-step-bar"></span>
          <span class="cnv-step-name">{{ s }}</span>
        </li>
      </ol>

      <!-- ══ 1 · BROKER ═══════════════════════════════════════════ -->
      <section v-if="step === 1" class="cnv-sec">
        <h1 class="cnv-h">Find your broker</h1>
        <p class="cnv-lead">Search by name. We'll show you its servers next.</p>

        <div v-if="link.linked" class="cnv-note cc-card">
          <span class="cc-dot" :class="link.connecting ? '' : 'cc-dot-live'"></span>
          <span>Linked now: <b class="cc-mono">{{ link.server || 'your broker' }}</b>. Connecting another account replaces it.</span>
        </div>

        <label class="cnv-search">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><line x1="21" y1="21" x2="16.5" y2="16.5" /></svg>
          <input
            v-model="query"
            type="search"
            inputmode="search"
            autocomplete="off"
            autocapitalize="off"
            spellcheck="false"
            :placeholder="`Search ${platform} brokers`"
            aria-label="Search brokers"
          />
          <span v-if="searching" class="cnv-spin" aria-hidden="true"></span>
        </label>

        <template v-if="!query.trim()">
          <span class="cc-label cnv-sub">Popular</span>
          <div class="cnv-chips">
            <button v-for="b in POPULAR" :key="b" class="cnv-chip" @click="query = b">{{ b }}</button>
          </div>
        </template>

        <div v-else-if="searchError" class="cnv-msg is-error">{{ searchError }}</div>

        <div v-else-if="brokers.length" class="cnv-list cc-card">
          <button v-for="b in brokers" :key="b.name" class="cnv-row" @click="pickBroker(b.name)">
            <span class="cnv-row-main">
              <span class="cnv-row-name">{{ b.name }}</span>
              <span class="cnv-row-meta">{{ b.count }} {{ platform }} server{{ b.count === 1 ? '' : 's' }}</span>
            </span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="9 18 15 12 9 6" /></svg>
          </button>
        </div>

        <p v-else-if="!searching && searched" class="cnv-msg">No {{ platform }} broker matches “{{ query.trim() }}”. Check the spelling, or enter your server by hand.</p>

        <button class="cnv-link" @click="goManual">Can't find your broker? Enter your server manually</button>
      </section>

      <!-- ══ 2 · SERVER ═══════════════════════════════════════════ -->
      <section v-else-if="step === 2" class="cnv-sec">
        <h1 class="cnv-h">{{ manual ? 'Type your server' : `Pick your ${broker} server` }}</h1>
        <p class="cnv-lead">{{ manual ? 'Copy it exactly as MetaTrader shows it.' : 'It is on your broker welcome email, and in MetaTrader.' }}</p>

        <template v-if="!manual">
          <div v-if="serversLoading" class="cnv-wait"><span class="cnv-spin"></span>Loading {{ broker }} servers…</div>
          <div v-else-if="serverError" class="cnv-msg is-error">{{ serverError }}</div>
          <template v-else>
            <template v-for="g in serverGroups" :key="g.label">
              <span class="cc-label cnv-sub">{{ g.label }}</span>
              <div class="cnv-list cc-card">
                <button v-for="s in g.items" :key="s" class="cnv-row" @click="pickServer(s)">
                  <span class="cnv-row-main"><span class="cnv-row-name cc-mono">{{ s }}</span></span>
                  <span class="cnv-badge">{{ platform }}</span>
                </button>
              </div>
            </template>
            <p v-if="!serverGroups.length" class="cnv-msg">No {{ platform }} servers listed for {{ broker }}. If your account is {{ otherPlatform }}, switch at the top, or type your server.</p>
          </template>
          <button class="cnv-link" @click="manual = true">Type your server instead</button>
        </template>

        <template v-else>
          <label class="cnv-field">
            <span class="cc-label">Broker server</span>
            <input
              v-model="manualServer"
              type="text"
              autocomplete="off"
              autocapitalize="off"
              spellcheck="false"
              placeholder="Broker-Server name"
              @keyup.enter="useManual"
            />
            <span class="cnv-help">In MetaTrader: Settings → Trade accounts, or the Login to Trade Account screen.</span>
          </label>
          <button class="cnv-cta" :disabled="!manualServer.trim()" @click="useManual">Continue</button>
          <button v-if="broker" class="cnv-link" @click="manual = false">Back to the {{ broker }} list</button>
        </template>
      </section>

      <!-- ══ 3 · LOGIN ════════════════════════════════════════════ -->
      <section v-else-if="step === 3" class="cnv-sec">
        <h1 class="cnv-h">Log in to {{ brokerOf(server) }}</h1>
        <p class="cnv-lead">Your password goes once to your broker to make the link. It is never stored on this phone.</p>

        <button class="cnv-sum" :disabled="busy" @click="step = 2">
          <span class="cnv-badge">{{ platform }}</span>
          <span class="cc-mono cnv-sum-srv">{{ server }}</span>
          <span class="cnv-sum-edit">Change</span>
        </button>

        <form class="cnv-form" @submit.prevent="doConnect">
          <label class="cnv-field">
            <span class="cc-label">Account number</span>
            <input v-model="login" type="text" inputmode="numeric" autocomplete="username" placeholder="Your login" :disabled="busy" />
          </label>
          <label class="cnv-field">
            <span class="cc-label">Password</span>
            <span class="cnv-pwd">
              <input v-model="password" :type="showPwd ? 'text' : 'password'" autocomplete="current-password" placeholder="Trading password" :disabled="busy" />
              <button type="button" class="cnv-eye" :aria-label="showPwd ? 'Hide password' : 'Show password'" @click="showPwd = !showPwd">{{ showPwd ? 'Hide' : 'Show' }}</button>
            </span>
            <span class="cnv-help">Use your trading password, not the investor (read-only) password.</span>
          </label>

          <div v-if="connError" class="cnv-msg is-error" role="alert">
            {{ connError }}
            <div v-if="suggestions.length" class="cnv-chips cnv-sugg">
              <button v-for="s in suggestions" :key="s" type="button" class="cnv-chip cc-mono" @click="pickServer(s)">{{ s }}</button>
            </div>
          </div>

          <div v-if="busy" class="cnv-pipe" aria-live="polite">
            <div class="cnv-pipe-line"><span class="cnv-node">Robot</span><span class="cnv-flow"></span><span class="cnv-node">{{ brokerOf(server) }}</span></div>
            <p>Handing your login to the broker. This can take up to two minutes.</p>
          </div>

          <button class="cnv-cta" type="submit" :disabled="busy || !canConnect">
            {{ busy ? 'Connecting' : 'Connect' }}
          </button>
        </form>
      </section>

      <!-- ══ 4 · CONNECTED ═════════════════════════════════════════ -->
      <section v-else class="cnv-sec cnv-done">
        <div class="cnv-burst" :class="{ wait: result.connecting }" aria-hidden="true">
          <span class="cnv-core"></span>
          <span class="cnv-beam"></span>
          <span class="cnv-dest"></span>
        </div>
        <span class="cnv-pill" :class="result.connecting ? 'amber' : 'green'">{{ result.connecting ? 'Linking' : 'Connected' }}</span>
        <h1 class="cnv-h">{{ result.connecting ? 'Almost there' : `${brokerOf(server)} is connected` }}</h1>
        <p class="cnv-lead">{{ result.message }}</p>
        <div class="cnv-acct cc-card">
          <span class="cnv-badge">{{ platform }}</span>
          <span class="cc-mono">{{ brokerOf(server) }} · ···{{ result.loginTail }}</span>
        </div>
        <button class="cnv-cta" @click="$router.push('/symbols')">Next: set up trading symbols</button>
        <button class="cnv-link" @click="$router.push('/accounts')">Go to My accounts</button>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { secStorage } from '../utils/secStorage.js'
import { theme, resolvedScheme } from '../stores/theme.js'
import { accent } from '../stores/accent.js'
import { session, pushEvent } from '../stores/session.js'
import { linkBroker, listServers, loadLinkState, loadAccount } from '../services/brokerService.js'
import { haptic } from '../composables/useSpeech.js'

const router = useRouter()

const PLATFORMS = ['MT5', 'MT4']
const STEPS = ['Broker', 'Server', 'Login', 'Connected']
/* Search shortcuts, from the design's "popular" row. Tapping one only fills
   the search box: whether the broker exists comes from MetaCopier, not here. */
const POPULAR = ['Exness', 'XM', 'Trade245', 'JustMarkets', 'Razor']

const step = ref(1)
const platform = ref(secStorage.getItem('CONNECT_PLATFORM') || 'MT5')
const otherPlatform = computed(() => platform.value === 'MT5' ? 'MT4' : 'MT5')

/* "Exness-MT5Real8" → "Exness". The broker is the server name's first part,
   which is also how metacopier-connect groups its "did you mean" list. */
function brokerOf(s) {
  return String(s || '').split('-')[0].trim() || String(s || '')
}

/* ── 1 · Broker search ────────────────────────────────────────────── */
const query = ref('')
const searching = ref(false)
const searched = ref(false)
const searchError = ref('')
const results = ref([])
let searchTimer
let searchSeq = 0

const brokers = computed(() => {
  const by = new Map()
  for (const s of results.value) {
    const b = brokerOf(s)
    by.set(b, (by.get(b) || 0) + 1)
  }
  return [...by].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
})

async function runSearch() {
  const q = query.value.trim()
  const seq = ++searchSeq
  if (q.length < 2) { results.value = []; searched.value = false; searchError.value = ''; return }
  searching.value = true
  const res = await listServers({ query: q, platform: platform.value })
  if (seq !== searchSeq) return
  searching.value = false
  searched.value = true
  searchError.value = res.ok ? '' : `${res.message} You can still enter your server by hand.`
  results.value = res.servers
}
watch(query, () => { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 280) })

/* ── 2 · Server ───────────────────────────────────────────────────── */
const broker = ref('')
const manual = ref(false)
const manualServer = ref('')
const servers = ref([])
const serversLoading = ref(false)
const serverError = ref('')

const serverGroups = computed(() => {
  const demo = servers.value.filter(s => /demo|trial|test/i.test(s))
  const real = servers.value.filter(s => !demo.includes(s))
  return [
    { label: 'Real', items: real },
    { label: 'Demo', items: demo },
  ].filter(g => g.items.length)
})

async function loadServers() {
  serversLoading.value = true
  serverError.value = ''
  const res = await listServers({ query: broker.value, platform: platform.value })
  serversLoading.value = false
  if (!res.ok) {
    /* Unknown is not empty: fall through to typing it, which always works. */
    serverError.value = `${res.message} Type your server instead.`
    manual.value = true
    return
  }
  servers.value = res.servers.filter(s => brokerOf(s).toLowerCase() === broker.value.toLowerCase())
}

function pickBroker(name) {
  haptic(15)
  broker.value = name
  manual.value = false
  step.value = 2
  loadServers()
}

function goManual() {
  broker.value = ''
  manual.value = true
  manualServer.value = query.value.includes('-') ? query.value.trim() : ''
  step.value = 2
}

function useManual() {
  const s = manualServer.value.trim()
  if (!s) return
  pickServer(s)
}

/* ── 3 · Login ────────────────────────────────────────────────────── */
const server = ref('')
const login = ref('')
const password = ref('')
const showPwd = ref(false)
const busy = ref(false)
const connError = ref('')
const suggestions = ref([])
const canConnect = computed(() => Boolean(server.value && String(login.value).trim() && password.value))

function pickServer(s) {
  haptic(15)
  server.value = s
  connError.value = ''
  suggestions.value = []
  step.value = 3
}

const result = ref({ connecting: false, message: '', loginTail: '' })

async function doConnect() {
  if (!canConnect.value || busy.value) return
  connError.value = ''
  suggestions.value = []
  busy.value = true
  haptic(20)
  try {
    const res = await linkBroker({
      accountNumber: login.value,
      password: password.value,
      server: server.value,
      platform: platform.value,
    })

    if (!res.ok) {
      connError.value = res.message
      suggestions.value = (res.suggestions || []).slice(0, 4)
      pushEvent('Broker link refused', 'error')
      return
    }

    /* The password has done its one job; it does not outlive the call. */
    const tail = String(login.value).trim().slice(-4)
    password.value = ''
    login.value = ''
    result.value = { connecting: res.connecting, message: res.message, loginTail: tail }
    pushEvent(res.connecting ? 'Broker linking…' : 'Broker link up', res.connecting ? 'info' : 'ok')
    haptic([20, 40, 30])
    step.value = 4
    refreshLink()
  } catch (err) {
    connError.value = err?.message || 'Connection failed. Try again.'
  } finally {
    busy.value = false
  }
}

/* ── Platform ─────────────────────────────────────────────────────── */
function pickPlatform(p) {
  if (platform.value === p) return
  haptic(15)
  platform.value = p
  secStorage.setItem('CONNECT_PLATFORM', p)
  /* Servers differ per platform: refresh whatever list is on screen. */
  if (step.value === 1 && query.value.trim()) runSearch()
  if (step.value === 2 && !manual.value && broker.value) loadServers()
}

/* ── Navigation ───────────────────────────────────────────────────── */
function goBack() {
  if (busy.value) return
  if (step.value === 2 || step.value === 3) { step.value--; return }
  router.push('/accounts')
}

/* ── Link state (what the old Terminal kept in sync) ──────────────── */
const link = ref({ linked: false, connecting: false, server: null })

async function refreshLink() {
  const state = await loadLinkState()
  link.value = state
  const up = state.linked === true && !state.connecting
  accent.brokerConnected = up
  session.brokerConnected = up
  session.brokerServer = state.server || null
  if (up) {
    const acct = await loadAccount()
    if (acct.ok && acct.balance != null) {
      accent.brokerBalance = acct.balance
      /* Currency travels WITH the figure, or a rand balance gets sized as dollars. */
      if (acct.currency) session.brokerCurrency = acct.currency
    }
  }
}

/* Old builds kept broker credentials in storage. Never again; remove them. */
function purgeLegacyCredentials() {
  secStorage.removeItem('MH_TERMINAL_CONFIG')
  secStorage.removeItem('MH_SESSIONS')
}

onMounted(() => {
  purgeLegacyCredentials()
  refreshLink()
})
onUnmounted(() => clearTimeout(searchTimer))
</script>

<style scoped>
/* height, NOT min-height: the app shell clips, this element must scroll. */
.cnv {
  position: relative; width: 100%; height: 100dvh;
  overflow-y: auto; overflow-x: hidden; -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
}
.cnv-wash {
  position: fixed; top: -14%; left: 50%; width: 130%; height: 46%; transform: translateX(-50%); pointer-events: none;
  background: radial-gradient(ellipse at 50% 40%, rgba(var(--cc-accent-rgb), .2) 0%, rgba(var(--cc-accent-rgb), .05) 46%, transparent 72%);
}
.cnv-inner { position: relative; z-index: 1; display: flex; flex-direction: column; padding: calc(var(--cc-safe-top) + 10px) 22px calc(var(--cc-safe-bottom) + 118px); }

/* Chrome */
.cnv-top { display: grid; grid-template-columns: 38px 1fr auto; align-items: center; gap: 10px; height: 44px; }
.cnv-top .cc-label { text-align: center; }
.cnv-back { display: grid; place-items: center; width: 38px; height: 38px; border: none; background: none; border-radius: var(--cc-r-chip); color: var(--cc-ink-3); cursor: pointer; transition: transform 180ms var(--cc-spring); }
.cnv-back:active { transform: scale(.92) }
.cnv-plat { display: flex; padding: 3px; border-radius: 999px; background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.08); }
.cnv-plat button { height: 28px; padding: 0 11px; border: 0; border-radius: 999px; background: none; color: var(--cc-ink-3); font: 600 12px var(--cc-font); cursor: pointer; transition: background .2s ease, color .2s ease; }
.cnv-plat button.on { background: rgba(var(--cc-accent-rgb), .9); color: #0B0B0F; }

/* Step strip */
.cnv-steps { list-style: none; margin: 16px 0 0; padding: 0; display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
.cnv-steps li { display: flex; flex-direction: column; gap: 6px; }
.cnv-step-bar { height: 4px; border-radius: 2px; background: rgba(255,255,255,.1); transition: background .3s ease, box-shadow .3s ease; }
.cnv-steps li.done .cnv-step-bar { background: rgba(var(--cc-accent-rgb), .55); }
.cnv-steps li.cur .cnv-step-bar { background: var(--cc-accent); box-shadow: 0 0 10px rgba(var(--cc-accent-rgb), .6); }
.cnv-step-name { font: 500 11px var(--cc-font); color: var(--cc-ink-3); letter-spacing: .02em; }
.cnv-steps li.cur .cnv-step-name { color: var(--cc-ink); }

/* Sections */
.cnv-sec { display: flex; flex-direction: column; gap: 12px; margin-top: 26px; animation: cnvIn .32s cubic-bezier(.2,.8,.2,1); }
@keyframes cnvIn { from { opacity: 0; transform: translateY(10px) } to { opacity: 1; transform: none } }
.cnv-h { margin: 0; font: 650 26px/1.15 var(--cc-font); letter-spacing: -.02em; color: var(--cc-ink); }
.cnv-lead { margin: 0 0 6px; font: 400 14.5px/1.45 var(--cc-font); color: var(--cc-ink-3); }
.cnv-sub { margin-top: 8px; }

.cnv-note { display: flex; align-items: center; gap: 10px; padding: 12px 14px; font: 400 13px/1.4 var(--cc-font); color: var(--cc-ink-2, var(--cc-ink)); }

.cnv-search { display: flex; align-items: center; gap: 10px; height: 54px; padding: 0 16px; border-radius: 16px; background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.1); color: var(--cc-ink-3); transition: border-color .2s ease, box-shadow .2s ease; }
.cnv-search:focus-within { border-color: rgba(var(--cc-accent-rgb), .7); box-shadow: 0 0 0 3px rgba(var(--cc-accent-rgb), .16); color: var(--cc-accent); }
.cnv-search input { flex: 1; min-width: 0; height: 100%; border: 0; outline: 0; background: none; color: var(--cc-ink); font: 400 16px var(--cc-font); }
.cnv-search input::placeholder { color: var(--cc-ink-3); }

.cnv-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.cnv-chip { height: 36px; padding: 0 14px; border-radius: 999px; border: 1px solid rgba(255,255,255,.12); background: rgba(255,255,255,.05); color: var(--cc-ink); font: 500 13.5px var(--cc-font); cursor: pointer; transition: transform .15s ease, border-color .2s ease; }
.cnv-chip:active { transform: scale(.96); }
.cnv-chip:hover { border-color: rgba(var(--cc-accent-rgb), .55); }

.cnv-list { display: flex; flex-direction: column; padding: 4px 0; overflow: hidden; }
.cnv-row { display: flex; align-items: center; gap: 12px; min-height: 56px; padding: 8px 16px; border: 0; background: none; color: var(--cc-ink); text-align: left; cursor: pointer; transition: background .15s ease; }
.cnv-row + .cnv-row { border-top: 1px solid rgba(255,255,255,.06); }
.cnv-row:active { background: rgba(var(--cc-accent-rgb), .1); }
.cnv-row svg { color: var(--cc-ink-3); flex: none; }
.cnv-row-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cnv-row-name { font: 600 15px var(--cc-font); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cnv-row-meta { font: 400 12.5px var(--cc-font); color: var(--cc-ink-3); }
.cnv-badge { flex: none; height: 22px; padding: 0 8px; display: inline-grid; place-items: center; border-radius: 6px; font: 700 10.5px var(--cc-font); letter-spacing: .06em; color: var(--cc-accent-text); background: rgba(var(--cc-accent-rgb), .12); border: 1px solid rgba(var(--cc-accent-rgb), .3); }

.cnv-link { align-self: center; margin-top: 6px; min-height: 40px; padding: 0 10px; border: 0; background: none; color: var(--cc-accent-text); font: 500 13.5px var(--cc-font); cursor: pointer; }
.cnv-msg { font: 400 13.5px/1.5 var(--cc-font); color: var(--cc-ink-3); }
.cnv-msg.is-error { padding: 12px 14px; border-radius: 14px; color: #FFB4B4; background: rgba(255, 93, 93, .08); border: 1px solid rgba(255, 93, 93, .22); }
.cnv-sugg { margin-top: 10px; }
.cnv-wait { display: flex; align-items: center; gap: 10px; padding: 26px 0; font: 400 14px var(--cc-font); color: var(--cc-ink-3); }
.cnv-spin { width: 15px; height: 15px; flex: none; border-radius: 50%; border: 2px solid rgba(255,255,255,.15); border-top-color: var(--cc-accent); animation: cnvSpin .8s linear infinite; }
@keyframes cnvSpin { to { transform: rotate(360deg) } }

/* Fields */
.cnv-form { display: flex; flex-direction: column; gap: 14px; }
.cnv-field { display: flex; flex-direction: column; gap: 8px; }
.cnv-field input { width: 100%; height: 54px; padding: 0 16px; border-radius: 16px; border: 1px solid rgba(255,255,255,.1); background: rgba(255,255,255,.05); color: var(--cc-ink); font: 400 16px var(--cc-font); outline: 0; transition: border-color .2s ease, box-shadow .2s ease; }
.cnv-field input:focus { border-color: rgba(var(--cc-accent-rgb), .7); box-shadow: 0 0 0 3px rgba(var(--cc-accent-rgb), .16); }
.cnv-field input::placeholder { color: var(--cc-ink-3); }
.cnv-help { font: 400 12.5px/1.45 var(--cc-font); color: var(--cc-ink-3); }
.cnv-pwd { position: relative; display: block; }
.cnv-pwd input { padding-right: 70px; }
.cnv-eye { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); height: 38px; padding: 0 10px; border: 0; background: none; color: var(--cc-accent-text); font: 600 13px var(--cc-font); cursor: pointer; }

.cnv-sum { display: flex; align-items: center; gap: 10px; height: 48px; padding: 0 14px; border-radius: 14px; border: 1px solid rgba(255,255,255,.1); background: rgba(255,255,255,.04); color: var(--cc-ink); cursor: pointer; }
.cnv-sum-srv { flex: 1; min-width: 0; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13.5px; }
.cnv-sum-edit { font: 600 12.5px var(--cc-font); color: var(--cc-accent-text); }

.cnv-cta {
  margin-top: 8px; height: 56px; border-radius: 999px; cursor: pointer;
  border: 1.5px solid var(--cc-accent); background: rgba(var(--cc-accent-rgb), .12); color: var(--cc-accent-text);
  font: 800 14px var(--cc-font); letter-spacing: .12em; text-transform: uppercase; box-shadow: var(--cc-glow-halo);
  transition: transform 200ms var(--cc-spring), opacity .2s ease;
}
.cnv-cta:active { transform: scale(.98) }
.cnv-cta:disabled { opacity: .45; box-shadow: none; cursor: default; }

/* Linking pipeline */
.cnv-pipe { padding: 14px 16px; border-radius: 16px; background: rgba(var(--cc-accent-rgb), .06); border: 1px solid rgba(var(--cc-accent-rgb), .2); }
.cnv-pipe p { margin: 10px 0 0; font: 400 13px/1.45 var(--cc-font); color: var(--cc-ink-3); }
.cnv-pipe-line { display: flex; align-items: center; gap: 10px; }
.cnv-node { flex: none; font: 600 12px var(--cc-font); color: var(--cc-ink); padding: 6px 10px; border-radius: 999px; background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.1); max-width: 40%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cnv-flow { position: relative; flex: 1; height: 2px; border-radius: 1px; background: rgba(var(--cc-accent-rgb), .2); overflow: hidden; }
.cnv-flow::after { content: ''; position: absolute; top: 0; left: -30%; width: 30%; height: 100%; background: linear-gradient(90deg, transparent, var(--cc-accent), transparent); animation: cnvFlow 1.3s ease-in-out infinite; }
@keyframes cnvFlow { to { left: 100% } }

/* Connected */
.cnv-done { align-items: center; text-align: center; }
.cnv-done .cnv-cta { align-self: stretch; }
.cnv-burst { position: relative; width: 220px; height: 90px; margin: 10px 0 4px; }
.cnv-core, .cnv-dest { position: absolute; top: 50%; width: 46px; height: 46px; margin-top: -23px; border-radius: 50%; border: 2px solid var(--cc-accent); background: rgba(var(--cc-accent-rgb), .15); }
.cnv-core { left: 0; box-shadow: 0 0 24px rgba(var(--cc-accent-rgb), .7); animation: cnvFlare 1.4s ease-out 1; }
.cnv-dest { right: 0; border-color: #4CD787; background: rgba(76, 215, 135, .15); box-shadow: 0 0 22px rgba(76, 215, 135, .55); animation: cnvArrive .5s ease-out .7s both; }
.cnv-burst.wait .cnv-dest { border-color: #F2B53A; background: rgba(242, 181, 58, .15); box-shadow: 0 0 22px rgba(242, 181, 58, .5); }
.cnv-beam { position: absolute; left: 46px; right: 46px; top: 50%; height: 2px; margin-top: -1px; background: linear-gradient(90deg, var(--cc-accent), #4CD787); transform-origin: left; animation: cnvBeam .7s ease-out both; }
.cnv-burst.wait .cnv-beam { background: linear-gradient(90deg, var(--cc-accent), #F2B53A); }
@keyframes cnvBeam { from { transform: scaleX(0) } to { transform: scaleX(1) } }
@keyframes cnvArrive { from { transform: scale(.4); opacity: 0 } to { transform: none; opacity: 1 } }
@keyframes cnvFlare { 0% { box-shadow: 0 0 0 rgba(var(--cc-accent-rgb), 0) } 40% { box-shadow: 0 0 44px rgba(var(--cc-accent-rgb), .95) } 100% { box-shadow: 0 0 24px rgba(var(--cc-accent-rgb), .7) } }
.cnv-pill { height: 26px; padding: 0 12px; display: inline-grid; place-items: center; border-radius: 999px; font: 700 11px var(--cc-font); letter-spacing: .08em; text-transform: uppercase; }
.cnv-pill.green { color: #4CD787; background: rgba(76, 215, 135, .12); border: 1px solid rgba(76, 215, 135, .35); }
.cnv-pill.amber { color: #F2B53A; background: rgba(242, 181, 58, .12); border: 1px solid rgba(242, 181, 58, .35); }
.cnv-acct { display: flex; align-items: center; gap: 10px; padding: 12px 16px; font-size: 14px; color: var(--cc-ink); }

@media (prefers-reduced-motion: reduce) {
  .cnv-sec, .cnv-flow::after, .cnv-core, .cnv-dest, .cnv-beam, .cnv-spin { animation: none; }
}
</style>
