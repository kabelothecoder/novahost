<template>
  <!--
    AI CHART SCANNER

    Ported from the "Nova Edge - Chart Scanner" design doc onto the command
    system. The doc lays it out as five artboards; on a handset it is one
    flow with two stages — INPUT, then the reading — because a scan result
    that lives on a different screen from the chart that produced it is a
    screen you have to remember your way back to.

    What the doc draws and this cannot do is called out in place rather than
    faked: there is no economic calendar on this platform, so Event Radar and
    two of the guardrails report as unrunnable instead of green.
  -->
  <div
    class="cmd scan"
    :data-scheme="resolvedScheme"
    :data-card="theme.cardStyle"
    :data-motion="theme.motion"
  >
    <div class="scan-wash" aria-hidden="true"></div>

    <header class="scan-head">
      <button class="scan-icon" aria-label="Back" @click="$router.push('/home')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <div class="scan-title">
        <h1>Chart Scanner</h1>
        <span class="scan-sub">{{ reading ? readingLine : 'Confluence engine · scored on device' }}</span>
      </div>
      <button v-if="reading" class="scan-icon" aria-label="New scan" @click="reset">
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 12a9 9 0 1 1-2.64-6.36" /><polyline points="21 3 21 9 15 9" />
        </svg>
      </button>
      <span v-else class="scan-icon-spacer"></span>
    </header>

    <div class="scan-body">
      <ScannerPaywall v-if="!hasScanner" @unlocked="onUnlocked" />

      <template v-else>
        <!-- ═══ 1 · SCAN INPUT ══════════════════════════════════════ -->
        <template v-if="!reading">
          <!--
            The instrument, and its live price.

            New, and it is what the doc leads the screen with. The chips are
            the robot's OWN allowance (`licenses.allowed_symbols`) rather
            than a list of majors — trading anything outside it is refused
            by metacopier-execute, so offering it here would be an invitation
            to a refusal.

            The quote beneath comes from `broker-quote`, which nothing on
            this platform called until now. It is enrichment, never a
            requirement: reading a chart is what the scanner is for and it
            has nothing to do with whether a broker is attached. Without one
            the card says so and the plan below still builds — it just
            cannot price itself in money.
          -->
          <section v-if="symbols.length" class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Instrument</h2>
              <span class="scan-sec-s">licence allowance</span>
            </div>
            <div class="scan-chips">
              <button
                v-for="s in symbols" :key="s"
                class="scan-chip" :class="{ 'is-on': symbol === s }"
                :aria-pressed="symbol === s"
                @click="pickSymbol(s)"
              >{{ s }}</button>
            </div>
          </section>

          <section v-if="symbol" class="scan-sec">
            <div class="scan-quote cc-card">
              <div class="scan-quote-top">
                <div class="scan-quote-id">
                  <span class="scan-quote-sym">{{ symbol }}</span>
                  <span class="scan-quote-broker cc-caption">
                    {{ quote.ok ? (quote.broker_symbol || 'your broker') : quoteNote }}
                  </span>
                </div>
                <div class="scan-quote-px">
                  <span v-if="quote.ok && quote.price != null" class="scan-quote-n cc-figure">
                    {{ fmtPx(quote.price) }}
                  </span>
                  <span v-else-if="quoteLoading" class="scan-quote-n cc-figure is-dim">…</span>
                  <span v-else class="scan-quote-n cc-figure is-dim">—</span>
                  <span v-if="quote.ok && quote.tradeable === false" class="scan-quote-closed cc-mono">
                    NOT TRADEABLE
                  </span>
                </div>
              </div>

              <div class="scan-quote-tiles">
                <div class="scan-tile">
                  <span class="cc-label">Spread</span>
                  <span class="scan-tile-v cc-figure">
                    {{ quote.ok && quote.spread_pips != null ? quote.spread_pips.toFixed(1) + 'p' : '—' }}
                  </span>
                </div>
                <div class="scan-tile">
                  <span class="cc-label">Pip</span>
                  <span class="scan-tile-v cc-figure">
                    {{ quote.ok && pipMoney != null ? money(pipMoney) + '/lot' : '—' }}
                  </span>
                </div>
                <div class="scan-tile">
                  <span class="cc-label">Session</span>
                  <span class="scan-tile-v scan-tile-sess">
                    <span class="cc-dot" :class="sessions.open ? 'cc-dot-live' : ''"></span>
                    {{ sessions.label }}
                  </span>
                </div>
              </div>
            </div>
          </section>

          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Scan mode</h2>
              <span class="scan-sec-s">{{ activeMode.tf }}</span>
            </div>
            <div class="scan-modes">
              <button
                v-for="m in MODES" :key="m.id"
                class="scan-mode" :class="{ 'is-on': mode === m.id }"
                :aria-pressed="mode === m.id"
                @click="mode = m.id; haptic(20)"
              >
                <span class="scan-mode-n">{{ m.name }}</span>
                <span class="scan-mode-tf cc-mono">{{ m.tf }}</span>
              </button>
            </div>
          </section>

          <!--
            Balance and risk, which is how the doc sizes a position — and it
            is the right way round. The old control was a raw lot stepper, so
            a user had to know what 0.05 lots risked on a 16-pip stop before
            they could choose it. Here they state the percentage they are
            willing to lose and the lot falls out of the stop the scan finds.

            The balance is REAL or it is an em dash. It is never a sample
            figure: this number decides position size, and a placeholder that
            looks like a balance is the single most expensive lie this screen
            could tell.
          -->
          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Risk</h2>
              <span class="scan-sec-s">{{ balance == null ? 'no account linked' : 'per trade' }}</span>
            </div>
            <div class="scan-risk">
              <div class="scan-tile cc-card">
                <span class="cc-label">Balance</span>
                <span class="scan-tile-v cc-figure">{{ balance == null ? '—' : money(balance) }}</span>
              </div>
              <div class="scan-tile cc-card scan-tile-risk">
                <span class="cc-label">Risk / trade</span>
                <span class="scan-risk-row">
                  <button class="scan-step-sm" aria-label="Less risk" @click="bumpRisk(-0.25)">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         stroke-width="2.6" stroke-linecap="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12" /></svg>
                  </button>
                  <span class="scan-tile-v cc-figure">{{ riskPct.toFixed(2) }}%</span>
                  <button class="scan-step-sm" aria-label="More risk" @click="bumpRisk(0.25)">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         stroke-width="2.6" stroke-linecap="round" aria-hidden="true">
                      <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                    </svg>
                  </button>
                </span>
                <span class="cc-caption">{{ riskMoney == null ? 'needs a linked account' : money(riskMoney) }}</span>
              </div>
            </div>
          </section>

          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Your chart</h2>
              <span class="scan-sec-s">JPG · PNG · WEBP</span>
            </div>

            <button class="scan-drop cc-card" :class="{ 'is-busy': scanning }" @click="pickFile" :disabled="scanning">
              <input ref="fileInput" type="file" hidden accept="image/*" @change="onFile" />

              <template v-if="!preview">
                <span class="scan-drop-ring">
                  <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect x="3" y="5" width="18" height="14" rx="2" />
                    <circle cx="8.5" cy="10" r="1.6" /><path d="m21 15-4.5-4.5L11 16" />
                  </svg>
                </span>
                <span class="scan-drop-t">Attach a chart screenshot</span>
                <span class="scan-drop-s cc-caption">Your drawings and levels are read too</span>
              </template>

              <template v-else>
                <img :src="preview" class="scan-preview" alt="Chart to analyse" />
                <span v-if="scanning" class="scan-laser" aria-hidden="true"></span>
                <span v-if="scanning" class="scan-busy cc-mono">Reading the chart…</span>
              </template>
            </button>

            <p v-if="error" class="scan-err cc-caption">{{ error }}</p>
          </section>
        </template>

        <!-- ═══ 2 · CONFLUENCE ══════════════════════════════════════ -->
        <template v-else>
          <section class="scan-sec">
            <div class="scan-verdict cc-card">
              <div class="scan-meter">
                <svg viewBox="0 0 100 100" class="scan-meter-svg" aria-hidden="true">
                  <circle class="scan-meter-track" cx="50" cy="50" r="42" />
                  <circle
                    v-if="score.score != null"
                    class="scan-meter-arc" :class="`is-${gradeTone}`"
                    cx="50" cy="50" r="42"
                    :stroke-dasharray="C"
                    :stroke-dashoffset="C - (C * score.score) / 100"
                  />
                </svg>
                <div class="scan-meter-mid">
                  <span v-if="score.score != null" class="scan-meter-n cc-figure">{{ score.score }}</span>
                  <span v-else class="scan-meter-n cc-figure">—</span>
                  <span class="scan-meter-d cc-caption">of 100</span>
                </div>
              </div>

              <div class="scan-verdict-copy">
                <span class="scan-dir" :class="dirTone">{{ score.direction || '—' }}</span>
                <span class="scan-grade" :class="`is-${gradeTone}`">{{ score.grade }}</span>
                <span class="scan-basis cc-caption">
                  {{ score.passed }} of {{ score.known }} checks passed
                  <template v-if="score.unknown.length"> · {{ score.unknown.length }} could not run</template>
                </span>
              </div>
            </div>

            <!--
              The model's own argument, at the top where the doc puts it —
              a paragraph of plain English before any table. It used to sit
              four sections down under "What it saw", which is where a user
              who already scrolled past the numbers never read it.
            -->
            <p v-if="reading.narrative" class="scan-narr cc-body">{{ reading.narrative }}</p>
          </section>

          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Score breakdown</h2>
              <span class="scan-sec-s cc-mono">
                {{ score.score == null ? '—' : score.score }} of 100
              </span>
            </div>
            <ul class="scan-checks">
              <li v-for="c in score.checks" :key="c.id" class="scan-check cc-card"
                  :class="c.ok === true ? 'is-pass' : c.ok === false ? 'is-fail' : 'is-unknown'">
                <span class="scan-check-mark">
                  <svg v-if="c.ok === true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12" /></svg>
                  <svg v-else-if="c.ok === false" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="3" stroke-linecap="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
                  <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="3" stroke-linecap="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12" /></svg>
                </span>
                <span class="scan-check-copy">
                  <span class="scan-check-l">{{ c.label }}</span>
                  <span class="scan-check-d cc-caption">{{ c.detail }}</span>
                </span>
                <!-- The arithmetic, shown. Not a constant +20: the score is
                     passed over KNOWN, so a reading that could answer four
                     questions is worth 25 each and printing 20 would not add
                     up to the figure above. "n/a" rather than "+0" for an
                     unknown — those are different claims. -->
                <span class="scan-check-pts cc-mono"
                      :class="c.ok === true ? 'is-pass' : c.ok === false ? 'is-fail' : 'is-unknown'">
                  {{ c.points == null ? 'n/a' : '+' + c.points }}
                </span>
              </li>
            </ul>
            <p class="scan-privacy cc-caption">
              Scored on your device from the chart reading. Nothing about your account left the phone.
            </p>
          </section>

          <!-- ═══ 3 · LADDER ════════════════════════════════════════ -->
          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Trade plan</h2>
              <span class="scan-sec-s cc-mono">{{ ladder.valid ? `1:${ladder.blendedRR.toFixed(2)} blended` : 'unusable' }}</span>
            </div>

            <div v-if="!ladder.valid" class="scan-feed cc-card">
              <p class="cc-caption">{{ ladder.reason }}</p>
            </div>

            <template v-else>
              <!--
                The plan as a PRICE COLUMN, which is the doc's signature
                element and the thing a table cannot do: the rungs are drawn
                in the order they sit on the chart, above or below the entry
                depending on direction, with the distance between them to
                scale. A trader reads their own chart this way round; a table
                sorted TP1→TP3 reads bottom-up on a sell and has to be
                decoded every time.

                Band heights are proportional to the real price gaps, floored
                so a tight rung never collapses to an invisible line.
              -->
              <div class="scan-rungs cc-card" :class="ladder.isBuy ? 'is-buy' : 'is-sell'">
                <div
                  v-for="r in rungs" :key="r.key"
                  class="scan-rung" :class="r.kind"
                  :style="{ flexGrow: r.grow }"
                >
                  <span class="scan-rung-l">
                    <span class="scan-rung-tag">{{ r.label }}</span>
                    <span class="scan-rung-meta cc-mono">{{ r.meta }}</span>
                  </span>
                  <span class="scan-rung-px cc-figure">{{ fmt(r.price) }}</span>
                </div>
              </div>

              <div class="scan-sec-head scan-sec-head-sub">
                <h3 class="scan-sec-t">Allocation</h3>
                <span class="scan-sec-s">tap to rebalance</span>
              </div>

              <div class="scan-allocs">
                <button
                  v-for="a in ALLOCATIONS" :key="a.id"
                  class="scan-alloc" :class="{ 'is-on': allocation === a.id }"
                  :aria-pressed="allocation === a.id"
                  @click="allocation = a.id; haptic(20)"
                >
                  <span class="scan-alloc-s cc-mono">{{ a.split.join('/') }}</span>
                  <span class="scan-alloc-n">{{ a.name }}</span>
                </button>
              </div>

              <!-- The split as one bar. Three numbers in a row are read one
                   at a time; a bar is read at once, which is the whole point
                   of choosing between three presets. -->
              <div class="scan-bar" aria-hidden="true">
                <span
                  v-for="l in ladder.legs" :key="l.level"
                  class="scan-bar-seg" :class="`is-${l.level.toLowerCase()}`"
                  :style="{ flexGrow: l.alloc }"
                >{{ l.alloc }}%</span>
              </div>

              <div class="scan-ladder cc-card">
                <div class="scan-row is-head">
                  <span class="cc-label">Level</span><span class="cc-label">R:R</span>
