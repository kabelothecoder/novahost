<template>
  <div class="h3" :style="{ '--a': accentHex }">
    <img class="h3-wash" :src="art" alt="" aria-hidden="true" />
    <div class="h3-veil"></div>
    <div class="h3-halo"></div>

    <div ref="scroller" class="h3-scroll">
      <div class="h3-stack">
        <button class="h3-back" @click="back">
          <span class="ms">chevron_left</span>{{ screen === 'help' ? 'Back' : 'Help' }}
        </button>
        <h1 class="h3-h1">{{ title }}</h1>

        <!-- ══ Help hub ══════════════════════════════════════════ -->
        <div v-if="screen === 'help'" key="help" class="h3-col h3-push">
          <button class="h3-status" @click="loadOverview">
            <i :style="{ background: statusColor, boxShadow: '0 0 8px ' + statusColor }"></i>{{ statusText }}
          </button>

          <div v-if="offline" class="h3-offline">
            <b>You're offline</b>
            <span>Your robot and the fixes need a connection. Guides and questions still work.</span>
          </div>

          <!-- Ask your robot: voice-turn in "assist" mode -->
          <section class="h3-card h3-ask" :class="{ on: listening }">
            <div class="h3-ask-head">
              <div class="h3-ask-title">Ask your robot</div>
              <div class="h3-muted">It knows the app and what's happening on your account right now.</div>
            </div>
            <input v-model="query" class="h3-input" :disabled="offline" enterkeyhint="send"
                   :placeholder="offline ? 'Needs internet' : 'Type a question'" @keydown.enter="ask()" />
            <div class="h3-ask-row">
              <div role="button" class="h3-mic" :class="{ on: listening, off: offline }"
                   :aria-label="listening ? 'Listening. Release to send.' : 'Hold to talk'"
                   @pointerdown.prevent="micDown" @pointerup="micUp" @pointerleave="micUp" @pointercancel="micUp"
                   @contextmenu.prevent>
                <span class="ms">{{ listening ? 'graphic_eq' : 'mic' }}</span>{{ micLabel }}
              </div>
              <button class="h3-send" :disabled="offline || thinking || !query.trim()" @click="ask()">Ask</button>
            </div>
            <div v-if="!asked && !thinking && !offline" class="h3-chips">
              <button v-for="s in suggestions" :key="s" class="h3-chip" @click="ask(s)">{{ s }}</button>
            </div>
            <div v-if="thinking" class="h3-thinking">Checking your account…</div>
            <div v-else-if="answer" class="h3-answer">
              <div class="h3-mono" :style="{ color: answer.fail ? 'var(--amber)' : 'var(--a)' }">{{ answer.fail ? 'NOT SURE' : 'YOUR ROBOT' }}</div>
              <p>{{ answer.text }}</p>
              <div class="h3-ans-btns">
                <button v-if="answer.go" class="h3-btn-a" @click="router.push(answer.go)">Take me there</button>
                <button v-else class="h3-btn-a" @click="openWhatsApp(answer.q)">Message support</button>
                <button class="h3-btn-g" @click="faqQuery = lastWord(answer.q)">Search questions</button>
              </div>
            </div>
          </section>

          <!-- Fix it: real refusals that nothing placed since has cleared -->
          <div v-if="fixes.length">
            <div class="h3-sec"><span>Fix it</span><small>{{ fixes.length }} need{{ fixes.length === 1 ? 's' : '' }} attention</small></div>
            <div class="h3-col">
              <div v-for="f in shownFixes" :key="f.id" class="h3-card h3-fix" :class="f.sev">
                <div class="h3-fix-head">
                  <i></i><span class="grow">{{ f.title }}</span><small>{{ ago(f.when) }}</small>
                </div>
                <div class="h3-fix-detail">{{ f.detail }}</div>
                <button class="h3-btn-a h3-full" :disabled="offline" @click="router.push(f.go)">{{ offline ? 'Needs internet' : f.btn }}</button>
              </div>
              <button v-if="fixes.length > 2" class="h3-more" @click="fixAll = !fixAll">
                {{ fixAll ? 'Show fewer' : 'Show ' + (fixes.length - 2) + ' more' }}
              </button>
            </div>
          </div>
          <div v-else-if="overview" class="h3-allgood">Nothing needs fixing. Your robot is ready to trade.</div>

          <!-- Guides -->
          <div>
            <div class="h3-sec"><span>Guides</span></div>
            <div class="h3-card">
              <button v-for="(g, i) in GUIDES" :key="g.name" class="h3-row" @click="openGuide(i)">
                <div class="grow h3-guide-row">
                  <div class="h3-guide-top">
                    <span>{{ g.name }}</span>
                    <small :style="{ color: guideDone[i] === g.steps.length ? 'var(--mint)' : '' }">{{ guideProgLabel(i) }}</small>
                  </div>
                  <div class="h3-bar"><div :style="{ width: guidePct(i) }"></div></div>
                </div>
                <span class="ms h3-chev">chevron_right</span>
              </button>
            </div>
          </div>

          <!-- Refused trades -->
          <button class="h3-card h3-why" @click="screen = 'why'">
            <div class="grow">
              <div class="h3-why-t">Why didn't my trade go in?</div>
              <div class="h3-muted">{{ refusedSummary }}</div>
            </div>
            <span class="ms h3-chev">chevron_right</span>
          </button>

          <!-- Questions -->
          <div>
            <div class="h3-sec"><span>Questions</span></div>
            <input v-model="faqQuery" class="h3-input h3-faq-in" placeholder="Search questions" />
            <div class="h3-col">
              <div v-for="g in faqGroups" :key="g.name">
                <div class="h3-faq-group">{{ g.name }}</div>
                <div class="h3-card">
                  <button v-for="it in g.items" :key="it.key" class="h3-faq" @click="faqOpen = faqOpen === it.key ? null : it.key">
                    <div class="h3-faq-q"><span class="grow">{{ it.q }}</span><span class="h3-mark">{{ it.open ? '–' : '+' }}</span></div>
                    <div v-if="it.open" class="h3-faq-a">{{ it.a }}</div>
                  </button>
                </div>
              </div>
              <div v-if="!faqGroups.length" class="h3-empty">
                <span>No questions match that.</span>
                <button @click="askFromFaq">Ask your robot instead</button>
              </div>
            </div>
          </div>

          <!-- People -->
          <div>
            <div class="h3-sec"><span>Talk to a person</span></div>
            <div class="h3-contacts">
              <button class="h3-card h3-contact" @click="openWhatsApp('')">
                <b>WhatsApp</b><small :style="{ color: offline ? 'var(--amber)' : '' }">{{ offline ? 'Needs internet' : 'Fastest reply' }}</small>
              </button>
              <a class="h3-card h3-contact" :href="'mailto:' + SUPPORT_EMAIL + '?subject=NovaHost%20help'">
                <b>Email</b><small>Within 24 hours</small>
              </a>
              <button class="h3-card h3-contact" @click="screen = 'ticket'">
                <b>Ticket</b><small>Sends your details</small>
              </button>
            </div>
          </div>
        </div>

        <!-- ══ Refused trades ═══════════════════════════════════ -->
        <div v-else-if="screen === 'why'" key="why" class="h3-col h3-push">
          <p class="h3-lede">Trades your robot refused in the last 7 days, with the reason your broker or your rules gave.</p>
          <div v-if="!refused.length" class="h3-allgood">{{ overview ? 'No refused trades this week.' : overviewError || 'Checking…' }}</div>
          <div v-for="(r, i) in refused" :key="i" class="h3-card h3-ref">
            <div class="h3-ref-head">
              <span class="h3-sym">{{ r.pair || '—' }}</span>
              <span :style="{ color: /buy/i.test(r.side) ? 'var(--mint)' : 'var(--red)' }">{{ cap(r.side) }}</span>
              <span class="grow"></span><small>{{ when(r.when) }}</small>
            </div>
            <div><small class="h3-muted">Reason</small><div class="h3-ref-r">{{ r.reason }}</div></div>
            <button v-if="r.go" class="h3-btn-a h3-full" @click="router.push(r.go)">{{ r.btn }}</button>
          </div>
        </div>

        <!-- ══ Guide ═══════════════════════════════════════════ -->
        <div v-else-if="screen === 'guide'" key="guide" class="h3-col h3-push">
          <div class="h3-guide-meta">
            <div><span>{{ guide.mins }} · {{ guide.steps.length }} steps</span><span>{{ gDone === guide.steps.length ? 'Complete' : gDone + ' of ' + guide.steps.length + ' done' }}</span></div>
            <div class="h3-bar"><div :style="{ width: guidePct(guideIdx) }"></div></div>
          </div>
          <div class="h3-card">
            <button v-for="(st, i) in guide.steps" :key="i" class="h3-step" @click="toggleStep(i)">
              <span class="h3-badge" :class="{ done: i < gDone, cur: i === gDone }">{{ i < gDone ? '✓' : i + 1 }}</span>
              <span class="grow">
                <b :style="{ color: i < gDone ? 'var(--ink-3)' : '' }">{{ st[0] }}</b>
                <small>{{ st[1] }}</small>
              </span>
            </button>
          </div>
          <button v-if="guide.go" class="h3-btn-g h3-full" @click="router.push(guide.go)">Open {{ guide.goName }}</button>
          <button class="h3-btn-a h3-full h3-big" @click="guideNext">
            {{ gDone === guide.steps.length ? 'Back to Help' : gDone === 0 ? 'Start' : 'Mark step ' + (gDone + 1) + ' done' }}
          </button>
        </div>

        <!-- ══ Ticket ══════════════════════════════════════════ -->
        <div v-else-if="screen === 'ticket'" key="ticket" class="h3-col h3-push h3-gap">
          <div>
            <div class="h3-sec"><span>What's it about?</span></div>
            <div class="h3-chips">
              <button v-for="t in TOPICS" :key="t" class="h3-chip" :class="{ on: topic === t }" @click="topic = t">{{ t }}</button>
            </div>
          </div>
          <textarea v-model="ticketMsg" class="h3-input h3-area" placeholder="Tell us what happened"></textarea>
          <div>
            <div class="h3-sec"><span>Attached for you</span></div>
            <p class="h3-lede h3-small">This is everything we'll see. Switch off anything you'd rather not send.</p>
            <div class="h3-card">
              <div v-for="a in attachList" :key="a.k" class="h3-att" :class="{ off: !attach[a.k] }">
                <div class="grow">
                  <b>{{ a.name }}</b>
                  <code v-for="(ln, i) in a.lines" :key="i">{{ ln }}</code>
                </div>
                <button class="h3-toggle" :class="{ on: attach[a.k] }" role="switch" :aria-checked="attach[a.k]" :aria-label="'Send ' + a.name"
                        @click="attach[a.k] = !attach[a.k]"><i></i></button>
              </div>
            </div>
          </div>
          <div v-if="ticketError" class="h3-err">{{ ticketError }}</div>
          <button class="h3-btn-a h3-full h3-big" :disabled="sending || offline || ticketMsg.trim().length < 5" @click="sendTicket">
            {{ offline ? 'Needs internet' : sending ? 'Sending…' : 'Send ticket' }}
          </button>
          <p class="h3-lede h3-small h3-center">We reply by email.</p>
        </div>

        <!-- ══ Sent ════════════════════════════════════════════ -->
        <div v-else-if="screen === 'sent'" key="sent" class="h3-col h3-sent">
          <div class="h3-sent-orb"><span class="ms">check</span></div>
          <div class="h3-sent-t">Ticket sent</div>
          <div class="h3-mono">Ticket #{{ sentRef }}</div>
          <p>We'll reply by email, usually within a day. Mention this number if you message us on WhatsApp.</p>
          <div class="h3-card h3-sent-box">
            <small>Sent with your message</small>
            <span>{{ sentParts.length ? 'Your message, ' + sentParts.join(', ') : 'Your message only' }}</span>
          </div>
          <button class="h3-btn-g h3-full h3-big" @click="screen = 'help'">Back to Help</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { supabase } from '../utils/supabaseClient.ts'
