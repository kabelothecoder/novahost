<template>
  <div class="home">
    <!-- Toast notification -->
    <Transition name="toast">
      <div v-if="showToast" class="startup-toast">
        <span class="toast-txt mono-tech">{{ toastMessage }}</span>
      </div>
    </Transition>

    <!-- Main Scroll Content -->
    <div class="home-scroll">
      
      <!-- Top Header Area -->
      <header class="home-header">
        <div class="header-left">
          <span class="welcome-tag">WELCOME</span>
          <h1 class="display-name">{{ displayName }}</h1>
        </div>
        <div class="robot-badge">
          <span class="robot-label">ACTIVE ROBOT</span>
          <span class="robot-val">{{ activeEa.name || 'Quantum Breaker' }}</span>
        </div>
      </header>

      <!-- Main Action Buttons (START and QUOTES) -->
      <section class="action-card">
        <div class="action-buttons-grid">
          <!-- START/STOP Button -->
          <button 
            class="action-btn start-btn" 
            :class="{ 'is-running': isRunning }"
            @click="toggleStart($event)"
          >
            <div class="btn-content">
              <span class="btn-title">{{ isRunning ? 'STOP ROBOT' : 'START ROBOT' }}</span>
              <span class="btn-subtext">{{ isRunning ? 'Active & monitoring markets' : 'Initialize algorithmic execution' }}</span>
            </div>
            <div class="btn-icon">
              <svg v-if="!isRunning" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z"/>
              </svg>
              <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <rect x="6" y="5" width="4" height="14" rx="1"/>
                <rect x="14" y="5" width="4" height="14" rx="1"/>
              </svg>
            </div>
          </button>

          <!-- VIEW QUOTES Button -->
          <button class="action-btn quotes-btn" @click="navPairs">
            <div class="btn-content">
              <span class="btn-title">VIEW QUOTES</span>
              <span class="btn-subtext">Manage active currency pairs</span>
            </div>
            <div class="btn-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
            </div>
          </button>
        </div>
      </section>

      <!-- secondary utility navigation -->
      <section class="utilities-section">
        <button class="util-btn" @click="$router.push('/terminal')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <polyline points="4 17 10 11 15 16 22 9"/>
            <polyline points="18 9 22 9 22 13"/>
          </svg>
          <span>LOGS TERMINAL</span>
        </button>
        <button class="util-btn" @click="$router.push('/settings')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
          <span>SETTINGS</span>
        </button>
      </section>

      <!-- Connected EA Inventory -->
      <section class="inventory-section">
        <div class="inventory-header">
          <span class="inv-label">CONNECTED ROBOTS</span>
          <button
            class="shape-toggle"
            @click="shapeMode = shapeMode === 'circle' ? 'square' : 'circle'"
            :title="shapeMode === 'circle' ? 'Switch to Square' : 'Switch to Circle'"
          >
            <svg v-if="shapeMode === 'square'" width="14" height="14" viewBox="0 0 16 16">
              <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.5" fill="none"/>
            </svg>
            <svg v-else width="14" height="14" viewBox="0 0 16 16">
              <rect x="3" y="3" width="10" height="10" rx="3" stroke="currentColor" stroke-width="1.5" fill="none"/>
            </svg>
          </button>
        </div>

        <!-- Empty state -->
        <div v-if="eaList.length === 0" class="empty-state-card" @click="addDemoEA">
          <div class="empty-plus">+</div>
          <span class="empty-msg">ADD KEYS — Require license key</span>
        </div>

        <!-- Robot List Stack -->
        <div v-else class="robot-stack">
          <div
            v-for="ea in eaList"
            :key="ea.id"
            class="robot-card"
            :class="{ 
              'is-live': isRunning && ea.active,
              'is-selected': activeEaId === ea.id
            }"
            @click="selectRobot(ea)"
          >
            <div class="robot-avatar" :class="shapeMode">
              <img :src="ea.avatar || '/robot_avatar.png'" alt="EA Bot" />
            </div>
            <div class="robot-info">
              <span class="robot-name">{{ ea.name }}</span>
              <span class="robot-status">
                {{ isRunning && ea.active ? '● Executing Trades' : '○ Standby' }}
              </span>
            </div>
            <div class="status-indicator-dot" :class="{ 'active': isRunning && ea.active }"></div>
          </div>
        </div>
      </section>

    </div>

    <!-- Voice Command Visualizer -->
    <div
      class="voice-visualizer"
      @touchstart="triggerVoice"
      @mousedown="triggerVoice"
    >
      <canvas ref="vizCanvas" class="viz-canvas"></canvas>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { animate, spring } from 'motion'
