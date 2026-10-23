<template>
  <!--
    ORBIT — v2's Classic Core / Neon Frame treatment.

    The doc replaces the pill with a 78px disc flanked by two quiet
    satellites, all inside one glass card. Kept as a separate branch
    rather than restyling the pill, because the pill is still what
    Focus Engine and Full-Bleed draw and the two are not interchangeable
    shapes.
  -->
  <div v-if="variant === 'orbit'" class="orbit cc-card">
    <button class="orbit-side" @click="go('/pairs')">
      <svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <line x1="18" y1="20" x2="18" y2="10" /><line x1="12" y1="20" x2="12" y2="4" />
        <line x1="6" y1="20" x2="6" y2="14" />
      </svg>
      <span>Symbols</span>
    </button>

    <button
      ref="btn"
      class="orbit-core"
      :data-running="session.isRunning"
      :aria-pressed="session.isRunning"
      :aria-label="session.isRunning ? 'Stop robot' : 'Start robot'"
      @click="toggle"
    >
      <svg v-if="!session.isRunning" width="30" height="30" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M8 5v14l11-7z" />
      </svg>
      <svg v-else width="28" height="28" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <rect x="6" y="5" width="4" height="14" rx="1" /><rect x="14" y="5" width="4" height="14" rx="1" />
      </svg>
      <span class="orbit-core-label">{{ session.isRunning ? 'STOP' : 'START' }}</span>
    </button>

    <button class="orbit-side" @click="go('/trades')">
      <svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M3 17l6-6 4 4 7-7" /><polyline points="14 8 20 8 20 14" />
      </svg>
      <span>Trades</span>
    </button>

    <PoweredMark class="orbit-status" />
  </div>

  <!--
    SOLO — Focus Engine's control, and the reason that layout is now a
    different screen from Full-Bleed Art.

    The two had drifted into the same composition: both drew HomeTopBar,
    both drew this pod's pill with the Symbols and Trades row under it,
    and the only difference left was whether the hero was the robot's art
    or its name set large. Side by side in the layout picker they read as
    one design twice.

    Focus Engine's own note calls it "the get on with it layout", so this
    is what it gets: one round control and the brand mark. No satellites,
    no secondary row — the name above it is the screen, and everything
    else is a tap away in the dock or the sheet. Full-Bleed keeps the pill
    and the two destinations, which is what makes it the marketing screen
    rather than the working one.
  -->
  <div v-else-if="variant === 'solo'" class="solo">
    <button
      ref="btn"
      class="solo-core"
      :data-running="session.isRunning"
      :aria-pressed="session.isRunning"
      :aria-label="session.isRunning ? 'Stop robot' : 'Start robot'"
      @click="toggle"
    >
      <span class="solo-ring" aria-hidden="true"></span>
      <svg v-if="!session.isRunning" width="34" height="34" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M8 5v14l11-7z" />
      </svg>
      <svg v-else width="32" height="32" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <rect x="6" y="5" width="4" height="14" rx="1" /><rect x="14" y="5" width="4" height="14" rx="1" />
      </svg>
      <span class="solo-label">{{ session.isRunning ? 'STOP' : 'START' }}</span>
    </button>

    <PoweredMark class="solo-status" />
  </div>

  <div v-else class="pod" :class="`pod-${variant}`">
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

    <PoweredMark class="pod-status" />

    <!-- Command Deck draws its own four-up action centre, so the pod's
         built-in pair would be a second, shorter copy of it directly
         above. Hidden with v-if rather than CSS so the buttons leave
         the tab order too. -->
    <div v-if="variant !== 'bare'" class="pod-actions">
      <button class="pod-btn" @click="go('/pairs')">
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <line x1="4" y1="21" x2="4" y2="14" /><line x1="4" y1="10" x2="4" y2="3" />
          <line x1="12" y1="21" x2="12" y2="12" /><line x1="12" y1="8" x2="12" y2="3" />
          <line x1="20" y1="21" x2="20" y2="16" /><line x1="20" y1="12" x2="20" y2="3" />
          <line x1="1" y1="14" x2="7" y2="14" /><line x1="9" y1="8" x2="15" y2="8" />
          <line x1="17" y1="16" x2="23" y2="16" />
        </svg>
        <span>Symbols</span>
      </button>

      <button class="pod-btn" @click="go('/trades')">
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M3 17l6-6 4 4 7-7" /><polyline points="14 8 20 8 20 14" />
        </svg>
        <span>Trades</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { animate } from 'motion'
