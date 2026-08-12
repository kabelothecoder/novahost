<template>
  <div class="splash">
    <!-- Ambient glow orb — subtle, not distracting -->
    <div class="ambient-orb" />

    <!-- Logo lockup -->
    <div class="logo-lockup">
      <div class="logo-frame glass">
        <img src="/app_logo.png" class="logo-img" alt="Nova Edge" />
      </div>

      <div class="brand-copy">
        <h1 class="brand-name">Nova Edge</h1>
        <p class="brand-sub mono-tech">Silent Precision</p>
      </div>
    </div>

    <!-- Loading indicator — three pulsing dots -->
    <div class="dots-loader">
      <span class="dot" style="animation-delay: 0s" />
      <span class="dot" style="animation-delay: 0.18s" />
      <span class="dot" style="animation-delay: 0.36s" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

/**
 * @description SplashView — acts as the entry gate for the app.
 * After 1.5 s it hands off to OnboardingView for new users.
 * The router's Smart Shield guard will intercept and redirect
 * to /activate if the user has already completed onboarding but
 * lacks a valid license, or straight to /home if fully licensed.
 */
const router = useRouter()
let timer = null

onMounted(() => {
  timer = setTimeout(() => {
    router.push('/onboarding')
  }, 1500)
})

onUnmounted(() => {
  clearTimeout(timer)
})
</script>

<style scoped>
/* ─── Shell ─────────────────────────────────────────────────── */
.splash {
  position: relative;
  width: 100%;
  height: 100dvh;
  background: #121212;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 48px;
  overflow: hidden;
}

/* ─── Ambient orb ────────────────────────────────────────────── */
.ambient-orb {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -55%);
  width: 340px;
  height: 340px;
  background: radial-gradient(
    circle at center,
    rgba(var(--accent-rgb), 0.08) 0%,
    transparent 70%
  );
  filter: blur(48px);
  pointer-events: none;
  animation: orb-breathe 4s ease-in-out infinite alternate;
}

@keyframes orb-breathe {
  from { opacity: 0.6; transform: translate(-50%, -55%) scale(0.9); }
  to   { opacity: 1;   transform: translate(-50%, -55%) scale(1.1); }
}

/* ─── Logo lockup ────────────────────────────────────────────── */
.logo-lockup {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

.logo-frame {
  width: 96px;
  height: 96px;
  border-radius: 22px;            /* Apple-style squircle */
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow:
    0 0 0 1px rgba(var(--accent-rgb), 0.12),
    0 8px 40px rgba(0, 0, 0, 0.5);
  animation: logo-emerge 0.8s var(--spring, cubic-bezier(0.34,1.56,0.64,1)) both;
}

@keyframes logo-emerge {
  from { opacity: 0; transform: scale(0.7) translateY(12px); }
  to   { opacity: 1; transform: scale(1)   translateY(0); }
}

.logo-img {
  width: 100%;
  height: 100%;
  object-fit: contain;            /* ← Task requirement */
  padding: 12px;
}

/* ─── Brand copy ─────────────────────────────────────────────── */
.brand-copy {
  text-align: center;
  animation: copy-emerge 0.8s var(--spring, cubic-bezier(0.34,1.56,0.64,1)) 0.15s both;
}

@keyframes copy-emerge {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}

.brand-name {
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.04em;
  color: #fff;
  line-height: 1;
}

.brand-sub {
  margin-top: 6px;
  font-size: 0.65rem;
  letter-spacing: 0.22em;
  color: rgba(255, 255, 255, 0.32);
  text-transform: uppercase;
}

/* ─── Dots loader ─────────────────────────────────────────────── */
.dots-loader {
  position: absolute;
  bottom: max(48px, env(safe-area-inset-bottom, 0px) + 32px);
  display: flex;
  gap: 6px;
  z-index: 1;
  animation: copy-emerge 0.8s ease 0.4s both;
}

.dot {
  display: block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(var(--accent-rgb), 0.5);
  animation: dot-pulse 1.2s ease-in-out infinite;
}

@keyframes dot-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50%       { opacity: 1;   transform: scale(1.2); }
}
</style>
