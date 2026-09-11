<template>
  <WidgetCard label="System Health">
    <div class="health">
      <div class="health-ring">
        <svg viewBox="0 0 100 100" class="health-svg" aria-hidden="true">
          <circle class="health-track" cx="50" cy="50" :r="R" />
          <!--
            The scored arc is omitted entirely when nothing is knowable,
            rather than drawn at zero. A ring sitting at empty reads as
            "everything failed", which is the opposite of "nothing has
            been measured yet" — the same distinction the Fleet tiles
            make with an em dash.
          -->
          <circle
            v-if="score.known > 0"
            class="health-arc"
            :class="`is-${score.state}`"
            cx="50" cy="50" :r="R"
            :stroke-dasharray="C"
            :stroke-dashoffset="C - (C * score.passed) / score.known"
          />
        </svg>

        <div class="health-centre">
          <span v-if="score.known > 0" class="health-frac cc-figure">
            {{ score.passed }}<span class="health-den">/{{ score.known }}</span>
          </span>
          <span v-else class="health-frac cc-figure health-none">—</span>
        </div>
      </div>

      <span class="health-state" :class="`is-${score.state}`">{{ score.word }}</span>

      <!-- Named, not counted. "1 unknown" tells the user a check is
           missing; naming it tells them which figure not to trust. -->
      <span v-if="score.unknown.length" class="health-note">
        No reading: {{ score.unknown.join(' · ') }}
      </span>
      <span v-else-if="score.failed.length" class="health-note">
        {{ score.failed.join(' · ') }}
      </span>
    </div>
  </WidgetCard>
</template>

<script setup>
import { computed } from 'vue'
import WidgetCard from '../WidgetCard.vue'
import { session, beatState } from '../../../stores/session.js'
import { robot } from '../../../stores/robot.js'

/* r=42 in a 100-box leaves room for an 8-wide stroke without the ring
   clipping at the viewBox edge. */
const R = 42
const C = 2 * Math.PI * R

/* Matches the Fleet Status widget's warn threshold, so a ping the tile
   paints amber can never be a passing check on the ring beside it. */
const SLOW_PING_MS = 400

/**
 * Readiness, scored honestly.
 *
 * Every check is pass, fail, or UNKNOWN, and unknown is a real third
 * answer rather than a fail — `beat` has no source on this platform at
 * all (nothing writes `device_activations.last_seen_at`, and the one
 * endpoint that does is the signal claim we must never call from here;
 * see services/fleetService.js). Scoring it as a failure would paint
 * every healthy install amber forever.
 *
 * So the denominator is what is KNOWN, not the number of checks, and
 * the unknown ones are named underneath. A 3/3 with "no reading: beat"
 * is the truth; a 3/4 would be a lie about a measurement we never took.
 */
const score = computed(() => {
  const beat = beatState()

  const checks = [
    /* Known even before the first poll: `false` here means no trading
       account is attached to the licence, which is a real state the
       user can act on — not an absence of data. */
    { label: 'broker', ok: session.brokerConnected },

    { label: 'ping', ok: session.pingMs == null ? null : session.pingMs <= SLOW_PING_MS },

    { label: 'beat', ok: beat === 'none' ? null : beat === 'ok' },

    /* An empty allowance is far more likely to mean "not fetched yet"
       than "this licence permits nothing", so it reads as unknown. */
    { label: 'symbols', ok: robot.allowedSymbols.length ? true : null },
  ]

  const passed = checks.filter(c => c.ok === true).length
  const failed = checks.filter(c => c.ok === false).map(c => c.label)
  /* Deliberately `not true and not false`, not `=== null`. The three
     buckets have to be exhaustive: a check that yielded `undefined` —
     a store field renamed, a façade getter arriving late — matched none
     of them and vanished from the widget entirely, silently shrinking
     the denominator. A missing reading must always surface as unknown,
     which is the one thing this widget exists to guarantee. */
  const unknown = checks.filter(c => c.ok !== true && c.ok !== false).map(c => c.label)
  const known   = passed + failed.length

  let state = 'idle'
  let word  = 'UNKNOWN'
  if (known === 0) {
    // leave as unknown
  } else if (failed.length === known) {
    state = 'bad'
    word  = 'OFFLINE'
  } else if (failed.length) {
    state = 'warn'
    word  = 'DEGRADED'
  } else {
    /* All known checks pass. Whether that reads as NOMINAL or STANDBY
       depends on the robot, not on the checks — a perfectly healthy
       install with the robot stopped is not "nominal", it is idle by
       choice, and saying NOMINAL over a stopped robot is how a user
       ends up believing they are trading when they are not. */
    state = session.isRunning ? 'ok' : 'idle'
    word  = session.isRunning ? 'NOMINAL' : 'STANDBY'
  }

  return { passed, known, failed, unknown, state, word }
})
</script>

<style scoped>
.health {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  /* Fills the bento cell it is given so the ring optically centres
     against the stat tiles stacked beside it. */
  flex: 1;
  justify-content: center;
}

/* ── Ring ────────────────────────────────────────────────────────── */
.health-ring {
  position: relative;
  width: 100%;
  /* Sized from the WORST case, not the current one. In the Command
     Deck bento this cell is exactly as tall as the four stat tiles
     beside it (250px), which leaves 188.7px inside the card. The
     tallest content this widget ever holds is ring + status + a
     two-line "no reading" note: 10 + 12.7 + 10 + 28 = 60.7 of
     furniture, so 126 is the largest ring that never forces the row —
     and therefore the tiles — to grow when a check goes unknown.
     At the previous 116 the healthy state sat in 50px of dead space. */
  max-width: 126px;
  aspect-ratio: 1;
}

.health-svg {
  display: block;
  width: 100%;
  height: 100%;
  /* Start the arc at 12 o'clock instead of 3. */
  transform: rotate(-90deg);
}

.health-track {
  fill: none;
  stroke: var(--cc-track);
  stroke-width: 8;
}

.health-arc {
  fill: none;
  stroke-width: 8;
  stroke-linecap: round;
  stroke: var(--cc-idle);
  transition: stroke-dashoffset 520ms var(--cc-ease), stroke 240ms var(--cc-ease);
}
.health-arc.is-ok   { stroke: var(--cc-live) }
.health-arc.is-warn { stroke: var(--cc-warn) }
.health-arc.is-bad  { stroke: var(--cc-sell) }
.health-arc.is-idle { stroke: var(--cc-ink-3) }

.health-centre {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
}

.health-frac {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  color: var(--cc-ink);
}
/* ink-3, not ink-4 — this is the denominator of the headline figure,
   not an annotation. */
.health-den { font-size: 14px; font-weight: 600; color: var(--cc-ink-3) }
.health-none { color: var(--cc-ink-3) }

/* ── Status ──────────────────────────────────────────────────────── */
.health-state {
  font-family: var(--cc-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--cc-ink-3);
  text-align: center;
}
.health-state.is-ok   { color: var(--cc-live) }
.health-state.is-warn { color: var(--cc-warn) }
.health-state.is-bad  { color: var(--cc-sell) }

/* ink-3 rather than the caption scale's ink-4: this line is the whole
   honesty contract of the widget, so it must not be the faintest thing
   in the cell. */
.health-note {
  font-size: 10px;
  line-height: 14px;
  text-align: center;
  color: var(--cc-ink-3);
  text-wrap: pretty;
}
</style>