import { secStorage } from '../utils/secStorage.js'
import { theme } from '../stores/theme.js'
import { activeEa, artFor } from '../stores/robot.js'
import { useVoiceTurn } from '../composables/useVoiceTurn.js'

const router = useRouter()
const accentHex = computed(() => theme.accent.hex)
const art = computed(() => artFor(activeEa.value))
const scroller = ref(null)

const SUPPORT_EMAIL = 'kabelongcwatywa@icloud.com'
const WHATSAPP = '27662552193'
const TOPICS = ['Trades', 'Broker', 'Payment', 'Something else']

/* ── Screens ─────────────────────────────────────────────────────── */
const screen = ref('help') // help | why | guide | ticket | sent
const TITLES = { help: 'Help & support', why: 'Refused trades', ticket: 'New ticket', sent: 'Ticket sent' }
const title = computed(() => (screen.value === 'guide' ? guide.value.name : TITLES[screen.value]))
watch(screen, () => nextTick(() => scroller.value?.scrollTo({ top: 0 })))
function back() {
  if (screen.value === 'help') router.push('/settings')
  else screen.value = 'help'
}

/* ── Connectivity ────────────────────────────────────────────────── */
const offline = ref(typeof navigator !== 'undefined' && navigator.onLine === false)
const setOnline = () => { offline.value = false; loadOverview() }
const setOffline = () => { offline.value = true }
onMounted(() => { window.addEventListener('online', setOnline); window.addEventListener('offline', setOffline) })
onUnmounted(() => { window.removeEventListener('online', setOnline); window.removeEventListener('offline', setOffline) })

