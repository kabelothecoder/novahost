<template>
  <div class="pod" :class="`pod-${variant}`">
    <!-- The one obvious glowing control the doc asks every layout to
         have. Everything else in the pod is secondary by construction. -->
    <button
      ref="btn"
      class="cc-ignition pod-start"
      :data-running="session.isRunning"
      :aria-pressed="session.isRunning"
      @click="toggle"
    >
      <svg v-if="!session.isRunning" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M8 5v14l11-7z" />
      </svg>
      <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <rect x="6" y="5" width="4" height="14" rx="1" />
        <rect x="14" y="5" width="4" height="14" rx="1" />
      </svg>
      <span>{{ session.isRunning ? 'STOP' : 'START' }}</span>
    </button>

    <span class="pod-status cc-mono">
      <span class="cc-dot" :class="{ 'cc-dot-live': session.isRunning }"></span>
      {{ statusLine }}
    </span>

    <div class="pod-actions">
      <button class="pod-btn" @click="goQuotes">
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
        </svg>
        <span>Quotes</span>
      </button>

      <button class="pod-btn" @click="$emit('assets')">
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <line x1="4" y1="21" x2="4" y2="14" /><line x1="4" y1="10" x2="4" y2="3" />
          <line x1="12" y1="21" x2="12" y2="12" /><line x1="12" y1="8" x2="12" y2="3" />
          <line x1="20" y1="21" x2="20" y2="16" /><line x1="20" y1="12" x2="20" y2="3" />
          <line x1="1" y1="14" x2="7" y2="14" /><line x1="9" y1="8" x2="15" y2="8" />
          <line x1="17" y1="16" x2="23" y2="16" />
        </svg>
        <span>Assets</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { animate } from 'motion'
import { session, startSession, stopSession, pushEvent } from '../../../stores/session.js'
import { robot } from '../../../stores/robot.js'
import { useSpeech, haptic } from '../../../composables/useSpeech.js'

defineProps({
  variant: { type: String, default: 'full' }, // 'full' | 'compact'
})
const emit = defineEmits(['assets', 'toast'])

const router = useRouter()
const { speak } = useSpeech()
const btn = ref(null)

/**
 * Reports what is actually true rather than a fixed "STANDBY · BROKER
 * LINKED" — the doc's own caption assumes a linked broker, and saying
 * that with none connected would be the screen lying about readiness.
 */
const statusLine = computed(() => {
  if (session.isRunning) return session.brokerConnected ? 'EXECUTING · LIVE' : 'RUNNING · NO BROKER'
  return session.brokerConnected ? 'STANDBY · BROKER LINKED' : 'STANDBY · NO BROKER'
})

function toggle() {
  const next = !session.isRunning
  if (next) startSession()
  else stopSession()

  /* Everything that MATTERS happens before anything decorative.
     This was the other way round, carried over verbatim from the old
     HomeView: `animate(...)` ran first and threw, so the event log,
     the toast and the spoken confirmation never ran at all. The robot
     started, and the screen said nothing about it. */
  const verb = next ? 'START' : 'STOP'
  haptic(next ? 60 : 30)
  pushEvent(`${verb} · ${robot.name}`, next ? 'ok' : 'idle')
  emit('toast', `Order received · ${verb}`)
  speak(`Order received. Executing ${verb}.`, {
    onMuted: () => emit('toast', `Order received · ${verb} (audio muted)`),
  })

  /* The throw itself: `{ easing: spring({...}) }` is not a shape this
     version of motion accepts — it reads `[0]` off an undefined and
     dies. `type: 'spring'` is the option it actually wants. Wrapped
     anyway, because a press animation must never be able to take a
     trading control down with it. */
  if (btn.value) {
    try {
      animate(btn.value, { scale: [0.95, 1.02, 1] }, { type: 'spring', stiffness: 400, damping: 22 })
    } catch (err) {
      console.warn('[ignition] press animation skipped:', err)
    }
  }
}

function goQuotes() {
  haptic(20)
  router.push('/pairs')
}
</script>

<style scoped>
.pod {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.pod-start {
  height: 54px;
  min-width: 190px;
  padding: 0 34px;
}
.pod-compact .pod-start {
  height: 46px;
  min-width: 150px;
  padding: 0 26px;
  font-size: 13px;
}

/* ink-3, not ink-4. This line sits on the backdrop rather than inside a
   card, and at 10px uppercase ink-4 measured 2.84:1 in light mode —
   under the 4.5 floor. ink-3 clears it at 5.58. */
.pod-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--cc-ink-3);
  /* The pod sits directly on mentor art in three of the five layouts,
     and on Focus Engine this line lands near the midpoint where the
     backdrop's bottom scrim has barely started. The shadow costs
     nothing on the layouts where it sits on a ground. */
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.65);
}
.cmd[data-scheme="light"] .pod-status {
  text-shadow: 0 1px 8px rgba(255, 255, 255, 0.85);
}

.pod-actions {
  display: flex;
  gap: 10px;
  margin-top: 2px;
}

.pod-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 16px;
  border-radius: var(--cc-r-chip);
  font-family: var(--cc-font);
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-ink-2);
  background: var(--cc-glass-thin);
  -webkit-backdrop-filter: var(--cc-glass-thin-blur);
  backdrop-filter: var(--cc-glass-thin-blur);
  border: 0.5px solid var(--cc-glass-rim);
  box-shadow: inset 0 1px 0 var(--cc-glass-lip);
  cursor: pointer;
  /* transform only — nothing here changes colour on press, so a colour
     transition bought nothing and was the element that exposed the
     stale-custom-property bug. */
  transition: transform 200ms var(--cc-spring);
}
.pod-btn:active { transform: scale(0.96) }
.pod-btn svg { color: var(--cc-accent-text) }

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .pod-btn { background: var(--cc-card-2); border: 1px solid var(--cc-border) }
}
</style>
