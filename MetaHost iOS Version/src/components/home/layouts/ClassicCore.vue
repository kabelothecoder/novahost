<template>
  <!--
    1 · CLASSIC CORE — "reworked · text bottom nav".

    The conventional one: art is a contained portrait, not the screen.
    Everything stacks in reading order, so it is the only layout that
    stays sensible with every widget switched on.

    The doc draws its own labelled bottom nav here. This port does not
    render one — the shared FloatingDock is the app's navigation, and a
    second bar would be two competing navs on one screen.
  -->
  <div class="classic">
    <HomeTopBar @interface="$emit('interface')" @robots="$emit('robots')" />

    <div class="classic-hero">
      <RobotHeroWidget size="md" />
    </div>

    <!-- v2 replaces the pill here with the orbit disc. -->
    <IgnitionPod variant="orbit" class="classic-pod" @assets="$emit('assets')" @toast="$emit('toast', $event)" />

    <div v-if="widgets.length" class="classic-widgets cc-ground cc-stagger">
      <WidgetSlot v-for="id in widgets" :key="id" :id="id" @toast="$emit('toast', $event)" />
    </div>

    <span class="classic-mark cc-label">Powered by Nova Edge</span>
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
.classic {
  position: relative;
  z-index: 1;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  padding: calc(var(--cc-safe-top) + 8px) 20px calc(var(--cc-safe-bottom) + 108px);
}

.classic-hero {
  display: flex;
  justify-content: center;
  padding: 26px 0 22px;
}

.classic-pod {
  max-width: 320px;
  align-self: center;
}

.classic-widgets {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.classic-mark {
  align-self: center;
  margin-top: 22px;
  font-size: 9px;
  letter-spacing: 0.22em;
  color: var(--cc-ink-4);
  position: relative;
  z-index: 1;
}
</style>
