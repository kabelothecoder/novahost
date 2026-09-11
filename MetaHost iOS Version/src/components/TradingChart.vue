<template>
  <div class="tvc" :class="{ 'tvc-tall': tall }">
    <iframe
      v-if="embedUrl"
      :key="embedUrl"
      ref="frame"
      class="tvc-frame"
      :src="embedUrl"
      :title="`${display} price chart`"
      frameborder="0"
      scrolling="no"
      allowtransparency="true"
      referrerpolicy="origin"
      @load="onLoad"
    />

    <!-- A dead grey box is the worst outcome on a screen someone opened to
         look at a price, so every state says which one it is. -->
    <div v-if="state === 'loading'" class="tvc-veil">
      <span class="tvc-spin" aria-hidden="true"></span>
      <span class="cc-caption">Loading {{ display }}…</span>
    </div>

    <div v-else-if="state === 'unmapped'" class="tvc-veil tvc-veil-msg">
      <span class="cc-label">No chart for {{ display }}</span>
      <p class="cc-caption tvc-msg">
        This instrument is not one we can chart yet. Your trade is unaffected.
      </p>
    </div>

    <div v-else-if="state === 'blocked'" class="tvc-veil tvc-veil-msg">
      <span class="cc-label">Chart unavailable</span>
      <p class="cc-caption tvc-msg">
        The chart could not load. It needs a connection, and some networks
        block it.
      </p>
      <button class="tvc-retry" @click="remount">Try again</button>
    </div>

    <!--
      Development only, and it earns its place: TradingView serves no chart to
      a page on localhost, so the whole component looks broken on every dev
      machine. Without this line the next person to touch it spends an hour
      debugging a component that is working. Stripped from production builds
      by the `import.meta.env.DEV` guard.
    -->
    <p v-if="isDevLocalhost" class="tvc-dev">
      dev: TradingView does not serve charts to localhost — expect this blank
      here, and check it on the deployed domain.
    </p>
  </div>
</template>

<script setup>
/**
 * A live candle chart, from TradingView's embedded Advanced Chart widget.
 *
 * ## Why the candles are not ours
 *
 * MetaCopier has no candle, bar or OHLC endpoint. `broker-quote`'s own
 * docstring settles it: "the whole v1 surface is accounts, positions, symbols
 * and a single live quote." There is no price history anywhere in this
 * product's own infrastructure, so a chart has to come from outside it.
 *
 * TradingView's embed is free, needs no key, carries every instrument on the
 * allowance, and is the chart these users already recognise.
 *
 * ## Why this builds the iframe itself instead of loading their script
 *
 * The documented integration injects
 * `embed-widget-advanced-chart.js` with the config as the script tag's text,
 * and that script does one thing: construct an iframe pointing at
 * `tradingview-widget.com/embed-widget/advanced-chart/#<config>`.
 *
 * Loading it per mount was actively harmful. The script is not idempotent —
 * the FIRST widget on a page came up with data and every remount after it
 * produced the shell with a null OHLC legend, because the already-executed
 * script's globals were still in place. This component remounts on every
 * symbol, interval and theme change, so in practice that meant the chart
 * worked once and then never again in a session.
 *
 * Building the URL directly removes the failure entirely: changing the symbol
 * is changing an `src`. It also keeps a third-party script out of the page,
 * which is worth having on its own.
 *
 * ## What it therefore CANNOT do
 *
 * It is a sealed cross-origin iframe. We cannot draw the entry, stop or
 * target onto the price — the drawing API belongs to the paid Charting
 * Library. So the levels are rendered BESIDE the chart by the caller rather
 * than faked on top of it, and this component does not pretend otherwise.
 *
 * If that trade-off stops being acceptable, the replacement is
 * `lightweight-charts` plus an OHLC feed, and it is contained: only this file
 * changes.
 *
 * ## Prices here are not the user's fill price
 *
 * The feed is OANDA's, not the subscriber's broker's. Spread, digits and the
 * exact level differ. Fine for reading structure, wrong for anything
 * quantitative — which is why sizing still goes through `broker-quote`
 * against the account that will actually fill the order. The caller is
 * expected to label the chart as indicative.
 */
import { ref, watch, computed, onBeforeUnmount } from 'vue'

