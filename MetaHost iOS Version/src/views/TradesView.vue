<template>
  <!--
    TRADES

    The screen that answers "did my trade actually happen, and what is it
    doing". Everything on it comes from the broker, because the broker is the
    only place that knows: `trade_logs` has no writer and no rows, and
    `metacopier-execute` forgets an order the moment it is accepted.
  -->
  <div
    class="cmd trd"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <header class="trd-head">
      <button class="trd-icon" aria-label="Back" @click="$router.push('/home')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <div class="trd-title">
        <h1>Trades</h1>
        <span class="trd-sub">{{ headerMeta }}</span>
      </div>
      <button
        class="trd-icon"
        :class="{ 'is-busy': busy }"
        :disabled="busy"
        aria-label="Refresh"
        @click="refreshAll"
      >
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="23 4 23 10 17 10" /><polyline points="1 20 1 14 7 14" />
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
        </svg>
      </button>
    </header>

    <div class="trd-body">

      <!-- ═══ NO ACCOUNT ═════════════════════════════════════════════
           Every figure below is an account property. Without a link there is
           nothing to show and nothing to retry, so the screen says so once
           and offers the fix rather than rendering empty tiles. -->
      <section v-if="noAccount" class="trd-sec">
        <div class="cc-card trd-empty">
          <span class="cc-label">No trading account</span>
          <p class="cc-caption trd-empty-msg">
            Connect your MT4 or MT5 account and your trades, balance and
            history appear here.
          </p>
          <button class="trd-cta" @click="$router.push('/terminal')">Connect a broker</button>
        </div>
      </section>

      <template v-else>
        <!-- ═══ 1 · THE ACCOUNT ══════════════════════════════════════ -->
        <section class="trd-sec">
          <div class="cc-card trd-acct">
            <div class="trd-acct-main">
              <span class="cc-label">Equity</span>
              <span class="cc-figure trd-equity">{{ amount(trades.equity ?? trades.balance) }}</span>
            </div>
            <div class="trd-acct-side">
              <div class="trd-kv">
                <span class="cc-caption">Balance</span>
                <span class="trd-kv-v">{{ amount(trades.balance) }}</span>
              </div>
              <div class="trd-kv">
                <span class="cc-caption">Floating</span>
                <!-- Null when nothing is open. Zero would read as "flat",
                     which is a different and wrong statement. -->
                <span class="trd-kv-v" :data-tone="tone(trades.floatingNet)">
                  {{ trades.floatingNet == null ? '—' : money(trades.floatingNet) }}
                </span>
              </div>
            </div>
          </div>
          <p v-if="!trades.currency && trades.balance != null" class="trd-warn cc-caption">
            The account currency could not be read, so these figures are
            unlabelled. Do not assume dollars.
          </p>
        </section>

        <!-- ═══ 2 · CHART ════════════════════════════════════════════ -->
        <section class="trd-sec">
          <div class="trd-sec-head">
            <h2 class="trd-sec-t">{{ chartSymbol || 'Chart' }}</h2>
            <div class="trd-ivals">
              <button
                v-for="iv in INTERVALS"
                :key="iv.id"
                class="trd-ival"
                :class="{ 'is-on': interval === iv.id }"
                @click="interval = iv.id"
              >{{ iv.label }}</button>
            </div>
          </div>

          <TradingChart
            v-if="chartSymbol"
            :symbol="chartSymbol"
            :scheme="resolvedScheme"
            :interval="interval"
          />
          <div v-else class="cc-card trd-empty">
            <p class="cc-caption trd-empty-msg">
              Open a trade, or pick a symbol below, to chart it.
            </p>
          </div>

          <!-- The levels the chart cannot draw.
               TradingView's embed is a sealed iframe with no drawing API, so
               the entry, stop and target are rendered HERE rather than faked
               onto the price. -->
          <div v-if="chartLevels" class="cc-card trd-levels">
            <div class="trd-level">
              <span class="cc-caption">Entry</span>
              <span class="trd-level-v">{{ chartLevels.open_price ?? '—' }}</span>
            </div>
            <div class="trd-level">
              <span class="cc-caption">Stop</span>
              <span class="trd-level-v trd-sl">{{ chartLevels.stop_loss || '—' }}</span>
            </div>
            <div class="trd-level">
              <span class="cc-caption">Target</span>
              <span class="trd-level-v trd-tp">{{ chartLevels.take_profit || '—' }}</span>
            </div>
          </div>
          <p class="trd-note cc-caption">
            Chart prices are an indicative feed, not your broker's. Your fill,
            spread and levels come from your own account.
          </p>

          <!-- Symbols with no open position, so a user can still look at one
               the robot is allowed to trade. -->
          <div v-if="otherSymbols.length" class="trd-chips">
            <button
              v-for="s in otherSymbols"
              :key="s"
              class="cc-chip trd-chip"
              :class="{ 'is-on': chartSymbol === s }"
              @click="pickSymbol(s)"
            >{{ s }}</button>
          </div>
        </section>

        <!-- ═══ 3 · OPEN ═════════════════════════════════════════════ -->
        <section class="trd-sec">
          <div class="trd-sec-head">
            <h2 class="trd-sec-t">Open</h2>
            <span v-if="trades.pendingCount" class="cc-chip">
              {{ trades.pendingCount }} pending
            </span>
          </div>

          <p v-if="trades.positionsError" class="trd-err cc-caption">
            {{ trades.positionsError }}
          </p>

          <div v-if="trades.positions.length" class="trd-list">
            <button
              v-for="p in trades.positions"
              :key="p.id || p.symbol + p.open_time"
              class="cc-card trd-row"
              :class="{ 'is-on': chartSymbol === p.base }"
              @click="pickPosition(p)"
            >
              <div class="trd-row-l">
                <span class="trd-side" :data-side="p.side">{{ p.side }}</span>
                <div class="trd-row-id">
                  <span class="trd-sym">{{ p.symbol || p.base }}</span>
                  <span class="cc-caption">
                    {{ p.volume != null ? p.volume + ' lots' : '—' }}
                    <template v-if="p.pending"> · {{ prettyOrderType(p.order_type) }}</template>
                    <template v-else-if="p.open_price"> @ {{ p.open_price }}</template>
                    <!-- Placed by hand in MetaTrader rather than by the
                         robot. Shown, never hidden: it is the user's open
                         risk either way. -->
                    <template v-if="!p.ours"> · manual</template>
                  </span>
                </div>
              </div>
              <div class="trd-row-r">
                <span v-if="p.pending" class="cc-chip trd-pend">WAITING</span>
                <span v-else class="trd-pl" :data-tone="tone(p.net_profit)">
                  {{ money(p.net_profit) }}
                </span>
              </div>
            </button>
          </div>

          <div v-else-if="!trades.positionsLoading" class="cc-card trd-empty">
            <p class="cc-caption trd-empty-msg">
              Nothing open right now. Trades your robot takes appear here
              within a few seconds.
            </p>
          </div>
        </section>

        <!-- ═══ 4 · PERFORMANCE ══════════════════════════════════════ -->
        <section class="trd-sec">
          <div class="trd-sec-head">
            <h2 class="trd-sec-t">{{ trades.anchored ? 'Balance' : 'Profit' }} · 90 days</h2>
            <span v-if="trades.stats" class="cc-chip">{{ trades.stats.closed_count }} closed</span>
          </div>

          <p v-if="trades.historyError" class="trd-err cc-caption">{{ trades.historyError }}</p>

          <div class="cc-card trd-perf">
            <EquityCurve
              :curve="trades.curve"
              :currency="trades.currency"
              :anchored="trades.anchored"
            />

            <div v-if="trades.stats && trades.stats.closed_count" class="trd-stats">
              <div class="trd-stat">
                <span class="cc-caption">Win rate</span>
                <span class="trd-stat-v">
                  {{ trades.stats.win_rate == null ? '—' : trades.stats.win_rate + '%' }}
                </span>
              </div>
              <div class="trd-stat">
                <span class="cc-caption">Net</span>
                <span class="trd-stat-v" :data-tone="tone(trades.stats.net_total)">
                  {{ money(trades.stats.net_total) }}
                </span>
              </div>
              <div class="trd-stat">
                <span class="cc-caption">Profit factor</span>
                <!-- Null when nothing has lost yet: the ratio is undefined,
                     and Infinity on a tile reads as a bug. -->
                <span class="trd-stat-v">{{ trades.stats.profit_factor ?? '—' }}</span>
              </div>
              <div class="trd-stat">
                <span class="cc-caption">Losses in a row</span>
                <span class="trd-stat-v" :data-tone="trades.stats.consecutive_losses >= 3 ? 'down' : 'flat'">
                  {{ trades.stats.consecutive_losses }}
                </span>
              </div>
            </div>
          </div>
        </section>

        <!-- ═══ 5 · CLOSED ═══════════════════════════════════════════ -->
        <section v-if="trades.closed.length" class="trd-sec">
          <div class="trd-sec-head">
            <h2 class="trd-sec-t">Recent closed</h2>
          </div>
          <div class="trd-list">
            <div
              v-for="t in trades.closed.slice(0, showAllClosed ? 50 : 8)"
              :key="t.id || t.close_time + t.symbol"
              class="cc-card trd-row trd-row-static"
            >
              <div class="trd-row-l">
                <span class="trd-side" :data-side="t.side">{{ t.side }}</span>
                <div class="trd-row-id">
                  <span class="trd-sym">{{ t.symbol || t.base }}</span>
                  <span class="cc-caption">
                    {{ t.volume != null ? t.volume + ' lots' : '—' }} · {{ closedAge(t.close_time) }}
                    <template v-if="!t.ours"> · manual</template>
                  </span>
                </div>
              </div>
              <span class="trd-pl" :data-tone="tone(t.net_profit)">{{ money(t.net_profit) }}</span>
            </div>
          </div>
          <button
            v-if="trades.closed.length > 8"
            class="trd-more"
            @click="showAllClosed = !showAllClosed"
          >
            {{ showAllClosed ? 'Show less' : `Show all ${Math.min(trades.closed.length, 50)}` }}
          </button>
          <p class="trd-note cc-caption">
            Broker history can lag live trading by up to 3 minutes.
          </p>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { theme, resolvedScheme } from '../stores/theme.js'
