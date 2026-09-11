<template>
  <component
    :is="component"
    v-if="component"
    @toast="$emit('toast', $event)"
  />
</template>

<script setup>
import { computed, defineAsyncComponent } from 'vue'

/**
 * Maps a widget id from the theme store's arrangement to its component.
 *
 * Async so a layout only pays for the widgets it actually shows — three
 * of the five layouts hide most of them by default, and the chart
 * scanner in particular pulls in a heavy dependency chain.
 *
 * An id with no entry renders NOTHING rather than a placeholder. A
 * stored arrangement from an older build can name a widget that no
 * longer exists; theme.js filters those out on load, but this is the
 * second line of defence and an empty slot is the correct outcome —
 * a visible "missing widget" box would be a bug report waiting to
 * happen for a widget the user never asked for.
 */
const REGISTRY = {
  openTrades:   () => import('./widgets/OpenTradesWidget.vue'),
  systemHealth: () => import('./widgets/SystemHealthWidget.vue'),
  robotList:    () => import('./widgets/RobotListWidget.vue'),
  fleetStatus:  () => import('./widgets/FleetStatusWidget.vue'),
  voiceConsole: () => import('./widgets/VoiceConsoleWidget.vue'),
  chartScanner: () => import('./widgets/ChartScannerWidget.vue'),
  terminalLog:  () => import('./widgets/TerminalLogWidget.vue'),
}

const props = defineProps({
  id: { type: String, required: true },
})
defineEmits(['toast'])

const component = computed(() => {
  const loader = REGISTRY[props.id]
  return loader ? defineAsyncComponent(loader) : null
})
</script>
