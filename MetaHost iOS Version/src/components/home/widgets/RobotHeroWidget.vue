<template>
  <div class="hero" :class="[`hero-${mode}`, `hero-${size}`]">
    <!-- Art. Omitted in Full-bleed: HomeBackdrop is already showing it
         at full strength, and a second copy would sit on top of itself. -->
    <div v-if="mode !== 'bleed'" class="hero-art" :class="`art-${mode}`">
      <img :src="artSrc" alt="" decoding="async" @error="failed = true" />
      <span v-if="mode === 'framed'" class="hero-ring"></span>

      <!--
        v2's corner badge. It reports the LICENCE-BOUND robot's version
        and only appears in Avatar mode, where there is a card corner to
        pin it to — on the circular Framed crop it would clip.
        Rendered only when the licence actually carries a version; the
        doc's "v6" is sample copy, not a constant to hardcode.
      -->
      <span v-if="mode === 'avatar' && robot.version" class="hero-badge">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12 1.6l2.4 2.05 3.15-.35.9 3.05 2.9 1.3-1.35 2.9 1.35 2.9-2.9 1.3-.9 3.05-3.15-.35L12 19.5l-2.4-2.05-3.15.35-.9-3.05-2.9-1.3 1.35-2.9L2.65 7.65l2.9-1.3.9-3.05 3.15.35z" />
          <path d="M10.6 14.2l-2.9-2.9 1.2-1.2 1.7 1.7 4-4 1.2 1.2z" fill="var(--cc-canvas)" />
        </svg>
        <span>{{ robot.version }}</span>
      </span>
    </div>

    <div class="hero-copy">
      <!-- v2 writes the mentor as a handle ("@swooshfx") rather than
           "Mentor · Swoosh FX". The @ is added only when the stored
           value is not already one, so a licence that supplies a real
           handle is not turned into "@@name". -->
      <span v-if="mentorHandle" class="cc-label hero-mentor">{{ mentorHandle }}</span>

      <h1 class="hero-name cc-name" :class="{ 'cc-ink-shadow': mode === 'bleed' }">
        {{ robot.name }}
      </h1>

      <!-- The doc's descriptor line ("Momentum scalper · XAUUSD, US30").
           Built from the licence's own allowed symbols rather than
           invented copy — omitted entirely when the licence lists none,
           because a hero that says nothing is better than one that
           says something untrue. -->
      <p v-if="symbolLine" class="hero-symbols" :class="{ 'cc-ink-shadow': mode === 'bleed' }">
        {{ symbolLine }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { activeArtMode } from '../../../stores/theme.js'
import { robot, activeEa, FALLBACK_AVATAR } from '../../../stores/robot.js'

const props = defineProps({
  /** Layouts set their own weight — Full-Bleed wants this enormous,
   *  Classic Core wants it modest. */
  size: { type: String, default: 'md' }, // 'sm' | 'md' | 'lg'

  /**
   * Force an art mode, ignoring the per-layout preference.
   *
   * For layouts whose entire identity IS a particular crop. Neon Frame is
   * the case that needed it: the art mode is a per-layout setting, and with
   * it on `bleed` the widget omits the art entirely — HomeBackdrop draws the
   * portrait full-screen and the frame is left as an empty bordered box with
   * a name in it. The layout stops being the thing it is named after.
   *
   * Left unset everywhere else, so the picker keeps working as designed.
   */
  forceMode: { type: String, default: null },
})

const failed = ref(false)
const mode = computed(() => props.forceMode || activeArtMode.value)

const artSrc = computed(() => {
  if (failed.value) return FALLBACK_AVATAR
  return activeEa.value?.avatar || robot.avatarUrl || FALLBACK_AVATAR
})

/** "@swooshfx". Spaces are stripped and the case lowered so a display
 *  name ("Swoosh FX") still reads as a handle; an already-@-prefixed
 *  value is passed through untouched. */
const mentorHandle = computed(() => {
  const m = (robot.mentor || '').trim()
  if (!m) return ''
  return m.startsWith('@') ? m : `@${m.replace(/\s+/g, '').toLowerCase()}`
})

const symbolLine = computed(() => {
  const s = robot.allowedSymbols
  if (!Array.isArray(s) || !s.length) return ''
  // Four is where the line starts wrapping on a 375pt phone.
  return s.length > 4 ? `${s.slice(0, 4).join(' · ')} +${s.length - 4}` : s.join(' · ')
})
</script>

<style scoped>
.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}

