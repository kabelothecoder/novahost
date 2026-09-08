<template>
  <div class="va">
    <div class="va-card">
      <img class="va-chart" :src="chart" alt="MT5 chart being read">

      <!-- Scan sweep -->
      <div class="va-scanwrap">
        <div class="va-sweep" />
      </div>

      <!-- Reticle corners -->
      <span class="va-br va-tl" />
      <span class="va-br va-tr" />
      <span class="va-br va-bl" />
      <span class="va-br va-brr" />
    </div>

    <!-- The read-back, on regular glass so the chart shows through -->
    <div class="va-signal nv-glass">
      <div class="va-row">
        <div class="va-sym">EURUSD</div>
        <div class="va-side">
          <span class="va-dot" />
          <span class="va-side-label">Buy setup</span>
        </div>
      </div>
      <div class="va-prices">
        <div class="va-cell">
          <span class="va-cell-label">Entry</span>
          <span class="va-cell-value">1.0842</span>
        </div>
        <div class="va-cell">
          <span class="va-cell-label">Stop</span>
          <span class="va-cell-value">1.0818</span>
        </div>
        <div class="va-cell">
          <span class="va-cell-label">Target</span>
          <span class="va-cell-value">1.0897</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import chart from '../../../assets/onboarding/chart_mt5.png'
</script>

<style scoped>
.va {
  position: relative;
  width: 236px;
  padding-bottom: 46px;
}

.va-card {
  position: relative;
  width: 236px;
  height: 294px;
  border-radius: 22px;
  overflow: hidden;
  background: var(--nv-surface);
  box-shadow: var(--nv-shadow-float);
}

.va-chart {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 44%;
}

.va-scanwrap {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.va-sweep {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 78px;
  background: linear-gradient(180deg, rgba(92, 156, 230, 0), rgba(92, 156, 230, 0.16));
  border-bottom: 2px solid var(--nv-accent);
  box-shadow: 0 6px 18px rgba(92, 156, 230, 0.45);
  animation: nhSweepY 2800ms cubic-bezier(0.4, 0, 0.6, 1) 500ms infinite;
}

.va-br {
  position: absolute;
  width: 26px;
  height: 26px;
  animation: nhBracket 2000ms ease-in-out infinite;
}
.va-tl {
  left: 12px; top: 12px;
  border-left: 2.5px solid var(--nv-accent);
  border-top: 2.5px solid var(--nv-accent);
  border-radius: 8px 0 0 0;
}
.va-tr {
  right: 12px; top: 12px;
  border-right: 2.5px solid var(--nv-accent);
  border-top: 2.5px solid var(--nv-accent);
  border-radius: 0 8px 0 0;
  animation-delay: 160ms;
}
.va-bl {
  left: 12px; bottom: 12px;
  border-left: 2.5px solid var(--nv-accent);
  border-bottom: 2.5px solid var(--nv-accent);
  border-radius: 0 0 0 8px;
  animation-delay: 320ms;
}
.va-brr {
  right: 12px; bottom: 12px;
  border-right: 2.5px solid var(--nv-accent);
  border-bottom: 2.5px solid var(--nv-accent);
  border-radius: 0 0 8px 0;
  animation-delay: 480ms;
}

.va-signal {
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 0;
  border-radius: 18px;
  padding: 14px 16px;
  animation: nhUp 520ms var(--nv-ease) 820ms both;
}

.va-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.va-sym {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.3px;
  color: var(--nv-ink);
}

.va-side {
  display: flex;
  align-items: center;
  gap: 6px;
}

.va-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--nv-success);
}

.va-side-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--nv-success-text);
}

.va-prices {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.va-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.va-cell-label {
  font-size: 12px;
  color: var(--nv-ink-3);
}

.va-cell-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--nv-ink);
  font-variant-numeric: tabular-nums;
}
</style>