/* ── Account overview: support-desk reads signal_executions ─────── */
const overview = ref(null)
const overviewError = ref('')
const checkedAt = ref(null)
const fixAll = ref(false)

async function loadOverview() {
  if (offline.value) return
  overviewError.value = ''
  try {
    const { data, error } = await supabase.functions.invoke('support-desk', {
      body: { action: 'overview', license_key: secStorage.getItem('LICENSE_KEY') || '' },
    })
    if (error || !data?.success) throw new Error(data?.error || 'unavailable')
    overview.value = data
    checkedAt.value = new Date()
  } catch {
    overviewError.value = "Couldn't check your account"
  }
}
onMounted(loadOverview)

const fixes = computed(() => overview.value?.fixes || [])
const shownFixes = computed(() => (fixAll.value ? fixes.value : fixes.value.slice(0, 2)))
const refused = computed(() => overview.value?.refused || [])
const refusedSummary = computed(() => {
  if (!overview.value) return overviewError.value || 'Checking…'
  const n = overview.value.refused_count
  if (!n) return 'None refused this week'
  return `${n} refused this week · last ${ago(refused.value[0]?.when)}`
})
const statusColor = computed(() => (offline.value || overviewError.value ? '#F0B95C' : '#4FE0B4'))
const statusText = computed(() => {
  if (offline.value) return 'Offline'
  if (overviewError.value) return overviewError.value + ' · tap to retry'
  if (!checkedAt.value) return 'Checking your account…'
  return 'Account checked · ' + checkedAt.value.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
})

/* ── Ask your robot ──────────────────────────────────────────────── */
const vt = useVoiceTurn('assist', { screen: 'support' })
const ROUTES = new Set(['/home', '/accounts', '/symbols', '/pairs', '/settings', '/scanner', '/trades', '/terminal', '/payment'])
const query = ref('')
const asked = ref(false)
const answer = ref(null)
const listening = computed(() => vt.state.value === 'listen')
const thinking = computed(() => vt.state.value === 'think')
const micLabel = computed(() => (offline.value ? 'Offline' : listening.value ? 'Listening… release' : 'Hold to talk'))
const suggestions = ["Why didn't my trade go in?", 'My broker disconnected', 'How do I set lot sizes?']

