<template>
  <!-- Floating obsidian dock — vapor glass pill -->
  <nav class="dock" role="navigation" aria-label="Main navigation">
    <div class="liquid-border"></div>
    <RouterLink
      v-for="item in tabs"
      :key="item.to"
      :to="item.to"
      class="dock-tab"
      :class="{ active: isActive(item.to) }"
      :aria-label="item.label"
    >
      <div class="dock-icon" v-html="item.icon" />
      <!-- Active indicator -->
      <div v-if="isActive(item.to)" class="dock-active-dot" />
    </RouterLink>
  </nav>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const isActive = (to) => route.path === to || (to === '/home' && route.path === '/')

const tabs = [
  {
    to: '/home',
    label: 'Home',
    icon: `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`
  },
  {
    to: '/terminal',
    label: 'MT5',
    icon: `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>`
  },
  {
    to: '/scanner',
    label: 'Scanner',
    icon: `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/><circle cx="12" cy="12" r="8"/><path d="M12 12h.01"/></svg>`
  },
  {
    to: '/settings',
    label: 'Settings',
    icon: `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93A10 10 0 0 1 21 12c0 5.52-4.48 10-10 10S1 17.52 1 12 5.48 2 11 2"/><path d="M22 2L12 12"/></svg>`
  }
]
</script>

<style scoped>
.dock {
  position: fixed;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 24px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: space-around;
  width: calc(100% - 32px);
  max-width: 340px;
  padding: 16px 20px;
  border-radius: 100px;
  
  /* Soft Vapor-Glass */
  background: rgba(var(--accent-rgb), 0.15);
  backdrop-filter: blur(40px);
  -webkit-backdrop-filter: blur(40px);
  box-shadow: 0 10px 30px rgba(0,0,0,0.5);
  transition: background 0.3s ease;
}

.liquid-border {
  position: absolute;
  inset: 0;
  border-radius: 100px;
  border: 1px solid transparent;
  background: linear-gradient(135deg,
    var(--accent) 0%,
    rgba(255,255,255,0.05) 50%,
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
  transition: background 0.3s ease;
}

.dock-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  color: rgba(255, 255, 255, 0.4);
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1), color 0.3s ease;
  position: relative;
  -webkit-tap-highlight-color: transparent;
}

.dock-tab:active {
  transform: scale(0.95); /* Tactile thud */
}

.dock-tab.active {
  color: var(--accent);
}

.dock-tab.active .dock-icon {
  filter: drop-shadow(0 0 8px rgba(var(--accent-rgb), 0.8));
}

.dock-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: filter 0.25s;
}

.dock-active-dot {
  position: absolute;
  bottom: -6px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 8px var(--accent);
}
</style>