<span class="cc-label">%</span><span class="cc-label">Lots</span>
                  <span class="cc-label">Est P/L</span>
                </div>
                <div v-for="l in ladder.legs" :key="l.level" class="scan-row"
                     :class="{ 'is-muted': mode2 === 'single' && l.level !== 'TP1' }">
                  <span class="scan-row-l">
                    <span class="scan-row-dot" :class="`is-${l.level.toLowerCase()}`"></span>
                    {{ l.level }}
                    <span v-if="l.projected" class="scan-proj cc-mono">proj</span>
                  </span>
                  <span class="cc-figure">1:{{ l.rr.toFixed(2) }}</span>
                  <span class="cc-figure">{{ mode2 === 'single' && l.level === 'TP1' ? 100 : l.alloc }}%</span>
                  <span class="cc-figure" :class="{ 'is-warn': l.undersized }">
                    {{ mode2 === 'single' && l.level === 'TP1' ? totalLot.toFixed(2) : l.lot.toFixed(2) }}
                  </span>
                  <!-- Money or an em dash, never a zero. Without a broker
                       spec there is no contract size and therefore no honest
                       figure — see pipValuePerLot() in confluence.js. -->
                  <span class="cc-figure is-up">{{ legMoney(l) }}</span>
                </div>
                <div class="scan-row is-foot">
                  <span class="scan-row-l">
                    <span class="scan-row-dot is-sl"></span>SL
                  </span>
                  <span class="cc-figure">{{ ladder.slPips.toFixed(1) }}p</span>
                  <span class="cc-figure">100%</span>
                  <span class="cc-figure">{{ totalLot.toFixed(2) }}</span>
                  <span class="cc-figure is-down">{{ ladder.riskMoney == null ? '—' : moneyShort(-ladder.riskMoney) }}</span>
                </div>
              </div>

              <div class="scan-totals">
                <span class="cc-caption">
                  Risk <b class="is-down">{{ ladder.riskMoney == null ? '—' : money(ladder.riskMoney) }}</b>
                  · Reward <b class="is-up">{{ rewardShown == null ? '—' : money(rewardShown) }}</b>
                </span>
                <span class="scan-totals-rr cc-mono">Blended 1:{{ effectiveLadder.blendedRR.toFixed(2) }}</span>
              </div>

              <p class="scan-note cc-caption">
                TP1 is the level read off your chart. TP2 and TP3 are 2R and 3R projections of the same stop.
                <template v-if="ladder.priced">
                  Money figures use your broker's own contract size for {{ pair }}.
                </template>
                <template v-else>
                  Pip figures are estimated from the instrument family — link a broker to price the plan in {{ balance == null ? 'money' : 'your account currency' }}.
                </template>
              </p>
            </template>
          </section>

          <!-- ═══ 4 · CONTEXT ═══════════════════════════════════════ -->
          <section v-if="timeframes.length" class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Timeframe alignment</h2>
              <span class="scan-sec-s">{{ timeframes.length }} read</span>
            </div>
            <!-- A grid, not a list: these are four readings of one market to
                 be compared with each other, and a column makes the eye
                 travel to do it. -->
            <div class="scan-mtf">
              <div v-for="t in timeframes" :key="t.timeframe" class="scan-tf cc-card">
                <div class="scan-tf-top">
                  <span class="scan-tf-id cc-mono">{{ t.timeframe }}</span>
                  <span class="scan-tf-arrow" :class="biasTone(t.bias)" v-html="biasArrow(t.bias)"></span>
                </div>
                <span class="scan-tf-bias" :class="biasTone(t.bias)">{{ t.bias }}</span>
                <span class="scan-tf-note cc-caption">{{ t.note }}</span>
              </div>
            </div>
          </section>

          <section class="scan-sec">
            <div class="scan-guards cc-card" :class="guards.blocked ? 'is-blocked' : 'is-clear'">
              <div class="scan-guards-head">
                <span class="scan-guards-icon">
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M12 3l7 3v5.5c0 4.3-3 8.2-7 9.5-4-1.3-7-5.2-7-9.5V6z" />
                    <polyline v-if="!guards.blocked" points="9 12 11 14 15 10" />
                    <line v-else x1="12" y1="8" x2="12" y2="13" />
                    <line v-if="guards.blocked" x1="12" y1="16" x2="12.01" y2="16" />
                  </svg>
                </span>
                <span class="scan-guards-copy">
                  <span class="scan-guards-t">{{ guards.blocked ? 'Execution blocked' : 'Guardrails passed' }}</span>
                  <span class="cc-caption">{{ guards.summary }} · checked on device</span>
                </span>
              </div>

              <ul class="scan-rules">
                <li v-for="r in guards.rules" :key="r.id" class="scan-rule"
                    :class="`is-${r.status.toLowerCase()}`">
                  <span class="scan-rule-copy">
                    <span class="scan-rule-l">{{ r.label }}</span>
                    <span class="scan-rule-d cc-caption">{{ r.detail }}</span>
                  </span>
                  <span class="scan-badge" :class="`is-${r.status.toLowerCase()}`">{{ r.status }}</span>
                </li>
              </ul>
            </div>

            <!--
              What to DO about a block. The doc calls it "Two ways forward",
              and it is the difference between a rule that helps and a rule
              that is just a locked door: every blocker here has a concrete
              next move, and both of them are the user's to make.
            -->
            <div v-if="guards.blocked && waysForward.length" class="scan-ways cc-card">
              <span class="scan-ways-t">Two ways forward</span>
              <p class="cc-caption scan-ways-d">{{ waysForward[0].detail }}</p>
              <div class="scan-ways-row">
                <button
                  v-for="w in waysForward" :key="w.id"
                  class="scan-way" :class="{ 'is-primary': w.primary }"
                  @click="w.run()"
                >{{ w.label }}</button>
              </div>
            </div>
          </section>

          <!-- ═══ 5 · EXECUTE ═══════════════════════════════════════ -->
          <section class="scan-sec">
            <div class="scan-sec-head">
              <h2 class="scan-sec-t">Send to broker</h2>
              <span class="scan-sec-s">{{ pair || 'no symbol' }}</span>
            </div>

            <!--
              The size, last, where it is actually committed.

              It used to be a stepper on the INPUT screen, which asked the
              user to choose a lot before anything knew the stop distance —
              the one number that decides what a lot costs. Risk-per-trade
              now sets it from the stop the scan found, and this stays as the
              override: someone with no broker linked has no balance and
              therefore no derived size, and they still have to be able to
              trade.
            -->
            <div class="scan-lot cc-card">
              <button class="scan-step" aria-label="Decrease lot" @click="bumpLot(-0.01)">
                <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12" /></svg>
              </button>
              <div class="scan-lot-v">
                <span class="scan-lot-num cc-figure">{{ totalLot.toFixed(2) }}</span>
                <span class="scan-lot-note cc-caption">total lots · {{ lotSource }}</span>
              </div>
              <button class="scan-step is-accent" aria-label="Increase lot" @click="bumpLot(0.01)">
                <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
                  <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                </svg>
              </button>
            </div>

            <!-- The choice is explicit and per-trade. One order and three are
                 different amounts of exposure and different failure modes;
                 neither is a default worth guessing at. -->
            <div class="scan-modes scan-modes-2">
              <button class="scan-mode" :class="{ 'is-on': mode2 === 'single' }"
                      :aria-pressed="mode2 === 'single'" @click="mode2 = 'single'; haptic(20)">
                <span class="scan-mode-n">Single order</span>
                <span class="scan-mode-tf cc-mono">{{ totalLot.toFixed(2) }} at TP1</span>
              </button>
              <button class="scan-mode" :class="{ 'is-on': mode2 === 'ladder' }"
                      :aria-pressed="mode2 === 'ladder'" @click="mode2 = 'ladder'; haptic(20)">
                <span class="scan-mode-n">Ladder</span>
                <span class="scan-mode-tf cc-mono">{{ fundedLegs }} orders</span>
              </button>
            </div>

            <p v-if="mode2 === 'ladder'" class="scan-note cc-caption">
              Each rung is a separate position at your broker, sent one at a time. If one is refused the
              earlier ones stay open and the rest are not sent — you will be told exactly which.
            </p>

            <button
              class="scan-exec"
              :class="{ 'is-blocked': guards.blocked }"
              :disabled="sending || guards.blocked || !pair || !ladder.valid"
              @click="execute"
            >
              <svg v-if="guards.blocked" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <rect x="4" y="11" width="16" height="9" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" />
              </svg>
              <svg v-else-if="!sending" width="17" height="17" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M13 2L4.5 13H11l-1 9 8.5-11H12z" />
              </svg>
              <svg v-else class="scan-spin" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M21 12a9 9 0 1 1-6.36-8.6" /></svg>
              <span>{{ execLabel }}</span>
            </button>

            <p v-if="guards.blocked" class="scan-note cc-caption">
              Only you can change your rules, in Guardrail settings.
            </p>
            <p v-else class="scan-note cc-caption">
              Review this sheet before anything reaches your broker.
            </p>
            <p v-if="result" class="scan-result cc-caption" :class="{ 'is-error': !resultOk }">{{ result }}</p>
          </section>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { theme, resolvedScheme } from '../stores/theme.js'
