<template>
  <div class="voice-view">
    <canvas ref="bgCanvas" class="voice-bg" />

    <header class="voice-header">
      <h2 class="screen-title">Thanos Assistant</h2>
      <span class="screen-sub">Voice Activated Trading Intelligence</span>
    </header>

    <div class="voice-orb-container" :class="{ 'speaking': accent.isSpeaking }">
      <!-- ── AUDIO VISUALIZER ORB ── -->
      <div class="orb-orbit">
        <div class="orb-ring ring-1" :style="ringStyle(1.2)" />
        <div class="orb-ring ring-2" :style="ringStyle(1.5)" />
        <div class="orb-ring ring-3" :style="ringStyle(1.8)" />
        
        <div class="orb-core glass liquid" :class="{ 'listening': accent.isListening }">
          <canvas ref="visualizer" class="orb-canvas" />
          <div class="orb-inner-glow" />
        </div>
      </div>

      <div class="voice-status">
        <span v-if="!accent.isListening && !accent.isSpeaking" class="status-msg">SAY "HEY THANOS"</span>
        <span v-if="accent.isListening" class="status-msg listening">I'm listening...</span>
        <span v-if="accent.isSpeaking" class="status-msg speaking">Thanos is speaking</span>
      </div>
    </div>

    <!-- ── CHAT HISTORY LOG ── -->
    <div class="chat-log-container glass">
      <div class="log-header">
        <span class="log-label">DATA FEED</span>
        <div class="log-dots"><span v-for="i in 3" :key="i" /></div>
      </div>
      <div class="log-scroll">
        <div v-for="(msg, i) in messages" :key="i" class="log-row" :class="msg.role">
          <span class="log-time">{{ msg.time }}</span>
          <span class="log-role">{{ msg.role.toUpperCase() }}:</span>
          <span class="log-text">{{ msg.text }}</span>
        </div>
      </div>
    </div>

    <!-- ── MIC TRIGGER & INPUT FALLBACK ── -->
    <div class="mic-trigger-wrap">
      <div v-if="!recognitionSupported" class="input-fallback glass liquid">
        <input 
          v-model="manualInput" 
          placeholder="Type message..." 
          @keyup.enter="sendManualMessage"
        />
        <button @click="sendManualMessage">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></svg>
        </button>
      </div>

      <button 
        v-else
        class="mic-btn glass liquid" 
        :class="{ active: accent.isListening }"
        @mousedown="startListening"
        @mouseup="stopListening"
        @touchstart.prevent="startListening"
        @touchend.prevent="stopListening"
      >
        <svg v-if="!accent.isListening" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="22"/></svg>
        <div v-else class="listening-waves">
          <span v-for="i in 3" :key="i" :style="{ animationDelay: i*0.1 + 's' }" />
        </div>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { accent } from '../stores/accent.js'
import { generateBgLoop } from '../utils/bgLoop.js'

const bgCanvas = ref(null)
const visualizer = ref(null)
const recognitionSupported = ref(true)
const manualInput = ref('')
let animId = null
let visualAnimId = null

const messages = ref([
  { time: '18:05', role: 'thanos', text: 'Quantum Core initialised. How can I assist your trades?' },
  { time: '18:06', role: 'user',   text: 'Scan XAUUSD for liquidity sweeps.' },
  { time: '18:06', role: 'thanos', text: 'Analysing Gold charts. Liquidity detected at 2042.50.' },
])

let recognition = null

onMounted(() => {
  // Speech Recognition Setup
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (SpeechRecognition) {
    recognition = new SpeechRecognition()
    recognitionSupported.value = true
    recognition.continuous = false
    recognition.interimResults = false
    
    recognition.onresult = (event) => {
      const text = event.results[0][0].transcript
      messages.value.push({ 
        time: new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), 
        role: 'user', 
        text 
      })
      processThanosResponse(text)
    }

    recognition.onerror = (event) => {
      console.error('Speech recognition error:', event.error)
      accent.isListening = false
    }
  } else {
    recognitionSupported.value = false
  }

  // Background
  const { drawFrame } = generateBgLoop(bgCanvas.value)
  const loop = (t) => {
    drawFrame(t)
    animId = requestAnimationFrame(loop)
  }
  animId = requestAnimationFrame(loop)

  initVisualizer()
})

