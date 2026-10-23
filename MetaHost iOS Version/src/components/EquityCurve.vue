<template>
  <div class="eq">
    <div v-if="points.length < 2" class="eq-empty cc-caption">
      {{ points.length ? 'One closed trade — not enough to draw a curve yet.' : 'No closed trades in this window.' }}
    </div>

    <template v-else>
      <svg
        class="eq-svg"
        :viewBox="`0 0 ${W} ${H}`"
        preserveAspectRatio="none"
        role="img"
        :aria-label="ariaLabel"
      >
        <defs>
          <linearGradient :id="gradId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%"   :stop-color="stroke" stop-opacity="0.28" />
            <stop offset="100%" :stop-color="stroke" stop-opacity="0" />
          </linearGradient>
        </defs>

        <!-- The starting level, so a curve that ends below where it began
             reads as a loss at a glance rather than as a shape. -->
        <line
          class="eq-base"
          :x1="0" :x2="W"
          :y1="yOf(points[0])" :y2="yOf(points[0])"
        />

        <path :d="areaPath" :fill="`url(#${gradId})`" />
        <path :d="linePath" fill="none" :stroke="stroke" stroke-width="2"
              stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" />

        <circle :cx="W" :cy="yOf(points[points.length - 1])" r="3" :fill="stroke" />
      </svg>

      <!-- The vertical range, labelled. Without this the compression is
           invisible: a curve that swings R20 and one that swings R20,000
           draw identically. -->
      <div class="eq-scale">
        <span class="cc-caption">Low {{ fmt(min) }}</span>
        <span class="cc-caption">High {{ fmt(max) }}</span>
      </div>
    </template>
  </div>
</template>

<script setup>
/**
 * The account's cumulative result, drawn.
 *
 * Deliberately minimal: no axes, no grid, no tooltips. It answers one
 * question — is this account going up or down, and how steadily — on a phone
 * screen. Anything quantitative is in the stat tiles beside it, where a
 * number can be read exactly rather than estimated off a 300px line.
 *
 * ## Why the y-axis does not start at zero
 *
 * A balance curve from R0 to R596 anchored at zero is a flat line with a
 * bump. Bank-balance charts are read for SHAPE, and zero-anchoring destroys
 * the only information in them. The starting level is drawn as a baseline
 * instead, which is the comparison that actually matters: above it the
 * account is up on the window, below it, down.
 *
 * The scale's ends are labelled so the compression is never hidden.
 */
import { computed } from 'vue'

const props = defineProps({
  /** `[{ t, v }]`, oldest first, as `broker-performance` returns it. */
  curve: { type: Array, default: () => [] },
  currency: { type: String, default: null },
  /** False when there was no balance to anchor to — the axis is profit. */
  anchored: { type: Boolean, default: true },
})

const W = 300
const H = 96
/* Breathing room top and bottom so the line never sits on the frame edge,
   where a stroke is clipped in half. */
const PAD = 8

/** Unique per instance: two curves on one page would otherwise share, and
 *  the second would take the first's gradient. */
const gradId = `eqg-${Math.random().toString(36).slice(2, 9)}`

const points = computed(() =>
  (props.curve || [])
    .map((p) => Number(p?.v))
    .filter((v) => Number.isFinite(v))
)

const min = computed(() => Math.min(...points.value))
const max = computed(() => Math.max(...points.value))

/** Up or down over the WHOLE window — the last point against the first, not
 *  against the peak. */
const up = computed(() => points.value[points.value.length - 1] >= points.value[0])
const stroke = computed(() => (up.value ? 'var(--cc-buy)' : 'var(--cc-sell)'))

function xOf(i) {
  const n = points.value.length - 1
  return n <= 0 ? 0 : (i / n) * W
}

function yOf(v) {
  const lo = min.value
  const hi = max.value
  /* A perfectly flat curve has no range to scale against; centring it is the
     only honest rendering, and dividing by zero would produce NaN in the
     path and silently blank the chart. */
  if (hi === lo) return H / 2
  const t = (v - lo) / (hi - lo)
  return H - PAD - t * (H - PAD * 2)
}

const linePath = computed(() =>
  points.value.map((v, i) => `${i ? 'L' : 'M'}${xOf(i).toFixed(2)},${yOf(v).toFixed(2)}`).join(' ')
)

const areaPath = computed(() => `${linePath.value} L${W},${H} L0,${H} Z`)

function fmt(v) {
  if (!Number.isFinite(v)) return '—'
  const body = Math.round(v).toLocaleString()
  return props.currency ? `${props.currency} ${body}` : body
}

const ariaLabel = computed(() => {
  const first = points.value[0]
  const last = points.value[points.value.length - 1]
  const what = props.anchored ? 'Account balance' : 'Cumulative profit'
  return `${what} over ${points.value.length} closed trades, from ${fmt(first)} to ${fmt(last)}.`
})
</script>

<style scoped>
.eq { display: flex; flex-direction: column; gap: 6px; }

.eq-svg {
  width: 100%;
  height: 96px;
  display: block;
  overflow: visible;
}

.eq-base {
  stroke: var(--cc-border-strong);
  stroke-width: 1;
  stroke-dasharray: 3 4;
  vector-effect: non-scaling-stroke;
}

.eq-scale { display: flex; justify-content: space-between; }

.eq-empty {
  padding: 26px 8px;
  text-align: center;
  line-height: 1.5;
}
</style>