import { session } from '../stores/session.js'
import { robot } from '../stores/robot.js'
import { trades } from '../stores/trades.js'
import { loadQuote, loadAccount } from '../services/brokerService.js'
import { authState } from '../services/authService'
import { haptic } from '../composables/useSpeech.js'
import ScannerPaywall from '../components/ScannerPaywall.vue'
import { scanChart, sendPlan, preflight } from '../services/scannerService.js'
import { buildLadder, scoreConfluence, checkGuardrails, ALLOCATIONS, MIN_LOT } from '../services/confluence.js'
import { loadPlan, normaliseSymbol } from '../services/symbolPlanService.js'
// TEMPORARY dev-only scanner unlock. The `import.meta.env.DEV &&` guard at
// its call site below makes Rollup drop this import from any production
// build. Delete this line and the marked usage together.
import { devScannerUnlocked } from '../utils/devGate.js'

/* The add-on gate. `analyze-chart` is the real authority — this only decides
   what to draw, and a client that lied here would still be refused.

   The `import.meta.env.DEV &&` is what makes the bypass safe: Vite replaces
   it with the literal `false` in a production build, the branch dies, and
   Rollup then drops devGate.js from the bundle entirely. Same pattern the
   router's gate bypass uses — see the note in utils/devGate.js. */
const hasScanner = computed(
  () => authState.hasScanner || (import.meta.env.DEV && devScannerUnlocked())
)

const MODES = [
  { id: 'scalp', name: 'Scalp', tf: 'M5–M15' },
  { id: 'day',   name: 'Day',   tf: 'H1–H4' },
  { id: 'swing', name: 'Swing', tf: 'H4–D1' },
]
const mode = ref('day')
const activeMode = computed(() => MODES.find(m => m.id === mode.value) || MODES[1])

const C = 2 * Math.PI * 42

const fileInput = ref(null)
const preview = ref(null)
const scanning = ref(false)
const error = ref('')
const reading = ref(null)

const allocation = ref('balanced')
/** 'single' | 'ladder'. Starts on single: one order is the smaller,
 *  more reversible action, and the ladder is opted into. */
const mode2 = ref('single')

