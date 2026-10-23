<template>
  <div class="sp">
    <!-- The chart, still working -->
    <div class="sp-chart">
      <img :src="chart" alt="Chart still running">

      <div class="sp-ticks">
        <span class="sp-tick" style="height:34px;background:var(--nv-accent)" />
        <span class="sp-tick" style="height:50px;background:var(--nv-accent-violet);animation-delay:280ms" />
        <span class="sp-tick" style="height:26px;background:var(--nv-accent);animation-delay:560ms" />
      </div>

      <div class="sp-pill sp-pill-live nv-glass-thin">
        <span class="sp-live-dot" />
        <span class="sp-live-label">Still trading</span>
      </div>
    </div>

    <!-- The phone, asleep -->
    <div class="sp-phone">
      <div class="sp-phone-ui">
        <span class="sp-sk" style="width:54%;height:8px" />
        <span class="sp-block" style="height:48px" />
        <span class="sp-block" style="height:30px" />
        <span class="sp-sk" style="width:70%;height:8px" />
      </div>
      <div class="sp-dim" />
      <div class="sp-indicator"><span /></div>
    </div>

    <div class="sp-pill sp-pill-sleep nv-glass-thin">
      <svg width="15" height="15" viewBox="0 0 15 15" fill="none" aria-hidden="true">
        <path d="M13 9.4 A6 6 0 1 1 5.6 2 A6.8 6.8 0 0 0 13 9.4 Z" fill="var(--nv-ink-3)" />
      </svg>
      <span class="sp-sleep-label">Phone asleep</span>
    </div>
  </div>
</template>

<script setup>
import chart from '../../../assets/onboarding/chart_mt5.png'
</script>

<style scoped>
.sp {
  position: relative;
  width: 300px;
  height: 314px;
}

.sp-chart {
  position: absolute;
  left: 16px;
  top: 0;
  width: 224px;
  height: 262px;
  border-radius: 22px;
  overflow: hidden;
  background: var(--nv-surface);
  box-shadow: 0 22px 50px rgba(20, 22, 26, 0.16);
  animation: nhUp 560ms var(--nv-ease) 120ms both;
}

.sp-chart img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 44% 42%;
}

.sp-ticks {
  position: absolute;
  right: 18px;
  bottom: 74px;
  display: flex;
  align-items: flex-end;
  gap: 5px;
  height: 56px;
}

.sp-tick {
  width: 6px;
  border-radius: 2px;
  transform-origin: bottom;
  animation: nhTick 1600ms ease-in-out infinite;
}

.sp-pill {
  display: flex;
  align-items: center;
  border-radius: 999px;
}

.sp-pill-live {
  position: absolute;
  left: 14px;
  top: 14px;
  gap: 7px;
  padding: 6px 12px;
}

.sp-live-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--nv-success);
  animation: nhEyes 1600ms ease-in-out infinite;
}

.sp-live-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--nv-success-text);
}

.sp-phone {
  position: absolute;
  right: 0;
  bottom: 16px;
  width: 112px;
  height: 206px;
  border-radius: 26px;
  border: 3px solid var(--nv-ink);
  background: var(--nv-surface);
  overflow: hidden;
  box-shadow: 0 22px 48px rgba(20, 22, 26, 0.26);
  transform: rotate(5deg);
  animation: nhUp 560ms var(--nv-ease) 300ms both;
}

.sp-phone-ui {
  position: absolute;
  inset: 0;
  padding: 14px 11px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sp-sk    { border-radius: 4px;  background: var(--nv-skeleton); }
.sp-block { width: 100%; border-radius: 10px; background: var(--nv-placeholder); }

/* The screen going dark and coming back — the whole point of the step. */
.sp-dim {
  position: absolute;
  inset: 0;
  background: var(--nv-phone-sleep);
  animation: nhDim 6200ms ease-in-out infinite;
}

.sp-indicator {
  position: absolute;
  bottom: 8px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
}

.sp-indicator span {
  width: 44px;
  height: 3px;
  border-radius: 999px;
  background: var(--nv-ink-3);
  opacity: 0.55;
}

.sp-pill-sleep {
  position: absolute;
  left: 0;
  bottom: 2px;
  gap: 9px;
  padding: 9px 15px;
  animation: nhUp 480ms var(--nv-ease) 480ms both;
}

.sp-sleep-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--nv-ink-2);
}
</style>
