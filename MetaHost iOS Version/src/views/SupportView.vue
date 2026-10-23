<template>
  <div class="support-view">
    <canvas ref="bgCanvas" class="support-bg" />

    <header class="support-header">
      <button class="support-back" aria-label="Back" @click="$router.push('/home')">
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <h2 class="screen-title">Help & Support</h2>
      <span class="screen-sub">Ask the assistant, or browse the answers</span>
    </header>

    <div ref="scroller" class="support-scroll">

      <!-- ══ Assistant: the teaching voice (voice-turn, mode "assist") ══ -->
      <section class="assist glass liquid" :class="'is-' + vt.state.value">
        <div class="assist-head">
          <div class="assist-orb" aria-hidden="true">
            <span v-for="i in 3" :key="i" class="orb-ring" :style="{ animationDelay: (i * .35) + 's' }" />
            <span class="ms orb-ic">{{ orbIcon }}</span>
          </div>
          <div>
            <div class="assist-name">NovaHost Assistant</div>
            <div class="assist-state">{{ stateText }}</div>
          </div>
        </div>

        <!-- Conversation -->
        <div v-if="thread.length" class="thread">
          <div v-for="(m, i) in thread" :key="i" class="bubble" :class="m.who">
            {{ m.text }}
            <button v-if="m.go" class="go-btn" @click="router.push(m.go)">Open {{ m.goName }} <span class="ms">arrow_forward</span></button>
          </div>
          <div v-if="vt.state.value === 'think'" class="bubble bot typing"><span /><span /><span /></div>
        </div>
        <p v-else class="assist-intro">
          I can teach you how your robot and the app work, and walk you through fixing any problem. Hold the button and ask, or type below.
        </p>

        <!-- Starters -->
        <div v-if="!thread.length" class="chips">
          <button v-for="c in starters" :key="c" class="chip" @click="ask(c)">{{ c }}</button>
        </div>

        <!-- Hold to talk -->
        <div role="button" class="talk" :class="{ on: vt.state.value === 'listen' }"
             :aria-label="vt.state.value === 'listen' ? 'Listening. Release to send.' : 'Hold to talk to the assistant'"
             @pointerdown.prevent="down" @pointerup="up" @pointerleave="up" @pointercancel="up"
             @contextmenu.prevent>
          <span class="ms">{{ vt.state.value === 'listen' ? 'graphic_eq' : 'mic' }}</span>
          <span>{{ vt.state.value === 'listen' ? 'Listening… release to send' : 'Hold to talk' }}</span>
        </div>

        <form class="type-row" @submit.prevent="ask(typed)">
          <input v-model="typed" class="type-in" placeholder="Or type your question" enterkeyhint="send" />
          <button class="type-go" type="submit" :disabled="!typed.trim() || busy" aria-label="Send">
            <span class="ms">arrow_upward</span>
          </button>
        </form>
      </section>

      <!-- Contact -->
      <div class="contact-strip glass liquid">
        <a class="contact-btn" href="mailto:support@novahost.io">
          <span class="ms contact-ico">mail</span>
          <span>Email</span>
        </a>
        <div class="strip-div" />
        <a class="contact-btn" href="https://t.me/novahost" target="_blank">
          <span class="ms contact-ico">send</span>
          <span>Telegram</span>
        </a>
      </div>

      <!-- FAQ -->
      <div class="search-wrap">
        <input v-model="query" type="search" class="input-glass search-input" placeholder="Search answers…" />
        <span class="ms search-icon">search</span>
      </div>

      <div class="faq-section" v-for="cat in filteredCategories" :key="cat.title">
        <h3 class="faq-cat-title">{{ cat.title }}</h3>
        <div class="faq-list">
          <div
            v-for="item in cat.items"
            :key="item.q"
            class="faq-tile glass liquid"
            :class="{ open: item.open }"
            @click="item.open = !item.open"
          >
            <div class="faq-header">
              <span class="faq-q">{{ item.q }}</span>
              <svg class="faq-chevron" :class="{ open: item.open }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </div>
            <Transition name="accordion">
              <div v-if="item.open" class="faq-answer">
                {{ item.a }}
                <button class="faq-more" @click.stop="ask(item.q)">Ask the assistant about this</button>
              </div>
            </Transition>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { generateBgLoop } from '../utils/bgLoop.js'
