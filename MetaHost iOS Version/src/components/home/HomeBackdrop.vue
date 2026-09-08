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
.cmd[data-scheme="light"] .cc-pool-a { opacity: 0.55 }
.cmd[data-scheme="light"] .cc-pool-b { opacity: 0.42 }

/* ── Art ─────────────────────────────────────────────────────────── */
.cc-art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* app_logo-style art is wide and top-heavy; biasing the crop below
     centre keeps the robot's face in frame on a tall phone. */
  object-position: 50% 38%;
}

/* Full-bleed: the art IS the screen. The scrims above do the legibility
   work, so it stays sharp. */
.cc-art-bleed {
  opacity: 0.85;
}

/* Avatar / Framed: the hero widget owns the real art, so the backdrop
   keeps only a heavy wash of it for colour continuity. */
.cc-art-avatar,
.cc-art-framed {
  opacity: 0.28;
  filter: blur(46px) saturate(130%);
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