import { robot } from '../stores/robot.js'
import { session } from '../stores/session.js'
import {
  trades, money, amount, tone,
  refreshPositions, refreshHistory,
  startPositionsPolling, stopPositionsPolling,
} from '../stores/trades.js'
import TradingChart from '../components/TradingChart.vue'
import EquityCurve from '../components/EquityCurve.vue'
import { haptic } from '../composables/useSpeech.js'

const INTERVALS = [
  { id: '15', label: '15m' },
  { id: '60', label: '1H' },
  { id: '240', label: '4H' },
  { id: 'D', label: '1D' },
]

const interval = ref('60')
const showAllClosed = ref(false)
/** Null until the user picks: the default follows the first open position, so
 *  the chart lands on what they actually have money in. */
const chosenSymbol = ref(null)

/* `linked === false` is a definite no. `null` means the status read failed,
   and an unknown link must not render as "you have no account" — that sends
   users to reconnect something that is fine. */
const noAccount = computed(() => session.brokerConnected === false && !trades.positions.length)

const busy = computed(() => trades.positionsLoading || trades.historyLoading)

const firstOpen = computed(() => trades.positions.find((p) => !p.pending) || trades.positions[0])

const chartSymbol = computed(() => chosenSymbol.value || firstOpen.value?.base || null)

