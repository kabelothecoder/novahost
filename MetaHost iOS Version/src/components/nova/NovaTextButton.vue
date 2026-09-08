<template>
  <button class="ntb" type="button" @click="$emit('click')">
    <span class="ntb-label"><slot /></span>
  </button>
</template>

<script setup>
/**
 * @description Quiet secondary action. Padded to Apple's 44pt minimum tap
 * target even though the label itself is much shorter than that.
 */
defineEmits(['click'])
</script>

<style scoped>
.ntb {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 16px;
  border: 0;
  background: none;
  font-family: inherit;
  cursor: pointer;
  transition: opacity 140ms linear;
}

.ntb:active { opacity: 0.5; }

.ntb-label {
  font-size: 15px;
  font-weight: 500;
  color: var(--nv-ink-3);
}
</style>