import { useVoiceTurn } from '../composables/useVoiceTurn.js'

const router = useRouter()
const bgCanvas = ref(null)
const scroller = ref(null)
const query = ref('')
const typed = ref('')
let animId = null

onMounted(() => {
  const { drawFrame } = generateBgLoop(bgCanvas.value)
  let last = 0
  const loop = (t) => {
    if (t - last > 33) { drawFrame(t); last = t }
    animId = requestAnimationFrame(loop)
  }
  animId = requestAnimationFrame(loop)
})
onUnmounted(() => cancelAnimationFrame(animId))

/* ── Assistant ───────────────────────────────────────────────────── */
const vt = useVoiceTurn('assist', { screen: 'support' })
const SCREEN_NAMES = { '/home': 'Home', '/accounts': 'Accounts', '/symbols': 'Symbols', '/pairs': 'Symbols', '/settings': 'Settings', '/scanner': 'Scanner', '/trades': 'Trades', '/terminal': 'Terminal' }
const thread = ref([])
const busy = computed(() => vt.state.value === 'listen' || vt.state.value === 'think')

// Each finished turn lands in the thread once: transcript, then the answer.
watch(() => vt.caption.value, (text) => {
  if (!text || vt.state.value !== 'answer') return
  if (vt.transcript.value) thread.value.push({ who: 'me', text: vt.transcript.value })
  const go = vt.suggest.value && SCREEN_NAMES[vt.suggest.value] ? vt.suggest.value : null
  thread.value.push({ who: 'bot', text, go, goName: go && SCREEN_NAMES[go] })
  nextTick(() => scroller.value?.scrollTo({ top: 0, behavior: 'smooth' }))
})

function down() { if (!busy.value) vt.start() }
function up() { if (vt.state.value === 'listen') vt.stop() }
function ask(text) {
  const t = String(text || '').trim()
  if (!t || busy.value) return
  typed.value = ''
  vt.send(t)
}

const stateText = computed(() => ({
  listen: 'Listening…', think: 'Thinking…', answer: 'Speaking', idle: 'Ready to help',
}[vt.state.value]))
const orbIcon = computed(() => ({ listen: 'graphic_eq', think: 'more_horiz', answer: 'volume_up', idle: 'support_agent' }[vt.state.value]))

const starters = [
  'How do I connect my broker?',
  'Why was my trade refused?',
  'How do I set lot sizes?',
  'Turn on notifications',
]

/* ── FAQ: kept to what the app actually does ─────────────────────── */
const categories = ref([
  {
    title: 'Getting started',
    items: [
      { q: 'What is NovaHost?', a: 'NovaHost hosts your trading robot in the cloud, so it runs without a PC or VPS staying on. You control it from this app.', open: false },
      { q: 'How do I connect my broker account?', a: 'Open Accounts and enter your MetaTrader 4 or 5 server, account number and password. Type the server name exactly as your broker shows it in MetaTrader.', open: false },
      { q: 'Which brokers work?', a: 'Any broker that offers MetaTrader 4 or 5, as long as its server can be reached from the cloud.', open: false },
    ],
  },
  {
    title: 'Your robot',
    items: [
      { q: 'How do I start or stop the robot?', a: 'Use the main button on Home. Stopping pauses new trades; it does not close the ones already open.', open: false },
      { q: 'How do I set lot sizes per symbol?', a: 'Open Symbols, pick the symbols you want to trade and set a lot and the most trades at once for each. "No size configured" means a symbol has no lot set yet.', open: false },
      { q: 'Can I talk to my robot?', a: 'Yes. Hold the mic on Home and ask about your account or trades. If it suggests a trade, nothing is placed until you tap Confirm, and the idea expires after 30 seconds.', open: false },
    ],
  },
  {
    title: 'Fixing problems',
    items: [
      { q: 'My trade was refused', a: 'The message on the trade says why. "Autotrading disabled by server" usually means your broker spells the symbol differently, such as XAUUSD.ifx. Check the symbol on the Symbols screen.', open: false },
      { q: 'My account will not connect', a: 'Most often the password or server name is wrong. Copy the server name from MetaTrader exactly, then try again.', open: false },
      { q: 'I am not getting notifications', a: 'On iPhone, add NovaHost to your Home Screen first, open it from there, then switch notifications on in Settings.', open: false },
      { q: 'I changed phones', a: 'Your licence is tied to one device. Contact support by email or Telegram to move it.', open: false },
    ],
  },
])