/* ── Art treatments ──────────────────────────────────────────────── */
.hero-art {
  position: relative;
  flex: none;
  display: grid;
  place-items: center;
}
.hero-art img {
  width: 100%;
  height: 100%;
  /**
   * `aspect-ratio` is doing the real work here, and without it the art
   * escapes its own frame.
   *
   * `.hero-art` is `display: grid` with an AUTO row, so the row's height
   * depends on its content — and an item asking for `height: 100%` of a row
   * that is sized by that item is a cyclic dependency, which browsers
   * resolve by treating the percentage as `auto`. The image then lays out at
   * its natural aspect: the robot art is a 576x1024 portrait, so in a 132px
   * frame it rendered **132 x 234** and spilled 102px out the bottom, over
   * the card and straight through the robot's name. Measured exactly that on
   * Neon Frame.
   *
   * Avatar mode had the same broken geometry and merely hid it behind
   * `overflow: hidden` — the image was still 120x213, so `object-position`
   * was being applied to a box that had already been cropped by the parent,
   * and the crop everyone tuned was not the crop being shown.
   *
   * A square ratio makes the height fall out of the width with no cycle, so
   * `object-fit: cover` gets a real box to crop into and the framed art can
   * keep `overflow: visible` for its ring glow.
   */
  aspect-ratio: 1;
  object-fit: cover;
  object-position: 50% 38%;
  display: block;
}

/* Avatar — a contained portrait. */
.art-avatar {
  width: 120px;
  height: 120px;
  border-radius: var(--cc-r-card-lg);
  overflow: hidden;
  border: 1px solid var(--cc-border);
  box-shadow: var(--cc-shadow-raised);
}
.hero-lg .art-avatar { width: 168px; height: 168px }
.hero-sm .art-avatar { width: 84px;  height: 84px }

/* Framed — the Neon Frame treatment: circular, with a glowing ring
   that is a separate element so the glow is not clipped by the
   overflow:hidden the image crop needs. */
.art-framed {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  overflow: visible;
}
.hero-lg .art-framed { width: 180px; height: 180px }
.hero-sm .art-framed { width: 92px;  height: 92px }

.art-framed img {
  border-radius: 50%;
  border: 1px solid var(--cc-border);
}
.hero-ring {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 1.5px solid var(--cc-accent);
  box-shadow: var(--cc-glow-halo);
  pointer-events: none;
}

/* ── Copy ────────────────────────────────────────────────────────── */
.hero-copy {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
}

/* A handle is a name, not a label — mono and un-tracked, the way v2
   sets it, rather than the wide uppercase of .cc-label. */
.hero-mentor {
  font-family: var(--cc-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.02em;
  text-transform: none;
  color: var(--cc-accent-text);
}

/* ── Verified badge ──────────────────────────────────────────────── */
.hero-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px 4px 6px;
  border-radius: var(--cc-r-chip);
  /* Its own scrim. This sits on raw mentor art, where neither the card
     tokens nor the layout scrims reach. */
  background: rgba(0, 0, 0, 0.52);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #FFF;
}
.hero-badge > span {
  font-family: var(--cc-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #FFF;
}
.hero-badge svg { color: var(--cc-accent); flex: none }

.hero-name {
  margin: 0;
  font-size: calc(26px * var(--cc-name-scale));
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.015em;
  color: var(--cc-name-color);
  /* The doc's requirement that the robot name stay legible at
     thumbnail size — long names shrink rather than wrap to three
     lines and push the ignition control off screen. */
  overflow-wrap: anywhere;
}
.hero-lg .hero-name { font-size: calc(34px * var(--cc-name-scale)) }
.hero-sm .hero-name { font-size: calc(20px * var(--cc-name-scale)) }

.hero-symbols {
  margin: 0;
  font-family: var(--cc-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--cc-ink-3);
}

/* Full-bleed puts the copy over the art itself, so it sits lower and
   leans on the bottom scrim rather than a card. */
.hero-bleed {
  justify-content: flex-end;
  gap: 0;
}
</style>