const totalLot = ref(0.05)
const lotSource = ref('manual')
/** Set the moment the user works the lot themselves, after which the
 *  risk-derived size stops overwriting their choice. */
const lotTouched = ref(false)

/* ── Instrument and its live price ───────────────────────────────────
   The symbol is the user's pick until a scan names one, and then the
   CHART wins: the reading is about that instrument, and pricing the plan
   against a different one would be the most expensive kind of silent
   mismatch. `pair` below encodes that order.

   The quote is enrichment. `broker-quote` needs a linked account and
   says NO_ACCOUNT_LINKED without one; the whole screen works either way,
   it just cannot put money on the plan. */
const symbol = ref('')
const quote = ref({ ok: false })
const quoteLoading = ref(false)
let quoteToken = 0

const symbols = computed(() => {
  const allowed = (robot.allowedSymbols || []).map(x => normaliseSymbol(String(x))).filter(Boolean)
  const scanned = pair.value
  /* The scanned instrument is offered even when it is off the allowance,
     because the user is looking at its reading — it is metacopier-execute
     that refuses the order, and it does so with a reason. Hiding it here
     would make the chip row disagree with the plan below it. */
  return [...new Set(scanned ? [scanned, ...allowed] : allowed)].slice(0, 8)
})

async function fetchQuote(sym) {
  if (!sym) { quote.value = { ok: false }; return }
  const mine = ++quoteToken
  quoteLoading.value = true
  const res = await loadQuote(sym)
  /* A stale answer must never overwrite a fresh one — tapping three chips
     quickly fires three requests and they do not come back in order. */
  if (mine !== quoteToken) return
  quote.value = res
  quoteLoading.value = false
}

function pickSymbol(s) {
  if (symbol.value === s) return
  symbol.value = s
  haptic(20)
  fetchQuote(s)
}

const quoteNote = computed(() => {
  if (quoteLoading.value) return 'pricing…'
  if (quote.value.ok) return 'your broker'
  if (quote.value.code === 'NO_ACCOUNT_LINKED') return 'no broker linked'
  return quote.value.message || 'no live price'
})

/** Money for one pip on one lot, in the account's quote currency. */
const pipMoney = computed(() => {
  const q = quote.value
  if (!q.ok) return null
  const c = Number(q.contract_size), pip = Number(q.pip_size)
  return c > 0 && pip > 0 ? c * pip : null
})

/* ── Sessions ────────────────────────────────────────────────────────
   Derived from the clock, not fetched. The London and New York windows
   are fixed UTC hours and the device already knows the time, so this is
   one of the few things on the screen that needs nothing from a server.
   Weekends are stated as closed rather than shown as a quiet Tokyo. */
const nowTick = ref(Date.now())
let sessionTimer = null
/* onUnmounted, not a cleanup returned from onMounted — Vue does not call a
   value returned from a lifecycle hook, so that shape is a timer that
   outlives the screen and keeps waking the device once a minute forever. */
onMounted(() => { sessionTimer = setInterval(() => { nowTick.value = Date.now() }, 60_000) })
onUnmounted(() => clearInterval(sessionTimer))

const sessions = computed(() => {
  const d = new Date(nowTick.value)
  const day = d.getUTCDay()
  if (day === 0 || day === 6) return { open: false, label: 'Weekend' }
  const h = d.getUTCHours() + d.getUTCMinutes() / 60
  const on = []
  if (h >= 0 && h < 9)   on.push('Tokyo')
  if (h >= 7 && h < 16)  on.push('London')
  if (h >= 12 && h < 21) on.push('New York')
  return on.length ? { open: true, label: on.join(' + ') } : { open: false, label: 'Between sessions' }
})

/* ── Balance and risk ────────────────────────────────────────────────
   The balance is the broker's or it is an em dash. A sample figure here
   would size every position the user placed, which makes it the single
   most expensive placeholder this screen could carry. */
const balance = computed(() => {
  const b = trades.balance
  /* `null` has to be rejected BEFORE Number() sees it: Number(null) is 0
     and Number.isFinite(0) is true, so the guard as first written turned
     "no account" into a balance of zero — which rendered as "USD 0.00", a
     figure that looks like a real empty account rather than an absent one.
     Same trap for an empty string. */
  if (b == null || b === '') return null
  const n = Number(b)
  return Number.isFinite(n) ? n : null
})
const currency = computed(() => trades.currency || quote.value.quote_currency || session.brokerCurrency || null)

const riskPct = ref(1)
const riskMoney = computed(() =>
  balance.value == null ? null : (balance.value * riskPct.value) / 100,
)

function bumpRisk(d) {
  riskPct.value = Math.min(5, Math.max(0.25, Math.round((riskPct.value + d) * 100) / 100))
  lotTouched.value = false
  haptic(15)
}

