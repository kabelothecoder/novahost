<template>
  <WidgetCard label="Robot List">
    <div class="rl">
      <button
        v-for="ea in robot.eaList"
        :key="ea.id"
        class="rl-row"
        :class="{ 'is-active': ea.id === robot.activeEaId }"
        @click="pick(ea)"
      >
        <span class="rl-art">
          <img :src="ea.avatar || FALLBACK_AVATAR" alt="" decoding="async" />
        </span>

        <span class="rl-copy">
          <span class="rl-name">{{ ea.name }}</span>
          <span class="rl-sub cc-mono">{{ subtitle(ea) }}</span>
        </span>

        <span v-if="session.isRunning && ea.id === robot.activeEaId" class="cc-dot cc-dot-live"></span>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="rl-chev" aria-hidden="true">
          <polyline points="9 18 15 12 9 6" />
        </svg>
      </button>

      <button class="rl-add" @click="$emit('toast', 'Connecting a second robot needs a new licence key')">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" aria-hidden="true">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        <span>Connect new robot</span>
      </button>
    </div>
  </WidgetCard>
</template>

<script setup>
import WidgetCard from '../WidgetCard.vue'
import { robot, selectRobot, FALLBACK_AVATAR } from '../../../stores/robot.js'
import { session } from '../../../stores/session.js'
import { haptic } from '../../../composables/useSpeech.js'

const emit = defineEmits(['toast'])

/**
 * The doc's row reads "Licence · 284 days". That figure comes from
 * `licenses.expires_at`, which is not on the client today — so the row
 * reports run state, which is known, instead of a licence countdown
 * that would have to be invented.
 */
function subtitle(ea) {
  if (session.isRunning && ea.id === robot.activeEaId) return 'Executing'
  return ea.id === robot.activeEaId ? 'Selected · standby' : 'Standby'
}

function pick(ea) {
  if (ea.id === robot.activeEaId) return
  selectRobot(ea.id)
  haptic(40)
  emit('toast', `Active robot · ${ea.name}`)
}
</script>

<style scoped>
.rl { display: flex; flex-direction: column; gap: 6px }

.rl-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: var(--cc-r-tile);
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background 200ms var(--cc-ease), border-color 200ms var(--cc-ease);
}
.rl-row.is-active {
  background: var(--cc-accent-dim);
  border-color: var(--cc-accent-line);
}

.rl-art {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--cc-card-2);
}
.rl-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 38%;
  display: block;
}

.rl-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rl-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--cc-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rl-sub { font-size: 10px; letter-spacing: 0.10em; text-transform: uppercase }

.rl-chev { color: var(--cc-ink-5); flex: none }

.rl-add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 4px;
  padding: 10px;
  border: 1px dashed var(--cc-border-strong);
  border-radius: var(--cc-r-tile);
  background: none;
  font-family: var(--cc-font);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--cc-ink-4);
  cursor: pointer;
  /* No colour/border transition: both read from scheme tokens, and a
     transition on a token-fed property keeps painting the old scheme
     after a swap. See the note in command.css. */
}
.rl-add:active { color: var(--cc-accent-text); border-color: var(--cc-accent-line) }
</style>
