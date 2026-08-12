<template>
  <div class="scanner-view" :class="{ 'glitch-mode': isScanning }">

    <header class="screen-header">
      <h2 class="screen-title">Vision HUD</h2>
      <span class="screen-sub">AI-powered chart analysis</span>
    </header>

    <div class="scanner-scroll">
      <div class="scanner-content">

        <!-- Scan Zone -->
        <div
          class="scan-zone"
          :class="{ 'laser-scan': isScanning, 'has-file': previewImage }"
          @click="triggerFile"
          :style="previewImage ? {} : {}"
        >
          <input type="file" ref="fileInput" hidden @change="handleFile" accept="image/*" />

          <div v-if="!previewImage && !isScanning" class="scan-prompt">
            <div class="scan-icon-ring">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                <path d="M21 12V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h7"/>
                <circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L12 15"/>
                <path d="M18 19h3"/><path d="M19.5 17.5v3"/>
              </svg>
            </div>
            <span class="prompt-main">DROP CHART IMAGE</span>
            <span class="prompt-sub">JPG · PNG · WEBP</span>
          </div>

          <img v-if="previewImage" :src="previewImage" class="preview-img" alt="Chart" />

          <div v-if="isScanning" class="scan-overlay">
            <div class="scan-laser"></div>
            <div class="scan-status">ANALYSING ARCHITECTURE...</div>
          </div>
        </div>

        <!-- Corner markers -->
        <div class="corner-markers" v-if="!previewImage && !isScanning">
          <div class="corner tl"></div>
          <div class="corner tr"></div>
          <div class="corner bl"></div>
          <div class="corner br"></div>
        </div>

        <!-- Verdict HUD -->
        <Transition name="verdict">
          <div v-if="verdict" class="verdict-hud">
            <div class="verdict-grid">
              <div class="verdict-card" :style="{ borderColor: verdict.color + '55' }">
                <span class="verdict-card-label">Direction</span>
                <span class="verdict-card-val" :style="{ color: verdict.color }">{{ verdict.direction }}</span>
              </div>
              <div class="verdict-card">
                <span class="verdict-card-label">Confidence</span>
                <span class="verdict-card-val">{{ verdict.confidence }}%</span>
              </div>
              <div class="verdict-card">
                <span class="verdict-card-label">Entry</span>
                <span class="verdict-card-val">{{ verdict.entry }}</span>
              </div>
              <div class="verdict-card">
                <span class="verdict-card-label">Target</span>
                <span class="verdict-card-val">{{ verdict.tp }}</span>
              </div>
            </div>

            <button class="execute-btn" @click="executeTrade">
              <span>EXECUTE POSITION</span>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="m13 2 9 9-9 9"/><path d="M22 11H3"/></svg>
            </button>
          </div>
        </Transition>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL

const fileInput = ref(null)
const previewImage = ref(null)
const isScanning = ref(false)
const verdict = ref(null)

function triggerFile() { if (!isScanning.value) fileInput.value.click() }

function handleFile(e) {
  const file = e.target.files[0]; if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => { previewImage.value = ev.target.result; startScan() }
  reader.readAsDataURL(file)
}

async function startScan() {
  isScanning.value = true; verdict.value = null

  try {
    const res = await fetch(`${SUPABASE_URL}/functions/v1/analyze-chart`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ imageBase64: previewImage.value })
    })

    if (!res.ok) throw new Error('Failed to reach analysis node')

    const data = await res.json()
    
    verdict.value = {
      direction: data.direction,
      color: data.direction === 'BULLISH' ? '#00FF87' : '#FF003C',
      confidence: data.confidence,
      entry: data.entry,
      tp: data.tp,
      sl: data.sl,
      patterns: data.patterns
    }
  } catch(e) {
    console.error(e)
    alert("AI Analysis encountered interference. Please try again.")
    previewImage.value = null
  } finally {
    isScanning.value = false
  }
}

async function executeTrade() {
  const btn = event.target.closest('.execute-btn')
  btn.innerHTML = 'DISPATCHING... <span class="spinner"></span>'
  
  try {
    const config = JSON.parse(localStorage.getItem('MH_TERMINAL_CONFIG') || '{}')
    const licenseKey = localStorage.getItem('MH_LICENSE_KEY') || 'DEMO_KEY_123'
    
    const res = await fetch(`${SUPABASE_URL}/functions/v1/dispatch-signal`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        licenseKey,
        server: config.server || 'Demo',
        account: config.account || 'Demo',
        password: config.password || '',
        pair: 'XAUUSD', /* Defaulting for demo */
        direction: verdict.value.direction,
        entry: verdict.value.entry,
        tp: verdict.value.tp,
        sl: verdict.value.sl
      })
    })

    if (!res.ok) throw new Error('Dispatch failed')

    btn.innerHTML = 'CONFIRMED ✓'
    btn.style.borderColor = '#00FF87'
    btn.style.color = '#00FF87'
    
    setTimeout(() => {
      verdict.value = null
      previewImage.value = null
    }, 2000)

  } catch(e) {
    alert("Dispatch failed. Check terminal connection.")
    btn.innerHTML = 'EXECUTE POSITION'
  }
}
</script>

<style scoped>
.scanner-view {
  width: 100%;
  min-height: 100dvh;
  display: flex; flex-direction: column;
  background: transparent;
}