/** Signed money in the account's own currency, or an em dash. */
function money(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  const body = Math.abs(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return currency.value ? `${currency.value} ${body}` : body
}

/**
 * The same figure, short enough for a five-column row.
 *
 * "USD 48.00" does not fit beside a level, an R:R, an allocation and a lot
 * on a 390px screen — it truncated to "USD 48.…", which is worse than no
 * column at all. The symbol replaces the code where one is unambiguous and
 * the decimals go: these are ESTIMATES of a target that has not filled, and
 * quoting them to the cent implies a precision the number does not have.
 * The full figure with its currency is still stated in the totals line and
 * on the quote card above.
 */
const CURRENCY_SIGN = {
  USD: '$', EUR: '€', GBP: '£', JPY: '¥', ZAR: 'R',
  AUD: 'A$', CAD: 'C$', CHF: 'CHF', NZD: 'NZ$',
}

function moneyShort(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  const cur = currency.value
  const sign = cur ? (CURRENCY_SIGN[cur] || cur + ' ') : ''
  const body = Math.round(Math.abs(n)).toLocaleString()
  return (n < 0 ? '-' : '+') + sign + body
}

function legMoney(l) {
  return l.money == null ? '—' : moneyShort(l.money)
}

/** Prices at the broker's own digits when it told us, its own guess when
 *  it did not. A five-decimal FX quote and a one-decimal index must not
 *  share a format. */
function fmtPx(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  const d = Number(quote.value.digits)
  return Number.isFinite(d) ? n.toFixed(d) : fmt(n)
}

const sending = ref(false)
const result = ref('')
const resultOk = ref(true)

/* ── Derived ─────────────────────────────────────────────────────── */
const pair = computed(() => normaliseSymbol(reading.value?.symbol_on_chart || ''))

const ladder = computed(() =>
  reading.value
    ? buildLadder(reading.value, {
        allocation: allocation.value,
        totalLot: totalLot.value,
        /* The broker's own contract size and pip size, when we have them.
           They are what turn a pip distance into a money figure, and their
           absence is why the plan could only ever show pips before. */
        spec: quote.value.ok ? quote.value : null,
      })
    : { valid: false, legs: [], reason: '' },
)
const score = computed(() =>
  reading.value ? scoreConfluence(reading.value, effectiveLadder.value)
                : { checks: [], passed: 0, known: 0, unknown: [], score: null, grade: 'UNKNOWN', direction: '' },
)
/**
 * The plan actually being sent — which is what the guardrails must judge.
 *
 * In 'single' mode only TP1 goes to the broker, at the full lot. Scoring
 * that against the LADDER's blended R:R was a real hole: TP2 and TP3 are
 * fixed 2R and 3R projections, so a balanced split floors blended at
 * 0.25*2 + 0.25*3 = 1.25 before TP1 contributes anything. A 0.50R first
 * target therefore blended to 1.50 and cleared a 1.5 minimum — passing a
 * single 0.50R trade on the strength of two rungs that were never placed.
 * Measured on a GBP/JPY setup: TP1 at 0.50R, execute enabled.
 */
const effectiveLadder = computed(() => {
  const l = ladder.value
  if (!l.valid || mode2.value === 'ladder') return l
  const first = l.legs[0]
  return {
    ...l,
    legs: [{ ...first, alloc: 100, lot: totalLot.value }],
    blendedRR: first.rr,
  }
})

const guards = computed(() => checkGuardrails(effectiveLadder.value))

const gradeTone = computed(() =>
  ({ STRONG: 'ok', MODERATE: 'warn', WEAK: 'bad', UNKNOWN: 'idle' })[score.value.grade] || 'idle',
)
const dirTone = computed(() =>
  score.value.direction === 'BUY' ? 'is-buy' : score.value.direction === 'SELL' ? 'is-sell' : '',
)
const timeframes = computed(() =>
  Array.isArray(reading.value?.timeframes) ? reading.value.timeframes : [],
)
const fundedLegs = computed(() => ladder.value.legs?.filter(l => l.lot > 0).length || 0)

/* Reward for the plan ACTUALLY being sent. In single mode only TP1 goes
   out, at the full lot — summing three rungs there would quote a number
   from two orders that were never placed, which is the same hole the
   `effectiveLadder` note below describes for the R:R. */
const rewardShown = computed(() => {
  const l = effectiveLadder.value
  if (!l.valid || !l.priced) return null
  return l.legs.reduce((sum, leg) => sum + (leg.money || 0), 0)
})

/**
 * The plan drawn as a price column, in chart order.
 *
 * Band heights are proportional to the real gaps between the levels, so
 * the picture is the plan rather than a decoration of it. Two rules make
 * it survive real data: every band gets a floor (`MIN_GROW`) so a tight
 * rung cannot collapse to an invisible line, and the order flips on a
 * sell — targets sit BELOW the entry there, and drawing them above would
 * be a picture of a different trade.
 */
const MIN_GROW = 0.6
const rungs = computed(() => {
  const l = ladder.value
  if (!l.valid) return []

  const points = [
    ...l.legs.map(leg => ({
      key: leg.level,
      label: leg.level,
      kind: 'is-tp',
      price: leg.price,
      meta: `1:${leg.rr.toFixed(1)} · ${mode2.value === 'single' && leg.level !== 'TP1' ? '—' : (mode2.value === 'single' ? '100' : leg.alloc) + '%'}`,
    })),
    {
      key: 'ENTRY',
      label: 'ENTRY',
      kind: 'is-entry',
      price: l.entry,
      meta: `${(reading.value?.entry_type || 'market').toLowerCase()} · ${totalLot.value.toFixed(2)} lots`,
    },
    {
      key: 'SL',
      label: 'SL',
      kind: 'is-sl',
      price: l.sl,
      meta: `${l.slPips.toFixed(1)} pips${l.riskMoney == null ? '' : ' · -' + money(l.riskMoney)}`,
    },
  ]

  /* Descending price is chart order for a BUY (targets on top); a SELL is
     the same list upside down. Sorting on the PRICE rather than assembling
     two hardcoded orders means a reading whose levels arrive in an odd
     order still draws correctly. */
  points.sort((x, y) => y.price - x.price)

  const span = Math.abs(points[0].price - points[points.length - 1].price) || 1
  return points.map((pt, i) => {
    const next = points[i + 1]
    const gap = next ? Math.abs(pt.price - next.price) / span : 0
    return { ...pt, grow: Math.max(MIN_GROW, gap * 6) }
  })
})

/**
 * What a user can actually DO about a block.
 *
 * Every entry is a real action, not advice. The doc's own framing is "two
 * ways forward", and the point of it is that a guardrail which only ever
 * says no is indistinguishable from a bug.
 */
const waysForward = computed(() => {
  const out = []
  const g = guards.value
  if (!g.blocked) return out

  const rrBlocked = g.blocks.some(b => b.id === 'rr')

  if (rrBlocked && ladder.value.valid) {
    /* A runner split weights the far targets, which is the one change on
       this screen that raises blended R:R without touching the stop. */
    if (allocation.value !== 'runner') {
      out.push({
        id: 'runner',
        label: 'Retune targets',
        primary: true,
        detail: 'A runner split weights TP2 and TP3, which lifts the blended R:R without moving your stop.',
        run: () => { allocation.value = 'runner'; haptic(30) },
      })
    }
    /* Single-mode judges TP1 alone, which is a genuinely different trade
       and sometimes the one that clears. Offered only when it would. */
    if (mode2.value === 'ladder') {
      const first = ladder.value.legs[0]
      if (first && first.rr >= 1.5) {
        out.push({
          id: 'single',
          label: 'Send TP1 only',
          detail: 'TP1 on its own clears your minimum at 1:' + first.rr.toFixed(2) + '.',
          run: () => { mode2.value = 'single'; haptic(30) },
        })
      }
    }
  }

  out.push({
    id: 'rescan',
    label: 'Scan again',
    detail: 'Or wait for a setup that clears the rule on its own.',
    run: reset,
  })

  return out.slice(0, 2)
})

const readingLine = computed(() => {
  const r = reading.value
  if (!r) return ''
  return [pair.value, r.timeframe_on_chart, r.entry_type].filter(Boolean).join(' · ')
})

const execLabel = computed(() => {
  if (sending.value) return 'Sending…'
  if (guards.value.blocked) return 'Blocked by guardrails'
  if (!pair.value) return 'No symbol on the chart'
  if (mode2.value === 'single') return `Send 1 order · ${totalLot.value.toFixed(2)}`
  return `Send ${fundedLegs.value} orders · ${totalLot.value.toFixed(2)}`
})

/** Prices carry the instrument's own decimals — a 5-decimal FX quote and a
 *  1-decimal index must not be forced to a shared format. */
function fmt(v) {
  const n = Number(v)
  if (!isFinite(n)) return '—'
  return n >= 1000 ? n.toFixed(2) : n.toFixed(n < 10 ? 5 : 3)
}

function biasTone(b) {
  const s = String(b).toUpperCase()
  return s === 'BULLISH' ? 'is-buy' : s === 'BEARISH' ? 'is-sell' : ''
}

/* Up, down or sideways. Neutral gets an arrow of its own rather than a
   blank: an empty cell reads as a timeframe that failed to load, and a
   ranging market is a reading, not a gap. */
function biasArrow(b) {
  const s = String(b).toUpperCase()
  if (s === 'BULLISH') return '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="6 11 12 5 18 11"/></svg>'
  if (s === 'BEARISH') return '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="6 13 12 19 18 13"/></svg>'
  return '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="13 6 19 12 13 18"/></svg>'
}

/**
 * The lot the risk budget actually buys.
 *
 * `risk / (pip value x stop distance)` — the standard sizing identity, and
 * the reason the input screen asks for a PERCENTAGE rather than a lot. It
 * needs three things that only exist together: a balance (broker), a pip
 * value (broker contract spec) and a stop distance (the scan). Any one
 * missing and this is null, and the manual stepper stays in charge.
 *
 * Floored to the 0.01 step, never rounded up: rounding up spends risk the
 * user did not allocate, which is the whole thing they just set.
 */
const suggestedLot = computed(() => {
  const l = ladder.value
  if (!l.valid || riskMoney.value == null || pipMoney.value == null) return null
  const perLot = pipMoney.value * l.slPips
  if (!(perLot > 0)) return null
  const raw = riskMoney.value / perLot
  const floored = Math.floor(raw / MIN_LOT) * MIN_LOT
  return floored >= MIN_LOT ? Number(floored.toFixed(2)) : null
})

/* Follows the risk budget until the user takes the wheel. Not a watcher on
   `totalLot` — that would fight itself, since writing the suggestion is
   itself a change to `totalLot`. */
watch(suggestedLot, (v) => {
  if (v == null || lotTouched.value) return
  totalLot.value = v
  lotSource.value = `${riskPct.value}% of balance`
}, { immediate: true })

/* ── Actions ─────────────────────────────────────────────────────── */
function bumpLot(d) {
  totalLot.value = Math.max(MIN_LOT, Math.round((totalLot.value + d) * 100) / 100)
  lotSource.value = 'manual'
  lotTouched.value = true
  haptic(15)
}

function pickFile() { if (!scanning.value) fileInput.value?.click() }

function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const fr = new FileReader()
  fr.onload = ev => { preview.value = ev.target.result; run() }
  fr.readAsDataURL(file)
}

async function run() {
  scanning.value = true
  error.value = ''
  reading.value = null
  try {
    const data = await scanChart(preview.value, mode.value)
    reading.value = data
    seedLot(data)
    /* The chart's instrument wins over the chip. Pricing a EUR/USD reading
       against the GBP/JPY the user last tapped would put a plausible,
       wrong money figure on every row of the plan. */
    const scanned = normaliseSymbol(data?.symbol_on_chart || '')
    if (scanned && scanned !== symbol.value) {
      symbol.value = scanned
      fetchQuote(scanned)
    }
    haptic(40)
  } catch (err) {
    /* The model's own "I could not read this" is more useful than a generic,
       so it is shown verbatim. */
    error.value = err?.message || 'The scan failed. Try a clearer screenshot.'
    preview.value = null
  } finally {
    scanning.value = false
  }
}

