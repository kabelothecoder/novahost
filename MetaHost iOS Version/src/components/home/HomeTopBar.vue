<template>
  <!--
    Two controls, not three.

    It used to open with a sliders icon that pushed /settings and close
    with a layers icon that opened the Interface Style sheet — two
    buttons a user reads as "settings", sitting either side of the robot
    name. Settings is already one of the four destinations in the dock at
    the bottom of every screen, so the left one was the redundant half and
    it is gone. The layers icon stays because the sheet it opens is not
    reachable from anywhere else, and because everything in it changes what
    is on the screen behind it — which is exactly why it is a sheet here
    rather than a row inside Settings.

    Losing the left button lets the robot chip start at the leading edge
    instead of floating in the middle with a gutter on either side. It is
    the screen's title as well as its switcher, and a title belongs there.
  -->
  <header class="topbar">
    <!--
      The switcher. Its background is the ROBOT'S OWN ART, cropped to the
      pill and darkened — not a generic robot glyph, which is what it used
      to carry. A mentor's product should be recognisable at 34px, and the
      one thing that identifies it is the art they uploaded.

      The darkening is not decoration. The art is a photograph whose
      luminance this file cannot know, and the name sits on top of it at
      13px; the scrim is what holds that text above the contrast floor on
      a bright frame. Same rule as PoweredMark's shadow.
    -->
    <button
      class="tb-robot cc-thin"
      :style="{ '--tb-art': artUrl }"
      :aria-label="`Robot: ${robot.name}. Switch robot`"
      @click="$emit('robots')"
    >
      <span class="tb-robot-art" aria-hidden="true">
        <img :src="artSrc" alt="" decoding="async" @error="failed = true" />
      </span>
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
import { ref, computed } from 'vue'
import { robot, activeEa, FALLBACK_AVATAR } from '../../stores/robot.js'

defineEmits(['interface', 'robots'])

/* A mentor URL that 404s would otherwise leave a blank pill with a name
   floating on it — the same failure HomeBackdrop guards. */
const failed = ref(false)

const artSrc = computed(() => {
  if (failed.value) return FALLBACK_AVATAR
  return activeEa.value?.avatar || robot.avatarUrl || FALLBACK_AVATAR
})

/* Quoted, because mentor art arrives as a base64 data URI as often as a
   URL and an unquoted url() breaks on the first character CSS treats as
   punctuation. */
const artUrl = computed(() => `url("${artSrc.value}")`)
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
  position: relative;
  /* Forms a stacking context, which is what makes the two z-index:-1
     pseudo-elements below land ABOVE the pill's own glass fill and still
     under its text. Without it `position: relative` alone leaves
     z-index: auto, the negatives escape to the nearest real context, and
     the art paints behind the whole top bar. */
  isolation: isolate;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  height: 34px;
  max-width: 66vw;
  padding: 0 12px 0 5px;
  border-radius: var(--cc-r-chip);
  overflow: hidden;
  font-family: var(--cc-font);
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-ink);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.tb-robot:active { transform: scale(0.96) }
.tb-robot > svg { color: var(--cc-ink-4); flex: none }

/* The art, twice: a crisp thumbnail at the leading edge, and the same
   image blown out across the whole pill behind the text. The wash is what
   makes the chip read as the robot rather than as a chip with a picture
   stuck to it. */
.tb-robot-art {
  position: relative;
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--cc-card-2);
}
.tb-robot-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* 38% is the crop the rest of the app uses for this portrait source —
     `cover` on a square frame from a 9:16 original otherwise lands on the
     hoodie rather than the face. */
  object-position: 50% 38%;
  display: block;
}

/* The wash. `-1` keeps it under the thumbnail and the label without a
   stacking context of its own; the pill's own `overflow: hidden` is what
   crops it to the shape. */
.tb-robot::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: var(--tb-art);
  background-size: cover;
  background-position: 50% 38%;
  opacity: 0.30;
  filter: blur(2px) saturate(120%);
}
/* The darkening, over the wash and under the text. Sized as a gradient
   rather than a flat fill so the leading edge — where the thumbnail and
   the first letters of the name are — is the darkest part of it. */
.tb-robot::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(90deg,
    rgba(0, 0, 0, 0.62) 0%,
    rgba(0, 0, 0, 0.48) 55%,
    rgba(0, 0, 0, 0.34) 100%);
}

/* Light mode scrims toward white instead: a dark wash under dark ink is
   the wrong direction, and the label is --cc-ink, which is near-black
   there. */
.cmd[data-scheme="light"] .tb-robot::after {
  background: linear-gradient(90deg,
    rgba(255, 255, 255, 0.74) 0%,
    rgba(255, 255, 255, 0.62) 55%,
    rgba(255, 255, 255, 0.50) 100%);
}
.cmd[data-scheme="light"] .tb-robot::before { opacity: 0.22 }

/* A long licence name must not push the icon button off the row. */
.tb-robot-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
