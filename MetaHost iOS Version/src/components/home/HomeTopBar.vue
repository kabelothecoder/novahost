<template>
  <header class="topbar">
    <button class="tb-icon" aria-label="Settings" @click="$router.push('/settings')">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="1.9" stroke-linecap="round" aria-hidden="true">
        <line x1="4" y1="21" x2="4" y2="14" /><line x1="4" y1="10" x2="4" y2="3" />
        <line x1="12" y1="21" x2="12" y2="12" /><line x1="12" y1="8" x2="12" y2="3" />
        <line x1="20" y1="21" x2="20" y2="16" /><line x1="20" y1="12" x2="20" y2="3" />
        <line x1="1" y1="14" x2="7" y2="14" /><line x1="9" y1="8" x2="15" y2="8" />
        <line x1="17" y1="16" x2="23" y2="16" />
      </svg>
    </button>

    <!-- Robot switcher. Reads as a control even with one robot, because
         it is also how a second one gets connected. -->
    <button class="tb-robot cc-thin" @click="$emit('robots')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <rect x="4" y="8" width="16" height="12" rx="3" />
        <path d="M12 8V4" /><circle cx="9" cy="14" r="1.2" fill="currentColor" />
        <circle cx="15" cy="14" r="1.2" fill="currentColor" />
      </svg>
      <span class="tb-robot-name">{{ robot.name }}</span>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <polyline points="6 9 12 15 18 9" />
      </svg>
    </button>

    <button class="tb-icon" aria-label="Interface style" @click="$emit('interface')">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <polygon points="12 2 2 7 12 12 22 7 12 2" />
        <polyline points="2 17 12 22 22 17" /><polyline points="2 12 12 17 22 12" />
      </svg>
    </button>
  </header>
</template>

<script setup>
import { robot } from '../../stores/robot.js'

defineEmits(['interface', 'robots'])
</script>

<style scoped>
.topbar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  height: 44px;
  flex: none;
}

.tb-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: none;
  background: none;
  border-radius: var(--cc-r-chip);
  color: var(--cc-ink-3);
  cursor: pointer;
  /* transform only. `color` here reads from a scheme token, and a
     transition on a token-fed property does not re-run when the token
     changes — this icon was the last element still painting the
     previous scheme's ink after a Light/Dark swap. The press tint
     snapping instead of easing is imperceptible at 180ms. */
  transition: transform 180ms var(--cc-spring);
}
.tb-icon:active { transform: scale(0.92); color: var(--cc-ink) }

.tb-robot {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  max-width: 58vw;
  padding: 0 12px;
  border-radius: var(--cc-r-chip);
  font-family: var(--cc-font);
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-ink-2);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.tb-robot:active { transform: scale(0.96) }
.tb-robot > svg:first-child { color: var(--cc-accent-text); flex: none }
.tb-robot > svg:last-child  { color: var(--cc-ink-4); flex: none }

/* A long licence name must not push the two icon buttons off the row. */
.tb-robot-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