/**
 * Seeds the lot from the Trading Symbols plan when the scanned instrument is
 * one the user has already sized. Their configured size for a symbol is a
 * better starting point than a constant, and it keeps the two screens
 * telling the same story.
 */
function seedLot(data) {
  const sym = normaliseSymbol(data?.symbol_on_chart || '')
  const row = sym ? loadPlan()[sym] : null
  if (row && row.enabled && row.lot > 0) {
    totalLot.value = row.lot
    lotSource.value = 'from Trading Symbols'
    /* A size the user set themselves on the Trading Symbols screen is a
       deliberate choice and outranks the risk calculation, the same way a
       tap on the stepper does. */
    lotTouched.value = true
  } else {
    lotSource.value = 'manual'
    lotTouched.value = false
  }
}

async function execute() {
  if (sending.value || guards.value.blocked) return
  sending.value = true
  result.value = ''
  haptic(60)

  const plan = {
    pair: pair.value,
    side: score.value.direction,
    sl: ladder.value.sl,
    entry: ladder.value.entry,
    legs: ladder.value.legs,
    totalLot: totalLot.value,
    orderType: reading.value?.entry_type || 'MARKET',
  }

  /* Validate server-side before anything real goes out — licence, allowance,
     position cap, stop sanity and symbol resolution all answered by the
     executor while `dry_run` keeps it from placing. */
  const pre = await preflight(plan)
  if (!pre.ok) {
    sending.value = false
    resultOk.value = false
    result.value = `Not sent — ${pre.message}`
    return
  }

  const res = await sendPlan(plan, mode2.value)
  sending.value = false
  resultOk.value = res.ok
  result.value = res.message
  if (res.ok) haptic([40, 60, 40])
}

function reset() {
  reading.value = null
  preview.value = null
  result.value = ''
  error.value = ''
  haptic(20)
}

function onUnlocked() { reset() }

/* The balance comes from the broker, through the same `broker-account`
   call the rest of the app uses. It is asked for once on open rather than
   polled: a balance moves when a trade closes, and this screen is open for
   the length of one decision. */
onMounted(async () => {
  const acct = await loadAccount()
  if (acct.ok) {
    if (acct.balance != null) trades.balance = Number(acct.balance)
    if (acct.currency) trades.currency = acct.currency
  }
  if (!symbol.value && symbols.value.length) {
    symbol.value = symbols.value[0]
    fetchQuote(symbol.value)
  }
})
</script>

<style scoped>
.scan {
  position: relative;
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
}

.scan-wash {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(120% 42% at 50% 0%, rgba(var(--cc-accent-rgb), 0.14), transparent 62%),
    radial-gradient(100% 38% at 88% 100%, rgba(var(--cc-accent-rgb), 0.06), transparent 60%);
}

