<template>
  <div class="vt">
    <div class="vt-rings">
      <div class="vt-ring-outer" :class="locked ? 'vt-ring-done' : 'vt-ring-spin'" />
      <div class="vt-ring-inner" :class="{ 'vt-ring-inner-done': locked }" />

      <NovaLogoMark :size="150" />

      <!-- Check badge lands only once the bind completes -->
      <div v-if="locked" class="vt-check">
        <svg width="26" height="22" viewBox="0 0 26 22" fill="none" aria-hidden="true">
          <path
            d="M4 11.4 L10 17 L22 4.6"
            stroke="var(--nv-success)"
            stroke-width="3.4"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-dasharray="30"
            class="vt-draw"
          />
        </svg>
      </div>
    </div>

    <!-- Working: three thinking dots. Done: the bound badge. -->
    <div v-if="!locked" class="vt-dots">
      <span class="vt-dot" />
      <span class="vt-dot" style="animation-delay:200ms" />
      <span class="vt-dot" style="animation-delay:400ms" />
    </div>

    <div v-else class="vt-bound">
      <span class="vt-bound-dot" />
      <span class="vt-bound-label nv-mono">DEVICE BOUND</span>
    </div>
  </div>
</template>

<script setup>
import NovaLogoMark from '../NovaLogoMark.vue'

defineProps({
  locked: { type: Boolean, default: false },
})
</script>

<style scoped>
.vt {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 30px;
}

.vt-rings {
  position: relative;
  width: 212px;
  height: 212px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.vt-ring-outer {
  position: absolute;
  inset: 0;
  border-radius: 999px;
}

.vt-ring-spin {
  border: 1.5px dashed rgba(92, 156, 230, 0.55);
  animation: nhSpin 9000ms linear infinite;
}

.vt-ring-done { border: 1.5px solid rgba(47, 163, 107, 0.35); }

.vt-ring-inner {
  position: absolute;
  inset: 22px;
  border-radius: 999px;
  border: 1.5px solid rgba(162, 136, 227, 0.28);
}

.vt-ring-inner-done { border-color: rgba(47, 163, 107, 0.2); }

.vt-check {
  position: absolute;
  right: 26px;
  bottom: 30px;
  width: 46px;
  height: 46px;
  border-radius: 999px;
  background: var(--nv-surface);
  box-shadow: 0 10px 22px rgba(20, 22, 26, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: nhPop 380ms var(--nv-spring) both;
}

.vt-draw { animation: nhDraw 440ms var(--nv-ease) 120ms both; }

.vt-dots {
  display: flex;
  align-items: center;
  gap: 9px;
}

.vt-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--nv-accent);
  animation: nhEyes 1200ms ease-in-out infinite;
}

.vt-bound {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: var(--nv-success-tint);
  animation: nhUp 420ms var(--nv-ease) 220ms both;
}

.vt-bound-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--nv-success);
}

.vt-bound-label {
  font-size: 10.5px;
  color: var(--nv-success-text);
}
</style>
