<template>
  <div class="activate-shell">

    <!-- Animated background orbs -->
    <div class="orb orb-1"></div>
    <div class="orb orb-2"></div>
    <div class="orb orb-3"></div>

    <!-- Glass card -->
    <div class="gate-card glass">
      <div class="gate-liquid-border"></div>

      <!-- Logo + heading -->
      <div class="gate-logo">
        <img src="/app_logo.png" alt="Nova Edge" class="gate-logo-img" />
        <div class="gate-logo-glow"></div>
      </div>

      <h1 class="gate-title">Nova Edge</h1>
      <p class="gate-sub mono-tech">PRECISION AI TRADING</p>
      <div class="gate-divider"></div>
      <p class="gate-label mono-tech">ENTER YOUR LICENSE KEY</p>

      <!-- Key input row -->
      <div class="key-row">
        <input
          ref="keyInput"
          v-model="licenseKey"
          class="key-input mono-tech"
          type="text"
          placeholder="XXXX-XXXX-XXXX-XXXX"
          spellcheck="false"
          autocomplete="off"
          @keyup.enter="handleActivate"
        />
        <!-- Scan clipboard button -->
        <button class="scan-btn" @click="scanClipboard" :disabled="scanning" title="Paste from clipboard">
          <svg v-if="!scanning" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <rect x="9" y="2" width="6" height="4" rx="1"/><path d="M8 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-2"/>
          </svg>
          <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" class="spin-icon">
            <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
          </svg>
        </button>
      </div>

      <!-- Error message -->
      <Transition name="err">
        <p v-if="errorMsg" class="gate-error mono-tech">{{ errorMsg }}</p>
      </Transition>

      <!-- Activate button -->
      <button
        class="activate-btn"
        :class="{ loading: activating }"
        @click="handleActivate"
        :disabled="activating || !licenseKey.trim()"
      >
        <span v-if="!activating">ACTIVATE ROBOT</span>
        <span v-else class="mono-tech">INITIALIZING…</span>
        <div v-if="!activating" class="btn-shimmer"></div>
      </button>

      <p class="gate-footer mono-tech">Secured by Nova Edge · v2.0</p>
    </div>

  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { supabase } from '../utils/supabaseClient.ts'
import { accent, SWATCHES } from '../stores/accent.js'
import { secStorage } from '../utils/secStorage.js'

const router    = useRouter()
const licenseKey = ref('')
const errorMsg   = ref('')
const activating = ref(false)
const scanning   = ref(false)
const keyInput   = ref(null)

// ── Scan clipboard ──────────────────────────────────
async function scanClipboard() {
  scanning.value = true
  try {
    const text = await navigator.clipboard.readText()
    if (text?.trim()) {
      licenseKey.value = text.trim()
      errorMsg.value = ''
    } else {
      errorMsg.value = 'Clipboard is empty.'
    }
  } catch {
    errorMsg.value = 'Clipboard access denied. Paste manually.'
  }
  scanning.value = false
}

