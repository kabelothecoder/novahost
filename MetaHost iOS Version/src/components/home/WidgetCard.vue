<template>
  <section class="wcard cc-card">
    <header v-if="label || $slots.action" class="wcard-head">
      <span class="cc-label">{{ label }}</span>
      <slot name="action" />
    </header>
    <slot />
  </section>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
})
</script>

<style scoped>
/* The shell every reorderable widget wears, so Card Style (glass / neon
   border / solid) is honoured in one place instead of five. */
.wcard {
  padding: 14px 16px 16px;
  width: 100%;
}

.wcard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  min-height: 18px;
}
</style>