/** The position whose levels are drawn beside the chart — only when the
 *  charted symbol is actually one the user holds. */
const chartLevels = computed(() => {
  if (!chartSymbol.value) return null
  return trades.positions.find((p) => p.base === chartSymbol.value) || null
})

/** Allowance minus what is already open, so the chips add options rather
 *  than repeat the list above them. */
const otherSymbols = computed(() => {
  const held = new Set(trades.positions.map((p) => p.base))
  return (robot.allowedSymbols || []).map(String).filter((s) => !held.has(s))
})

const headerMeta = computed(() => {
  if (noAccount.value) return 'No broker linked'
  if (trades.openCount) {
    const pl = trades.floatingNet == null ? '' : ` · ${money(trades.floatingNet)}`
    return `${trades.openCount} open${pl}`
  }
  return trades.positionsLoading ? 'Checking…' : 'Nothing open'
})

function prettyOrderType(t) {
  return String(t || '').replace(/([a-z])([A-Z])/g, '$1 $2')
}

/** "2m", "18m", "1h", "3d" — the same compact age column the signal feed uses. */
function closedAge(ts) {
  if (!ts) return ''
  const s = Math.max(0, (Date.now() - new Date(ts).getTime()) / 1000)
  if (s < 60) return `${Math.floor(s)}s ago`
  const m = s / 60
  if (m < 60) return `${Math.floor(m)}m ago`
  const h = m / 60
  if (h < 24) return `${Math.floor(h)}h ago`
  return `${Math.floor(h / 24)}d ago`
}

