<template>
  <div class="ob-shell">

    <!-- ─── Slides viewport ──────────────────────────────── -->
    <div class="slides-track" :style="{ transform: `translateX(-${currentStep * 100}%)` }">

      <!-- Step 1 — Value -->
      <div class="slide">
        <h2 class="slide-title">The ultimate<br>mobile trading VPS.</h2>
        <p class="slide-body">
          Run 24/7 expert advisors, right from your pocket.
        </p>

        <div class="card-stack">
          <!-- Back card -->
          <div class="soft-card card-back">
            <div class="value-chip">
              <div class="chip-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              </div>
              <span class="chip-label">Low Latency Execution</span>
            </div>
            <div class="value-chip">
              <div class="chip-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/></svg>
              </div>
              <span class="chip-label">Hosted in the Cloud</span>
            </div>
          </div>
          <!-- Front card -->
          <div class="soft-card card-front">
            <div class="value-chip">
              <div class="chip-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
              </div>
              <span class="chip-label">99.9% Uptime SLA</span>
            </div>
            <div class="value-chip">
              <div class="chip-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              </div>
              <span class="chip-label">VPS-Grade Security</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Step 2 — Identity -->
      <div class="slide">
        <div class="icon-badge">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        </div>
        <h2 class="slide-title mt-4">Secure Your<br>Connection.</h2>
        <p class="slide-body">Set a display name for your trading session.</p>

        <div class="soft-card full-width mt-6">
          <label class="input-label">DISPLAY NAME</label>
          <input
            v-model="displayName"
            class="soft-input"
            type="text"
            placeholder="e.g. ALPHA TRADER"
            spellcheck="false"
            autocomplete="off"
            maxlength="32"
          />
        </div>
      </div>

      <!-- Step 3 — Risk Protection -->
      <div class="slide">
        <div class="icon-badge teal-badge">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg>
        </div>
        <h2 class="slide-title mt-4">Smart Risk<br>Protection.</h2>
        <p class="slide-body">Guardrails built into every session to protect your capital.</p>

        <div class="risk-stack mt-5">
          <div class="soft-card risk-chip">
            <div class="risk-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>
            </div>
            <div class="risk-content">
              <span class="risk-title">Daily Loss Limit</span>
              <span class="risk-desc">Halts trading if drawdown exceeds threshold</span>
            </div>
          </div>
          <div class="soft-card risk-chip">
            <div class="risk-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="M18 17V9"/><path d="M13 17V5"/><path d="M8 17v-3"/></svg>
            </div>
            <div class="risk-content">
              <span class="risk-title">Spread Filter</span>
              <span class="risk-desc">Skips entries during abnormal spread widening</span>
            </div>
          </div>
          <div class="soft-card risk-chip">
            <div class="risk-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--primary-blue)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            </div>
            <div class="risk-content">
              <span class="risk-title">Margin Safety Check</span>
              <span class="risk-desc">Validates free margin before every order</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Step 4 — System Setup -->
      <div class="slide">
        <div class="soft-card spinner-card">
          <div class="spinner" />
        </div>
        <h2 class="slide-title mt-6">Configuring<br>Workspace…</h2>
        <p class="slide-body">Setting up your secure environment.</p>

        <div class="status-dots mt-5">
          <span class="status-dot"></span>
          <span class="status-dot"></span>
          <span class="status-dot"></span>
        </div>
        <p class="sub-caption mt-3">This will only take a moment.</p>
      </div>

      <!-- Step 5 — Activation -->
      <div class="slide">
        <div class="icon-badge lock-badge">
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--lock-teal)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        </div>
        <h2 class="slide-title mt-4">Connection<br>Secured.</h2>
        <p class="slide-body">
          Enter your license key to activate your terminal.
        </p>

        <div class="soft-card full-width mt-5">
          <label class="input-label">LICENSE KEY</label>
          <input
            v-model="licenseKey"
            class="soft-input password-input"
            type="password"
            placeholder="XXXX-XXXX-XXXX-XXXX"
            spellcheck="false"
            autocomplete="off"
            @keyup.enter="activateTerminal"
          />
          <button
            class="activate-btn mt-4"
            :disabled="!licenseKey.trim()"
            @click="activateTerminal"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="btn-icon"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
            ACTIVATE TERMINAL
          </button>
        </div>
        <p class="sub-caption mt-4">Your license key was provided at purchase.<br>Contact support if you need help.</p>
      </div>

    </div>

    <!-- ─── Bottom controls ───────────────────────────────── -->
    <div class="controls-bar">
      <!-- Pagination Dots -->
      <div class="pagination">
        <div
          v-for="n in TOTAL_STEPS"
          :key="n"
          class="dot"
          :class="{ active: n - 1 === currentStep }"
        />
      </div>

      <!-- Next / Activate FAB -->
      <button
        v-if="currentStep !== 3"
        class="next-pill"
        @click="currentStep === 4 ? activateTerminal() : nextStep()"
      >
        <span v-if="currentStep === 4" class="btn-inner">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
          Activate
        </span>
        <span v-else class="btn-inner">
          Next
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
        </span>
      </button>
    </div>

  </div>
