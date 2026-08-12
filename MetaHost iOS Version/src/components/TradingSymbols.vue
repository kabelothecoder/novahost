<template>
  <section class="symbols-section">
    <div class="section-header">
      <h2 class="section-title">Markets</h2>
      <span class="section-count">{{ pairs.length }} pairs</span>
    </div>

    <!-- CSS Grid: pair selection grid -->
    <div class="pairs-grid" role="list">
      <button
        v-for="pair in pairs"
        :key="pair.id"
        class="pair-card glass liquid-glass"
        :class="{ selected: selectedId === pair.id }"
        role="listitem"
        :aria-pressed="selectedId === pair.id"
        :aria-label="`Select ${pair.label} pair`"
        @click="selectPair(pair)"
      >
        <!-- Category tag -->
        <span class="pair-category" :class="`cat-${pair.category}`">
          {{ pair.category.toUpperCase() }}
        </span>

        <!-- Symbol -->
        <div class="pair-symbol-row">
          <div class="pair-icon-wrap" :class="`icon-${pair.category}`">
            <span class="pair-icon">{{ pair.icon }}</span>
          </div>
          <div class="pair-labels">
            <span class="pair-base">{{ pair.base }}</span>
            <span class="pair-separator">/</span>
            <span class="pair-quote">{{ pair.quote }}</span>
          </div>
        </div>

        <!-- Mock price display -->
        <div class="pair-price-row">
          <span class="pair-price" :class="pair.direction">
            {{ pair.price }}
          </span>
          <span class="pair-change" :class="pair.direction">
            {{ pair.change }}
          </span>
        </div>

        <!-- Selected indicator -->
        <div v-if="selectedId === pair.id" class="selected-ring" />
      </button>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'

const emit = defineEmits(['select'])

const selectedId = ref(null)

const pairs = ref([
  { id: 'eurusd', base: 'EUR', quote: 'USD', label: 'EURUSD', category: 'forex',  icon: '🇪🇺', price: '1.0842', change: '+0.12%', direction: 'up'   },
  { id: 'gbpusd', base: 'GBP', quote: 'USD', label: 'GBPUSD', category: 'forex',  icon: '🇬🇧', price: '1.2634', change: '-0.08%', direction: 'down' },
  { id: 'usdjpy', base: 'USD', quote: 'JPY', label: 'USDJPY', category: 'forex',  icon: '🇯🇵', price: '151.22', change: '+0.31%', direction: 'up'   },
  { id: 'xauusd', base: 'XAU', quote: 'USD', label: 'XAUUSD', category: 'metals', icon: '🥇', price: '2385.4', change: '+0.55%', direction: 'up'   },
  { id: 'btcusd', base: 'BTC', quote: 'USD', label: 'BTCUSD', category: 'crypto', icon: '₿',  price: '68,240', change: '+2.14%', direction: 'up'   },
  { id: 'ethusd', base: 'ETH', quote: 'USD', label: 'ETHUSD', category: 'crypto', icon: 'Ξ',  price: '3,491',  change: '-1.02%', direction: 'down' },
  { id: 'sp500',  base: 'S&P', quote: '500', label: 'SP500',  category: 'index',  icon: '📈', price: '5,243',  change: '+0.44%', direction: 'up'   },
  { id: 'nas100', base: 'NAS', quote: '100', label: 'NAS100', category: 'index',  icon: '💠', price: '18,340', change: '-0.23%', direction: 'down' },
])

function selectPair(pair) {
  selectedId.value = selectedId.value === pair.id ? null : pair.id
  emit('select', pair)
}
</script>

<style scoped>
.symbols-section {
  width: 100%;
  padding: 0 16px;
  animation: fade-in-up 0.9s 0.2s var(--ease-spring) both;
}

.section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.section-count {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
}

/* ── CSS Grid: auto-fill with 160px min ── */
.pairs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

/* ── Pair Card ── */
.pair-card {
  position: relative;
  border-radius: 16px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;
  border: none;
  text-align: left;
  outline: none;
  transition:
    transform 0.2s var(--ease-spring),
    box-shadow 0.2s var(--ease-smooth),
    background 0.2s var(--ease-smooth);
  -webkit-user-select: none;
  user-select: none;
}

.pair-card:active {
  transform: scale(0.97);
}

.pair-card.selected {
  background: rgba(0, 212, 255, 0.08);
  border-color: rgba(0, 212, 255, 0.5);
  box-shadow:
    0 0 0 1.5px rgba(0, 212, 255, 0.5),
    0 8px 24px rgba(0, 212, 255, 0.15),
    inset 0 0 20px rgba(0, 212, 255, 0.05);
  transform: scale(1.02);
}

/* ── Category Tag ── */
.pair-category {
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.1em;
  padding: 2px 6px;
  border-radius: 4px;
  width: fit-content;
}

.cat-forex  { background: rgba(0,212,255,0.12); color: var(--color-cyan); }
.cat-crypto { background: rgba(124,58,237,0.15); color: #a78bfa; }
.cat-metals { background: rgba(245,158,11,0.15); color: var(--color-gold); }
.cat-index  { background: rgba(34,197,94,0.12);  color: #4ade80; }

/* ── Symbol Row ── */
.pair-symbol-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pair-icon-wrap {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.icon-forex  { background: var(--color-cyan-dim); }
.icon-crypto { background: var(--color-violet-dim); }
.icon-metals { background: var(--color-gold-dim); }
.icon-index  { background: rgba(34,197,94,0.1); }

.pair-labels {
  display: flex;
  align-items: center;
  gap: 1px;
}

.pair-base {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.pair-separator {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin: 0 1px;
}

.pair-quote {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

/* ── Price Row ── */
.pair-price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pair-price {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  font-weight: 600;
}

.pair-change {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 500;
  padding: 2px 5px;
  border-radius: 4px;
}

.up   { color: #4ade80; }
.down { color: #f87171; }

.up.pair-change   { background: rgba(34,197,94,0.1); }
.down.pair-change { background: rgba(248,113,113,0.1); }

/* ── Selected Glow Ring ── */
.selected-ring {
  position: absolute;
  inset: -1px;
  border-radius: 16px;
  border: 1.5px solid rgba(0, 212, 255, 0.6);
  pointer-events: none;
  animation: pulse-ring 2s ease-in-out infinite;
}
</style>
