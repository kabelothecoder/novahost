<template>
  <!--
    A live miniature of the Home v4 layout (design 1q / 2r).

    It renders the real layout component in `mini` mode — not a diagram of
    it — so the tile plays the user's own robot art in its accent with the
    chosen Falling FX, and cannot drift from the screen it describes. Mini
    mode is non-interactive and draws effects at reduced resolution.
  -->
  <div class="pv" aria-hidden="true">
    <CommandCore v-if="id === 'core'" mini />
    <RobotHome v-else mini :layout="id === 'focus' ? 'focus' : id === 'classic' ? 'classic' : 'full'" />
  </div>
</template>

<script setup>
import RobotHome from '../home/v4/RobotHome.vue'
import CommandCore from '../home/v4/CommandCore.vue'

defineProps({
  id: { type: String, required: true },
})
</script>

<style scoped>
.pv {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
}
</style>