</template>

<script setup>
/**
 * @description OnboardingView — 5-step Soft UI slider.
 * Preserves the previous Vue state logic (displayName, licenseKey, 
 * simulated server setup timeout, and router handoff) while completely
 * overhauling the visuals to a premium light-mode aesthetic.
 */
import { ref, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const TOTAL_STEPS = 5
const currentStep = ref(0)

// ── Shared state ──────────────────────────────
const displayName = ref('')
const licenseKey  = ref('')

// ── Step 4 auto-advance simulation ────────────
let simulationTimer = null

watch(currentStep, (step) => {
  if (step === 3) {
    // Step 4: simulate 2.5-second server allocation
    simulationTimer = setTimeout(() => {
      currentStep.value = 4
    }, 2500)
  }
})

onUnmounted(() => {
  clearTimeout(simulationTimer)
})

// ── Navigation ────────────────────────────────
function nextStep() {
  if (currentStep.value < TOTAL_STEPS - 1) {
    currentStep.value++
  }
}

/**
 * @description Finalises onboarding. Persists displayName and
 * ONBOARDING_COMPLETED flag, then routes to /activate.
 */
function activateTerminal() {
  if (!licenseKey.value.trim() && currentStep.value === 4) return

  // Persist display name for downstream views
  localStorage.setItem('DISPLAY_NAME', displayName.value.trim() || 'ALPHA TRADER')
  // Mark onboarding done
  localStorage.setItem('ONBOARDING_COMPLETED', 'true')

  router.push({ name: 'activate' })
}
</script>

<style scoped>
/* ─── Design Tokens & Shell ──────────────────────────────── */
.ob-shell {
  --primary-blue: #5B9CF6;
  --header-dark: #1A1D20;
  --sub-grey: #8A94A6;
  --bg-start: #F4F7F9;
  --bg-end: #E8EEF2;
  --input-bg: #F0F3F7;
  --lock-teal: #4AC9B0;
  
  position: relative;
  width: 100%;
  height: 100dvh;
  background: linear-gradient(180deg, var(--bg-start) 0%, var(--bg-end) 100%);
  color: var(--header-dark);
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Inter', sans-serif;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ─── Slide track ───────────────────────────────────────── */
.slides-track {
  display: flex;
  flex: 1;
  transition: transform 0.5s cubic-bezier(0.25, 1, 0.5, 1);
  will-change: transform;
}

.slide {
  min-width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 32px 100px; /* leaves room for bottom nav */
  box-sizing: border-box;
  text-align: center;
}

/* ─── Typography & Utilities ────────────────────────────── */
.slide-title {
  font-size: 30px;
  font-weight: 700;
  color: var(--header-dark);
  line-height: 1.25;
  margin: 0 0 10px 0;
  letter-spacing: -0.5px;
}

.slide-body {
  font-size: 15px;
  color: var(--sub-grey);
  line-height: 1.5;
  max-width: 280px;
  margin: 0 0 30px 0;
}

.mt-3 { margin-top: 12px; }
.mt-4 { margin-top: 16px; }
.mt-5 { margin-top: 24px; }
.mt-6 { margin-top: 36px; }

.sub-caption {
  font-size: 12px;
  color: rgba(138, 148, 166, 0.7);
  line-height: 1.4;
}

/* ─── Soft UI Primitives ────────────────────────────────── */
.soft-card {
  background: #FFFFFF;
  border-radius: 28px;
  box-shadow: 0px 24px 48px rgba(0, 0, 0, 0.06);
  padding: 20px;
  box-sizing: border-box;
}

.full-width {
  width: 100%;
  max-width: 340px;
  text-align: left;
}

.icon-badge {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: #F0F4FF;
  display: flex;
  align-items: center;
  justify-content: center;
}

.teal-badge { background: #EFF7FF; }
.lock-badge { background: #E0F7F3; width: 80px; height: 80px; }

/* ─── Step 1 (Value - Tilted Cards) ─────────────────────── */
.card-stack {
  position: relative;
  width: 100%;
  max-width: 320px;
  height: 180px;
  margin: 0 auto;
}

.card-back {
  position: absolute;
  top: 10px;
  left: 0;
  right: 20px;
  height: 150px;
  transform: rotate(-10deg) translate(-10px, 10px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
}

.card-front {
  position: absolute;
  top: -8px;
  left: 12px;
  right: 0;
  height: 150px;
  transform: rotate(4deg);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
}

.value-chip {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chip-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #F0F4FF;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chip-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--header-dark);
}

/* ─── Input Fields ──────────────────────────────────────── */
.input-label {
  display: block;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1.2px;
  color: var(--sub-grey);
  margin-bottom: 10px;
}

.soft-input {
  width: 100%;
  border: none;
  background: var(--input-bg);
  border-radius: 14px;
  padding: 14px 16px;
  font-size: 15px;
  font-weight: 500;
  color: var(--header-dark);
  box-sizing: border-box;
  outline: none;
}

.soft-input::placeholder {
  color: var(--sub-grey);
  font-weight: 400;
}

.password-input {
  letter-spacing: 2px;
}

.password-input::placeholder {
  letter-spacing: 1px;
}

/* ─── Step 3 (Risk Chips) ───────────────────────────────── */
.risk-stack {
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-chip {
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  border-radius: 20px; /* slightly smaller radius for stacked items */
  text-align: left;
}

.risk-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: #F0F4FF;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.risk-content {
  display: flex;
  flex-direction: column;
}

.risk-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--header-dark);
  margin-bottom: 2px;
}

.risk-desc {
  font-size: 12px;
  color: var(--sub-grey);
  line-height: 1.3;
}

/* ─── Step 4 (Spinner) ──────────────────────────────────── */
.spinner-card {
  width: 160px;
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 40px;
}

.spinner {
  width: 74px;
  height: 74px;
  border-radius: 50%;
  border: 6px solid #DDE6F5;
  border-top-color: var(--primary-blue);
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.status-dots {
  display: flex;
  gap: 6px;
  justify-content: center;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary-blue);
  animation: pulse-dot 1.2s infinite alternate;
}

.status-dot:nth-child(2) { animation-delay: 0.2s; }
.status-dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes pulse-dot {
  0% { opacity: 0.3; }
  100% { opacity: 1; }
}

/* ─── Step 5 (Activate Button) ──────────────────────────── */
.activate-btn {
  width: 100%;
  height: 52px;
  border-radius: 50px;
  background: var(--primary-blue);
  border: none;
  color: #FFF;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1.5px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  box-shadow: 0px 8px 24px rgba(91, 156, 246, 0.3);
  transition: transform 0.2s, box-shadow 0.2s, opacity 0.2s;
}

.activate-btn:not(:disabled):active {
  transform: scale(0.96);
  box-shadow: 0px 4px 12px rgba(91, 156, 246, 0.2);
}

.activate-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

.btn-icon {
  margin-top: -1px;
}

/* ─── Bottom Navigation ─────────────────────────────────── */
.controls-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0 28px 28px;
  padding-bottom: max(28px, env(safe-area-inset-bottom));
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 10;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50px;
  background: #CDD5E0;
  transition: width 0.3s cubic-bezier(0.25, 1, 0.5, 1), background 0.3s;
}

.dot.active {
  width: 22px;
  background: var(--primary-blue);
}

.next-pill {
  position: absolute;
  right: 28px;
  height: 50px;
  padding: 0 24px;
  border-radius: 50px;
  background: var(--primary-blue);
  border: none;
  color: #FFF;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  cursor: pointer;
  box-shadow: 0px 6px 16px rgba(91, 156, 246, 0.25);
  transition: transform 0.2s;
}

.next-pill:active {
  transform: scale(0.95);
}

.btn-inner {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