watch(() => vt.caption.value, (text) => {
  if (!text || vt.state.value !== 'answer') return
  const q = vt.transcript.value || query.value
  const go = vt.suggest.value && ROUTES.has(vt.suggest.value) ? vt.suggest.value : null
  answer.value = { q, text, go, fail: false }
  if (vt.transcript.value) query.value = vt.transcript.value
})
// A turn that ended without any answer is a "not sure", with a person as the way out.
watch(() => vt.state.value, (s, prev) => {
  if (prev === 'think' && s === 'idle' && !answer.value) {
    answer.value = { q: query.value, text: "I can't answer that one yet. A person can, on WhatsApp.", go: null, fail: true }
  }
})

function ask(text) {
  const t = String(text ?? query.value).trim()
  if (!t || offline.value || thinking.value || listening.value) return
  query.value = t
  asked.value = true
  answer.value = null
  vt.send(t)
}
function micDown() { if (!offline.value && !thinking.value) { asked.value = true; answer.value = null; vt.start() } }
function micUp() { if (listening.value) vt.stop() }
function askFromFaq() { const q = faqQuery.value; faqQuery.value = ''; ask(q) }
const lastWord = (s) => String(s || '').trim().split(/\s+/).pop()?.replace(/[?.!]/g, '') || ''

function openWhatsApp(text) {
  const msg = text ? `Hi NovaHost, ${text}` : 'Hi NovaHost, I need help with '
  window.open(`https://wa.me/${WHATSAPP}?text=${encodeURIComponent(msg)}`, '_blank')
}

/* ── Guides: progress is per device ──────────────────────────────── */
const GUIDES = [
  { name: 'Connect your broker', mins: '3 min', go: '/accounts', goName: 'Accounts', steps: [
    ['Open Accounts', 'From Home or Settings.'],
    ['Enter your MetaTrader login', 'Account number, password and server, exactly as MetaTrader shows them.'],
    ['Wait for Connected', 'We only check the login. No trade is placed.'],
    ['Turn your robot on', 'Home › the main button.'] ] },
  { name: 'Set up symbols & lot sizes', mins: '3 min', go: '/pairs', goName: 'Symbols', steps: [
    ['Open Symbols', 'Your robot can only trade what is set up here.'],
    ['Pick your symbols', 'Choose from what your robot is allowed to trade.'],
    ['Set a lot for each', '"No size configured" means a symbol has no lot yet, so trades on it are refused.'],
    ['Set the most trades at once', 'Your robot stops opening new ones on that symbol at the limit.'] ] },
  { name: 'Use the Chart Scanner', mins: '2 min', go: '/scanner', goName: 'Scanner', steps: [
    ['Open the scanner', 'Home › Scan a chart.'],
    ['Add a chart screenshot', 'From MetaTrader or any charting app.'],
    ['Read the result', 'Direction, entry, stop, targets and how sure it is.'],
    ['Execute or save it', 'Nothing is placed until you tap Execute.'] ] },
  { name: 'Talk to your robot', mins: '1 min', go: '/home', goName: 'Home', steps: [
    ['Hold the mic on Home', 'Ask about your account, trades or the market.'],
    ['Confirm any trade idea', 'Nothing is placed until you tap Confirm. Ideas expire after 30 seconds.'] ] },
  { name: 'Turn on notifications', mins: '1 min', go: '/settings', goName: 'Settings', steps: [
    ['Add NovaHost to your Home Screen', 'In Safari, Share › Add to Home Screen.'],
    ['Open it from the Home Screen', 'iPhone only sends notifications to installed apps.'],
    ['Switch notifications on', 'Settings › Notifications.'] ] },
  { name: 'Move to a new phone', mins: '2 min', steps: [
    ['Keep your licence key', 'It is in the email you got when you paid.'],
    ['Ask us to move it', 'A licence works on one phone at a time. Send a ticket or message us on WhatsApp.'],
    ['Enter your key on the new phone', 'Then reconnect your broker.'] ] },
]
const GUIDE_KEY = 'nh.help.guides'
const guideDone = ref((() => {
  try { const v = JSON.parse(localStorage.getItem(GUIDE_KEY)); if (Array.isArray(v)) return GUIDES.map((_, i) => Number(v[i]) || 0) } catch { /* private mode */ }
  return GUIDES.map(() => 0)
})())
watch(guideDone, (v) => { try { localStorage.setItem(GUIDE_KEY, JSON.stringify(v)) } catch { /* private mode */ } }, { deep: true })

const guideIdx = ref(0)
const guide = computed(() => GUIDES[guideIdx.value])
const gDone = computed(() => guideDone.value[guideIdx.value] || 0)
const guidePct = (i) => Math.round(((guideDone.value[i] || 0) / GUIDES[i].steps.length) * 100) + '%'
function guideProgLabel(i) {
  const d = guideDone.value[i] || 0, n = GUIDES[i].steps.length
  return d === n ? 'Done' : d ? `${d} of ${n}` : GUIDES[i].mins
}
function openGuide(i) { guideIdx.value = i; screen.value = 'guide' }
function toggleStep(i) { guideDone.value[guideIdx.value] = i < gDone.value ? i : i + 1 }
function guideNext() {
  if (gDone.value === guide.value.steps.length) { screen.value = 'help'; return }
  guideDone.value[guideIdx.value] = gDone.value + 1
}

