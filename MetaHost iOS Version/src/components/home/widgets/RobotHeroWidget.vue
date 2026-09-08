<template>
  <div class="hero" :class="[`hero-${mode}`, `hero-${size}`]">
    <!-- Art. Omitted in Full-bleed: HomeBackdrop is already showing it
         at full strength, and a second copy would sit on top of itself. -->
    <div v-if="mode !== 'bleed'" class="hero-art" :class="`art-${mode}`">
      <img :src="artSrc" alt="" decoding="async" @error="failed = true" />
      <span v-if="mode === 'framed'" class="hero-ring"></span>
    </div>

    <div class="hero-copy">
      <span v-if="robot.mentor" class="cc-label hero-mentor">
        Mentor · {{ robot.mentor }}
      </span>

      <h1 class="hero-name" :class="{ 'cc-ink-shadow': mode === 'bleed' }">
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

defineProps({
  /** Layouts set their own weight — Full-Bleed wants this enormous,
   *  Classic Core wants it modest. */
  size: { type: String, default: 'md' }, // 'sm' | 'md' | 'lg'
})

const failed = ref(false)
const mode = computed(() => activeArtMode.value)

const artSrc = computed(() => {
  if (failed.value) return FALLBACK_AVATAR
  return activeEa.value?.avatar || robot.avatarUrl || FALLBACK_AVATAR
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

.hero-mentor {
  color: var(--cc-accent-text);
}

.hero-name {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.015em;
  color: var(--cc-ink);
  /* The doc's requirement that the robot name stay legible at
     thumbnail size — long names shrink rather than wrap to three
     lines and push the ignition control off screen. */
  overflow-wrap: anywhere;
}
.hero-lg .hero-name { font-size: 34px }
.hero-sm .hero-name { font-size: 20px }

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