import { accent } from '../stores/accent.js'
import MetaApi from 'metaapi.cloud-sdk'
import { supabase } from '../utils/supabaseClient.ts'

const router = useRouter()
const vizCanvas = ref(null)

// ── State ───────────────────────────────────────
const isRunning = ref(false)
const showToast = ref(false)
const toastMessage = ref('Welcome, Swoosh. Systems armed.')
const displayName = ref('Swoosh')

// Seed EA list from license identity
const eaList = ref([
  {
    id: 1,
    name: accent.robotName || 'MetaBot',
    active: true,
    avatar: accent.avatarUrl || '/robot_avatar.png'
  }
])
const activeEaId = ref(1)
const activeEa = computed(() => eaList.value.find(e => e.id === activeEaId.value) || eaList.value[0])

const userAvatar = computed(() => accent.avatarUrl || null)
const tradePulseActive = ref(false)
const shapeMode = ref('square') // 'circle' | 'square'
const audioAmplitude = ref(1)

// Keep first EA card in sync if robot name / avatar changes in store
watch(() => accent.robotName, (name) => {
  if (eaList.value[0]) eaList.value[0].name = name
})
watch(() => accent.avatarUrl, (url) => {
  if (eaList.value[0]) eaList.value[0].avatar = url || '/robot_avatar.png'
})

function selectRobot(ea) {
  activeEaId.value = ea.id
  if (navigator.vibrate) navigator.vibrate(40)
  toastMessage.value = `ACTIVE NODE · ${ea.name}`
  showToast.value = true
  setTimeout(() => showToast.value = false, 2000)
  speakPhrase(`${ea.name} activated.`)
}

// Speech Recognition
const SR = window.SpeechRecognition || window.webkitSpeechRecognition
const recognition = SR ? new SR() : null

// MetaAPI Integration
const setupMetaApi = async () => {
  const token = localStorage.getItem('METAAPI_TOKEN')
  const accountId = localStorage.getItem('METAAPI_ACCOUNT_ID')
  if (!token || !accountId) return
  try {
    const api = new MetaApi(token)
    const account = await api.metatraderAccountApi.getAccount(accountId)
    await account.waitConnected()
    const conn = account.getStreamingConnection()
    await conn.connect()
  } catch (err) {
    console.error('MetaAPI:', err)
  }
}

// Voice Visualizer
let vizAnimId = null
function startVizLoop() {
  const canvas = vizCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')

  const draw = () => {
    canvas.width = canvas.offsetWidth
    canvas.height = canvas.offsetHeight
    const w = canvas.width
    const h = canvas.height
    ctx.clearRect(0, 0, w, h)

    const amp = audioAmplitude.value
    const accentHex = accent.current?.hex || '#FF6B35'

    const bars = 48
    const barW = (w / bars) - 1
    for (let i = 0; i < bars; i++) {
      const noise = isRunning.value
        ? Math.abs(Math.sin(Date.now() / 200 + i) * amp * 12 + Math.random() * amp * 6)
        : 1
      const barH = Math.max(1, noise)
      const x = i * (barW + 1)
      const y = (h - barH) / 2
      ctx.fillStyle = accentHex + 'AA'
      ctx.fillRect(x, y, barW, barH)
    }

    vizAnimId = requestAnimationFrame(draw)
  }
  draw()
}

// Mic amplitude simulation
let ampAnimId = null
function startAmpLoop() {
  const tick = () => {
    if (isRunning.value) {
      audioAmplitude.value = 1 + Math.random() * 4
    } else {
      audioAmplitude.value = 1
    }
    ampAnimId = requestAnimationFrame(tick)
  }
  tick()
}

// TTS
async function speakPhrase(text) {
  const apiKey = localStorage.getItem('ELEVENLABS_API_KEY')
  const voiceId = localStorage.getItem('ELEVENLABS_VOICE_ID') || 'pFZP5JQG7iQjIQuC4Bku'
  if (apiKey) {
    try {
      const r = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${voiceId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'xi-api-key': apiKey },
        body: JSON.stringify({ text, model_id: 'eleven_monolingual_v1', voice_settings: { stability: 0.5, similarity_boost: 0.75 } })
      })
      if (r.ok) { 
        const audio = new Audio(URL.createObjectURL(await r.blob()))
        audio.play().catch(err => {
          console.warn('Autoplay blocked by iOS:', err)
          showToast.value = true
          toastMessage.value = `${text} (Audio Muted)`
        })
        return 
      }
    } catch(e){}
  }
  if ('speechSynthesis' in window) {
    const u = new SpeechSynthesisUtterance(text)
    u.pitch = 0.8; u.rate = 0.9
    window.speechSynthesis.speak(u)
  }
}