/* ── Questions ───────────────────────────────────────────────────── */
const FAQ = [
  { name: 'Getting started', items: [
    ['Do I need to keep the app open?', 'No. Once your broker is connected your robot runs on our servers. You can close the app or switch off your phone.'],
    ['Which brokers work?', 'Any broker that offers MetaTrader 4 or 5, as long as its server can be reached from the cloud.'] ] },
  { name: 'Trading', items: [
    ['Why did my robot skip a trade?', 'Usually one of your rules stopped it: no lot size set for the symbol, not enough margin, or the trade limit was reached. "Why didn\'t my trade go in?" shows the exact reason.'],
    ['"Autotrading disabled by server"', 'This usually means your broker spells the symbol differently, such as XAUUSD.ifx. Check the symbol on the Symbols screen.'],
    ['Does stopping the robot close my trades?', 'No. Stopping pauses new trades. Trades already open stay open at your broker.'] ] },
  { name: 'Payments', items: [
    ['Where is my licence key?', 'In the email we sent after you paid.'],
    ['What happens when my licence ends?', 'Open trades stay open. Your robot stops opening new ones until you renew.'] ] },
  { name: 'Phones', items: [
    ['Can I use two phones?', 'One phone at a time per licence. To move to a new phone, send a ticket or message us on WhatsApp.'],
    ["I'm not getting notifications", 'On iPhone, add NovaHost to your Home Screen first, open it from there, then switch notifications on in Settings.'] ] },
]
const faqQuery = ref('')
const faqOpen = ref(null)
const faqGroups = computed(() => {
  const fq = faqQuery.value.trim().toLowerCase()
  return FAQ.map((g, gi) => ({
    name: g.name,
    items: g.items.map(([q, a], i) => ({ q, a, key: gi + '-' + i }))
      .filter((it) => !fq || (it.q + ' ' + it.a).toLowerCase().includes(fq))
      .map((it) => ({ ...it, open: !!fq || faqOpen.value === it.key })),
  })).filter((g) => g.items.length)
})

/* ── Ticket ──────────────────────────────────────────────────────── */
const topic = ref('Trades')
const ticketMsg = ref('')
const attach = reactive({ device: true, licence: true, errors: true })
const sending = ref(false)
const ticketError = ref('')
const sentRef = ref('')
const sentParts = ref([])

const deviceLine = (() => {
  const ua = navigator.userAgent
  const ios = /OS (\d+)[._](\d+)/.exec(ua)
  const standalone = window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone
  return [/iPad/.test(ua) ? 'iPad' : /iPhone/.test(ua) ? 'iPhone' : 'Browser', ios ? `iOS ${ios[1]}.${ios[2]}` : '', standalone ? 'installed' : 'Safari tab'].filter(Boolean).join(' · ')
})()
const maskedKey = computed(() => {
  const k = secStorage.getItem('LICENSE_KEY') || ''
  return k ? k.slice(0, 5) + '····' + k.slice(-4) : 'No key on this phone'
})
const errorLines = computed(() => refused.value.slice(0, 3).map((r) => `${when(r.when)}  ${r.pair || ''} ${r.reason}`.trim()))
const attachList = computed(() => [
  { k: 'device', name: 'Device', lines: [deviceLine] },
  { k: 'licence', name: 'Licence', lines: [maskedKey.value + (overview.value?.licence ? ' · ' + overview.value.licence.status : '')] },
  { k: 'errors', name: 'Last errors', lines: errorLines.value.length ? errorLines.value : ['None this week'] },
])

async function sendTicket() {
  if (sending.value) return
  sending.value = true
  ticketError.value = ''
  try {
    const { data, error } = await supabase.functions.invoke('support-desk', {
      body: {
        action: 'ticket',
        license_key: secStorage.getItem('LICENSE_KEY') || '',
        topic: topic.value,
        message: ticketMsg.value,
        attachments: {
          device: attach.device ? deviceLine : undefined,
          licence: attach.licence,
          errors: attach.errors ? errorLines.value : undefined,
        },
      },
    })
    let res = data
    if (error && !res && error.context?.json) { try { res = await error.context.json() } catch { /* no body */ } }
    if (!res?.success) throw new Error(res?.error || 'Could not send your ticket. Check your connection and try again.')
    sentRef.value = res.ref
    sentParts.value = attachList.value.filter((a) => attach[a.k]).map((a) => a.name.toLowerCase())
    ticketMsg.value = ''
    screen.value = 'sent'
  } catch (e) {
    ticketError.value = e.message
  } finally {
    sending.value = false
  }
}

/* ── Formatting ──────────────────────────────────────────────────── */
const cap = (s) => (s ? s.charAt(0).toUpperCase() + s.slice(1).toLowerCase() : '')
function when(iso) {
  if (!iso) return ''
  const d = new Date(iso), now = new Date()
  const t = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  if (d.toDateString() === now.toDateString()) return 'Today ' + t
  if (d.toDateString() === new Date(now - 864e5).toDateString()) return 'Yesterday ' + t
  return d.toLocaleDateString([], { weekday: 'short' }) + ' ' + t
}
function ago(iso) {
  if (!iso) return ''
  const m = Math.round((Date.now() - new Date(iso)) / 60000)
  if (m < 1) return 'now'
  if (m < 60) return m + 'm ago'
  if (m < 1440) return Math.round(m / 60) + 'h ago'
  return when(iso)
}
</script>

