<template>
  <!-- Background: canvas animation (replaces bg_loop.mp4 when video unavailable) -->
  <canvas ref="bgCanvas" class="bg-canvas" aria-hidden="true" />

  <!-- Foreground: robot hero avatar -->
  <div class="hero-wrapper" :class="[`mode-${shapeMode}`]">
    <!-- Rotating outer ring -->
    <div class="hero-ring">
      <svg class="ring-svg" viewBox="0 0 260 260" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="130" cy="130" r="124" stroke="url(#ringGrad)" stroke-width="1.5" stroke-dasharray="12 6" />
        <defs>
          <linearGradient id="ringGrad" x1="0" y1="0" x2="260" y2="260" gradientUnits="userSpaceOnUse">
            <stop offset="0%"   stop-color="#00d4ff" stop-opacity="0.9"/>
            <stop offset="50%"  stop-color="#7c3aed" stop-opacity="0.6"/>
            <stop offset="100%" stop-color="#00d4ff" stop-opacity="0.2"/>
          </linearGradient>
        </defs>
      </svg>
    </div>

    <!-- Glass container — shape switches via :class -->
    <div class="hero-glass liquid-glass glass" :class="`shape-${shapeMode}`">
      <!-- Neon glow ring behind avatar -->
      <div class="glow-ring" />

      <!-- Avatar image -->
      <img
        class="hero-avatar"
        src="/robot_avatar.png"
        alt="Nova Edge AI Trading Robot"
        draggable="false"
      />

      <!-- Status badge -->
      <div class="status-badge glass">
        <span class="status-dot" />
        <span class="status-text">AI ONLINE</span>
      </div>
    </div>

    <!-- Shape mode toggle -->
    <div class="shape-toggle glass liquid-glass">
      <button
        class="toggle-btn"
        :class="{ active: shapeMode === 'circle' }"
        @click="$emit('update:shapeMode', 'circle')"
        aria-label="Circle mode"
      >
        <svg width="18" height="18" viewBox="0 0 18 18"><circle cx="9" cy="9" r="7" stroke="currentColor" stroke-width="1.5" fill="none"/></svg>
      </button>
      <button
        class="toggle-btn"
        :class="{ active: shapeMode === 'square' }"
        @click="$emit('update:shapeMode', 'square')"
        aria-label="Square mode"
      >
        <svg width="18" height="18" viewBox="0 0 18 18"><rect x="2" y="2" width="14" height="14" rx="3" stroke="currentColor" stroke-width="1.5" fill="none"/></svg>
      </button>
    </div>

    <!-- Tagline -->
    <div class="hero-tagline">
      <h1 class="hero-name">Nova Edge</h1>
      <p class="hero-subtitle">Precision-Engineered AI Trading</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { generateBgLoop } from '../utils/bgLoop.js'

const props = defineProps({
  shapeMode: {
    type: String,
    default: 'circle',
    validator: v => ['circle', 'square'].includes(v)
  }
})
defineEmits(['update:shapeMode'])

const bgCanvas = ref(null)
let animId = null

onMounted(() => {
  const { drawFrame, canvas } = generateBgLoop(bgCanvas.value)
  let lastTime = 0
  function loop(time) {
    if (time - lastTime > 33) { // ~30fps cap for battery
      drawFrame(time)
      lastTime = time
    }
    animId = requestAnimationFrame(loop)
  }
  animId = requestAnimationFrame(loop)
})

onUnmounted(() => {
  if (animId) cancelAnimationFrame(animId)
})
</script>

<style scoped>
/* ── Background Canvas ── */
.bg-canvas {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 0;
  pointer-events: none;
}

/* ── Hero Wrapper ── */
.hero-wrapper {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  animation: fade-in-up 0.8s var(--ease-spring) both;
  /* Clip the decorative ring to the hero area */
  overflow: hidden;
  padding: 40px 16px 8px;
  border-radius: 0 0 32px 32px;
}

/* ── Rotating Ring ── */
.hero-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 260px;
  height: 260px;
  animation: spin-slow 12s linear infinite;
  pointer-events: none;
  z-index: 0;
}

.ring-svg {
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 0 8px rgba(0,212,255,0.4));
}

/* ── Glass Shape Container ── */
.hero-glass {
  position: relative;
  width: 200px;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: border-radius 0.5s var(--ease-spring);
  animation: float 4s ease-in-out infinite;
}

.hero-glass.shape-circle {
  border-radius: 50%;
}

.hero-glass.shape-square {
  border-radius: 28px;
  /* Chamfer clip for square mode */
  clip-path: polygon(
    16px 0%, calc(100% - 16px) 0%,
    100% 16px, 100% calc(100% - 16px),
    calc(100% - 16px) 100%, 16px 100%,
    0% calc(100% - 16px), 0% 16px
  );
}

/* ── Glow Ring ── */
.glow-ring {
  position: absolute;
  inset: -20px;
  border-radius: inherit;
  background: conic-gradient(
    from 0deg,
    var(--color-cyan-glow),
    var(--color-violet-glow),
    var(--color-cyan-glow)
  );
  opacity: 0.35;
  filter: blur(24px);
  animation: spin-slow 8s linear infinite reverse;
  pointer-events: none;
}

/* ── Avatar Image ── */
.hero-avatar {
  width: 90%;
  height: 90%;
  object-fit: cover;
  border-radius: inherit;
  user-select: none;
  position: relative;
  z-index: 1;
  animation: pulse-ring 3s ease-in-out infinite;
}

/* ── Status Badge ── */
.status-badge {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 20px;
  z-index: 2;
}

.status-dot {
  width: 7px;
  height: 7px;
  background: #22c55e;
  border-radius: 50%;
  box-shadow: 0 0 8px #22c55e, 0 0 14px #22c55e;
  animation: pulse-ring 1.8s ease-in-out infinite;
}

.status-text {
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: #22c55e;
}

/* ── Shape Toggle ── */
.shape-toggle {
  display: flex;
  align-items: center;
  border-radius: 24px;
  padding: 4px;
  gap: 2px;
}

.toggle-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.25s var(--ease-smooth);
}

.toggle-btn.active {
  background: var(--color-cyan-dim);
  color: var(--color-cyan);
  box-shadow: 0 0 12px var(--color-cyan-glow);
}

/* ── Tagline ── */
.hero-tagline {
  text-align: center;
}

.hero-name {
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, #00d4ff 0%, #a855f7 50%, #00d4ff 100%);
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: shimmer 4s linear infinite;
}

.hero-subtitle {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  margin-top: 2px;
}
</style>