const filteredCategories = computed(() => {
  if (!query.value.trim()) return categories.value
  const q = query.value.toLowerCase()
  return categories.value
    .map(cat => ({ ...cat, items: cat.items.filter(i => i.q.toLowerCase().includes(q) || i.a.toLowerCase().includes(q)) }))
    .filter(cat => cat.items.length > 0)
})
</script>

<style scoped>
.ms { font-family: 'Material Symbols Outlined'; font-weight: 400; font-style: normal; line-height: 1; letter-spacing: normal; font-feature-settings: 'liga'; }
.support-view { position: relative; width: 100%; height: 100dvh; display: flex; flex-direction: column; overflow: hidden; }
.support-bg { position: absolute; inset: 0; width: 100%; height: 100%; z-index: 0; }

.support-header { position: relative; z-index: 2; padding: calc(var(--safe-top) + 16px) 20px 0; }
.support-back { display: grid; place-items: center; width: 38px; height: 38px; margin: 0 0 6px -10px; border: none; background: none; color: rgba(232,240,255,.6); cursor: pointer; }
.support-back:active { transform: scale(0.92) }
.screen-title { font-size: 1.4rem; font-weight: 700; color: #E8F0FF; letter-spacing: -0.02em; }
.screen-sub { font-size: 0.78rem; color: rgba(232,240,255,.55); display: block; margin-top: 2px; }

.support-scroll { position: relative; z-index: 1; flex: 1; overflow-y: auto; -webkit-overflow-scrolling: touch; overscroll-behavior: contain; padding: 14px 16px 130px; display: flex; flex-direction: column; gap: 16px; }

/* ── Assistant ── */
.assist { border-radius: 24px; padding: 18px; display: flex; flex-direction: column; gap: 14px; }
.assist-head { display: flex; align-items: center; gap: 14px; }
.assist-orb { position: relative; width: 52px; height: 52px; flex-shrink: 0; display: grid; place-items: center; border-radius: 9999px; background: radial-gradient(circle at 35% 30%, rgba(var(--accent-rgb), .55), rgba(var(--accent-rgb), .12) 70%); }
.orb-ic { position: relative; z-index: 1; color: #fff; font-size: 24px; }
.orb-ring { position: absolute; inset: 0; border-radius: 9999px; border: 1.5px solid rgba(var(--accent-rgb), .5); opacity: 0; }
.is-listen .orb-ring, .is-answer .orb-ring { animation: orb 1.6s ease-out infinite; }
@keyframes orb { from { transform: scale(1); opacity: .8 } to { transform: scale(1.7); opacity: 0 } }
@media (prefers-reduced-motion: reduce) { .orb-ring { animation: none !important; } }
.assist-name { font-size: 1rem; font-weight: 600; color: #E8F0FF; }
.assist-state { font-size: .78rem; color: rgba(var(--accent-rgb), .85); margin-top: 2px; }
.assist-intro { margin: 0; font-size: .86rem; line-height: 1.5; color: rgba(232,240,255,.7); }

.thread { display: flex; flex-direction: column; gap: 8px; max-height: 300px; overflow-y: auto; }
.bubble { max-width: 85%; padding: 10px 14px; border-radius: 18px; font-size: .86rem; line-height: 1.45; }
.bubble.me { align-self: flex-end; background: rgba(var(--accent-rgb), .22); color: #fff; border-bottom-right-radius: 6px; }
.bubble.bot { align-self: flex-start; background: rgba(255,255,255,.07); color: #E8F0FF; border-bottom-left-radius: 6px; }
.go-btn { display: flex; width: fit-content; align-items: center; gap: 4px; margin-top: 8px; padding: 6px 12px; border-radius: 9999px; border: 0; background: rgba(var(--accent-rgb), .9); color: #0B0B0E; font-size: .78rem; font-weight: 600; }
.go-btn .ms { font-size: 16px; }
.typing { display: flex; gap: 4px; padding: 14px; }
.typing span { width: 6px; height: 6px; border-radius: 9999px; background: rgba(232,240,255,.6); animation: dot 1s infinite; }
.typing span:nth-child(2) { animation-delay: .15s } .typing span:nth-child(3) { animation-delay: .3s }
@keyframes dot { 0%, 100% { opacity: .3 } 50% { opacity: 1 } }

.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip { border: 1px solid rgba(var(--accent-rgb), .35); background: rgba(var(--accent-rgb), .08); color: #E8F0FF; font-size: .78rem; padding: 8px 12px; border-radius: 9999px; cursor: pointer; }
.chip:active { transform: scale(.96); }

.talk { display: flex; align-items: center; justify-content: center; gap: 10px; height: 56px; border-radius: 9999px; background: var(--accent); color: #0B0B0E; font-weight: 600; font-size: .95rem; user-select: none; -webkit-user-select: none; -webkit-touch-callout: none; touch-action: none; cursor: pointer; transition: transform .15s, box-shadow .2s; }
.talk.on { transform: scale(1.03); box-shadow: 0 0 36px -4px var(--accent); }
.talk .ms { font-size: 22px; }

.type-row { display: flex; gap: 8px; }
.type-in { flex: 1; height: 44px; border-radius: 9999px; border: 1px solid rgba(255,255,255,.1); background: rgba(255,255,255,.05); color: #E8F0FF; padding: 0 16px; font-size: .88rem; outline: none; }
.type-in:focus { border-color: rgba(var(--accent-rgb), .5); }
.type-go { width: 44px; height: 44px; border-radius: 9999px; border: 0; background: rgba(var(--accent-rgb), .9); color: #0B0B0E; display: grid; place-items: center; }
.type-go:disabled { opacity: .35; }

/* ── Contact ── */
.contact-strip { border-radius: var(--radius-tight); display: flex; align-items: center; justify-content: space-around; padding: 12px; }
.contact-btn { display: flex; flex-direction: column; align-items: center; gap: 4px; text-decoration: none; color: rgba(232,240,255,0.75); font-size: 0.78rem; font-weight: 500; }
.contact-ico { font-size: 22px; color: var(--accent); }
.strip-div { width: 1px; height: 32px; background: rgba(255,255,255,0.07); }

/* ── FAQ ── */
.search-wrap { position: relative; }
.search-input { padding-right: 40px; }
.search-icon { position: absolute; right: 14px; top: 50%; transform: translateY(-50%); font-size: 20px; opacity: 0.45; pointer-events: none; color: #E8F0FF; }
.faq-section { display: flex; flex-direction: column; gap: 8px; }
.faq-cat-title { font-size: 0.82rem; font-weight: 600; color: rgba(var(--accent-rgb), 0.75); letter-spacing: 0.04em; padding: 0 4px; }
.faq-list { display: flex; flex-direction: column; gap: 6px; }
.faq-tile { border-radius: var(--radius-tight); overflow: hidden; cursor: pointer; transition: all 0.25s var(--smooth); }
.faq-tile.open { background: rgba(var(--accent-rgb), 0.06); border-color: rgba(var(--accent-rgb), 0.25); }
.faq-header { display: flex; align-items: center; justify-content: space-between; padding: 13px 14px; gap: 10px; }
.faq-q { font-size: 0.88rem; color: #E8F0FF; font-weight: 500; line-height: 1.3; flex: 1; text-align: left; }
.faq-chevron { flex-shrink: 0; transition: transform 0.3s var(--spring); color: rgba(var(--accent-rgb), 0.5); }
.faq-chevron.open { transform: rotate(180deg); color: var(--accent); }
.faq-answer { padding: 0 14px 14px; font-size: 0.82rem; color: rgba(232,240,255,0.7); line-height: 1.6; }
.faq-more { display: block; margin-top: 8px; background: none; border: 0; padding: 0; color: var(--accent); font-size: .8rem; font-weight: 600; cursor: pointer; }

.accordion-enter-active, .accordion-leave-active { transition: all 0.3s var(--smooth); overflow: hidden; }
.accordion-enter-from, .accordion-leave-to { max-height: 0; opacity: 0; }
.accordion-enter-to, .accordion-leave-from { max-height: 260px; opacity: 1; }
</style>