/* ── Header ──────────────────────────────────────────────────────── */
.scan-head {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: calc(var(--cc-safe-top) + 10px) 18px 12px;
  background: var(--cc-glass-thin);
  -webkit-backdrop-filter: var(--cc-glass-thin-blur);
  backdrop-filter: var(--cc-glass-thin-blur);
  border-bottom: 0.5px solid var(--cc-glass-rim);
}
.scan-icon {
  flex: none;
  display: grid; place-items: center;
  width: 44px; height: 44px;
  border: none; background: none;
  border-radius: var(--cc-r-chip);
  color: var(--cc-ink-3);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.scan-icon:active { transform: scale(0.9) }
.scan-icon-spacer { width: 44px; height: 44px; flex: none }

.scan-title { flex: 1; min-width: 0 }
.scan-title h1 {
  margin: 0; font-size: 19px; font-weight: 700;
  letter-spacing: -0.02em; color: var(--cc-ink);
}
.scan-sub {
  display: block; margin-top: 2px; font-size: 11px; color: var(--cc-ink-3);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ── Body ────────────────────────────────────────────────────────── */
.scan-body {
  position: relative; z-index: 1;
  display: flex; flex-direction: column; gap: 24px;
  padding: 20px 18px calc(var(--cc-safe-bottom) + 118px);
}
.scan-sec { display: flex; flex-direction: column; gap: 11px }
.scan-sec-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px }
.scan-sec-t { margin: 0; font-size: 15px; font-weight: 600; letter-spacing: -0.01em; color: var(--cc-ink) }
.scan-sec-s { font-size: 11px; color: var(--cc-ink-3); flex: none }

/* ── Mode / allocation pickers ───────────────────────────────────── */
.scan-modes { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
.scan-modes-2 { grid-template-columns: repeat(2, 1fr) }
.scan-mode {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
  min-height: 56px; padding: 9px 6px;
  border: 1px solid var(--cc-border); border-radius: var(--cc-r-tile);
  background: var(--cc-card-2);
  cursor: pointer;
  transition: border-color 200ms var(--cc-ease), transform 180ms var(--cc-spring);
}
.scan-mode:active { transform: scale(0.96) }
.scan-mode.is-on { border-color: var(--cc-accent); background: var(--cc-accent-dim) }
.scan-mode-n { font-size: 12.5px; font-weight: 600; color: var(--cc-ink-3) }
.scan-mode.is-on .scan-mode-n { color: var(--cc-ink) }
.scan-mode-tf { font-size: 9px; color: var(--cc-ink-4) }
.scan-mode.is-on .scan-mode-tf { color: var(--cc-accent-text) }

.scan-allocs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
/* The SPLIT leads and the name follows, reversing the old order: "50/25/25"
   is the thing being chosen and "Balanced" is what it is called. */
.scan-alloc { flex-direction: column }
.scan-alloc {
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  min-height: 46px; padding: 8px 6px;
  border: 1px solid var(--cc-border); border-radius: var(--cc-r-tile);
  background: var(--cc-card-2); cursor: pointer;
}
.scan-alloc.is-on { border-color: var(--cc-accent); background: var(--cc-accent-dim) }
.scan-alloc-n { font-size: 11.5px; font-weight: 600; color: var(--cc-ink-3) }
.scan-alloc.is-on .scan-alloc-n { color: var(--cc-ink) }
.scan-alloc-s { font-size: 9px; color: var(--cc-ink-4) }

/* ── Lot stepper ─────────────────────────────────────────────────── */
.scan-lot { display: flex; align-items: center; gap: 12px; padding: 12px 14px }
.scan-step {
  flex: none; display: grid; place-items: center;
  width: 44px; height: 44px; border-radius: 13px;
  border: 1px solid var(--cc-border-strong); background: var(--cc-card-2);
  color: var(--cc-ink-2); cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.scan-step:active { transform: scale(0.92) }
.scan-step.is-accent { border-color: var(--cc-accent-line); background: var(--cc-accent-dim); color: var(--cc-accent-text) }
.scan-lot-v { flex: 1; text-align: center; min-width: 0 }
.scan-lot-num { display: block; font-size: 24px; font-weight: 600; line-height: 1; color: var(--cc-accent-text) }
.scan-lot-note { display: block; margin-top: 4px; font-size: 10px }

/* ── Drop zone ───────────────────────────────────────────────────── */
.scan-drop {
  position: relative;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px;
  min-height: 190px; padding: 20px;
  cursor: pointer; overflow: hidden;
  border-style: dashed !important;
}
.scan-drop:disabled { cursor: default }
.scan-drop-ring {
  display: grid; place-items: center;
  width: 54px; height: 54px; border-radius: 50%;
  border: 1px solid var(--cc-accent-line); background: var(--cc-accent-dim);
  color: var(--cc-accent-text);
}
.scan-drop-t { font-size: 13.5px; font-weight: 600; color: var(--cc-ink-2) }
.scan-drop-s { text-align: center }
.scan-preview { width: 100%; border-radius: var(--cc-r-tile); display: block }

/* The laser is decoration, and decoration must not be the thing that tells
   you a scan is running — the mono caption under it does that. */
.scan-laser {
  position: absolute; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, transparent, var(--cc-accent), transparent);
  box-shadow: var(--cc-glow-edge);
  animation: scanSweep 1.6s var(--cc-ease) infinite;
}
@keyframes scanSweep { 0% { top: 4% } 50% { top: 92% } 100% { top: 4% } }
.scan-busy {
  position: absolute; bottom: 10px;
  font-size: 10px; letter-spacing: 0.14em; text-transform: uppercase;
  color: var(--cc-ink-2);
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.7);
}

.scan-err { margin: 0; color: var(--cc-warn) }

/* ── Verdict meter ───────────────────────────────────────────────── */
.scan-verdict { display: flex; align-items: center; gap: 16px; padding: 16px }
.scan-meter { position: relative; flex: none; width: 96px; height: 96px }
.scan-meter-svg { width: 100%; height: 100%; transform: rotate(-90deg) }
.scan-meter-track { fill: none; stroke: var(--cc-track); stroke-width: 8 }
.scan-meter-arc {
  fill: none; stroke-width: 8; stroke-linecap: round; stroke: var(--cc-idle);
  transition: stroke-dashoffset 560ms var(--cc-ease), stroke 240ms var(--cc-ease);
}
.scan-meter-arc.is-ok { stroke: var(--cc-live) }
.scan-meter-arc.is-warn { stroke: var(--cc-warn) }
.scan-meter-arc.is-bad { stroke: var(--cc-sell) }
.scan-meter-mid { position: absolute; inset: 0; display: grid; place-content: center; text-align: center }
.scan-meter-n { font-size: 26px; font-weight: 700; line-height: 1; color: var(--cc-ink) }
.scan-meter-d { display: block; margin-top: 2px; font-size: 9px }

.scan-verdict-copy { display: flex; flex-direction: column; gap: 4px; min-width: 0 }
.scan-dir {
  font-size: 22px; font-weight: 700; letter-spacing: -0.01em; color: var(--cc-ink-3);
}
.scan-dir.is-buy { color: var(--cc-buy) }
.scan-dir.is-sell { color: var(--cc-sell) }
.scan-grade {
  font-family: var(--cc-mono); font-size: 11px; font-weight: 700; letter-spacing: 0.14em;
  color: var(--cc-ink-3);
}
.scan-grade.is-ok { color: var(--cc-live) }
.scan-grade.is-warn { color: var(--cc-warn) }
.scan-grade.is-bad { color: var(--cc-sell) }

/* ── Checks ──────────────────────────────────────────────────────── */
.scan-checks { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px }
.scan-check { display: flex; align-items: center; gap: 11px; padding: 11px 13px }
.scan-check-mark {
  flex: none; display: grid; place-items: center;
  width: 22px; height: 22px; border-radius: 7px;
  font-family: var(--cc-mono); font-size: 12px; font-weight: 700;
  background: var(--cc-card-2); color: var(--cc-ink-4);
}
.scan-check.is-pass .scan-check-mark { background: var(--cc-buy-dim); color: var(--cc-buy) }
.scan-check.is-fail .scan-check-mark,
.scan-check.is-block .scan-check-mark { background: var(--cc-sell-dim); color: var(--cc-sell) }
.scan-check.is-warn .scan-check-mark { background: rgba(232, 163, 61, 0.14); color: var(--cc-warn) }
.scan-check-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.scan-check-l { font-size: 12.5px; font-weight: 600; color: var(--cc-ink-2) }
.scan-check-d { font-size: 11px; line-height: 16px }

.scan-badge {
  flex: none; padding: 3px 7px; border-radius: 5px;
  font-family: var(--cc-mono); font-size: 8.5px; font-weight: 700; letter-spacing: 0.10em;
  background: var(--cc-card-2); color: var(--cc-ink-4);
}
.scan-badge.is-pass { background: var(--cc-buy-dim); color: var(--cc-buy) }
.scan-badge.is-block { background: var(--cc-sell-dim); color: var(--cc-sell) }
.scan-badge.is-warn { background: rgba(232, 163, 61, 0.14); color: var(--cc-warn) }

.scan-privacy, .scan-note { margin: 0; line-height: 16px }

/* ── Ladder ──────────────────────────────────────────────────────── */
.scan-ladder { padding: 6px 13px 10px }
.scan-row {
  display: grid;
  /* Five columns now: the Est P/L one is the whole reason the broker
     contract spec is fetched. Level is widest because it carries a dot, a
     name and sometimes a "proj" tag. */
  grid-template-columns: 1.15fr 0.82fr 0.62fr 0.72fr 1fr;
  gap: 8px; align-items: center;
  padding: 9px 0;
  border-bottom: 1px solid var(--cc-border-soft);
}
.scan-row:last-child { border-bottom: none }
.scan-row.is-head { padding: 8px 0 6px; border-bottom: 1px solid var(--cc-border) }
.scan-row.is-foot { border-top: 1px solid var(--cc-border); border-bottom: none; margin-top: 2px }
.scan-row.is-muted { opacity: 0.38 }
.scan-row > span { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.scan-row > span:not(:first-child) { text-align: right }
.scan-row .is-up   { color: var(--cc-buy) }
.scan-row .is-down { color: var(--cc-sell) }
.scan-row-l { font-size: 12px; font-weight: 700; color: var(--cc-ink); display: flex; align-items: center; gap: 6px }
.scan-proj { font-size: 8px; letter-spacing: 0.08em; color: var(--cc-ink-4) }
.scan-row .is-warn { color: var(--cc-warn) }

/* ── MTF ─────────────────────────────────────────────────────────── */
.scan-mtf { padding: 6px 13px }
.scan-tf {
  display: grid; grid-template-columns: 44px 72px 1fr; gap: 9px; align-items: baseline;
  padding: 10px 0; border-bottom: 1px solid var(--cc-border-soft);
}
.scan-tf:last-child { border-bottom: none }
.scan-tf-id { font-size: 11px; font-weight: 700; color: var(--cc-ink) }
.scan-tf-bias { font-size: 11px; font-weight: 700; color: var(--cc-ink-3) }
.scan-tf-bias.is-buy { color: var(--cc-buy) }
.scan-tf-bias.is-sell { color: var(--cc-sell) }
.scan-tf-note { font-size: 11px; line-height: 16px }

.scan-feed { padding: 15px }
.scan-feed p { margin: 0 }
.scan-narr { font-size: 13px; line-height: 20px; color: var(--cc-ink-2) }

/* ── Execute ─────────────────────────────────────────────────────── */
.scan-exec {
  display: flex; align-items: center; justify-content: center; gap: 9px;
  min-height: 52px;
  border: none; border-radius: var(--cc-r-chip);
  background: var(--cc-accent); color: var(--cc-accent-ink);
  font-family: var(--cc-font); font-size: 13.5px; font-weight: 700; letter-spacing: 0.06em;
  cursor: pointer;
  box-shadow: var(--cc-glow-halo);
  transition: transform 200ms var(--cc-spring), background 220ms var(--cc-ease);
}
.scan-exec:active { transform: scale(0.98) }
.scan-exec:disabled { cursor: default; box-shadow: none; opacity: 0.5 }
.scan-exec.is-blocked {
  background: var(--cc-card-2); color: var(--cc-ink-3);
  border: 1px solid var(--cc-sell-dim); opacity: 1;
}
.scan-spin { animation: scanSpin 900ms linear infinite }
@keyframes scanSpin { to { transform: rotate(360deg) } }

.scan-result { margin: 0; text-align: center }
.scan-result.is-error { color: var(--cc-warn) }

/* ── Instrument chips ────────────────────────────────────────────────
   Wraps rather than scrolls. A horizontal strip hides options off the
   right edge, and the whole point of this row is that the user can see
   what their licence allows without hunting for it. */
.scan-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.scan-chip {
  height: 36px;
  padding: 0 16px;
  border-radius: var(--cc-r-chip);
  border: 1px solid var(--cc-border);
  background: var(--cc-card);
  font-family: var(--cc-font);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--cc-ink-3);
  cursor: pointer;
  transition: transform 180ms var(--cc-spring);
}
.scan-chip:active { transform: scale(0.95) }
.scan-chip.is-on {
  color: var(--cc-accent-ink);
  background: var(--cc-accent);
  border-color: var(--cc-accent);
  box-shadow: var(--cc-glow-edge);
}

/* ── Live quote ──────────────────────────────────────────────────── */
.scan-quote { padding: 16px 18px 14px }
.scan-quote-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}
.scan-quote-id { min-width: 0 }
.scan-quote-sym {
  display: block;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--cc-ink);
}
.scan-quote-broker { display: block; margin-top: 2px }
.scan-quote-px { text-align: right; flex: none }
.scan-quote-n {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: var(--cc-ink);
}
.scan-quote-n.is-dim { color: var(--cc-ink-5) }
.scan-quote-closed {
  display: block;
  margin-top: 2px;
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--cc-warn);
}

