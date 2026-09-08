<template>
  <div class="ba">
    <div class="ba-row">
      <span
        v-for="(b, i) in rowOne"
        :key="b.label"
        class="ba-tile"
        :class="{ 'ba-tile-bordered': b.bordered }"
        :style="{ width: b.w + 'px', animationDelay: (100 + i * 70) + 'ms' }"
      >
        <img :src="b.src" :alt="b.label">
      </span>
    </div>

    <div class="ba-row ba-row-two">
      <span
        v-for="(b, i) in rowTwo"
        :key="b.label"
        class="ba-tile"
        :class="{ 'ba-tile-bordered': b.bordered }"
        :style="{ width: b.w + 'px', animationDelay: (310 + i * 70) + 'ms' }"
      >
        <img :src="b.src" :alt="b.label">
      </span>
    </div>

    <!-- broker -> host connector -->
    <svg class="ba-arrow" width="30" height="52" viewBox="0 0 30 52" fill="none" aria-hidden="true">
      <path
        d="M15 2 V40"
        :stroke="'var(--nv-flow-line)'"
        stroke-width="1.5"
        stroke-dasharray="5 5"
        class="ba-flow"
      />
      <path
        d="M8 35 L15 43 L22 35"
        :stroke="'var(--nv-flow-line)'"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>

    <div class="ba-host">
      <NovaLogoMark :size="124" />
    </div>
  </div>
</template>

<script setup>
import NovaLogoMark from '../NovaLogoMark.vue'
import exness      from '../../../assets/onboarding/broker_exness.png'
import trade245    from '../../../assets/onboarding/broker_trade245.png'
import xm          from '../../../assets/onboarding/broker_xm.png'
import razor       from '../../../assets/onboarding/broker_razor.png'
import justmarkets from '../../../assets/onboarding/broker_justmarkets.png'

/* Widths are per-brand so each wordmark keeps its own aspect ratio. */
const rowOne = [
  { label: 'Exness',   src: exness,   w: 55 },
  { label: 'Trade245', src: trade245, w: 71 },
  { label: 'XM',       src: xm,       w: 62 },
]

const rowTwo = [
  { label: 'Razor Markets', src: razor,       w: 115 },
  { label: 'JustMarkets',   src: justmarkets, w: 82, bordered: true },
]
</script>

<style scoped>
.ba {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ba-row {
  display: flex;
  gap: 10px;
  justify-content: center;
  align-items: center;
}

.ba-row-two { margin-top: 10px; }

.ba-tile {
  flex: none;
  height: 54px;
  border-radius: 15px;
  overflow: hidden;
  background: var(--nv-surface);
  box-shadow: var(--nv-shadow-card);
  animation: nhUp 480ms var(--nv-ease) both;
}

.ba-tile-bordered { border: 1px solid var(--nv-border-soft); }

.ba-tile img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ba-arrow { display: block; }

.ba-flow { animation: nhFlow 1400ms linear infinite; }

.ba-host {
  position: relative;
  width: 124px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: nhPop 560ms var(--nv-spring) 420ms both;
}
</style>