// ── Validate license against Supabase ──────────────
async function handleActivate() {
  if (!licenseKey.value.trim()) return
  activating.value = true
  errorMsg.value   = ''

  try {
    const key = licenseKey.value.trim().toUpperCase()

    // Demo bypass
    if (key === 'DEMO-1234') {
      secStorage.setItem('LICENSE_KEY', 'DEMO-1234-WAKANDA') // Must be >= 12 chars for the router guard!
      secStorage.setItem('ROBOT_NAME', 'WAKANDA AI (DEMO)')
      secStorage.setItem('AVATAR_URL', '')
      secStorage.setItem('ACCENT_COLOR', 'Amber')
      secStorage.setItem('ALLOWED_SYMBOLS', ['XAUUSD', 'BTCUSD'])
      secStorage.setItem('LICENSE_VALID', 'true')

      const swatch = SWATCHES.find(s => s.name === 'Amber') || SWATCHES[0]
      accent.current   = swatch
      accent.robotName = 'WAKANDA AI (DEMO)'
      accent.avatarUrl = ''
      accent.allowedSymbols = ['XAUUSD', 'BTCUSD']

      await speakGreeting('WAKANDA AI DEMO')
      sessionStorage.setItem('MH_FRESH_ACTIVATION', '1')
      router.replace('/parent-setup')
      return
    }

    // Call validate-license Edge Function
    const { data, error } = await supabase.functions.invoke('validate-license', {
      body: { license_key: key }
    })

    if (error || !data || !data.success) {
      errorMsg.value = data?.error || 'Invalid or expired license key.'
      activating.value = false
      return
    }

    // Map the hex returned by the server to iOS swatches
    const colorMapping = { '#FF6B35': 'Amber', '#00E5FF': 'Cyan', '#A259FF': 'Violet' }
    const serverColorUpper = data.accent_color?.toUpperCase()
    const swatchName = colorMapping[serverColorUpper] || data.accent_color || 'Crimson'
    const allowedSymbols = data.allowed_symbols || []

    // Persist to secStorage
    secStorage.setItem('LICENSE_KEY', key)
    secStorage.setItem('ROBOT_NAME',  data.product_name || 'MetaBot')
    secStorage.setItem('AVATAR_URL',  data.avatar_url   || '')
    secStorage.setItem('ACCENT_COLOR', swatchName)
    secStorage.setItem('ALLOWED_SYMBOLS', allowedSymbols)
    secStorage.setItem('LICENSE_VALID', 'true')

    // Sync accent store immediately
    const swatch = SWATCHES.find(s => s.name === swatchName) || SWATCHES[0]
    accent.current   = swatch
    accent.robotName = data.product_name || 'MetaBot'
    accent.avatarUrl = data.avatar_url   || ''
    accent.allowedSymbols = allowedSymbols

    // Audio greeting
    await speakGreeting(data.product_name || 'MetaBot')

    // Mark as fresh activation so HomeView greets with ElevenLabs
    sessionStorage.setItem('MH_FRESH_ACTIVATION', '1')

    // Navigate to Parent Setup
    router.replace('/parent-setup')
  } catch (err) {
    console.error('Activation error:', err)
    errorMsg.value = 'Connection error. Try again.'
    activating.value = false
  }
}

// ── ElevenLabs TTS greeting ─────────────────────────
async function speakGreeting(robotName) {
  const text    = `Welcome Swoosh. ${robotName} is online. Let's chop the markets.`
  const apiKey  = localStorage.getItem('ELEVENLABS_API_KEY')
  const voiceId = localStorage.getItem('ELEVENLABS_VOICE_ID') || 'pFZP5JQG7iQjIQuC4Bku'

  if (apiKey) {
    try {
      const r = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${voiceId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'xi-api-key': apiKey },
        body: JSON.stringify({
          text,
          model_id: 'eleven_monolingual_v1',
          voice_settings: { stability: 0.5, similarity_boost: 0.75 }
        })
      })
      if (r.ok) {
        new Audio(URL.createObjectURL(await r.blob())).play()
        return
      }
    } catch {}
  }
  // Fallback: Web Speech API
  if ('speechSynthesis' in window) {
    const u = new SpeechSynthesisUtterance(text)
    u.pitch = 0.8; u.rate = 0.9
    window.speechSynthesis.speak(u)
  }
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════════
   SHELL
═══════════════════════════════════════════════════ */
.activate-shell {
  position: relative;
  width: 100%;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 20px;
  background: #000;
  overflow: hidden;
}

/* ── Animated Orbs ── */
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
  animation: orb-drift 12s ease-in-out infinite alternate;
}
.orb-1 {
  width: 340px; height: 340px;
  top: -120px; left: -80px;
  background: radial-gradient(circle, rgba(var(--accent-rgb),0.35) 0%, transparent 70%);
  animation-delay: 0s;
}
.orb-2 {
  width: 260px; height: 260px;
  bottom: -80px; right: -60px;
  background: radial-gradient(circle, rgba(var(--accent-rgb),0.25) 0%, transparent 70%);
  animation-delay: -4s;
}
.orb-3 {
  width: 200px; height: 200px;
  top: 40%; left: 60%;
  background: radial-gradient(circle, rgba(255,255,255,0.04) 0%, transparent 70%);
  animation-delay: -8s;
}
@keyframes orb-drift {
  0%   { transform: translate(0, 0) scale(1); }
  100% { transform: translate(30px, 40px) scale(1.15); }
}

/* ═══════════════════════════════════════════════════
   GLASS CARD
═══════════════════════════════════════════════════ */
.gate-card {
  position: relative;
  width: 100%;
  max-width: 360px;
  border-radius: 28px;
  padding: 36px 28px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;

  /* Soft Glass — 40px blur per spec */
  background: rgba(255,255,255,0.055);
  backdrop-filter: blur(40px);
  -webkit-backdrop-filter: blur(40px);
  box-shadow:
    0 20px 60px rgba(0,0,0,0.6),
    inset 0 1px 0 rgba(255,255,255,0.09);
}

