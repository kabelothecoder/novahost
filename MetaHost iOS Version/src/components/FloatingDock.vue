<template>
  <!-- Floating glass pill. Restyled onto the command system; the four
       destinations are unchanged. -->
  <nav class="dock cc-thin" role="navigation" aria-label="Main navigation">
    <RouterLink
      v-for="item in tabs"
      :key="item.to"
      :to="item.to"
      class="dock-tab"
      :class="{ active: isActive(item.to) }"
      :aria-label="item.label"
      :aria-current="isActive(item.to) ? 'page' : undefined"
    >
      <span class="dock-icon" v-html="item.icon" />
      <span class="dock-label">{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const isActive = (to) => route.path === to || (to === '/home' && route.path === '/')

/* Stroke width is set in CSS, not on the icons: the doc's rule is
   "filled = active, outline 300 = rest", and the closest honest
   equivalent for a stroke icon set is a weight change plus the accent.
   Leaving it out of the markup keeps that one rule in one place. */
const tabs = [
  {
    to: '/home',
    label: 'Home',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`
  },
  {
    to: '/terminal',
    label: 'MT5',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>`
  },
  {
    to: '/scanner',
    label: 'Scanner',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8V5a2 2 0 0 1 2-2h3"/><path d="M16 3h3a2 2 0 0 1 2 2v3"/><path d="M21 16v3a2 2 0 0 1-2 2h-3"/><path d="M8 21H5a2 2 0 0 1-2-2v-3"/><polyline points="7 14 10 11 13 14 17 9"/></svg>`
  },
  {
    to: '/settings',
    label: 'Settings',
    icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>`
  },
]
</script>

<style scoped>
.dock {
  position: fixed;
  bottom: calc(var(--cc-safe-bottom) + 20px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 200;
  display: flex;
  align-items: stretch;
  width: calc(100% - 32px);
  max-width: 340px;
  padding: 8px 6px;
  border-radius: var(--cc-r-chip);
  /* Material comes from .cc-thin. The accent tint is a wash on top of it
     rather than the background itself — the old dock used
     rgba(--accent-rgb, .15) AS the background, which meant the pill's
     whole colour changed with the swatch and the neon swatches turned it
     into a solid slab. */
  box-shadow:
    inset 0 1px 0 var(--cc-glass-lip),
    0 12px 32px rgba(0, 0, 0, 0.42);
}

.dock-tab {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 5px 2px;
  border-radius: var(--cc-r-chip);
  text-decoration: none;
  color: var(--cc-ink-4);
  -webkit-tap-highlight-color: transparent;
  /* transform only — colour here reads from a scheme token, and a
     transition on a token-fed property keeps painting the previous
     scheme after a Light/Dark swap. */
  transition: transform 200ms var(--cc-spring);
}
.dock-tab:active { transform: scale(0.94) }

.dock-tab.active { color: var(--cc-accent-text) }

.dock-icon {
  display: grid;
  place-items: center;
}
/* The doc's "filled = active, outline 300 = rest", expressed for a
   stroke icon set. */
.dock-icon :deep(svg) { stroke-width: 1.6 }
.dock-tab.active .dock-icon :deep(svg) {
  stroke-width: 2.1;
  filter: drop-shadow(0 0 calc(7px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.75 * var(--cc-glow))));
}

.dock-label {
  font-family: var(--cc-font);
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1;
  /* Four labels on a 375pt phone: "Settings" is the widest and still
     fits, but a longer one must clip rather than wrap the pill open. */
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dock-tab.active .dock-label { font-weight: 700 }
</style>
