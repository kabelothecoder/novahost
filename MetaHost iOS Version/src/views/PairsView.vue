<template>
  <div class="pairs-view">

    <header class="screen-header">
      <div class="header-top">
        <button class="back-btn" @click="$router.push('/home')">‹</button>
        <div>
          <h2 class="screen-title">Pair Management</h2>
          <span class="screen-sub">Permitted configurations</span>
        </div>
      </div>
    </header>

    <div class="pairs-scroll">
      <div class="allowed-list">
        <div v-for="pair in allowedPairs" :key="pair.id" class="acc-card">
          <button class="acc-header" @click="pair.open = !pair.open">
            <div class="acc-left">
              <span class="acc-icon">{{ pair.icon }}</span>
              <div>
                <span class="acc-name">{{ pair.id }}</span>
                <span class="acc-sub">{{ pair.cat.toUpperCase() }} · Lot: {{ pair.lot }}</span>
              </div>
            </div>
            <div class="acc-right">
              <span class="acc-pnl" :class="pair.pnl >= 0 ? 'pos':'neg'">
                {{ pair.pnl >= 0 ? '+':'' }}{{ pair.pnl }}%
              </span>
              <svg class="acc-chevron" :class="{ open: pair.open }" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </div>
          </button>
          <Transition name="accordion">
            <div v-if="pair.open" class="acc-body">
              <div class="input-row">
                <label class="input-label">Lot Size</label>
                <input type="number" v-model="pair.lot" step="0.01" min="0.01" class="field-glass" />
              </div>
              <div class="input-row">
                <label class="input-label">Max Trades</label>
                <input type="number" v-model="pair.maxTrades" min="1" class="field-glass" />
              </div>
              <div class="input-row">
                <label class="input-label">Stop Loss (pips)</label>
                <input type="number" v-model="pair.sl" min="0" class="field-glass" />
              </div>
              <div class="acc-actions">
                <button class="action-btn action-save" @click="pair.open=false">Save</button>
              </div>
            </div>
          </Transition>
        </div>
        <p v-if="allowedPairs.length === 0" class="empty-msg">✦ No symbols assigned by portal.<br>Please contact your admin.</p>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { accent } from '../stores/accent.js'

function getCategory(symbol) {
  if (symbol.includes('XAU') || symbol.includes('XAG')) return 'metals'
  if (symbol.includes('BTC') || symbol.includes('ETH')) return 'crypto'
  if (symbol.includes('SP500') || symbol.includes('NAS')) return 'index'
  if (symbol.includes('OIL')) return 'energy'
  return 'forex'
}

const iconMap = { forex:'💱', metals:'🥇', crypto:'₿', index:'📈', energy:'⛽' }

const allowedPairs = computed(() => {
  const symbols = accent.allowedSymbols || []
  return symbols.map(sym => {
    const cat = getCategory(sym)
    return {
      id: sym,
      cat,
      open: false,
      lot: 0.10,
      maxTrades: 3,
      sl: 50,
      pnl: +(Math.random() * 10 - 3).toFixed(1),
      icon: iconMap[cat] || '●'
    }
  })
})
</script>

<style scoped>
.pairs-view {
  width: 100%;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* ── Header ── */
.screen-header {
  padding: calc(env(safe-area-inset-top, 16px) + 16px) 20px 8px;
  position: relative;
  z-index: 2;
}
.header-top { display: flex; align-items: center; gap: 14px; }
.back-btn {
  width: 36px; height: 36px;
  border-radius: 50%;
  border: 1px solid rgba(var(--accent-rgb), 0.3);
  background: rgba(var(--accent-rgb), 0.08);
  color: var(--accent);
  font-size: 1.4rem;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  line-height: 1;
  flex-shrink: 0;
}
.screen-title {
  font-size: 1.3rem; font-weight: 800;
  color: #E8F0FF; letter-spacing: -0.02em; margin: 0;
}
.screen-sub {
  font-family: 'Courier New', monospace; font-size: 0.65rem;
  color: rgba(var(--accent-rgb), 0.5); letter-spacing: 0.1em;
  display: block; margin-top: 2px;
}



/* ── Accordion ── */
.allowed-list { display: flex; flex-direction: column; gap: 10px; }
.acc-card {
  border-radius: 14px;
  overflow: hidden;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.07);
}
.acc-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px;
  background: transparent; border: none;
  width: 100%; cursor: pointer; color: #E8F0FF;
}
.acc-left { display: flex; align-items: center; gap: 12px; }
.acc-icon { font-size: 1.2rem; }
.acc-name {
  display: block; font-size: 0.92rem; font-weight: 700;
  color: #E8F0FF; text-align: left; font-family: 'Courier New', monospace;
}
.acc-sub {
  display: block;
  font-family: 'Courier New', monospace; font-size: 0.62rem;
  color: rgba(var(--accent-rgb), 0.45); text-align: left;
}
.acc-right { display: flex; align-items: center; gap: 10px; }
.acc-pnl { font-family: 'Courier New', monospace; font-size: 0.85rem; font-weight: 700; }
.acc-pnl.pos { color: #00FF87; }
.acc-pnl.neg { color: #FF3D5A; }
.acc-chevron { transition: transform 0.3s ease; color: rgba(255,255,255,0.4); }
.acc-chevron.open { transform: rotate(180deg); }

.acc-body {
  padding: 0 16px 16px;
  display: flex; flex-direction: column; gap: 10px;
  border-top: 1px solid rgba(255,255,255,0.06);
  padding-top: 14px;
}
.input-row { display: flex; flex-direction: column; gap: 5px; }
.input-label {
  font-family: 'Courier New', monospace;
  font-size: 0.62rem; letter-spacing: 0.1em;
  color: rgba(var(--accent-rgb), 0.55);
}
.field-glass {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px;
  color: #E8F0FF;
  padding: 10px 14px;
  font-size: 0.9rem;
  font-family: 'Courier New', monospace;
  width: 100%;
  outline: none;
}
.field-glass:focus { border-color: rgba(var(--accent-rgb), 0.5); }

.acc-actions { display: flex; gap: 10px; margin-top: 4px; }
.action-btn {
  flex: 1; padding: 11px;
  border-radius: 10px;
  font-family: 'Courier New', monospace;
  font-size: 0.78rem; font-weight: 700;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: all 0.2s ease;
}
.action-save {
  background: rgba(var(--accent-rgb), 0.15);
  border: 1px solid rgba(var(--accent-rgb), 0.4);
  color: var(--accent);
}
.action-save:hover { box-shadow: 0 0 16px rgba(var(--accent-rgb), 0.3); }
.action-remove {
  background: rgba(255,61,90,0.1);
  border: 1px solid rgba(255,61,90,0.3);
  color: #FF3D5A;
  flex: 0 0 auto;
  padding: 11px 16px;
}

.empty-msg {
  text-align: center;
  color: rgba(232,240,255,0.3);
  font-size: 0.85rem;
  padding: 48px 20px;
  font-family: 'Courier New', monospace;
}

.accordion-enter-active, .accordion-leave-active {
  transition: all 0.3s ease; overflow: hidden;
}
.accordion-enter-from, .accordion-leave-to { max-height: 0; opacity: 0; }
.accordion-enter-to, .accordion-leave-from { max-height: 300px; opacity: 1; }
</style>