.scan-quote-tiles {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--cc-border-soft);
}

.scan-tile { display: flex; flex-direction: column; gap: 4px; min-width: 0 }
.scan-tile-v {
  font-size: 14px;
  font-weight: 600;
  color: var(--cc-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.scan-tile-sess { display: inline-flex; align-items: center; gap: 6px; font-size: 13px }

/* ── Risk ────────────────────────────────────────────────────────── */
.scan-risk {
  display: grid;
  grid-template-columns: 1fr 1.15fr;
  gap: 10px;
}
.scan-risk .scan-tile { padding: 13px 15px; gap: 5px }
.scan-tile-risk .scan-tile-v { font-size: 17px }
.scan-risk-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.scan-step-sm {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border: 1px solid var(--cc-border);
  border-radius: 9px;
  background: none;
  color: var(--cc-ink-3);
  cursor: pointer;
  transition: transform 160ms var(--cc-spring);
}
.scan-step-sm:active { transform: scale(0.9); color: var(--cc-accent-text) }

/* ── The model's own words ───────────────────────────────────────── */
.scan-narr {
  margin: 12px 2px 0;
  text-align: center;
  text-wrap: pretty;
}

/* ── Points on a check row ───────────────────────────────────────── */
.scan-check-pts {
  flex: none;
  align-self: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--cc-ink-4);
}
.scan-check-pts.is-pass { color: var(--cc-live) }
.scan-check-pts.is-fail { color: var(--cc-warn) }
.scan-check-pts.is-unknown { color: var(--cc-ink-5); font-size: 10px; letter-spacing: 0.08em }

/* ── The price column ────────────────────────────────────────────────
   The plan in chart order, band heights proportional to the real price
   gaps. Each rung is tinted toward what it is: green above the entry on a
   buy, red below it. The tint is on a ::before at low alpha rather than on
   the band itself, so the label and the price keep the card's own ink and
   its contrast. */
.scan-rungs {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 260px;
  padding: 0;
}
.scan-rung {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-basis: 0;
  padding: 9px 14px;
  min-height: 34px;
  border-bottom: 1px dashed var(--cc-border-soft);
}
.scan-rung:last-child { border-bottom: none }
.scan-rung::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
}
/* Targets are the profit side, the stop is the loss side, and which one
   sits above the entry depends on the direction — the tint follows the
   ROLE, not the position, so a sell is not drawn as an upside-down buy
   with the colours still pointing the wrong way. */
.scan-rung.is-tp::before    { background: linear-gradient(180deg, rgba(63, 214, 168, 0.16), rgba(63, 214, 168, 0.03)) }
.scan-rung.is-sl::before    { background: linear-gradient(180deg, rgba(224, 84, 78, 0.04), rgba(224, 84, 78, 0.18)) }
.scan-rung.is-entry::before { background: rgba(var(--cc-ink-rgb, 244, 244, 246), 0.05) }
.scan-rung.is-entry {
  border-top: 1px solid var(--cc-ink-4);
  border-bottom: 1px solid var(--cc-ink-4);
  min-height: 30px;
}

.scan-rung-l {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.scan-rung-tag {
  font-family: var(--cc-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--cc-ink-2);
}
.scan-rung.is-tp    .scan-rung-tag { color: var(--cc-buy) }
.scan-rung.is-sl    .scan-rung-tag { color: var(--cc-sell) }
.scan-rung.is-entry .scan-rung-tag { color: var(--cc-ink) }
.scan-rung-meta {
  font-size: 10px;
  color: var(--cc-ink-4);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.scan-rung-px {
  position: relative;
  flex: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-ink);
}

.scan-sec-head-sub { margin-top: 4px }

/* ── Allocation bar ──────────────────────────────────────────────── */
.scan-bar {
  display: flex;
  gap: 2px;
  height: 18px;
  border-radius: 6px;
  overflow: hidden;
}
.scan-bar-seg {
  display: grid;
  place-items: center;
  flex-basis: 0;
  min-width: 0;
  font-family: var(--cc-mono);
  font-size: 9px;
  font-weight: 700;
  color: #0B0B0D;
  overflow: hidden;
  transition: flex-grow 260ms var(--cc-ease);
}
/* Three shades of the same green, nearest target brightest — the ladder's
   own order made visible without introducing a second hue that would have
   to mean something. */
.scan-bar-seg.is-tp1 { background: #3FD6A8 }
.scan-bar-seg.is-tp2 { background: #47C6C0 }
.scan-bar-seg.is-tp3 { background: #4FB4D6 }

.scan-row-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
}
.scan-row-dot.is-tp1 { background: #3FD6A8 }
.scan-row-dot.is-tp2 { background: #47C6C0 }
.scan-row-dot.is-tp3 { background: #4FB4D6 }
.scan-row-dot.is-sl  { background: var(--cc-sell) }

.scan-totals {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 0 2px;
}
.scan-totals b { font-weight: 700 }
.scan-totals .is-up   { color: var(--cc-buy) }
.scan-totals .is-down { color: var(--cc-sell) }
.scan-totals-rr { font-size: 11px; font-weight: 700; color: var(--cc-ink-2) }

/* ── Timeframe grid ──────────────────────────────────────────────── */
.scan-mtf {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.scan-tf {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  min-width: 0;
}
.scan-tf-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.scan-tf-id { font-size: 10px; letter-spacing: 0.12em; color: var(--cc-ink-4) }
.scan-tf-arrow { display: grid; place-items: center; color: var(--cc-ink-4) }
.scan-tf-arrow.is-buy  { color: var(--cc-buy) }
.scan-tf-arrow.is-sell { color: var(--cc-sell) }
.scan-tf-bias {
  font-size: 14px;
  font-weight: 700;
  text-transform: capitalize;
  color: var(--cc-ink);
}
.scan-tf-bias.is-buy  { color: var(--cc-buy) }
.scan-tf-bias.is-sell { color: var(--cc-sell) }
.scan-tf-note {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Guardrails panel ────────────────────────────────────────────────
   One panel with a verdict at the top, not a list of equal cards. The
   verdict is the thing being asked — "can I send this" — and the rules
   are the working that produced it. */
.scan-guards { padding: 0; overflow: hidden }
.scan-guards.is-clear   { border-color: rgba(63, 214, 168, 0.30) }
.scan-guards.is-blocked { border-color: rgba(224, 84, 78, 0.34) }

.scan-guards-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}
.scan-guards.is-clear   .scan-guards-head { background: rgba(63, 214, 168, 0.07) }
.scan-guards.is-blocked .scan-guards-head { background: rgba(224, 84, 78, 0.08) }

.scan-guards-icon { display: grid; place-items: center; flex: none }
.scan-guards.is-clear   .scan-guards-icon { color: var(--cc-buy) }
.scan-guards.is-blocked .scan-guards-icon { color: var(--cc-sell) }
.scan-guards-copy { display: flex; flex-direction: column; gap: 2px; min-width: 0 }
.scan-guards-t { font-size: 14px; font-weight: 700; color: var(--cc-ink) }

.scan-rules { list-style: none; margin: 0; padding: 0 }
.scan-rule {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 16px;
  border-top: 1px solid var(--cc-border-soft);
}
.scan-rule-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.scan-rule-l { font-size: 13px; font-weight: 600; color: var(--cc-ink-2) }
.scan-rule-d { overflow: hidden; text-overflow: ellipsis; white-space: nowrap }

/* ── Two ways forward ────────────────────────────────────────────── */
.scan-ways {
  margin-top: 10px;
  padding: 14px 16px 16px;
  border-color: var(--cc-accent-line);
}
.scan-ways-t { font-size: 13px; font-weight: 700; color: var(--cc-ink) }
.scan-ways-d { margin: 5px 0 12px }
.scan-ways-row { display: flex; flex-wrap: wrap; gap: 8px }
.scan-way {
  height: 34px;
  padding: 0 14px;
  border-radius: var(--cc-r-chip);
  border: 1px solid var(--cc-border);
  background: none;
  font-family: var(--cc-font);
  font-size: 12px;
  font-weight: 600;
  color: var(--cc-ink-2);
  cursor: pointer;
  transition: transform 170ms var(--cc-spring);
}
.scan-way:active { transform: scale(0.95) }
.scan-way.is-primary {
  color: var(--cc-accent-ink);
  background: var(--cc-accent);
  border-color: var(--cc-accent);
}
</style>