function executeCommand(cmd) {
  if (navigator.vibrate) navigator.vibrate([30, 50, 30])
  toastMessage.value = `Order received · ${cmd}`
  showToast.value = true
  setTimeout(() => { showToast.value = false }, 2500)
  speakPhrase(`Order received. Executing ${cmd}.`)
}

function toggleStart(event) {
  isRunning.value = !isRunning.value
  const el = event?.currentTarget
  if (el) animate(el, { scale: [0.95, 1.02, 1] }, { easing: spring({ stiffness: 400, damping: 22 }) })
  if (isRunning.value) {
    if (navigator.vibrate) navigator.vibrate(60)
    executeCommand('START')
  } else {
    executeCommand('STOP')
  }
}

function navPairs() {
  router.push('/pairs')
}

function addDemoEA() {
  eaList.value.push({ id: Date.now(), name: 'NovaHost EA v6', active: true })
  if (navigator.vibrate) navigator.vibrate(20)
}

function triggerVoice() {
  if (recognition) { 
    try { recognition.start() } catch(e){} 
  } else { 
    executeCommand('START')
    isRunning.value = true 
  }
}

async function checkUserSession() {
  try {
    const { data: { session } } = await supabase.auth.getSession()
    if (session?.user) {
      displayName.value = session.user.user_metadata?.full_name || session.user.email?.split('@')[0] || 'Swoosh'
      const { data, error } = await supabase
        .from('profiles')
        .select('avatar_url')
        .eq('id', session.user.id)
        .single()
      if (data && !error && data.avatar_url) {
        userAvatar.value = data.avatar_url
      }
    }
  } catch (err) {
    console.error('Check user session error:', err)
  }
}

let tradeChannel = null
let brokerAccountChannel = null
let signalsChannel = null

function setupRealtimeSubscription() {
  tradeChannel = supabase
    .channel('trade_logs_channel')
    .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'trade_logs' }, () => {
      pulseCrimson()
    })
    .subscribe()

  brokerAccountChannel = supabase
    .channel('broker_sync')
    .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'broker_accounts' }, (payload) => {
      if (accent.brokerConnected) {
        accent.brokerBalance = payload.new.balance
        toastMessage.value = `Balance Updated: $${payload.new.balance.toLocaleString()}`
        showToast.value = true
        setTimeout(() => showToast.value = false, 2000)
      }
    })
    .subscribe()

  signalsChannel = supabase
    .channel('signals_push')
    .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'signals' }, (payload) => {
      const signal = payload.new
      if (Notification.permission === 'granted') {
        new Notification('Trade Executed: ' + signal.pair, {
          body: `${signal.type} @ ${signal.price} | SL: ${signal.sl} | TP: ${signal.tp}`,
          icon: accent.avatarUrl || '/robot_avatar.png'
        })
      }
      pulseCrimson()
      speakPhrase(`Trade executed on ${signal.pair}. ${signal.type} position opened.`)
    })
    .subscribe()
}

function pulseCrimson() {
  tradePulseActive.value = true
  if (navigator.vibrate) navigator.vibrate([100, 50, 100])
  toastMessage.value = 'TRADE EXECUTED'
  showToast.value = true
  setTimeout(() => { showToast.value = false }, 2500)
  setTimeout(() => {
    tradePulseActive.value = false
  }, 1500)
}

onMounted(() => {
  setupMetaApi()
  checkUserSession()
  setupRealtimeSubscription()
  startAmpLoop()
  startVizLoop()

  const freshActivation = sessionStorage.getItem('MH_FRESH_ACTIVATION')
  if (freshActivation) {
    sessionStorage.removeItem('MH_FRESH_ACTIVATION')
    const robotName = accent.robotName || 'MetaBot'
    const text = `Welcome Swoosh. ${robotName} is online. Let's chop the markets.`
    toastMessage.value = `${robotName.toUpperCase()} IS ONLINE`
    setTimeout(() => {
      if (navigator.vibrate) navigator.vibrate([30, 40, 60])
      showToast.value = true
      speakPhrase(text)
      setTimeout(() => { showToast.value = false }, 4000)
    }, 600)
  } else {
    setTimeout(() => {
      if (navigator.vibrate) navigator.vibrate(20)
      toastMessage.value = `${(accent.robotName || 'MetaBot').toUpperCase()} · ARMED`
      showToast.value = true
      setTimeout(() => { showToast.value = false }, 2800)
    }, 500)
  }

  if (recognition) {
    recognition.continuous = true
    recognition.interimResults = false
    recognition.onresult = (e) => {
      const cmd = e.results[e.results.length - 1][0].transcript.trim().toUpperCase()
      if (cmd.includes('START')) { isRunning.value = true; executeCommand('START') }
      else if (cmd.includes('STOP')) { isRunning.value = false; executeCommand('STOP') }
      else if (cmd.includes('PAIRS')) router.push('/pairs')
    }
    try { recognition.start() } catch(e){}
  }
})