function pickPosition(p) {
  haptic(12)
  chosenSymbol.value = p.base
}

function pickSymbol(s) {
  haptic(12)
  chosenSymbol.value = s
}

async function refreshAll() {
  haptic(15)
  await Promise.all([refreshPositions(), refreshHistory()])
}

onMounted(() => {
  startPositionsPolling()
  refreshHistory()
})

onUnmounted(stopPositionsPolling)
</script>

<style scoped>
.trd {
  /* height, NOT min-height. `.app-shell` is `height: 100dvh; overflow:
     hidden`, so it clips this view; with min-height this element grows
     past the shell rather than scrolling inside it and everything below
     the fold becomes unreachable. Installed to the Home screen there is
     no collapsing URL bar to jostle it into view, so the page just
     freezes — which is the bug this fixes.

     overflow-y: auto is what makes this element the scroll container. */
  height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  background: var(--cc-canvas);
  color: var(--cc-ink);
  font-family: var(--cc-font);
}

/* ── Header ── */
.trd-head {
  position: sticky; top: 0; z-index: 5;
  display: flex; align-items: center; gap: 12px;
  padding: calc(var(--cc-safe-top, 0px) + 14px) 16px 12px;
  background: var(--cc-canvas);
  border-bottom: 1px solid var(--cc-border-soft);
}
.trd-title { flex: 1; min-width: 0; }
.trd-title h1 { margin: 0; font-size: 19px; font-weight: 700; letter-spacing: -0.01em; }
.trd-sub { display: block; font-size: 11.5px; color: var(--cc-ink-4); margin-top: 1px; }

.trd-icon {
  flex: none; width: 38px; height: 38px;
  display: grid; place-items: center;
  border-radius: 9999px;
  border: 1px solid var(--cc-border);
  background: var(--cc-card);
  color: var(--cc-ink-2);
  cursor: pointer;
}
.trd-icon:disabled { opacity: 0.5; cursor: default; }
.trd-icon.is-busy svg { animation: trd-rot 0.9s linear infinite; }
@keyframes trd-rot { to { transform: rotate(360deg) } }

/* ── Body ── */
.trd-body {
  display: flex; flex-direction: column; gap: 22px;
  padding: 18px 16px calc(var(--cc-safe-bottom, 0px) + 96px);
}
.trd-sec { display: flex; flex-direction: column; gap: 10px; }
.trd-sec-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.trd-sec-t { margin: 0; font-size: 14px; font-weight: 650; color: var(--cc-ink-2); }

/* ── Account ── */
.trd-acct { display: flex; align-items: center; gap: 16px; padding: 16px; }
.trd-acct-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.trd-equity { font-size: 25px; font-weight: 700; letter-spacing: -0.02em; }
.trd-acct-side { display: flex; flex-direction: column; gap: 9px; text-align: right; }
.trd-kv { display: flex; flex-direction: column; gap: 1px; }
.trd-kv-v { font-size: 13px; font-weight: 600; font-family: var(--cc-mono); }