onUnmounted(() => {
  cancelAnimationFrame(animId)
  cancelAnimationFrame(visualAnimId)
})

function initVisualizer() {
  const ctx = visualizer.value.getContext('2d')
  const size = 180
  visualizer.value.width = size
  visualizer.value.height = size

  const render = (t) => {
    ctx.clearRect(0, 0, size, size)
    const centerX = size / 2
    const centerY = size / 2
    const radius = 60
    
    // Simulate mic input volume
    if (accent.isListening || accent.isSpeaking) {
      accent.micVolume = 0.5 + Math.sin(t * 0.01) * 0.3
    } else {
      accent.micVolume = 0.1
    }

    const points = 60
    ctx.beginPath()
    for (let i = 0; i < points; i++) {
        const angle = (i / points) * Math.PI * 2
        const distortion = Math.sin(angle * 5 + t * 0.005) * (accent.micVolume * 20)
        const r = radius + distortion
        const x = centerX + Math.cos(angle) * r
        const y = centerY + Math.sin(angle) * r
        if (i === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
    }
    ctx.closePath()
    ctx.strokeStyle = accent.current.hex
    ctx.lineWidth = 2
    ctx.stroke()
    
    // Core glow
    ctx.shadowBlur = 15
    ctx.shadowColor = accent.current.hex
    
    visualAnimId = requestAnimationFrame(render)
  }
  visualAnimId = requestAnimationFrame(render)
}

async function startListening() {
  if (recognition) {
    accent.isListening = true
    try {
      recognition.start()
    } catch (e) { console.error(e) }
  } else {
    // If recognition not supported, we show fallback or just mock
    accent.isListening = true
    setTimeout(stopListening, 2000)
  }
}

async function stopListening() {
  accent.isListening = false
  if (recognition) {
    try {
      recognition.stop()
    } catch (e) { console.error(e) }
  } else {
    // Manual mock for when recognition fails/missing
    const mockUserText = "Analyze current market liquidity."
    messages.value.push({ 
      time: new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), 
      role: 'user', 
      text: mockUserText
    })
    processThanosResponse(mockUserText)
  }
}

function sendManualMessage() {
  if (!manualInput.value.trim()) return
  const text = manualInput.value
  messages.value.push({ 
    time: new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), 
    role: 'user', 
    text 
  })
  manualInput.value = ''
  processThanosResponse(text)
}

async function processThanosResponse(input) {
  // Simple contextual logic
  let response = "Understood. Analysing the data stream."
  const lower = input.toLowerCase()
  
  if (lower.includes('gold') || lower.includes('gold')) {
    response = "Gold analysis complete. Buying pressure rising at 2045. Recommendation: Long Position."
  } else if (lower.includes('profit') || lower.includes('balance') || lower.includes('money')) {
    response = "Account equity is scaling optimally. Current profit target hit."
  } else if (lower.includes('stop') || lower.includes('halt')) {
    response = "Trade operations suspended. Systems on standby."
  }

  messages.value.push({ 
    time: new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), 
    role: 'thanos', 
    text: response 
  })
  
  await speakThanos(response)
}

async function speakThanos(text) {
  accent.isSpeaking = true
  const API_KEY = import.meta.env.VITE_ELEVENLABS_API_KEY
  const VOICE_ID = 'pNInz6obpg8ndEao7m8D'

  if (!API_KEY) {
    console.error('VITE_ELEVENLABS_API_KEY is not set — voice playback disabled.')
    accent.isSpeaking = false
    return
  }
  
  try {
    const response = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${VOICE_ID}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'xi-api-key': API_KEY
      },
      body: JSON.stringify({
        text,
        model_id: 'eleven_multilingual_v2',
        voice_settings: { stability: 0.4, similarity_boost: 0.8 }
      })
    })

    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const audio = new Audio(url)
    
    // Simpler visualizer fallback for mobile performance
    accent.micVolume = 0.8
    await audio.play()

    audio.onended = () => {
      accent.isSpeaking = false
      accent.micVolume = 0
    }
  } catch (err) {
    console.error('TTS Error:', err)
    accent.isSpeaking = false
  }
}

function ringStyle(scale) {
  const vol = accent.micVolume || 0.1
  const s = scale + (vol * 0.5)
  return {
    borderColor: `${accent.current.hex}30`,
    transform: `scale(${s})`,
    boxShadow: `0 0 ${vol * 40}px ${accent.current.hex}20`
  }
}
</script>