/* Liquid gradient border */
.gate-liquid-border {
  position: absolute;
  inset: 0;
  border-radius: 28px;
  border: 1px solid transparent;
  background: linear-gradient(135deg,
    rgba(var(--accent-rgb), 0.7) 0%,
    rgba(255,255,255,0.08) 50%,
    transparent 100%
  ) border-box;
  -webkit-mask:
    linear-gradient(#fff 0 0) padding-box,
    linear-gradient(#fff 0 0);
  mask:
    linear-gradient(#fff 0 0) padding-box,
    linear-gradient(#fff 0 0);
  -webkit-mask-composite: destination-out;
  mask-composite: exclude;
  pointer-events: none;
}

/* ── Logo ── */
.gate-logo {
  position: relative;
  width: 80px; height: 80px;
  border-radius: 20px;
  overflow: hidden;
}
.gate-logo-img {
  width: 100%; height: 100%;
  object-fit: cover;
}
.gate-logo-glow {
  position: absolute;
  inset: 0;
  box-shadow: inset 0 0 20px rgba(var(--accent-rgb), 0.4);
  border-radius: 20px;
}

/* ── Text ── */
.gate-title {
  font-size: 1.8rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, #fff 0%, var(--accent) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
}
.gate-sub {
  font-size: 0.6rem;
  letter-spacing: 0.25em;
  color: rgba(var(--accent-rgb), 0.7);
  margin: -8px 0 0;
}
.gate-divider {
  width: 60%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(var(--accent-rgb),0.4), transparent);
}
.gate-label {
  font-size: 0.62rem;
  letter-spacing: 0.18em;
  color: rgba(255,255,255,0.4);
  align-self: flex-start;
}

/* ── Key Row ── */
.key-row {
  width: 100%;
  display: flex;
  gap: 8px;
  align-items: center;
}
.key-input {
  flex: 1;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(var(--accent-rgb), 0.25);
  border-radius: 12px;
  padding: 13px 14px;
  color: #fff;
  font-family: 'Courier New', monospace;
  font-size: 0.82rem;
  letter-spacing: 0.06em;
  outline: none;
  transition: border-color 0.25s, box-shadow 0.25s;
  -webkit-appearance: none;
  appearance: none;
}
.key-input::placeholder {
  color: rgba(255,255,255,0.18);
}
.key-input:focus {
  border-color: rgba(var(--accent-rgb), 0.7);
  box-shadow: 0 0 0 3px rgba(var(--accent-rgb), 0.12);
}
.scan-btn {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  border: 1px solid rgba(var(--accent-rgb), 0.3);
  background: rgba(var(--accent-rgb), 0.12);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  -webkit-tap-highlight-color: transparent;
}
.scan-btn:active { transform: scale(0.92); opacity: 0.8; }
.scan-btn:disabled { opacity: 0.4; cursor: default; }
.spin-icon { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ── Error ── */
.gate-error {
  align-self: flex-start;
  font-size: 0.65rem;
  letter-spacing: 0.06em;
  color: #FF4444;
  margin: -4px 0;
}
.err-enter-active, .err-leave-active { transition: all 0.3s ease; }
.err-enter-from, .err-leave-to { opacity: 0; transform: translateY(-4px); }

/* ── Activate Button ── */
.activate-btn {
  position: relative;
  width: 100%;
  padding: 16px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg,
    rgba(var(--accent-rgb), 0.9) 0%,
    rgba(var(--accent-rgb), 0.6) 100%
  );
  color: #fff;
  font-family: 'Courier New', monospace;
  font-size: 0.82rem;
  font-weight: 900;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.25s ease;
  -webkit-tap-highlight-color: transparent;
  box-shadow: 0 4px 20px rgba(var(--accent-rgb), 0.4);
}
.activate-btn:not(:disabled):active { transform: scale(0.97); }
.activate-btn:disabled { opacity: 0.5; cursor: default; }
.activate-btn.loading { opacity: 0.7; }

/* Shimmer sweep */
.btn-shimmer {
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg,
    transparent 30%,
    rgba(255,255,255,0.18) 50%,
    transparent 70%
  );
  background-size: 200% 100%;
  animation: btn-sweep 2.5s linear infinite;
}
@keyframes btn-sweep {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.gate-footer {
  font-size: 0.55rem;
  letter-spacing: 0.1em;
  color: rgba(255,255,255,0.2);
  margin-top: 4px;
}

.mono-tech { font-family: 'Courier New', monospace; }
</style>
