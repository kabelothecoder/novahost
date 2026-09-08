<template>
  <div class="wa">
    <div class="wa-glow" />
    <div class="wa-float">
      <NovaLogoMark :size="248" />
    </div>
  </div>
</template>

<script setup>
import NovaLogoMark from '../NovaLogoMark.vue'
</script>

<style scoped>
.wa {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.wa-glow {
  position: absolute;
  width: 280px;
  height: 280px;
  border-radius: 999px;
  background: radial-gradient(
    circle,
    rgba(162, 136, 227, 0.34),
    rgba(92, 156, 230, 0.12) 55%,
    transparent 72%
  );
  animation: nhGlow 4200ms ease-in-out infinite;
}

.wa-float {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: nhFloat 5200ms ease-in-out infinite;
}
</style>
