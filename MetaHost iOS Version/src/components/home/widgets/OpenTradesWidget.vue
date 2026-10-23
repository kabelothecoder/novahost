<template>
  <WidgetCard label="Open Trades">
    <div class="ot">
      <!-- Headline: what the account is doing right now, in one line. -->
      <button class="ot-head" @click="open">
        <div class="ot-head-l">
          <span class="ot-count">
            {{ trades.openCount }}<span class="ot-count-u">{{ trades.openCount === 1 ? ' open' : ' open' }}</span>
          </span>
          <span v-if="trades.pendingCount" class="cc-caption">
            + {{ trades.pendingCount }} waiting
          </span>
          <span v-else-if="subline" class="cc-caption">{{ subline }}</span>
        </div>

        <span class="ot-pl" :data-tone="tone(trades.floatingNet)">
          {{ trades.floatingNet == null ? '—' : money(trades.floatingNet) }}
        </span>
      </button>

      <!-- At most three. The widget is a glance; the list is /trades. -->
      <div v-if="rows.length" class="ot-rows">
        <button v-for="p in rows" :key="p.id || p.symbol + p.open_time" class="ot-row" @click="open">
          <span class="ot-side" :data-side="p.side">{{ p.side }}</span>
          <span class="ot-sym">{{ p.symbol || p.base }}</span>
          <span v-if="p.pending" class="ot-row-r cc-caption">waiting</span>
          <span v-else class="ot-row-r ot-row-pl" :data-tone="tone(p.net_profit)">
            {{ money(p.net_profit) }}
          </span>
        </button>
        <button v-if="trades.positions.length > rows.length" class="ot-more" @click="open">
          {{ trades.positions.length - rows.length }} more
        </button>
      </div>

      <!-- Each of these is a different situation with a different answer, so
           none of them collapse into a shared "nothing to show". -->
      <p v-else-if="noAccount" class="ot-msg cc-caption">
        No broker linked yet — connect one to see your trades.
      </p>
      <p v-else-if="trades.positionsError" class="ot-msg ot-err cc-caption">
        {{ trades.positionsError }}
      </p>
      <p v-else-if="trades.positionsLoading" class="ot-msg cc-caption">Checking…</p>
      <p v-else class="ot-msg cc-caption">Nothing open right now.</p>
    </div>
  </WidgetCard>
</template>

<script setup>
/**
 * Open positions, on Home.
 *
 * A glance, not a screen: count, floating P/L, and at most three rows. Every
 * one of them is a doorway to /trades rather than a control — the widget owns
 * no actions, so there is nowhere here a user can accidentally change their
 * open risk.
 *
 * The poll is reference-counted in the store, so this widget and the Trades
 * screen being open at once cost one request, and the first of the two to
 * unmount does not stop the other's updates.
 */
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import WidgetCard from '../WidgetCard.vue'
import { session } from '../../../stores/session.js'
import {
  trades, money, tone, startPositionsPolling, stopPositionsPolling,
} from '../../../stores/trades.js'
import { haptic } from '../../../composables/useSpeech.js'

const router = useRouter()

const rows = computed(() => trades.positions.slice(0, 3))

/* `brokerConnected === false` is a definite no. An unknown link state must
   not render as "connect a broker" — that sends users to reconnect an
   account that is fine. */
const noAccount = computed(
  () => session.brokerConnected === false && !trades.positions.length
)

const subline = computed(() => {
  if (trades.openCount) return null
  return trades.currency && trades.balance != null
    ? `${trades.currency} ${Math.round(trades.balance).toLocaleString()}`
    : null
})

function open() {
  haptic(15)
  router.push('/trades')
}

onMounted(startPositionsPolling)
onUnmounted(stopPositionsPolling)
</script>

<style scoped>
.ot { display: flex; flex-direction: column; gap: 10px; }

.ot-head {
  display: flex; align-items: baseline; justify-content: space-between; gap: 12px;
  width: 100%; padding: 0; border: none; background: none;
  color: inherit; font: inherit; text-align: left; cursor: pointer;
}
.ot-head-l { display: flex; flex-direction: column; gap: 1px; min-width: 0; }

.ot-count { font-size: 20px; font-weight: 700; letter-spacing: -0.02em; color: var(--cc-ink); }
.ot-count-u { font-size: 12px; font-weight: 500; color: var(--cc-ink-4); letter-spacing: 0; }

.ot-pl { font-size: 15px; font-weight: 700; font-family: var(--cc-mono); flex: none; }

.ot-rows {
  display: flex; flex-direction: column; gap: 1px;
  border-top: 1px solid var(--cc-border-soft);
  padding-top: 9px;
}

.ot-row {
  display: flex; align-items: center; gap: 9px;
  width: 100%; padding: 5px 0; border: none; background: none;
  color: inherit; font: inherit; text-align: left; cursor: pointer;
}
.ot-side {
  flex: none;
  border-radius: var(--cc-r-chip);
  font-family: var(--cc-mono);
  font-size: 9px; font-weight: 800; letter-spacing: 0.05em;
  padding: 3px 5px;
  background: var(--cc-recess); color: var(--cc-ink-4);
}
.ot-side[data-side="BUY"]  { background: var(--cc-buy-dim);  color: var(--cc-buy); }
.ot-side[data-side="SELL"] { background: var(--cc-sell-dim); color: var(--cc-sell); }

.ot-sym { font-size: 12.5px; font-weight: 600; flex: 1; min-width: 0; }
.ot-row-r { flex: none; }
.ot-row-pl { font-size: 12px; font-weight: 650; font-family: var(--cc-mono); }

.ot-more {
  align-self: flex-start;
  border: none; background: none; padding: 4px 0;
  color: var(--cc-accent-text); font: inherit; font-size: 11px; font-weight: 600;
  cursor: pointer;
}

.ot-msg { line-height: 1.5; margin: 0; }
.ot-err { color: var(--cc-sell-text, var(--cc-sell)); }

[data-tone="up"]   { color: var(--cc-buy); }
[data-tone="down"] { color: var(--cc-sell); }
[data-tone="flat"] { color: var(--cc-ink-3); }
</style>