<style scoped>
.h3 {
  --ink: #F4F4F7; --ink-2: #B4B4BE; --ink-3: #9C9CA8; --ink-4: #8E8E99; --ink-5: #6E6E78;
  --glass: rgba(22, 22, 28, .62);
  --hair: rgba(255, 255, 255, .07);
  --amber: #F0B95C; --mint: #4FE0B4; --red: #FF8FA3;
  --safe-t: env(safe-area-inset-top, 0px);
  --safe-b: env(safe-area-inset-bottom, 0px);
  position: relative; height: 100dvh; overflow: hidden; overflow: clip; isolation: isolate;
  background: #09090C; color: var(--ink);
  font-family: Geist, 'SF Pro Text', system-ui, sans-serif;
  font-variant-numeric: tabular-nums; -webkit-font-smoothing: antialiased;
}
.h3 .ms { font-family: 'Material Symbols Outlined'; font-variation-settings: 'wght' 300; font-size: 20px; line-height: 1; font-weight: normal; font-style: normal; letter-spacing: normal; white-space: nowrap; flex: none; }
.h3 :where(button) { font: inherit; color: inherit; background: none; border: 0; padding: 0; text-align: left; cursor: pointer; -webkit-tap-highlight-color: transparent; }
.h3 :where(button:disabled) { cursor: default; opacity: .45; }
.h3 :where(a) { color: inherit; text-decoration: none; }
.h3 .grow { flex: 1; min-width: 0; }