<style scoped>
.voice-view {
  position: relative;
  width: 100%;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.voice-bg { position: absolute; inset: 0; width: 100%; height: 100%; z-index: 0; }

.voice-header {
  position: relative;
  z-index: 2;
  padding: calc(var(--safe-top) + 16px) 20px 0;
}
.screen-title { font-size: 1.4rem; font-weight: 700; color: #E8F0FF; }
.screen-sub { font-family: var(--font-mono); font-size: 0.68rem; color: rgba(var(--accent-rgb), 0.5); letter-spacing: 0.1em; display: block; margin-top: 2px; }

.voice-orb-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 1;
  margin-top: -40px;
}

.orb-orbit {
  position: relative;
  width: 180px;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.orb-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1px solid;
  transition: transform 0.2s, box-shadow 0.2s;
}

.orb-core {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
  transition: all 0.5s var(--spring);
}
.orb-core.listening {
  transform: scale(1.1);
  box-shadow: 0 0 40px var(--accent);
}

.orb-canvas { width: 100%; height: 100%; }

.orb-inner-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle, rgba(var(--accent-rgb), 0.2) 0%, transparent 70%);
  pointer-events: none;
}

.voice-status {
  margin-top: 50px;
  text-align: center;
}
.status-msg {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  letter-spacing: 0.2em;
  color: rgba(255,255,255,0.4);
  text-transform: uppercase;
}
.status-msg.listening { color: var(--accent); animation: pulse-glow 1s infinite; }
.status-msg.speaking { color: var(--neon-green); text-shadow: 0 0 10px var(--neon-green); }

.chat-log-container {
  position: absolute;
  bottom: 120px;
  left: 20px;
  right: 20px;
  height: 150px;
  border-radius: var(--radius-tight);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.log-label { font-family: var(--font-mono); font-size: 0.55rem; letter-spacing: 0.15em; color: rgba(255,255,255,0.3); }
.log-dots { display: flex; gap: 4px; }
.log-dots span { width: 4px; height: 4px; border-radius: 50%; border: 1px solid rgba(255,255,255,0.2); }

.log-scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.log-row { font-size: 0.72rem; line-height: 1.4; display: flex; gap: 8px; }
.log-time { color: rgba(255,255,255,0.25); font-family: var(--font-mono); }
.log-role { font-weight: 700; color: rgba(var(--accent-rgb), 0.6); }
.user .log-role { color: rgba(255,255,255,0.5); }
.log-text { color: rgba(232,240,255,0.8); }

.mic-trigger-wrap {
  position: absolute;
  bottom: calc(var(--safe-bottom) + 130px); 
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  z-index: 1000;
  pointer-events: none;
}

.input-fallback {
  width: 90%;
  max-width: 400px;
  height: 54px;
  border-radius: var(--radius-tight);
  display: flex;
  align-items: center;
  padding: 0 8px 0 20px;
  gap: 12px;
  pointer-events: auto;
  border: 1px solid rgba(255,255,255,0.1);
}
.input-fallback input {
  flex: 1;
  background: none;
  border: none;
  color: #fff;
  font-size: 0.9rem;
  outline: none;
}
.input-fallback input::placeholder { color: rgba(255,255,255,0.3); }
.input-fallback button {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--accent);
  color: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 0 15px var(--accent);
}

.mic-btn {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  pointer-events: auto;
  box-shadow: 0 8px 30px rgba(0,0,0,0.6), 0 0 0 1px rgba(255,255,255,0.1);
  transition: all 0.3s var(--spring);
}
.mic-btn:active { transform: scale(0.9); }
.mic-btn.active {
  background: rgba(var(--accent-rgb), 0.3);
  border-color: var(--accent);
  box-shadow: 0 0 40px var(--accent), inset 0 0 15px rgba(255,255,255,0.1);
}

.listening-waves { display: flex; gap: 4px; }
.listening-waves span {
  width: 4px;
  height: 20px;
  background: var(--accent);
  border-radius: var(--radius-tight);
  animation: bar-wave 0.8s ease-in-out infinite;
}

@keyframes bar-wave {
  0%, 100% { height: 8px; }
  50% { height: 24px; }
}
</style>