.screen-header {
  padding: calc(env(safe-area-inset-top, 16px) + 16px) 20px 0;
}
.screen-title { font-size: 1.3rem; font-weight: 800; color: #E8F0FF; margin: 0; }
.screen-sub {
  font-family: 'Courier New', monospace; font-size: 0.65rem;
  color: rgba(var(--accent-rgb), 0.5); letter-spacing: 0.1em;
  display: block; margin-top: 2px;
}

.scanner-scroll {
  flex: 1; overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding-bottom: 120px;
}
.scanner-content {
  padding: 20px 16px;
  display: flex; flex-direction: column; gap: 16px;
  position: relative;
}

/* Scan Zone */
.scan-zone {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 18px;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
  border: 1px solid rgba(var(--accent-rgb), 0.2);
  background: rgba(var(--accent-rgb), 0.03);
  cursor: pointer;
  position: relative;
  transition: all 0.3s ease;
}
.scan-zone.has-file { border-color: rgba(var(--accent-rgb), 0.4); }
.scan-zone.laser-scan { border-color: var(--accent); box-shadow: 0 0 30px rgba(var(--accent-rgb), 0.2); }

.scan-prompt {
  display: flex; flex-direction: column; align-items: center; gap: 14px;
  color: rgba(255,255,255,0.25);
}
.scan-icon-ring {
  width: 72px; height: 72px;
  border-radius: 50%;
  border: 1.5px solid rgba(var(--accent-rgb), 0.3);
  background: rgba(var(--accent-rgb), 0.06);
  display: flex; align-items: center; justify-content: center;
  color: rgba(var(--accent-rgb), 0.6);
}
.prompt-main {
  font-family: 'Courier New', monospace;
  font-weight: 800; letter-spacing: 0.1em;
  color: rgba(255,255,255,0.45);
}
.prompt-sub {
  font-family: 'Courier New', monospace;
  font-size: 0.65rem; letter-spacing: 0.12em;
}

.preview-img { width: 100%; height: 100%; object-fit: cover; opacity: 0.65; }

.scan-overlay {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  flex-direction: column; gap: 12px;
  background: rgba(0,0,0,0.5);
  backdrop-filter: blur(4px);
}
.scan-laser {
  position: absolute; top: 0; left: 0; right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  box-shadow: 0 0 16px var(--accent);
  animation: laser-sweep 1.8s ease-in-out infinite;
}
@keyframes laser-sweep {
  0%   { top: 0%; }
  50%  { top: calc(100% - 2px); }
  100% { top: 0%; }
}
.scan-status {
  font-family: 'Courier New', monospace;
  font-size: 0.78rem; font-weight: 800;
  color: var(--accent); letter-spacing: 0.12em;
  animation: status-blink 0.8s ease-in-out infinite;
  position: relative; z-index: 1;
}
@keyframes status-blink { 0%,100%{opacity:1} 50%{opacity:0.4} }

/* Corner HUD markers */
.corner-markers {
  position: absolute;
  top: 20px; left: 16px; right: 16px;
  aspect-ratio: 1;
  pointer-events: none;
}
.corner {
  position: absolute;
  width: 20px; height: 20px;
}
.tl { top: 12px; left: 12px; border-top: 2px solid rgba(var(--accent-rgb),0.5); border-left: 2px solid rgba(var(--accent-rgb),0.5); border-radius: 4px 0 0 0; }
.tr { top: 12px; right: 12px; border-top: 2px solid rgba(var(--accent-rgb),0.5); border-right: 2px solid rgba(var(--accent-rgb),0.5); border-radius: 0 4px 0 0; }
.bl { bottom: 12px; left: 12px; border-bottom: 2px solid rgba(var(--accent-rgb),0.5); border-left: 2px solid rgba(var(--accent-rgb),0.5); border-radius: 0 0 0 4px; }
.br { bottom: 12px; right: 12px; border-bottom: 2px solid rgba(var(--accent-rgb),0.5); border-right: 2px solid rgba(var(--accent-rgb),0.5); border-radius: 0 0 4px 0; }

/* Verdict */
.verdict-hud { display: flex; flex-direction: column; gap: 14px; }
.verdict-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px;
}
.verdict-card {
  padding: 16px;
  border-radius: 14px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  display: flex; flex-direction: column; gap: 6px;
}
.verdict-card-label {
  font-family: 'Courier New', monospace;
  font-size: 0.6rem; letter-spacing: 0.14em;
  color: rgba(255,255,255,0.35);
}
.verdict-card-val {
  font-family: 'Courier New', monospace;
  font-size: 1rem; font-weight: 900; color: #fff;
}

.execute-btn {
  width: 100%; padding: 16px;
  border-radius: 14px;
  background: rgba(var(--accent-rgb), 0.12);
  border: 1.5px solid var(--accent);
  color: var(--accent);
  font-family: 'Courier New', monospace;
  font-size: 0.9rem; font-weight: 900; letter-spacing: 0.12em;
  display: flex; align-items: center; justify-content: center; gap: 12px;
  cursor: pointer;
  box-shadow: 0 0 28px rgba(var(--accent-rgb), 0.2);
  transition: all 0.3s ease;
}
.execute-btn:active { transform: scale(0.97); box-shadow: 0 0 40px rgba(var(--accent-rgb), 0.4); }

.verdict-enter-active { transition: all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1); }
.verdict-enter-from { opacity: 0; transform: translateY(30px); }
</style>