import PoweredMark from '../PoweredMark.vue'
import { session, startSession, stopSession, pushEvent } from '../../../stores/session.js'
import { robot } from '../../../stores/robot.js'
import { haptic } from '../../../composables/useSpeech.js'

defineProps({
  variant: { type: String, default: 'full' }, // 'full' | 'compact' | 'bare' | 'orbit' | 'solo'
})
/* No emits. The pod's only outward signal was the press announcement,
   and with that gone it has nothing to tell its parent — the run state it
   changes lives in stores/session.js, which every layout already reads. */

const router = useRouter()
const btn = ref(null)

/* The line under the ignition control used to report run and broker
   state — "STANDBY · NO BROKER" and its three siblings. It is the brand
   mark now, by request: this is the screen mentors photograph, and the
   caption a prospect reads should say whose product it is.

   Run state did not go with it. The control itself is the readout —
   START and STOP are different words on a differently coloured disc, and
   the running one leaves the accent entirely for the sell red. Broker
   state is the one thing this row genuinely no longer says, and it is
   still stated in full on Neon Frame's own broker strip, on the Open
   Trades widget ("No broker linked yet") and on the System Health card. */

function toggle() {
  const next = !session.isRunning
  if (next) startSession()
  else stopSession()

  /* Everything that MATTERS happens before anything decorative.
     This was the other way round, carried over verbatim from the old
     HomeView: `animate(...)` ran first and threw, so the event log never
     ran at all. The robot started, and the screen said nothing about it.

     What the press NO LONGER does is announce itself. It used to raise
     "Order received · START" as a toast over the top of the screen and
     speak "Order received. Executing START." out loud. Both were removed
     by request, and both were noise rather than information: the control
     is its own readout — START and STOP are different words on a
     differently coloured disc, and the running one leaves the accent
     entirely for the sell red — so a banner repeating the word the user
     just pressed told them nothing they could not see, and a phone
     talking in public told everyone else too.

     The terminal event stays. That is a LOG, not an announcement: it is
     the record of when the robot was armed, and it is the only thing on
     the device that can answer "when did I start this". */
  const verb = next ? 'START' : 'STOP'
  haptic(next ? 60 : 30)
  pushEvent(`${verb} · ${robot.name}`, next ? 'ok' : 'idle')

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

/**
 * The pod's two secondary destinations.
 *
 * They used to be "Quotes" and "Assets" (and "Pairs" and "Info" on the
 * orbit variant) — four labels, two buttons, ONE destination. Quotes
 * pushed /pairs directly; Assets emitted `assets`, which HomeView turned
 * into `router.push('/pairs')`. Two controls side by side that went to
 * the same screen, which is what made the pair read as broken.
 *
 * Symbols keeps /pairs, which is what that screen is: the per-symbol
 * lot-size and risk configuration for the licence. Trades takes the other
 * slot and goes to /trades — open positions, the chart and the history,
 * the one thing the pod could not previously reach and the question a
 * user with a running robot actually has.
 *
 * Both route from here rather than one routing and one emitting, so the
 * two siblings work the same way. The `assets` emit is gone with the
 * duplicate; Command Deck raises its own and is unaffected.
 */
function go(path) {
  haptic(20)
  router.push(path)
}
</script>

<style scoped>
/* ══ ORBIT ══════════════════════════════════════════════════════════
   One glass card: two satellites and a disc, with the status line
   spanning underneath. Grid rather than flex so the disc is centred on
   the CARD, not on the space left over by the satellites — the two
   labels are different widths ("Pairs" / "Info"), and with flex the
   glowing control ends up a few pixels off-axis, which is visible
   precisely because it is the one thing that glows.               */
.orbit {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  justify-items: center;
  row-gap: 12px;
  width: 100%;
  padding: 16px 14px 14px;
}

.orbit-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  /* The disc is unmissable; these must still be reachable. */
  min-width: 64px;
  min-height: 58px;
  padding: 4px 8px;
  border: none;
  background: none;
  border-radius: var(--cc-r-tile);
  color: var(--cc-ink-2);
  cursor: pointer;
  transition: transform 200ms var(--cc-spring);
}
.orbit-side:active { transform: scale(0.94) }
.orbit-side span {
  font-family: var(--cc-font);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--cc-ink-3);
}

/* The disc. A radial accent core over a dark well, ringed and haloed —
   the doc's "one obvious glowing control". */
