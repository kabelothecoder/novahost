<template>
  <div class="ca">
    <span
      v-for="(c, i) in candles"
      :key="i"
      class="ca-candle"
      :style="{
        height: c.h + '%',
        background: c.accent || 'var(--nv-candle-idle)',
        animationDelay: (120 + i * 50) + 'ms',
      }"
    />
  </div>
</template>

<script setup>
const candles = [
  { h: 36 },
  { h: 58 },
  { h: 44 },
  { h: 76,  accent: 'var(--nv-accent)' },
  { h: 52 },
  { h: 100, accent: 'var(--nv-accent-violet)' },
  { h: 64 },
]
</script>

<style scoped>
.ca {
  display: flex;
  align-items: flex-end;
  gap: 5px;
  height: 56px;
}

.ca-candle {
  width: 7px;
  border-radius: 3px;
  transform-origin: bottom;
  animation: nhCandle 520ms var(--nv-ease) both;
}
</style>
