<template>
  <!--
    2 · FOCUS ENGINE — "art forward · floating pill".

    Distinguished from Full-Bleed Art by composition, not content:
    Full-Bleed anchors everything to the bottom scrim and sells the
    mentor; Focus Engine centres on the robot's NAME, set as stacked
    display type, and strips the screen to one control. It is the
    "get on with it" layout.
  -->
  <div class="focus">
    <HomeTopBar @interface="$emit('interface')" @robots="$emit('robots')" />

    <div class="focus-body">
      <h1 class="focus-name cc-name cc-ink-shadow">
        <span v-for="(line, i) in nameLines" :key="i" class="focus-line">{{ line }}</span>
      </h1>

      <IgnitionPod class="focus-pod" @assets="$emit('assets')" @toast="$emit('toast', $event)" />
    </div>

    <div v-if="widgets.length" class="focus-widgets cc-ground cc-stagger">
      <WidgetSlot v-for="id in widgets" :key="id" :id="id" @toast="$emit('toast', $event)" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { activeWidgets } from '../../../stores/theme.js'
import { robot } from '../../../stores/robot.js'
import HomeTopBar from '../HomeTopBar.vue'
import WidgetSlot from '../WidgetSlot.vue'
import IgnitionPod from '../widgets/IgnitionPod.vue'

defineEmits(['interface', 'robots', 'assets', 'toast'])

const widgets = computed(() => activeWidgets.value)

/**
 * The doc sets the name as stacked display lines ("ALGO / WAVE EA").
 * Splitting on words rather than characters keeps it readable for any
 * name: one word stays one line, two or three break evenly, and
 * anything longer collapses to two lines so the type can stay large
 * instead of shrinking to fit a five-line stack.
 */
const nameLines = computed(() => {
  const words = (robot.name || '').trim().split(/\s+/).filter(Boolean)
  if (words.length <= 1) return words
  if (words.length === 2) return words
  if (words.length === 3) return [words[0], words.slice(1).join(' ')]
  const mid = Math.ceil(words.length / 2)
  return [words.slice(0, mid).join(' '), words.slice(mid).join(' ')]
})
</script>

<style scoped>
.focus {
  position: relative;
  z-index: 1;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  /* 108px here reserved room for the floating dock. Focus Engine renders
     chrome-free (see DOCKLESS_LAYOUTS in App.vue), so that reservation is
     now just a dead band under the composition. 28px is the breathing
     room the last element wants against the screen edge. */
  padding: calc(var(--cc-safe-top) + 8px) 20px calc(var(--cc-safe-bottom) + 28px);
}

/* Centred, unlike Full-Bleed's bottom anchor.

   min-height rather than flex-grow: the ground below also grows, and
   two growing siblings SPLIT the free space — which pushed the name
   into the upper third instead of the centre as soon as any widget was
   shown. Sizing the composition to the first screen instead makes the
   hero own the viewport and the widgets begin below the fold, whatever
   the widget count. */
.focus-body {
  flex: 0 0 auto;
  min-height: calc(100dvh - var(--cc-safe-top) - var(--cc-safe-bottom) - 90px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 40px;
}

.focus-name {
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  text-align: center;
  text-transform: uppercase;
  color: var(--cc-name-color);
}

.focus-line {
  font-size: calc(clamp(30px, 11vw, 46px) * var(--cc-name-scale));
  font-weight: 800;
  line-height: 1.02;
  letter-spacing: -0.03em;
  /* A long single word must shrink rather than overflow the viewport. */
  overflow-wrap: anywhere;
}

/* The first line takes the accent, so the eye lands on the name before
   the control — the doc's own emphasis. */
/* Only while the name colour is Default. This layout tints the first
   line so the eye lands on the name, but once someone has explicitly
   PICKED a name colour, a half-accent name reads as a broken setting
   rather than as a treatment — the accent option still gets both lines
   accent, which is the same result by a different route. */
[data-name-color="default"] .focus-line:first-child { color: var(--cc-accent-text) }

.focus-pod { max-width: 300px }

.focus-widgets {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
