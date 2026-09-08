<template>
  <!--
    3 · FULL-BLEED ART — "the marketing screen".
    The art is the screen; chrome is reduced to a single top row and the
    copy sits on the bottom scrim. By default this layout hides all five
    reorderable widgets (see DEFAULT_HIDDEN in stores/theme.js), so what
    renders is the two pinned ones over the backdrop — which is the
    point of it.
  -->
  <div class="bleed">
    <HomeTopBar @interface="$emit('interface')" @robots="$emit('robots')" />

    <div class="bleed-body">
      <RobotHeroWidget size="lg" />
      <IgnitionPod class="bleed-pod" @assets="$emit('assets')" @toast="$emit('toast', $event)" />
    </div>

    <!-- Any widget the user un-hides lands here, below the fold, so it
         never competes with the marketing composition above it. The
         ground is required, not decorative: stacked glass over the same
         full-bleed art shows the art through every card at once. -->
    <div v-if="widgets.length" class="bleed-widgets cc-ground cc-stagger">
      <WidgetSlot v-for="id in widgets" :key="id" :id="id" @toast="$emit('toast', $event)" />
    </div>

    <span class="bleed-mark cc-label">Powered by Nova Edge</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { activeWidgets } from '../../../stores/theme.js'
import HomeTopBar from '../HomeTopBar.vue'
import WidgetSlot from '../WidgetSlot.vue'
import RobotHeroWidget from '../widgets/RobotHeroWidget.vue'
import IgnitionPod from '../widgets/IgnitionPod.vue'

defineEmits(['interface', 'robots', 'assets', 'toast'])

const widgets = computed(() => activeWidgets.value)
</script>

<style scoped>
.bleed {
  position: relative;
  z-index: 1;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  padding: calc(var(--cc-safe-top) + 8px) 20px calc(var(--cc-safe-bottom) + 108px);
}

/* The composition: everything is pushed to the lower third so the art
   behind it reads, and the bottom scrim does the legibility work.

   min-height, not flex-grow — the ground below grows too, and two
   growing siblings split the free space, which lifted the composition
   off the bottom as soon as a widget was shown. */
.bleed-body {
  flex: 0 0 auto;
  min-height: calc(100dvh - var(--cc-safe-top) - var(--cc-safe-bottom) - 170px);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  gap: 26px;
  padding-bottom: 18px;
}

.bleed-pod { max-width: 320px }

/* No margin-top: .cc-ground supplies the 34px lead-in that the
   gradient fades across. */
.bleed-widgets {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Sits directly on the art with no card behind it, so it takes ink-3
   and a scrim-coloured shadow. ink-4 measured 2.84:1 in light mode and
   ink-5 vanished entirely on darker mentor images; ink-3 clears 4.5.
   The shadow has to flip with the scheme — a black glow under dark
   text on a pale backdrop just makes it muddy. */
.bleed-mark {
  align-self: center;
  margin-top: 18px;
  font-size: 9px;
  letter-spacing: 0.22em;
  color: var(--cc-ink-3);
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.60);
  /* The ground above runs off the bottom of the layout to cover the
     fixed backdrop, and as a positioned element it would paint over
     this static one. Lifting it into the same layer keeps it visible. */
  position: relative;
  z-index: 1;
}
.cmd[data-scheme="light"] .bleed-mark {
  text-shadow: 0 1px 8px rgba(255, 255, 255, 0.85);
}
</style>