const props = defineProps({
  /** Canonical NovaHost name — XAUUSD, NAS100, US30. */
  symbol: { type: String, default: '' },
  /** 'dark' | 'light', normally the app's resolved scheme. */
  scheme: { type: String, default: 'dark' },
  /** TradingView interval: 1, 5, 15, 60, 240, D, W. */
  interval: { type: String, default: '60' },
  tall: { type: Boolean, default: false },
})

const EMBED_BASE = 'https://www.tradingview-widget.com/embed-widget/advanced-chart/'

/**
 * Canonical name → TradingView symbol.
 *
 * Every instrument on any licence's allowance is listed explicitly rather
 * than guessed. A wrong guess here is not a blank chart, it is the WRONG
 * INSTRUMENT drawn under the right title — a user reading gold structure off
 * a silver chart is worse served than one told we cannot chart it.
 *
 * OANDA throughout for FX, metals and indices so the feed is internally
 * consistent: one venue's session breaks and one venue's pricing, rather than
 * a different exchange per row. Crypto and VIX have no OANDA listing worth
 * using, so they name their own.
 */
const TV_SYMBOLS = {
  XAUUSD: 'OANDA:XAUUSD',
  XAGUSD: 'OANDA:XAGUSD',
  EURUSD: 'OANDA:EURUSD',
  GBPUSD: 'OANDA:GBPUSD',
  USDJPY: 'OANDA:USDJPY',
  NAS100: 'OANDA:NAS100USD',
  US30:   'OANDA:US30USD',
  SPX500: 'OANDA:SPX500USD',
  GER40:  'OANDA:DE30EUR',
  UK100:  'OANDA:UK100GBP',
  JP225:  'OANDA:JP225USD',
  BTCUSD: 'COINBASE:BTCUSD',
  ETHUSD: 'COINBASE:ETHUSD',
  VIX:    'TVC:VIX',
}

/** Brokers decorate symbols (`XAUUSD.m`, `.US30.`, `Gold`); charting must not care. */
function canon(s) {
  return String(s || '').replace(/[^A-Za-z0-9]/g, '').toUpperCase()
}

/**
 * Broker spellings that do not simply strip to a canonical name.
 *
 * `broker-positions` already resolves these server-side, so in practice this
 * receives a canonical name. Kept because the chart is also opened from the
 * allowance chips and from a scan reading, neither of which goes through that
 * function — and a symbol that silently charts nothing is worse than a
 * duplicated table.
 */
const ALIASES = {
  GOLD: 'XAUUSD', GOLDUSD: 'XAUUSD',
  SILVER: 'XAGUSD', SILVERUSD: 'XAGUSD',
  USTEC: 'NAS100', US100: 'NAS100', USTECH: 'NAS100', NDX100: 'NAS100', NASDAQ: 'NAS100',
  DJ30: 'US30', WS30: 'US30', USA30: 'US30', DOW: 'US30',
  US500: 'SPX500', SP500: 'SPX500', USA500: 'SPX500',
  DE40: 'GER40', DAX40: 'GER40', DAX: 'GER40', GER30: 'GER40',
  FTSE100: 'UK100', JPN225: 'JP225', NIKKEI: 'JP225',
  BTCUSDT: 'BTCUSD', BITCOIN: 'BTCUSD',
  ETHUSDT: 'ETHUSD', ETHEREUM: 'ETHUSD',
}

const tvSymbol = computed(() => {
  const c = canon(props.symbol)
  if (!c) return null
  if (TV_SYMBOLS[c]) return TV_SYMBOLS[c]
  if (ALIASES[c] && TV_SYMBOLS[ALIASES[c]]) return TV_SYMBOLS[ALIASES[c]]

  /* A decorated broker name whose base IS on a list: `XAUUSDm` -> XAUUSD.
     Longest key first, so a shorter name can never claim a longer one's
     symbol. Aliases are checked too, which is what makes `USTECm` resolve. */
  const keys = [...Object.keys(TV_SYMBOLS), ...Object.keys(ALIASES)]
    .sort((a, b) => b.length - a.length)
  const hit = keys.find((k) => c.startsWith(k))
  if (!hit) return null
  return TV_SYMBOLS[hit] || TV_SYMBOLS[ALIASES[hit]] || null
})

const display = computed(() => canon(props.symbol) || 'this symbol')