.trd-warn { color: var(--cc-warn); line-height: 1.5; }
.trd-err  { color: var(--cc-sell-text, var(--cc-sell)); line-height: 1.5; }

/* ── Chart ── */
.trd-ivals { display: flex; gap: 4px; }
.trd-ival {
  border: 1px solid var(--cc-border);
  background: transparent;
  color: var(--cc-ink-4);
  border-radius: 9999px;
  font: inherit; font-size: 11px; font-weight: 600;
  padding: 4px 10px; cursor: pointer;
}
.trd-ival.is-on {
  background: var(--cc-accent-dim);
  border-color: var(--cc-accent-line);
  color: var(--cc-accent-text);
}

.trd-levels { display: flex; padding: 12px 14px; }
.trd-level { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.trd-level-v { font-size: 13px; font-weight: 600; font-family: var(--cc-mono); }
.trd-sl { color: var(--cc-sell); }
.trd-tp { color: var(--cc-buy); }

.trd-note { line-height: 1.5; margin: 0; }

.trd-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.trd-chip { cursor: pointer; border: 1px solid var(--cc-border); }
.trd-chip.is-on {
  background: var(--cc-accent-dim);
  border-color: var(--cc-accent-line);
  color: var(--cc-accent-text);
}

/* ── Rows ── */
.trd-list { display: flex; flex-direction: column; gap: 8px; }
.trd-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  width: 100%; padding: 12px 14px;
  border: 1px solid var(--cc-border);
  text-align: left; cursor: pointer;
  color: inherit; font: inherit;
}
.trd-row-static { cursor: default; }
.trd-row.is-on { border-color: var(--cc-accent-line); }
.trd-row-l { display: flex; align-items: center; gap: 11px; min-width: 0; }
.trd-row-id { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.trd-sym { font-size: 14px; font-weight: 650; }

.trd-side {
  flex: none;
  border-radius: var(--cc-r-chip);
  font-family: var(--cc-mono);
  font-size: 10px; font-weight: 800; letter-spacing: 0.06em;
  padding: 4px 7px;
  background: var(--cc-recess); color: var(--cc-ink-4);
}
.trd-side[data-side="BUY"]  { background: var(--cc-buy-dim);  color: var(--cc-buy); }
.trd-side[data-side="SELL"] { background: var(--cc-sell-dim); color: var(--cc-sell); }

.trd-row-r { flex: none; }
.trd-pl { font-size: 14px; font-weight: 700; font-family: var(--cc-mono); }
.trd-pend { font-size: 10px; }

/* Colour is never the only signal — the figure itself carries its sign. */
[data-tone="up"]   { color: var(--cc-buy); }
[data-tone="down"] { color: var(--cc-sell); }
[data-tone="flat"] { color: var(--cc-ink-3); }

/* ── Performance ── */
.trd-perf { display: flex; flex-direction: column; gap: 14px; padding: 14px; }
.trd-stats {
  display: grid; grid-template-columns: repeat(2, 1fr);
  gap: 12px 10px;
  padding-top: 12px;
  border-top: 1px solid var(--cc-border-soft);
}
.trd-stat { display: flex; flex-direction: column; gap: 2px; }
.trd-stat-v { font-size: 15px; font-weight: 700; font-family: var(--cc-mono); }

/* ── Empty / CTA ── */
.trd-empty {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 26px 20px; text-align: center;
}
.trd-empty-msg { max-width: 34ch; line-height: 1.55; margin: 0; }
.trd-cta, .trd-more {
  border-radius: 9999px;
  border: 1px solid var(--cc-accent-line);
  background: var(--cc-accent-dim);
  color: var(--cc-accent-text);
  font: inherit; font-size: 12.5px; font-weight: 600;
  padding: 9px 20px; cursor: pointer;
  align-self: center;
}
.trd-more { margin-top: 2px; }
</style>
