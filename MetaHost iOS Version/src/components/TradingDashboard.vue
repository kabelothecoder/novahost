<template>
  <div class="dashboard">
    <!-- RobotHero - top section -->
    <section class="hero-section">
      <RobotHero v-model:shapeMode="shapeMode" />
    </section>

    <!-- Trading Symbols Grid -->
    <main class="grid-section">
      <TradingSymbols @select="onPairSelect" />
    </main>

    <!-- Selected pair toast -->
    <Transition name="toast">
      <div v-if="selectedPair" class="pair-toast glass liquid-glass">
        <span class="toast-label">Selected</span>
        <span class="toast-pair">{{ selectedPair.label }}</span>
        <button class="toast-close" @click="selectedPair = null" aria-label="Close">✕</button>
      </div>
    </Transition>

    <!-- Bottom Nav Bar (iOS safe-area aware) -->
    <nav class="bottom-nav glass liquid-glass" role="navigation" aria-label="Main navigation">
      <button
        v-for="item in navItems"
        :key="item.id"
        class="nav-item"
        :class="{ active: activeNav === item.id }"
        @click="activeNav = item.id"
        :aria-label="item.label"
        :aria-current="activeNav === item.id ? 'page' : undefined"
      >
        <span class="nav-icon" v-html="item.icon" />
        <span class="nav-label">{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import RobotHero from './RobotHero.vue'
import TradingSymbols from './TradingSymbols.vue'

const shapeMode = ref('circle')
const selectedPair = ref(null)
const activeNav = ref('home')

function onPairSelect(pair) {
  selectedPair.value = pair
}

const navItems = [
  {
    id: 'home',
    label: 'Home',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`
  },
  {
    id: 'markets',
    label: 'Markets',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>`
  },
  {
    id: 'ai',
    label: 'AI',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v4l3 3"/><circle cx="12" cy="12" r="2"/></svg>`
  },
  {
    id: 'wallet',
    label: 'Wallet',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>`
  },
  {
    id: 'profile',
    label: 'Profile',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`
  }
]
</script>

<style scoped>
/* ── Dashboard Shell ── */
.dashboard {
  position: relative;
  width: 100%;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* Account for iOS status bar */
  padding-top: max(var(--safe-top), 20px);
}

/* ── Hero Section ── */
.hero-section {
  flex: 0 0 auto;
  display: flex;
  justify-content: center;
  padding: 12px 16px 24px;
}

/* ── Scrollable Grid Section ── */
.grid-section {
  flex: 1 1 auto;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  padding-bottom: calc(var(--safe-bottom) + 80px);
  /* Custom scroll snap for smoothness */
  scroll-behavior: smooth;
}

/* ── Selected Pair Toast ── */
.pair-toast {
  position: fixed;
  top: max(var(--safe-top), 16px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-radius: 24px;
  white-space: nowrap;
}

.toast-label {
  font-size: 0.72rem;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.toast-pair {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--color-cyan);
}

.toast-close {
  background: none;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  font-size: 0.75rem;
  line-height: 1;
  padding: 2px;
}

/* Toast transition */
.toast-enter-active, .toast-leave-active {
  transition: all 0.35s var(--ease-spring);
}
.toast-enter-from, .toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-12px) scale(0.92);
}

/* ── Bottom Nav Bar ── */
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 10px 8px;
  padding-bottom: max(var(--safe-bottom), 10px);
  border-radius: 24px 24px 0 0;
  border-bottom: none;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-text-muted);
  padding: 6px 14px;
  border-radius: 16px;
  transition: all 0.25s var(--ease-smooth);
  min-width: 52px;
}

.nav-item.active {
  color: var(--color-cyan);
  background: var(--color-cyan-dim);
}

.nav-item:active {
  transform: scale(0.92);
}

.nav-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: filter 0.25s;
}

.nav-item.active .nav-icon {
  filter: drop-shadow(0 0 6px var(--color-cyan-glow));
}

.nav-label {
  font-size: 0.62rem;
  font-weight: 500;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  line-height: 1;
}
</style>