const embedUrl = computed(() => {
  if (!tvSymbol.value) return null
  const cfg = {
    symbol: tvSymbol.value,
    interval: props.interval,
    theme: props.scheme === 'light' ? 'light' : 'dark',
    style: '1',               // candles
    locale: 'en',
    autosize: true,
    timezone: 'Etc/UTC',
    hide_side_toolbar: true,  // no drawing tools on a phone
    /* Their timeframe row duplicated the interval buttons this component is
       given, and the two disagreed — tapping theirs left ours highlighting
       the wrong one. Ours wins because it is styled to the app, and dropping
       theirs gives ~48px back to the candles. */
    hide_top_toolbar: true,
    hide_legend: false,
    allow_symbol_change: false,
    save_image: false,
    withdateranges: false,
    calendar: false,
    /* No watchlist, no news, no hotlists: each is another surface offering
       instruments this user is not licensed to trade. */
    details: false,
    hotlist: false,
  }
  return `${EMBED_BASE}?locale=en#${encodeURIComponent(JSON.stringify(cfg))}`
})

const frame = ref(null)
const state = ref('loading')
let failTimer = null
/** Bumped to force Vue to discard and recreate the iframe on a manual retry. */
const attempt = ref(0)

const isDevLocalhost = computed(
  () => import.meta.env.DEV && /^(localhost|127\.0\.0\.1|\[::1\])$/.test(location.hostname)
)

function onLoad() {
  clearTimeout(failTimer)
  state.value = 'ready'
}

function arm() {
  clearTimeout(failTimer)

  if (!tvSymbol.value) {
    state.value = 'unmapped'
    return
  }

  state.value = 'loading'
  /* The iframe is cross-origin, so there is no way to ask it whether it
     actually drew anything — only whether it loaded. A frame that never
     loads at all is the case this catches. */
  failTimer = setTimeout(() => {
    if (state.value === 'loading') state.value = 'blocked'
  }, 12000)
}

function remount() {
  attempt.value++
  const f = frame.value
  if (f) {
    const src = f.src
    f.src = 'about:blank'
    requestAnimationFrame(() => { f.src = src })
  }
  arm()
}

/* Watched as a STRING, not as an array.
   `watch(() => [a, b, c], fn)` builds a fresh array on every evaluation, so
   it can never compare equal to the previous value and the handler fires
   whenever the source is re-read — remounting the chart for no reason. A
   joined key compares by value. */
watch(
  () => `${props.symbol}|${props.scheme}|${props.interval}`,
  arm,
  { immediate: true }
)

onBeforeUnmount(() => clearTimeout(failTimer))
</script>

<style scoped>
.tvc {
  position: relative;
  width: 100%;
  /* The legend alone takes ~56px of this. Below about 320 the candles get
     less room than the text describing them. */
  height: 320px;
  border-radius: var(--cc-r-card);
  overflow: hidden;
  background: var(--cc-recess);
  border: 1px solid var(--cc-border);
}
.tvc-tall { height: 62vh; min-height: 340px; }

.tvc-frame { width: 100%; height: 100%; border: 0; display: block; }

.tvc-veil {
  position: absolute; inset: 0;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 10px;
  background: var(--cc-recess);
  padding: 20px; text-align: center;
}
.tvc-veil-msg { gap: 8px; }
.tvc-msg { max-width: 30ch; line-height: 1.5; margin: 0; }

.tvc-spin {
  width: 22px; height: 22px; border-radius: 50%;
  border: 2px solid var(--cc-border-strong);
  border-top-color: var(--cc-accent);
  animation: tvc-rot 0.8s linear infinite;
}
@keyframes tvc-rot { to { transform: rotate(360deg) } }

.tvc-retry {
  margin-top: 4px;
  border-radius: 9999px;
  border: 1px solid var(--cc-accent-line);
  background: var(--cc-accent-dim);
  color: var(--cc-accent-text);
  font: inherit; font-size: 12px; font-weight: 600;
  padding: 7px 16px; cursor: pointer;
}

.tvc-dev {
  position: absolute; left: 0; right: 0; bottom: 0;
  margin: 0; padding: 7px 10px;
  font-size: 10.5px; line-height: 1.4;
  color: var(--cc-warn);
  background: rgba(0, 0, 0, 0.55);
  text-align: center;
}

/* A spinner that never stops is worse than no spinner on a screen someone is
   watching for a fill. Honoured wherever the user asked for less motion. */
@media (prefers-reduced-motion: reduce) {
  .tvc-spin { animation-duration: 2.4s }
}
</style>
