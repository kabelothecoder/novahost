<template>
  <!--
    Teleported to <body>, and carrying its own `.cmd` token context — for
    the same two reasons InterfaceStyleSheet is. The dock is
    `position: fixed; z-index: 200` and rendered after the RouterView, so
    at equal z-index it wins on DOM order; and the route transition puts a
    `transform` on the view, which forms a stacking context no descendant
    z-index can escape.
  -->
  <Teleport to="body">
    <div
      class="cmd rsheet-root"
      :data-scheme="resolvedScheme"
      :data-card="theme.cardStyle"
      :data-motion="theme.motion"
      role="dialog"
      aria-modal="true"
      aria-label="Your robots"
    >
      <div class="rsheet-scrim" @click="$emit('close')"></div>

      <div class="rsheet">
        <header class="rsheet-head">
          <span class="rsheet-grab"></span>
          <div class="rsheet-title-row">
            <h2 class="cc-heading">Your robots</h2>
            <button class="rsheet-x" aria-label="Close" @click="$emit('close')">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" aria-hidden="true">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <p class="cc-caption rsheet-blurb">
            Every licence activated on this device. Switching one swaps its
            name, art, colour and trading symbols together.
          </p>
        </header>

        <div class="rsheet-body">
          <RobotRows
            @toast="$emit('toast', $event)"
            @switched="$emit('close')"
          />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { theme, resolvedScheme } from '../../stores/theme.js'
import RobotRows from './RobotRows.vue'

defineEmits(['close', 'toast'])
</script>

<style scoped>
.rsheet-root {
  position: fixed;
  inset: 0;
  /* Above the floating dock, which is also fixed at 200. */
  z-index: 400;
  display: flex;
  align-items: flex-end;
}

.rsheet-scrim {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  -webkit-backdrop-filter: blur(3px);
  backdrop-filter: blur(3px);
  animation: ccFade 220ms var(--cc-ease) both;
}
.cmd[data-scheme="light"] .rsheet-scrim { background: rgba(20, 22, 26, 0.34) }

.rsheet {
  position: relative;
  width: 100%;
  max-height: 82dvh;
  display: flex;
  flex-direction: column;
  border-radius: var(--cc-r-sheet) var(--cc-r-sheet) 0 0;
  background: var(--cc-canvas-2);
  border-top: 0.5px solid var(--cc-glass-rim);
  box-shadow: var(--cc-shadow-sheet);
  animation: ccSheet 300ms var(--cc-ease) both;
}

.rsheet-head {
  flex: none;
  padding: 10px 20px 14px;
  border-bottom: 1px solid var(--cc-border-soft);
}
.rsheet-grab {
  display: block;
  width: 36px;
  height: 4px;
  margin: 0 auto 12px;
  border-radius: 2px;
  background: var(--cc-border-strong);
}
.rsheet-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.rsheet-x {
  display: grid;
  place-items: center;
  width: 32px; height: 32px;
  border: none; background: none;
  color: var(--cc-ink-3);
  cursor: pointer;
}
.rsheet-blurb { margin: 6px 0 0 }

.rsheet-body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 16px 20px calc(var(--cc-safe-bottom) + 24px);
}
</style>