.h3-wash { position: absolute; left: -80px; top: -80px; width: calc(100% + 160px); height: 620px; object-fit: cover; filter: blur(44px) brightness(.45) saturate(.9); opacity: .55; pointer-events: none; }
.h3-veil { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(9,9,12,.55) 0%, rgba(9,9,12,.86) 40%, #09090C 75%); pointer-events: none; }
.h3-halo { position: absolute; inset: 0; background: radial-gradient(70% 28% at 50% 12%, color-mix(in srgb, var(--a) 20%, transparent), transparent 70%); pointer-events: none; }

.h3-scroll { position: absolute; inset: 0; z-index: 2; overflow-x: hidden; overflow-y: auto; -webkit-overflow-scrolling: touch; overscroll-behavior: contain; padding: calc(var(--safe-t) + 14px) 16px calc(var(--safe-b) + 96px); scrollbar-width: none; }
.h3-scroll::-webkit-scrollbar { display: none; }
.h3-stack { display: flex; flex-direction: column; gap: 14px; max-width: 560px; margin: 0 auto; }
.h3-col { display: flex; flex-direction: column; gap: 10px; }
.h3-col.h3-push { gap: 22px; }
.h3-gap { gap: 20px; }
.h3-push { animation: h3Push .3s cubic-bezier(.2,0,0,1) both; }
@keyframes h3Push { from { opacity: 0; transform: translateX(18px) } to { opacity: 1; transform: none } }
@keyframes h3Fade { from { opacity: 0 } to { opacity: 1 } }
@media (prefers-reduced-motion: reduce) { .h3-push { animation: none; } }

.h3-back { align-self: flex-start; display: flex; align-items: center; gap: 2px; height: 36px; padding: 0 12px 0 6px; border-radius: 999px; background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.1); -webkit-backdrop-filter: blur(18px); backdrop-filter: blur(18px); font-size: 14px; font-weight: 500; color: #E4E4EA; }
.h3-back:active { transform: scale(.97); }
.h3-h1 { margin: 0; padding: 6px 4px 2px; font-size: 32px; font-weight: 700; letter-spacing: -.025em; line-height: 1.1; text-wrap: balance; }
.h3-lede { margin: 0; padding: 0 4px; font-size: 14px; line-height: 1.5; color: #98A2B3; text-wrap: pretty; }
.h3-small { font-size: 12.5px; padding-bottom: 10px; }
.h3-center { text-align: center; }
.h3-muted { font-size: 13px; line-height: 1.5; color: var(--ink-4); }
.h3-mono { font-family: 'Geist Mono', 'JetBrains Mono', monospace; font-size: 10.5px; letter-spacing: .12em; }

.h3-card { border-radius: 20px; overflow: hidden; background: var(--glass); -webkit-backdrop-filter: blur(24px) saturate(140%); backdrop-filter: blur(24px) saturate(140%); border: 1px solid rgba(255,255,255,.08); }
.h3-sec { display: flex; align-items: baseline; justify-content: space-between; padding: 0 6px 10px; font-size: 14px; font-weight: 600; color: #A7B0C0; }
.h3-sec small { font-size: 12.5px; font-weight: 400; color: var(--ink-4); }

.h3-status { align-self: center; display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: #A7B0C0; margin-top: -4px; }
.h3-status i { width: 7px; height: 7px; border-radius: 999px; }
.h3-offline { display: flex; flex-direction: column; gap: 4px; padding: 14px 16px; border-radius: 18px; background: rgba(240,185,92,.1); border: 1px solid rgba(240,185,92,.35); }
.h3-offline b { font-size: 14px; color: #F7D9A3; }
.h3-offline span { font-size: 13px; line-height: 1.5; color: #D8C7A6; }

/* Ask */
.h3-ask { border-radius: 24px; padding: 18px; display: flex; flex-direction: column; gap: 14px; border-color: rgba(255,255,255,.12); transition: border-color .2s; }
.h3-ask.on { border-color: color-mix(in srgb, var(--a) 60%, transparent); }
.h3-ask-head { display: flex; flex-direction: column; gap: 4px; }
.h3-ask-title { font-size: 21px; font-weight: 600; letter-spacing: -.3px; }
.h3-input { width: 100%; height: 54px; border-radius: 16px; background: rgba(0,0,0,.28); border: 1px solid rgba(255,255,255,.1); padding: 0 16px; color: #F2F4F7; font: inherit; font-size: 16px; outline: none; }
.h3-input:focus { border-color: color-mix(in srgb, var(--a) 55%, transparent); }
.h3-input:disabled { opacity: .5; }
.h3-ask-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.h3-mic, .h3-send { height: 50px; border-radius: 999px; display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 14px; font-weight: 600; }
.h3-mic { background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.14); color: #E9EAEC; user-select: none; -webkit-user-select: none; -webkit-touch-callout: none; touch-action: none; cursor: pointer; transition: all .16s; }
.h3-mic.on { background: var(--a); border-color: var(--a); color: #0A0C10; box-shadow: 0 0 30px -6px var(--a); }
.h3-mic.off { color: var(--ink-5); pointer-events: none; }
.h3 .h3-send { justify-content: center; background: var(--a); color: #0A0C10; }
.h3-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.h3 .h3-chip { padding: 8px 12px; border-radius: 999px; background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.1); font-size: 12.5px; color: #C6CDD8; }
.h3 .h3-chip.on { background: color-mix(in srgb, var(--a) 14%, transparent); border-color: color-mix(in srgb, var(--a) 60%, transparent); color: #F4F6FA; }
.h3-thinking { padding-top: 12px; border-top: 1px solid var(--hair); font-size: 13.5px; color: #A7B0C0; animation: h3Fade .6s ease infinite alternate; }
.h3-answer { padding-top: 14px; border-top: 1px solid var(--hair); display: flex; flex-direction: column; gap: 12px; animation: h3Fade .26s ease both; }
.h3-answer p { margin: 0; font-size: 15px; line-height: 1.55; text-wrap: pretty; }
.h3-ans-btns { display: flex; gap: 10px; flex-wrap: wrap; }
.h3 .h3-btn-a, .h3 .h3-btn-g { display: flex; align-items: center; justify-content: center; padding: 11px 16px; border-radius: 999px; font-size: 14px; font-weight: 600; }
.h3 .h3-btn-a { background: var(--a); color: #0A0C10; }
.h3 .h3-btn-g { background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.14); color: #E9EAEC; font-weight: 500; }
.h3-full { width: 100%; height: 44px; }
.h3-big { height: 52px; font-size: 15px !important; }
.h3-btn-a:active, .h3-btn-g:active, .h3-send:active { transform: scale(.98); }

/* Fix it */
.h3-fix { padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.h3-fix.red { border-color: rgba(255,107,132,.32); }
.h3-fix.amber { border-color: rgba(240,185,92,.3); }
.h3-fix-head { display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 600; }
.h3-fix-head i { width: 8px; height: 8px; border-radius: 999px; flex: none; }
.h3-fix.red i { background: var(--red); } .h3-fix.amber i { background: var(--amber); }
.h3-fix-head small { font-size: 12px; font-weight: 400; color: var(--ink-4); }
.h3-fix-detail { font-size: 13px; line-height: 1.5; color: #AEB6C4; }
.h3 .h3-more { align-self: center; padding: 10px; font-size: 13.5px; font-weight: 500; color: var(--a); }
.h3-allgood { border-radius: 20px; padding: 16px; background: rgba(79,224,180,.08); border: 1px solid rgba(79,224,180,.3); font-size: 14px; color: #BFF3E2; text-align: center; }

/* Rows */
.h3 .h3-row { width: 100%; display: flex; align-items: center; gap: 14px; padding: 15px 18px; border-bottom: 1px solid var(--hair); }
.h3-row:last-child { border-bottom: 0; }
.h3-row:active, .h3-faq:active, .h3-step:active { background: rgba(255,255,255,.04); }
.h3-chev { color: var(--ink-5); }
.h3-guide-row { display: flex; flex-direction: column; gap: 8px; }
.h3-guide-top { display: flex; justify-content: space-between; gap: 10px; font-size: 15px; font-weight: 500; }
.h3-guide-top small { font-size: 12px; font-weight: 400; color: var(--ink-4); white-space: nowrap; }
.h3-bar { height: 3px; border-radius: 999px; background: rgba(255,255,255,.1); overflow: hidden; }
.h3-bar div { height: 100%; background: var(--a); border-radius: 999px; transition: width .3s; }

.h3 .h3-why { display: flex; align-items: center; gap: 14px; padding: 18px; }
.h3-why-t { font-size: 16px; font-weight: 600; margin-bottom: 4px; }

/* FAQ */
.h3-faq-in { height: 48px; background: rgba(255,255,255,.055); margin-bottom: 12px; font-size: 15px; }
.h3-faq-group { font-size: 12px; color: var(--ink-4); padding: 0 6px 8px; }
.h3 .h3-faq { width: 100%; display: flex; flex-direction: column; gap: 8px; padding: 15px 18px; border-bottom: 1px solid var(--hair); }
.h3-faq:last-child { border-bottom: 0; }
.h3-faq-q { display: flex; gap: 12px; align-items: flex-start; font-size: 14.5px; font-weight: 500; line-height: 1.4; }
.h3-mark { color: var(--ink-5); font-size: 16px; }
.h3-faq-a { font-size: 13.5px; line-height: 1.6; color: #AEB6C4; text-wrap: pretty; }
.h3-empty { border-radius: 20px; padding: 18px; background: rgba(255,255,255,.04); border: 1px dashed rgba(255,255,255,.14); display: flex; flex-direction: column; gap: 10px; align-items: center; text-align: center; font-size: 14px; color: #C6CDD8; }
.h3-empty button { font-size: 13.5px; font-weight: 600; color: var(--a); }

/* Contacts */
.h3-contacts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.h3 .h3-contact { padding: 14px 12px; display: flex; flex-direction: column; gap: 6px; min-height: 92px; }
.h3-contact b { font-size: 14px; font-weight: 600; }
.h3-contact small { font-size: 12px; line-height: 1.4; color: var(--ink-4); }
.h3-contact:active { transform: scale(.98); }

/* Refused */
.h3-ref { padding: 16px; display: flex; flex-direction: column; gap: 10px; }
.h3-ref-head { display: flex; align-items: baseline; gap: 10px; font-size: 12.5px; font-weight: 600; }
.h3-ref-head small { font-size: 12px; font-weight: 400; color: var(--ink-4); }
.h3-sym { font-family: 'Geist Mono', 'JetBrains Mono', monospace; font-size: 14px; font-weight: 500; }
.h3-ref-r { font-size: 14px; line-height: 1.5; color: #E9EAEC; margin-top: 3px; }

/* Guide */
.h3-guide-meta { display: flex; flex-direction: column; gap: 8px; padding: 0 6px; }
.h3-guide-meta > div:first-child { display: flex; justify-content: space-between; font-size: 13px; color: #A7B0C0; }
.h3-guide-meta .h3-bar { height: 4px; }
.h3 .h3-step { width: 100%; display: flex; gap: 14px; padding: 16px 18px; border-bottom: 1px solid var(--hair); }
.h3-step:last-child { border-bottom: 0; }
.h3-step b { display: block; font-size: 15px; font-weight: 500; margin-bottom: 4px; }
.h3-step small { display: block; font-size: 13px; line-height: 1.5; color: var(--ink-4); }
.h3-badge { width: 28px; height: 28px; flex: none; border-radius: 999px; display: flex; align-items: center; justify-content: center; font-size: 12.5px; font-weight: 600; background: rgba(255,255,255,.06); color: var(--ink-4); border: 1px solid rgba(255,255,255,.14); }
.h3-badge.cur { color: #F4F6FA; border-color: color-mix(in srgb, var(--a) 60%, transparent); }
.h3-badge.done { background: var(--a); border-color: var(--a); color: #0A0C10; }

/* Ticket */
.h3-area { height: 120px; padding: 14px 16px; line-height: 1.5; resize: none; background: rgba(255,255,255,.055); }
.h3-att { display: flex; gap: 14px; padding: 15px 18px; border-bottom: 1px solid var(--hair); align-items: flex-start; }
.h3-att:last-child { border-bottom: 0; }
.h3-att b { display: block; font-size: 14.5px; font-weight: 500; margin-bottom: 5px; }
.h3-att code { display: block; font-family: 'Geist Mono', 'JetBrains Mono', monospace; font-size: 11px; line-height: 1.5; color: #A7B0C0; overflow-wrap: anywhere; }
.h3-att.off b { color: #79828F; } .h3-att.off code { color: #596170; }
.h3 .h3-toggle { width: 38px; height: 22px; flex: none; border-radius: 999px; background: rgba(255,255,255,.13); padding: 2px; transition: background .2s; }
.h3-toggle i { display: block; width: 18px; height: 18px; border-radius: 999px; background: #F4F6FA; transition: transform .2s; }
.h3 .h3-toggle.on { background: var(--a); }
.h3-toggle.on i { transform: translateX(16px); }
.h3-err { padding: 12px 14px; border-radius: 14px; background: rgba(255,90,95,.12); border: 1px solid rgba(255,90,95,.35); font-size: 13.5px; color: #FFD9DA; }

/* Sent */
.h3-sent { align-items: center; text-align: center; gap: 14px; padding-top: 32px; animation: h3Fade .32s ease both; }
.h3-sent-orb { width: 64px; height: 64px; border-radius: 999px; display: grid; place-items: center; background: rgba(79,224,180,.14); border: 1px solid rgba(79,224,180,.45); box-shadow: 0 0 30px rgba(79,224,180,.25); color: var(--mint); }
.h3-sent-orb .ms { font-size: 30px; }
.h3-sent-t { font-size: 24px; font-weight: 600; letter-spacing: -.4px; }
.h3-sent .h3-mono { font-size: 13px; color: #A7B0C0; letter-spacing: .04em; }
.h3-sent p { margin: 0; font-size: 14.5px; line-height: 1.6; color: #C6CDD8; max-width: 300px; }
.h3-sent-box { width: 100%; padding: 14px 16px; display: flex; flex-direction: column; gap: 6px; text-align: left; margin-top: 8px; }
.h3-sent-box small { font-size: 12px; color: var(--ink-4); }
.h3-sent-box span { font-size: 13.5px; }
</style>
