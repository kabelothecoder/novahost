<template>
  <div class="nv-rail" role="progressbar" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="pct">
    <div class="nv-rail-fill" :style="{ width: pct + '%' }" />
  </div>
</template>

<script setup>
/**
 * @description Top progress rail. Sits directly under the status bar /
 * dynamic island, so it is offset by the safe-area inset rather than the
 * design's hardcoded 44px.
 */
defineProps({
  /** Completion, 0–100. */
  pct: { type: Number, required: true },
})
</script>

<style scoped>
.nv-rail {
  position: absolute;
  top: var(--nv-safe-top);
  left: 0;
  right: 0;
  height: 3px;
  z-index: 6;
  background: var(--nv-track);
}

.nv-rail-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--nv-accent-violet), var(--nv-accent));
  transition: width 460ms var(--nv-ease);
}
</style>
