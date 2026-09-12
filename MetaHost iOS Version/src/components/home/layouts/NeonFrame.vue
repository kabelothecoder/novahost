<template>
  <!--
    4 · NEON FRAME — "circular art · text nav".

    The identity is the frame itself: an accent-bordered panel holding
    a ringed circular portrait, with the broker link stated plainly
    underneath. Its art mode defaults to `framed`, which is what draws
    the glowing ring in RobotHeroWidget.

    The frame's glow is driven by --cc-glow like everything else, so
    setting Glow to Off leaves a clean bordered panel rather than
    breaking the layout's whole premise.
  -->
  <div class="neon">
    <HomeTopBar @interface="$emit('interface')" @robots="$emit('robots')" />

    <!-- ART ABOVE.
         One card, and it holds the portrait and nothing else. The earlier
         version put the art, the ignition and the broker chip inside a
         single bordered panel, which made the frame a container rather
         than a subject — the glowing border ran around a stack of
         controls, so the thing being framed was a form. Splitting them is
         what lets the art actually be the hero and the controls read as
         controls. -->
    <div class="neon-art">
      <!-- forceMode exists for one failure only: with the art mode left to
           the per-layout picker it can resolve to `bleed`, and the widget
           then omits the art entirely — leaving a glowing empty box with a
           name in it while HomeBackdrop draws the portrait full-screen
           behind it. Observed exactly that.

           It forces `portrait` rather than `framed` now. The circle rendered
           the mentor's art at 132px inside a panel drawn to be the hero of
           the screen, so the accent border framed a token and the rest of the
           card was empty. Portrait hands the whole card to the art and hangs
           the name off the bottom of it, which is what the frame was for. -->
      <RobotHeroWidget size="md" force-mode="portrait" />
    </div>

    <!-- CONTROLS BELOW, on their own ground.
         v2 draws Pairs · disc · Assets here too. -->
    <div class="neon-controls">
      <IgnitionPod variant="orbit" class="neon-pod" />

      <!-- The doc prints "CONNECTED · Exness · MT5 · 41273". The server is
           known once a broker is linked; the login is not on the client, so
           the strip reports what it has rather than printing a placeholder. -->
      <div class="neon-broker" :class="{ 'is-on': session.brokerConnected }">
        <span class="cc-dot" :class="{ 'cc-dot-live': session.brokerConnected }"></span>
        <span class="neon-broker-label cc-mono">{{ brokerLine }}</span>
      </div>
    </div>

    <div v-if="widgets.length" class="neon-widgets cc-ground cc-stagger">
      <WidgetSlot v-for="id in widgets" :key="id" :id="id" @toast="$emit('toast', $event)" />
    </div>

  </div>
</template>

<script setup>
import { computed } from 'vue'
import { activeWidgets } from '../../../stores/theme.js'
import { session } from '../../../stores/session.js'
import HomeTopBar from '../HomeTopBar.vue'
import WidgetSlot from '../WidgetSlot.vue'
import RobotHeroWidget from '../widgets/RobotHeroWidget.vue'
import IgnitionPod from '../widgets/IgnitionPod.vue'

defineEmits(['interface', 'robots', 'assets', 'toast'])

const widgets = computed(() => activeWidgets.value)

const brokerLine = computed(() => {
  if (!session.brokerConnected) return 'No broker linked'
  const parts = ['Connected']
  if (session.brokerServer) parts.push(session.brokerServer)
  if (session.brokerLogin)  parts.push(session.brokerLogin)
  return parts.join(' · ')
})
</script>

<style scoped>
.neon {
  position: relative;
  z-index: 1;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  padding: calc(var(--cc-safe-top) + 8px) 20px calc(var(--cc-safe-bottom) + 108px);
}

/* ── The art card ────────────────────────────────────────────────────
   This layout's whole identity is the frame, so its border is deliberately
   the loudest accent edge in the app: a 2px rule where everything else uses
   a hairline, a doubled outer bloom, and an inner rim that lights the art
   from its own edges rather than sitting on top of it.

   Every layer is multiplied by --cc-glow, so Glow: Off still collapses this
   to a clean 2px bordered card instead of leaving a stub halo behind. That
   is the premise the layout is built on — the frame survives, the bloom is
   the setting. */
.neon-art {
  position: relative;
  margin: 22px 0 0;
  padding: 26px 20px;
  display: flex;
  justify-content: center;
  border: 2px solid var(--cc-accent-line);
  border-radius: var(--cc-r-sheet);
  /* A scrim UNDER the glass, not plain --cc-glass-fill.
     HomeBackdrop paints the same robot art full-screen, and through a
     thin glass card that art reads straight through the frame — so the
     portrait inside ends up sitting on a full-bleed copy of itself and
     the name renders over the backdrop's face. The frame stopped being a
     frame and became a window onto the thing it was framing.
     0.82 of the canvas tone is enough to settle it while leaving a hint
     of the art behind the glass. */
  background:
    linear-gradient(rgba(var(--cc-tone-rgb), 0.82), rgba(var(--cc-tone-rgb), 0.82)),
    var(--cc-glass-fill);
  -webkit-backdrop-filter: var(--cc-glass-blur);
  backdrop-filter: var(--cc-glass-blur);
  box-shadow:
    0 0 calc(22px * var(--cc-glow)) calc(3px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.38 * var(--cc-glow))),
    0 0 calc(70px * var(--cc-glow)) calc(16px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.20 * var(--cc-glow))),
    inset 0 0 calc(40px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.16 * var(--cc-glow))),
    inset 0 0 0 1px rgba(var(--cc-accent-rgb), calc(0.28 * var(--cc-glow))),
    var(--cc-shadow-raised);
}

/* A second ring just outside the border, offset so it reads as light
   escaping the frame rather than as a thicker edge. `inset: -7px` keeps it
   clear of the border box; pointer-events are off so it cannot eat a tap
   meant for the art. */
.neon-art::after {
  content: '';
  position: absolute;
  inset: -7px;
  border-radius: calc(var(--cc-r-sheet) + 7px);
  border: 1px solid rgba(var(--cc-accent-rgb), calc(0.30 * var(--cc-glow)));
  box-shadow: 0 0 calc(26px * var(--cc-glow)) rgba(var(--cc-accent-rgb), calc(0.22 * var(--cc-glow)));
  pointer-events: none;
}

/* ── The controls ────────────────────────────────────────────────────
   No card and no border. The art above is the only framed thing on the
   screen; giving the controls an edge of their own would put two competing
   rectangles on a layout whose entire premise is one frame. */
.neon-controls {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .neon-art { background: var(--cc-card) }
}

.neon-pod { max-width: 300px }

.neon-broker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  border-radius: var(--cc-r-chip);
  border: 1px solid var(--cc-border);
  background: var(--cc-card-2);
}
.neon-broker.is-on {
  border-color: var(--cc-accent-line);
  background: var(--cc-accent-dim);
  box-shadow: var(--cc-glow-edge);
}
.neon-broker.is-on .neon-broker-label { color: var(--cc-accent-text) }

.neon-broker-label {
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--cc-ink-3);
}

.neon-widgets {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