onUnmounted(() => {
  if (ampAnimId) cancelAnimationFrame(ampAnimId)
  if (vizAnimId) cancelAnimationFrame(vizAnimId)
  if (recognition) try { recognition.stop() } catch(e){}
  if (tradeChannel) supabase.removeChannel(tradeChannel)
  if (brokerAccountChannel) supabase.removeChannel(brokerAccountChannel)
  if (signalsChannel) supabase.removeChannel(signalsChannel)
})
</script>

<style scoped>
.home {
  position: relative;
  width: 100%;
  height: 100dvh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background-color: #121212; /* Solid dark theme background */
  color: #FFFFFF;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

.home-scroll {
  position: relative;
  z-index: 10;
  flex: 1;
  overflow-y: auto;
  padding: 24px 20px 140px;
}

/* Header styling */
.home-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}

.welcome-tag {
  font-size: 10px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 0.15em;
  display: block;
  margin-bottom: 4px;
}

.display-name {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
  letter-spacing: -0.02em;
}

.robot-badge {
  background: rgba(255, 107, 53, 0.1);
  border: 1px solid rgba(255, 107, 53, 0.3);
  padding: 8px 14px;
  border-radius: 12px;
  text-align: right;
}

.robot-label {
  font-size: 9px;
  font-weight: 700;
  color: var(--accent);
  letter-spacing: 0.1em;
  display: block;
  margin-bottom: 2px;
}

.robot-val {
  font-size: 12px;
  font-weight: 600;
  color: #FFFFFF;
}

/* Action Buttons section */
.action-card {
  margin-bottom: 24px;
}

.action-buttons-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-btn {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22px 24px;
  border-radius: 18px;
  border: none;
  cursor: pointer;
  transition: transform 0.2s, background-color 0.2s;
  text-align: left;
  outline: none;
}

.btn-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.btn-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.btn-subtext {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.btn-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Start Button Specifics */
.start-btn {
  background-color: var(--accent); /* Orange accent */
  color: #FFFFFF;
}

.start-btn.is-running {
  background-color: #333333;
}

.start-btn.is-running .btn-subtext {
  color: rgba(255, 255, 255, 0.6);
}

/* Quotes Button Specifics */
.quotes-btn {
  background-color: #1A1A1A;
  color: #FFFFFF;
  border: 1px solid #2A2A2A;
}

.quotes-btn:active, .start-btn:active {
  transform: scale(0.97);
}

/* Utility buttons */
.utilities-section {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
}

.util-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #2A2A2A;
  background-color: #1A1A1A;
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;
}

.util-btn:active {
  background-color: #2A2A2A;
}

/* Inventory section styling */
.inventory-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.inventory-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.inv-label {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 0.1em;
}

.shape-toggle {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  padding: 4px;
}

.empty-state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  border-radius: 20px;
  border: 1px dashed #2A2A2A;
  background-color: #1A1A1A;
  cursor: pointer;
}

.empty-plus {
  font-size: 24px;
  color: var(--accent);
  margin-bottom: 8px;
}

.empty-msg {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.robot-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.robot-card {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-radius: 16px;
  background-color: #1A1A1A;
  border: 1px solid #2A2A2A;
  cursor: pointer;
  transition: border-color 0.2s;
}

.robot-card.is-selected {
  border-color: var(--accent);
}

.robot-avatar {
  width: 44px;
  height: 44px;
  overflow: hidden;
  margin-right: 16px;
}

.robot-avatar.circle {
  border-radius: 50%;
}

.robot-avatar.square {
  border-radius: 8px;
}

.robot-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.robot-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.robot-name {
  font-size: 14px;
  font-weight: 600;
}

.robot-status {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
}

.status-indicator-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #333333;
}

.status-indicator-dot.active {
  background-color: var(--accent);
  box-shadow: 0 0 8px var(--accent);
}

/* Toast styling */
.startup-toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 300;
  padding: 12px 24px;
  background-color: #1A1A1A;
  border: 1px solid #2A2A2A;
  border-radius: 30px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.5);
}

.toast-txt {
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
}

/* Voice Visualizer */
.voice-visualizer {
  position: fixed;
  bottom: 24px;
  left: 20px;
  right: 20px;
  height: 32px;
  z-index: 100;
  background: rgba(26, 26, 26, 0.8);
  backdrop-filter: blur(10px);
  border: 1px solid #2A2A2A;
  border-radius: 16px;
  padding: 4px;
  cursor: pointer;
}

.viz-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
