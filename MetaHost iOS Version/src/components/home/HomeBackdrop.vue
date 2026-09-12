<template>
  <div class="cc-backdrop" aria-hidden="true">
    <!-- Accent pools. Always painted, in every art mode: glass only
         reads if there is colour behind it, and a robot with no mentor
         art would otherwise leave every card as grey plastic. -->
    <div class="cc-pool cc-pool-a"></div>
    <div class="cc-pool cc-pool-b"></div>

    <!-- Art. Full strength when the layout is drawn around it, a heavy
         blurred wash otherwise — in Avatar and Framed modes the art
         belongs to the hero widget, not the background, but a tint of
         it still ties the screen to the robot. -->
    <img
      v-if="artSrc"
      :src="artSrc"
      class="cc-art"
      :class="`cc-art-${mode}`"
      alt=""
      decoding="async"
      @error="failed = true"
    />

    <!-- Contrast floor. The doc's notes call this not optional: accent,
         art mode and glow multiply into combinations that go illegible,
         so the scrims are unconditional rather than applied when a
         particular image "looks like it needs one". -->
    <div class="cc-scrim-t"></div>
    <div class="cc-scrim-b"></div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { theme, activeArtMode } from '../../stores/theme.js'
import { robot, activeEa, FALLBACK_AVATAR } from '../../stores/robot.js'

const failed = ref(false)

const mode = computed(() => activeArtMode.value)

/* Per-robot art wins, then licence art, then the shipped robot. `failed`
   catches a mentor URL that 404s — without it a dead link leaves the
   backdrop as bare pools with no explanation. */
const artSrc = computed(() => {
  if (failed.value) return FALLBACK_AVATAR
  return activeEa.value?.avatar || robot.avatarUrl || FALLBACK_AVATAR
})
</script>

<style scoped>
.cc-backdrop {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: var(--cc-canvas);
}

/* ── Accent pools ─────────────────────────────────────────────────────
   Two counter-phased radial blobs. translate3d keeps them on the
   compositor, so the backdrop-filter of the cards above is not
   re-rasterised on every frame — the single biggest cost of animating
   anything under a glass surface on iOS. */
.cc-pool {
  position: absolute;
  border-radius: 50%;
  filter: blur(64px);
  will-change: transform;
  /* Glow Mode reaches the backdrop too. These pools ARE the accent light
     in the room, so leaving them fixed meant Off still lit the whole
     screen and Intense could not light it any further — the setting only
     ever moved the ignition halo. Clamped at the bottom rather than
     multiplied to zero: Off means "no bloom", not "no colour behind the
     glass", and glass with nothing behind it is grey plastic. */
  opacity: clamp(0.45, calc(0.45 + 0.55 * var(--cc-glow, 1)), 1);
}
.cc-pool-a {
  top: -18%;
  left: -22%;
  width: 86%;
  height: 52%;
  background: radial-gradient(circle at 50% 50%,
    rgba(var(--cc-accent-rgb), 0.38) 0%,
    rgba(var(--cc-accent-rgb), 0.10) 55%,
    transparent 72%);
  animation: ccDriftA 26s var(--cc-ease) infinite;
}
.cc-pool-b {
  bottom: -22%;
  right: -26%;
  width: 96%;
  height: 58%;
  background: radial-gradient(circle at 50% 50%,
    rgba(var(--cc-accent-rgb), 0.26) 0%,
    rgba(var(--cc-accent-rgb), 0.07) 58%,
    transparent 74%);
  animation: ccDriftB 32s var(--cc-ease) infinite;
}

/* Light mode needs a much weaker wash — the same alpha that reads as a
   glow on #0E0E11 reads as a stain on #F4F7F9. */
.cmd[data-scheme="light"] .cc-pool-a { opacity: clamp(0.25, calc(0.30 + 0.30 * var(--cc-glow, 1)), 0.62) }
.cmd[data-scheme="light"] .cc-pool-b { opacity: clamp(0.18, calc(0.22 + 0.24 * var(--cc-glow, 1)), 0.48) }

/* ── Art ─────────────────────────────────────────────────────────── */
.cc-art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* 38%, re-derived for v2's portrait art rather than inherited.
     The source is 576x1024 (0.5625); a phone viewport is narrower than
     that, so `cover` fills on HEIGHT and this Y is inert on a handset —
     it only bites in a wide preview pane, where 38% keeps the eyeline
     just above centre instead of cropping to the hoodie. */
  object-position: 50% 38%;
}

/* Full-bleed: the art IS the screen. The scrims above do the legibility
   work, so it stays sharp. */
.cc-art-bleed {
  opacity: 0.85;
}

/* Avatar / Framed: the hero widget owns the real art, so the backdrop
   keeps a blurred wash of it rather than the image.

   0.28 at blur(46px) was not a wash, it was a rumour — on the layouts
   that also lay an opaque ground over the top (Signal Feed, Command
   Deck) the art contributed about five percent of the final pixel and
   the robot was effectively absent from its own Home. 0.38 at 34px is
   still unmistakably blurred — nothing here competes with text — but the
   silhouette and the colour of the mentor's art are visible, which is
   the whole reason the layer exists. */
.cc-art-avatar,
.cc-art-framed {
  opacity: 0.38;
  filter: blur(34px) saturate(130%);
  transform: scale(1.18); /* hides the blur's transparent edges */
}
.cmd[data-scheme="light"] .cc-art-avatar,
.cmd[data-scheme="light"] .cc-art-framed {
  opacity: 0.16;
}
.cmd[data-scheme="light"] .cc-art-bleed {
  opacity: 0.62;
}

@media (prefers-reduced-motion: reduce) {
  .cc-pool { animation: none }
}
</style>