.orbit-core {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 78px;
  height: 78px;
  border-radius: 50%;
  border: 1.5px solid var(--cc-accent);
  background:
    radial-gradient(circle at 50% 42%,
      rgba(var(--cc-accent-rgb), 0.30) 0%,
      rgba(var(--cc-accent-rgb), 0.10) 46%,
      rgba(0, 0, 0, 0.72) 100%);
  color: #FFF;
  cursor: pointer;
  box-shadow: var(--cc-glow-halo);
  transition: transform 220ms var(--cc-spring), box-shadow 240ms var(--cc-ease),
              border-color 240ms var(--cc-ease);
}
.orbit-core:active { transform: scale(0.955) }

/* Running is a STOP affordance, so it leaves the accent entirely —
   a red disc must not still be wearing the mentor's colour. */
.orbit-core[data-running="true"] {
  border-color: var(--cc-sell);
  background:
    radial-gradient(circle at 50% 42%,
      rgba(224, 84, 78, 0.34) 0%,
      rgba(224, 84, 78, 0.12) 46%,
      rgba(0, 0, 0, 0.74) 100%);
  box-shadow: 0 0 calc(26px * var(--cc-glow)) var(--cc-sell-dim);
}

.orbit-core-label {
  font-family: var(--cc-font);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.16em;
  color: rgba(255, 255, 255, 0.92);
}

/* Spans all three columns, under the row. */
/* Placement only — type, colour and the art-proof shadow all live in
   PoweredMark, so the two variants cannot drift apart. */
.orbit-status { grid-column: 1 / -1 }

/* The disc keeps white ink on both schemes: its own well is dark in
   light mode too, so the light scheme's ink tokens would vanish. */
.cmd[data-scheme="light"] .orbit-core,
.cmd[data-scheme="light"] .orbit-core-label { color: #FFF }

/* ══ SOLO ═══════════════════════════════════════════════════════════
   One disc, one mark. Larger than the orbit core (112px against 78px)
   because nothing flanks it — the orbit disc is sized to sit between two
   satellites inside a card, and at that size on an empty screen it reads
   as a small button someone forgot to finish rather than as the point of
   the layout.                                                        */
.solo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  width: 100%;
}

.solo-core {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 112px;
  height: 112px;
  border-radius: 50%;
  border: 1.5px solid var(--cc-accent);
  background:
    radial-gradient(circle at 50% 42%,
      rgba(var(--cc-accent-rgb), 0.32) 0%,
      rgba(var(--cc-accent-rgb), 0.11) 46%,
      rgba(0, 0, 0, 0.74) 100%);
  color: #FFF;
  cursor: pointer;
  box-shadow: var(--cc-glow-halo);
  transition: transform 220ms var(--cc-spring), box-shadow 240ms var(--cc-ease),
              border-color 240ms var(--cc-ease);
}
.solo-core:active { transform: scale(0.955) }

/* A second ring outside the disc, breathing. The only motion on the
   screen, and it is what stops a lone circle on full-bleed art reading
   as decoration rather than as the control. */
.solo-ring {
  position: absolute;
  inset: -10px;
  border-radius: 50%;
  border: 1px solid rgba(var(--cc-accent-rgb), calc(0.34 * var(--cc-glow, 1)));
  animation: ccHalo 3.4s var(--cc-ease) infinite;
  pointer-events: none;
}

/* Running is a STOP affordance, so it leaves the accent entirely — a red
   disc must not still be wearing the mentor's colour. */
.solo-core[data-running="true"] {
  border-color: var(--cc-sell);
  background:
    radial-gradient(circle at 50% 42%,
      rgba(224, 84, 78, 0.36) 0%,
      rgba(224, 84, 78, 0.13) 46%,
      rgba(0, 0, 0, 0.76) 100%);
  box-shadow: 0 0 calc(32px * var(--cc-glow)) var(--cc-sell-dim);
}
.solo-core[data-running="true"] .solo-ring { border-color: rgba(224, 84, 78, 0.40) }

.solo-label {
  font-family: var(--cc-font);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.18em;
  color: rgba(255, 255, 255, 0.92);
}

/* The disc keeps white ink on both schemes: its own well is dark in light
   mode too, so the light scheme's ink tokens would vanish. */
.cmd[data-scheme="light"] .solo-core,
.cmd[data-scheme="light"] .solo-label { color: #FFF }

/* Placement only — type, colour and the art-proof shadow live in
   PoweredMark, so the variants cannot drift apart. */
.solo-status { margin-top: 2px }

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
/* See .orbit-status — the mark styles itself. */
.pod-status { margin-top: 2px }

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
