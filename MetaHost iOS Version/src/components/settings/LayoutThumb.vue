<template>
  <button
    class="thumb"
    :class="{ 'is-active': active }"
    :aria-pressed="active"
    @click="$emit('pick', id)"
  >
    <span class="thumb-frame">
      <!--
        Wireframes, not screenshots. The doc asks for thumbnails rather
        than a list, and a real screenshot would be wrong the moment the
        accent, art or widget arrangement changed. These show STRUCTURE,
        which is the thing actually being chosen.
      -->
      <svg viewBox="0 0 60 104" class="thumb-svg" aria-hidden="true">
        <!-- chrome common to every layout -->
        <rect class="w-dim" x="8"  y="7" width="10" height="3" rx="1.5" />
        <rect class="w-dim" x="24" y="6" width="16" height="5" rx="2.5" />
        <rect class="w-dim" x="46" y="7" width="6"  height="3" rx="1.5" />

        <template v-if="id === 'classic'">
          <rect class="w-art"  x="21" y="18" width="18" height="18" rx="4" />
          <rect class="w-ink"  x="16" y="40" width="28" height="4" rx="2" />
          <rect class="w-acc"  x="17" y="49" width="26" height="8" rx="4" />
          <rect class="w-card" x="8"  y="63" width="44" height="13" rx="3" />
          <rect class="w-card" x="8"  y="79" width="44" height="13" rx="3" />
        </template>

        <template v-else-if="id === 'focus'">
          <rect class="w-art" x="0" y="0" width="60" height="104" />
          <rect class="w-scr" x="0" y="0" width="60" height="104" />
          <rect class="w-acc-ink" x="15" y="38" width="30" height="6" rx="3" />
          <rect class="w-ink" x="11" y="48" width="38" height="6" rx="3" />
          <rect class="w-acc" x="17" y="62" width="26" height="9" rx="4.5" />
        </template>

        <template v-else-if="id === 'bleed'">
          <rect class="w-art" x="0" y="0" width="60" height="104" />
          <rect class="w-scr-b" x="0" y="46" width="60" height="58" />
          <rect class="w-ink" x="13" y="62" width="34" height="6" rx="3" />
          <rect class="w-dim" x="19" y="72" width="22" height="3" rx="1.5" />
          <rect class="w-acc" x="17" y="80" width="26" height="9" rx="4.5" />
        </template>

        <template v-else-if="id === 'neon'">
          <rect class="w-frame" x="6" y="16" width="48" height="60" rx="6" />
          <circle class="w-ring" cx="30" cy="33" r="10" />
          <rect class="w-ink" x="18" y="49" width="24" height="4" rx="2" />
          <rect class="w-acc" x="17" y="58" width="26" height="8" rx="4" />
          <rect class="w-card" x="8" y="81" width="44" height="12" rx="3" />
        </template>

        <template v-else-if="id === 'feed'">
          <rect class="w-ink" x="8" y="17" width="26" height="5" rx="2.5" />
          <rect class="w-acc" x="17" y="26" width="26" height="8" rx="4" />
          <rect class="w-card" x="8" y="39" width="44" height="10" rx="2.5" />
          <rect class="w-dim" x="8"  y="54" width="14" height="3" rx="1.5" />
          <rect class="w-card" x="8" y="60" width="44" height="12" rx="2.5" />
          <rect class="w-card" x="8" y="75" width="44" height="12" rx="2.5" />
        </template>

        <!-- The bento is the whole point of this layout, so the
             wireframe leads with the uneven grid: one tall hero cell
             carrying the gauge, a stack of stat tiles beside it. -->
        <template v-else-if="id === 'deck'">
          <rect class="w-card" x="8"  y="16" width="21" height="27" rx="3" />
          <circle class="w-ring" cx="18.5" cy="29.5" r="6.5" />
          <rect class="w-card" x="31" y="16" width="21" height="6" rx="2" />
          <rect class="w-card" x="31" y="23" width="21" height="6" rx="2" />
          <rect class="w-card" x="31" y="30" width="21" height="6" rx="2" />
          <rect class="w-card" x="31" y="37" width="21" height="6" rx="2" />
          <rect class="w-acc"  x="17" y="47" width="26" height="8" rx="4" />
          <rect class="w-card" x="8"    y="59" width="9.5" height="9" rx="2" />
          <rect class="w-card" x="19.3" y="59" width="9.5" height="9" rx="2" />
          <rect class="w-card" x="30.6" y="59" width="9.5" height="9" rx="2" />
          <rect class="w-card" x="41.9" y="59" width="9.5" height="9" rx="2" />
          <rect class="w-card" x="8" y="72" width="44" height="9" rx="2.5" />
          <rect class="w-card" x="8" y="83" width="44" height="9" rx="2.5" />
          <rect class="w-dim"  x="8" y="95" width="30" height="3" rx="1.5" />
        </template>
      </svg>
    </span>

    <span class="thumb-name">{{ name }}</span>
  </button>
</template>

<script setup>
defineProps({
  id:     { type: String, required: true },
  name:   { type: String, required: true },
  active: { type: Boolean, default: false },
})
defineEmits(['pick'])
</script>

<style scoped>
.thumb {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  min-width: 0;
}

.thumb-frame {
  display: block;
  width: 100%;
  aspect-ratio: 60 / 104;
  border-radius: 10px;
  overflow: hidden;
  border: 1.5px solid var(--cc-border);
  background: var(--cc-canvas-2);
  transition: border-color 180ms var(--cc-ease), box-shadow 180ms var(--cc-ease);
}
.thumb.is-active .thumb-frame {
  border-color: var(--cc-accent);
  box-shadow: var(--cc-glow-edge);
}

.thumb-svg { display: block; width: 100%; height: 100% }

.thumb-name {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--cc-ink-4);
  text-align: center;
  /* Two-word names must not force the 3-column grid wider. */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.thumb.is-active .thumb-name { color: var(--cc-ink) }

/* Wireframe parts. Kept as classes so the whole set retints with the
   accent and the scheme instead of being hardcoded per shape. */
.w-dim   { fill: var(--cc-ink-5); opacity: .55 }
.w-ink   { fill: var(--cc-ink-3) }
.w-card  { fill: var(--cc-card); stroke: var(--cc-border); stroke-width: 1 }
.w-acc   { fill: var(--cc-accent) }
.w-acc-ink { fill: var(--cc-accent); opacity: .85 }
.w-art   { fill: var(--cc-ink-5); opacity: .35 }
.w-scr   { fill: var(--cc-canvas); opacity: .45 }
.w-scr-b { fill: var(--cc-canvas); opacity: .72 }
.w-frame { fill: none; stroke: var(--cc-accent); stroke-width: 1.25; opacity: .8 }
.w-ring  { fill: var(--cc-ink-5); stroke: var(--cc-accent); stroke-width: 1.25; opacity: .9 }
</style>
