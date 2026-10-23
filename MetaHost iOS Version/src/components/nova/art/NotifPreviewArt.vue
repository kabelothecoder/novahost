<template>
  <div class="np">
    <!-- A real iOS notification is a glass card, so this one is too. -->
    <div class="np-card nv-glass">
      <div class="np-icon">
        <NovaLogoMark :size="36" alt="" />
      </div>
      <div class="np-text">
        <div class="np-head">
          <span class="np-app">NovaHost</span>
          <span class="np-time">now</span>
        </div>
        <p class="np-preview">{{ preview }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * @description Live preview of how the bot will address the user, shown
 * above the display-name field. Updates as they type.
 */
import NovaLogoMark from '../NovaLogoMark.vue'

defineProps({
  preview: { type: String, required: true },
})
</script>

<style scoped>
.np {
  display: flex;
  align-items: center;
  justify-content: center;
}

.np-card {
  width: 268px;
  border-radius: 18px;
  padding: 13px 15px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
  animation: nhUp 520ms var(--nv-ease) 120ms both;
}

.np-icon {
  flex: none;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.np-text { min-width: 0; }

.np-head {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin-bottom: 3px;
}

.np-app {
  font-size: 12px;
  font-weight: 600;
  color: var(--nv-ink);
}

.np-time {
  font-family: var(--nv-mono);
  font-size: 9.5px;
  color: var(--nv-ink-3);
}

.np-preview {
  margin: 0;
  font-size: 12.5px;
  line-height: 18px;
  color: var(--nv-ink-2);
}
</style>
