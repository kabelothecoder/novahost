<template>
  <!--
    CHART SCANNER · v3

    Built from "Nova Edge - Chart Scanner v3" in the Claude Design project. The
    doc's flow is seven steps: symbol → style → strategy → size → chart → scan →
    result. Fundamentals (the economic calendar) and Scan history each get a
    screen of their own.

    Where the doc draws something this platform cannot do yet, the screen leaves
    it out rather than faking it:
      - "Use live chart" (candles pulled from the broker): no candle feed, so a
        screenshot is the only chart source.
      - Tokens and token packs: the scanner is a once-off unlock, not metered.
      - Outcomes in history ("Hit TP1 · +R48"): scans are not joined to trades.
        History lists what was scanned and whether it was sent.
      - The news release card and "Alert me": a standalone iOS PWA cannot notify.
  -->
  <div class="cmd sc" :data-scheme="resolvedScheme" :data-card="theme.cardStyle" :data-motion="theme.motion">
    <div class="sc-wash" aria-hidden="true"></div>

    <!-- ── Top bar ─────────────────────────────────────────────────── -->
    <header class="sc-top">
      <button class="sc-round" :aria-label="screen === 'symbol' || !hasScanner ? 'Back to Home' : 'Back'" @click="goBack">
        <span class="ms">arrow_back</span>
      </button>
      <div v-if="hasScanner && stepIdx" class="sc-segs" aria-hidden="true">
        <span v-for="i in 7" :key="i" class="sc-seg" :class="{ 'is-on': i <= stepIdx, 'is-now': i === stepIdx }"></span>
      </div>
      <div v-else class="sc-top-t">{{ topTitle }}</div>
      <button v-if="hasScanner && stepIdx && screen !== 'scan'" class="sc-pill" aria-label="Scan history" @click="openHistory">
        <span class="ms">history</span>{{ history.length || '' }}
      </button>
    </header>

    <ScannerPaywall v-if="!hasScanner" class="sc-paywall" @unlocked="onUnlocked" />

    <template v-else>
      <div ref="scroller" class="sc-scroll" :class="{ 'has-bar': hasBottomBar }">

        <!-- ═══ 1 · SYMBOL ═══════════════════════════════════════════ -->
        <section v-if="screen === 'symbol'" class="sc-step">
          <div class="sc-head">
            <div class="sc-kicker">STEP 1 OF 7</div>
            <h1 class="sc-h1">What do you want to scan?</h1>
            <p class="sc-lede">Symbols your robot can trade on this account.</p>
          </div>

          <button v-if="newsSoon" class="sc-brief" @click="openFund">
            <span class="sc-dot is-warn is-pulse"></span>
            <span class="sc-brief-copy">
              <span class="sc-brief-t">{{ newsSoon.title }} {{ countdownText(newsSoon) }}</span>
              <span class="sc-brief-s">{{ newsSoon.currency }} · high impact · spreads widen at news time</span>
            </span>
            <span class="ms">arrow_forward</span>
          </button>

          <template v-if="symbols.length">
            <div class="sc-list">
              <button
                v-for="(r, i) in symbolRows" :key="r.sym"
                class="sc-row" :class="{ 'is-on': r.sym === symbol, 'has-div': i > 0 }"
                :aria-pressed="r.sym === symbol"
                @click="pickSymbol(r.sym)"
              >
                <span class="sc-row-main">
                  <span class="sc-row-t">{{ r.sym }}</span>
                  <span class="sc-row-sub">
                    <span class="mono">{{ r.code }}</span>
                    <span v-if="r.badge" class="sc-badge is-warn"><span class="sc-dot is-warn is-pulse"></span>{{ r.badge }}</span>
                    <span v-if="r.closed" class="sc-badge">Market closed</span>
                  </span>
                </span>
                <span class="sc-row-px mono">{{ r.price }}</span>
                <span class="sc-radio" :class="{ 'is-on': r.sym === symbol }"><span></span></span>
              </button>
            </div>
            <p class="sc-fine">Prices from your broker. <button class="sc-link" @click="openFund">Economic calendar</button></p>
          </template>

          <div v-else class="sc-empty">
            <span class="sc-badge is-warn is-lg"><span class="sc-dot is-warn"></span>No ready symbols</span>
            <h2 class="sc-h2">Set up symbols first</h2>
            <p class="sc-lede">
              {{ robot.name }} has no symbols on its allowance yet. The scanner only reads symbols your robot can trade.
            </p>
            <div class="sc-ghosts">
              <div class="sc-ghost"><span>Gold</span><span class="mono">needs a match</span></div>
              <div class="sc-ghost"><span>US30</span><span class="mono">needs a match</span></div>
            </div>
          </div>
        </section>

        <!-- ═══ 2 · STYLE ════════════════════════════════════════════ -->
        <section v-else-if="screen === 'style'" class="sc-step">
          <div class="sc-head">
            <div class="sc-kicker">STEP 2 OF 7 · {{ symbol }}</div>
            <h1 class="sc-h1">How do you want to trade it?</h1>
            <p class="sc-lede">Your style sets the timeframes I read, how long you hold and how wide the stop is.</p>
          </div>
          <div class="sc-cards">
            <button
              v-for="s in STYLES" :key="s.id"
              class="sc-card is-pick" :class="{ 'is-on': mode === s.id }"
              :aria-pressed="mode === s.id"
              @click="mode = s.id; haptic(20)"
            >
              <span class="sc-card-top">
                <span class="sc-card-t">{{ s.name }}</span>
                <span class="sc-radio" :class="{ 'is-on': mode === s.id }"><span></span></span>
              </span>
              <span class="sc-trio">
                <span><span class="sc-k">Hold</span><span class="sc-v">{{ s.hold }}</span></span>
                <span><span class="sc-k">Charts</span><span class="sc-v mono">{{ s.tf }}</span></span>
                <span><span class="sc-k">Stop</span><span class="sc-v">{{ s.stop }}</span></span>
              </span>
            </button>
          </div>
        </section>

        <!-- ═══ 3 · STRATEGY ═════════════════════════════════════════ -->
        <section v-else-if="screen === 'strategy'" class="sc-step">
          <div class="sc-head">
            <div class="sc-kicker">STEP 3 OF 7 · {{ symbol }} · {{ styleObj.name }}</div>
            <h1 class="sc-h1">Which entry strategy?</h1>
            <p class="sc-lede">I only look for this kind of setup, and it sets how the entry is placed.</p>
          </div>
          <div class="sc-cards">
            <button
              v-for="s in STRATEGIES" :key="s.id"
              class="sc-card is-pick" :class="{ 'is-on': strategy === s.id }"
              :aria-pressed="strategy === s.id"
              @click="strategy = s.id; haptic(20)"
            >
              <span class="sc-card-top">
                <span class="sc-card-main">
                  <span class="sc-card-t">{{ s.name }}</span>
                  <span class="sc-tag mono">{{ s.tag }}</span>
                </span>
                <span class="sc-radio" :class="{ 'is-on': strategy === s.id }"><span></span></span>
              </span>
              <span class="sc-card-d">{{ s.desc }}</span>
              <span class="sc-card-f">Looks for: {{ s.looks }}</span>
              <span class="sc-card-o">{{ s.order }}</span>
            </button>
          </div>
        </section>

        <!-- ═══ 4 · SIZE ═════════════════════════════════════════════ -->
        <section v-else-if="screen === 'size'" class="sc-step is-tight">
          <div class="sc-head">
            <div class="sc-kicker">STEP 4 OF 7 · {{ symbol }} · {{ strategyObj.name }}</div>
            <h1 class="sc-h1">How big?</h1>
            <p class="sc-lede">Set the lot size and the number of trades. The scan finds the stop, then I check the risk.</p>
          </div>

          <div class="sc-card">
            <div class="sc-stepper">
              <span class="sc-stepper-v">
                <span class="sc-k">Lot size · per trade</span>
                <span class="sc-big mono">{{ lotPer.toFixed(2) }}</span>
              </span>
              <button class="sc-round is-lg" :disabled="lotPer <= MIN_LOT" aria-label="Smaller lot" @click="setLot(lotPer - MIN_LOT)"><span class="ms">remove</span></button>
              <button class="sc-round is-lg" :disabled="lotPer >= LOT_MAX" aria-label="Bigger lot" @click="setLot(lotPer + MIN_LOT)"><span class="ms">add</span></button>
            </div>
            <div class="sc-presets">
              <button
                v-for="p in LOT_PRESETS" :key="p"
                class="sc-chip mono" :class="{ 'is-on': Math.abs(p - lotPer) < 1e-9 }"
                @click="setLot(p)"
              >{{ p.toFixed(2) }}</button>
            </div>
            <div class="sc-rule"></div>
            <div class="sc-stepper">
              <span class="sc-stepper-v">
                <span class="sc-k">Number of trades</span>
                <span class="sc-big mono">{{ tradeCount }}</span>
              </span>
              <button class="sc-round is-lg" :disabled="tradeCount <= 1" aria-label="Fewer trades" @click="setTrades(tradeCount - 1)"><span class="ms">remove</span></button>
              <button class="sc-round is-lg" :disabled="tradeCount >= MAX_TRADES" aria-label="More trades" @click="setTrades(tradeCount + 1)"><span class="ms">add</span></button>
            </div>
            <p class="sc-note">Same stop on every trade. Targets split between TP1 and TP2. {{ lotSourceNote }}</p>
          </div>

          <div class="sc-card">
            <div class="sc-kicker">YOUR RISK LIMIT</div>
            <div class="sc-stepper">
              <span class="sc-stepper-v">
                <span class="sc-big mono">{{ riskPct.toFixed(2) }}%</span>
                <span class="sc-k">{{ balance == null ? 'of your balance' : `${money(riskMoney)} of ${money(balance)}` }}</span>
              </span>
              <button class="sc-round is-lg" :disabled="riskPct <= 0.25" aria-label="Lower risk limit" @click="bumpRisk(-0.25)"><span class="ms">remove</span></button>
              <button class="sc-round is-lg" :disabled="riskPct >= 5" aria-label="Raise risk limit" @click="bumpRisk(0.25)"><span class="ms">add</span></button>
            </div>
            <div class="sc-well">
              <div v-for="(z, i) in sizeRows" :key="z.k" class="sc-kv" :class="{ 'has-div': i > 0 }">
                <span>{{ z.k }}</span><span class="mono" :class="z.tone">{{ z.v }}</span>
              </div>
            </div>
            <p class="sc-note">{{ sizeNote }}</p>
          </div>
        </section>

        <!-- ═══ 5 · CHART ════════════════════════════════════════════ -->
        <section v-else-if="screen === 'chart'" class="sc-step">
          <div class="sc-head">
            <div class="sc-kicker">STEP 5 OF 7 · {{ symbol }} · {{ styleObj.name }} · {{ strategyObj.name }}</div>
            <h1 class="sc-h1">Add your chart</h1>
            <p class="sc-lede">A screenshot of {{ symbol }} from your terminal. Your drawings are read too.</p>
          </div>

          <div class="sc-kicker">FRAMING GUIDE</div>
          <div class="sc-frames" aria-hidden="true">
            <div class="sc-frame-yes">
              <div class="sc-frame-box">
                <span class="mono sc-frame-n">100+ candles</span>
                <span class="mono sc-frame-scale">price scale</span>
              </div>
              <span class="is-buy">Sideways · yes</span>
            </div>
            <div class="sc-frame-no">
              <div class="sc-frame-box"></div>
              <span class="is-sell">Portrait · no</span>
            </div>
          </div>
          <div class="sc-list is-quiet">
            <div v-for="(g, i) in guideRules" :key="i" class="sc-guide" :class="{ 'has-div': i > 0 }">
              <span class="ms is-buy">check</span><span>{{ g }}</span>
            </div>
          </div>

          <input ref="fileInput" type="file" hidden accept="image/*" @change="onFile" />
          <button v-if="!preview" class="sc-drop" @click="pickFile">
            <span class="ms">add_photo_alternate</span>
            <span class="sc-drop-t">Choose screenshot</span>
            <span class="mono sc-drop-s">landscape chart screenshot</span>
          </button>
          <div v-else-if="isLandscape" class="sc-file is-ok">
            <img :src="preview" alt="" class="sc-file-img" />
            <span class="sc-file-copy">
              <span class="mono">{{ fileName }}</span>
              <span class="is-buy">{{ imgSize.w }} × {{ imgSize.h }} · landscape</span>
            </span>
            <button class="sc-link" @click="pickFile">Replace</button>
          </div>
          <div v-else class="sc-file is-bad">
            <span class="sc-file-copy">
              <span class="mono">{{ fileName }} · {{ imgSize.w }} × {{ imgSize.h }}</span>
              <span>This one is portrait, so I can only see part of the price history. Turn your phone sideways and take it again.</span>
            </span>
            <span class="sc-file-acts">
              <button class="sc-btn is-quiet is-sm" @click="pickFile">Choose another</button>
              <button class="sc-link" @click="allowPortrait = true">Scan it anyway</button>
            </span>
          </div>
        </section>

        <!-- ═══ 6 · SCANNING ═════════════════════════════════════════ -->
        <section v-else-if="screen === 'scan'" class="sc-step is-center">
          <div class="sc-core" aria-hidden="true">
            <span class="sc-core-ring"></span>
            <span class="sc-core-ring is-late"></span>
            <span class="sc-core-face">
              <img :src="avatar" alt="" />
              <span class="sc-core-sweep"></span>
            </span>
            <span class="sc-core-arc"></span>
          </div>
          <div class="sc-scan-t">
            <h1 class="sc-h2">Scanning {{ symbol }}</h1>
            <p class="mono sc-scan-s">{{ styleObj.name }} · {{ styleObj.tfs.join(' · ') }} · {{ strategyObj.name }} · screenshot</p>
          </div>
          <div class="sc-list">
            <div
              v-for="(k, i) in scanStages" :key="k.l"
              class="sc-stage" :class="[`is-${k.st}`, { 'has-div': i > 0 }]"
            >
              <span class="ms" :class="{ 'is-spin': k.st === 'run' }">{{ k.glyph }}</span>
              <span class="sc-stage-copy">
                <span class="sc-stage-l">{{ k.l }}{{ k.st === 'run' ? '…' : '' }}</span>
                <span class="sc-stage-r">{{ k.st === 'wait' ? 'Waiting' : k.st === 'run' ? 'Checking' : k.r }}</span>
              </span>
            </div>
          </div>
        </section>

        <!-- ═══ 7 · RESULT ═══════════════════════════════════════════ -->
        <section v-else-if="screen === 'result' && reading" class="sc-step is-tight">
          <div class="sc-kicker">STEP 7 OF 7 · {{ pair }} · {{ strategyObj.name }}</div>

          <div v-if="reading.stale" class="sc-banner is-bad" role="status">
            <span class="ms">schedule</span>
            <span>{{ reading.stale_reason }} This reading describes the chart as it was, not the market as it is.</span>
          </div>

          <button v-if="resultNews" class="sc-banner is-warn" @click="openFund">
            <span class="sc-dot is-warn is-pulse"></span>
            <span class="sc-banner-copy">
              <span class="sc-banner-t">{{ resultNews.title }} {{ countdownText(resultNews) }} · high impact</span>
              <span class="sc-banner-s">This scan reads the chart only. The calendar is in Fundamentals.</span>
            </span>
            <span class="ms">arrow_forward</span>
          </button>

          <div class="sc-verdict">
            <div class="sc-ring" :style="{ background: ringBg, boxShadow: `0 0 50px -10px ${vColor}` }">
              <div class="sc-ring-in">
                <span class="sc-ring-n">{{ conf ?? '—' }}</span>
                <span class="sc-ring-b">{{ bandOf(conf) }} confidence</span>
              </div>
            </div>
            <div class="sc-vtext" :class="vTone">{{ verdict }}</div>
            <div class="sc-chips">
              <span class="sc-chip is-static">{{ styleObj.name }}</span>
              <span class="sc-chip is-static">{{ strategyObj.name }}</span>
              <span v-if="tfText" class="sc-chip is-static mono">{{ tfText }}</span>
            </div>
            <p class="sc-summary">{{ summary }}</p>
          </div>

          <div class="sc-card">
            <div class="sc-kicker">WHAT LINED UP</div>
            <div v-for="g in lined.good" :key="g.l" class="sc-line">
              <span class="ms is-buy">check</span>
              <span class="sc-line-copy"><span class="sc-line-l">{{ g.l }}</span><span class="sc-line-r">{{ g.r }}</span></span>
            </div>
            <p v-if="!lined.good.length" class="sc-note">Nothing on this chart cleared a check.</p>
            <div class="sc-kicker is-split">WHAT DIDN’T</div>
            <div v-for="g in lined.bad" :key="g.l" class="sc-line">
              <span class="ms" :class="g.warn ? 'is-warn' : 'is-sell'">{{ g.warn ? 'error' : 'close' }}</span>
              <span class="sc-line-copy"><span class="sc-line-l">{{ g.l }}</span><span class="sc-line-r">{{ g.r }}</span></span>
            </div>
            <p v-if="!lined.bad.length" class="sc-note">Every check that could run passed.</p>
            <p v-if="lined.unknown.length" class="sc-note is-split">
              Couldn’t check: {{ lined.unknown.map(u => u.l.toLowerCase()).join(', ') }}.
            </p>
          </div>

          <template v-if="isTrade && plan.valid">
            <div class="sc-card">
              <div class="sc-card-top">
                <span class="sc-kicker">ORDER</span>
                <button v-if="canMarket || orderOverride" class="sc-btn is-quiet is-xs" @click="sheet = 'order'">Change</button>
              </div>
              <div class="sc-card-t">{{ orderName }}</div>
              <p class="sc-body">{{ orderWhy }}</p>
              <div class="sc-ladder" :style="{ height: '172px' }">
                <span class="sc-zone is-win" :style="{ top: lad.pTop + '%', height: lad.pH + '%' }"></span>
                <span class="sc-zone is-loss" :style="{ top: lad.lTop + '%', height: lad.lH + '%' }"></span>
                <div v-for="l in lad.rows" :key="l.label" class="sc-lvl" :class="l.kind" :style="{ top: l.top + '%' }">
                  <span class="sc-lvl-l">{{ l.label }}</span>
                  <span class="sc-lvl-line"></span>
                  <span class="sc-lvl-px mono">{{ l.price }}</span>
                </div>
              </div>
            </div>

            <div class="sc-card" :class="{ 'is-bad': overBudget }">
              <div class="sc-kicker">SIZE</div>
              <div class="sc-card-top">
                <span class="sc-card-t">{{ lotPer.toFixed(2) }} lot × {{ tradeCount }} {{ tradeCount > 1 ? 'trades' : 'trade' }}</span>
                <span class="sc-fine">this account</span>
              </div>
              <div class="sc-cells">
                <div><span class="sc-k">At risk</span><span class="mono" :class="{ 'is-sell': overBudget }">{{ riskText }}</span></div>
                <div><span class="sc-k">Of balance</span><span class="mono" :class="{ 'is-sell': overBudget }">{{ sizing ? sizing.share.toFixed(1) + '%' : '—' }}</span></div>
                <div><span class="sc-k">R:R</span><span class="mono">{{ rrText }}</span></div>
              </div>
              <p class="sc-note">{{ resultSizeNote }}</p>
            </div>
          </template>

          <div class="sc-card is-quiet">
            <div>
              <div class="sc-kicker">THE READ</div>
              <p class="sc-body">{{ reading.narrative || 'No narrative came back with this scan.' }}</p>
            </div>
            <div class="sc-split">
              <div class="sc-kicker">{{ isTrade ? 'INVALIDATION' : 'WHEN TO LOOK AGAIN' }}</div>
              <p class="sc-body">{{ invalidation }}</p>
            </div>
          </div>

          <div v-if="isTrade && blocks.length" class="sc-card is-blocked">
            <div class="sc-kicker is-sell">BLOCKED · FIX TO PLACE</div>
            <div v-for="b in blocks" :key="b.id" class="sc-block">
              <span class="sc-block-t">{{ b.t }}</span>
              <span class="sc-body">{{ b.d }}</span>
              <button v-if="b.fix" class="sc-btn is-quiet is-sm" @click="b.run()">{{ b.fix }}</button>
            </div>
            <p class="sc-note">Only you can change your limits. Nothing reaches your broker while this is red.</p>
          </div>
        </section>

        <!-- ═══ FUNDAMENTALS ═════════════════════════════════════════ -->
        <section v-else-if="screen === 'fund'" class="sc-step">
          <div class="sc-head">
            <h1 class="sc-h1">Fundamentals</h1>
            <p class="sc-lede">The economic calendar, kept apart from the scanner, which only reads the chart.</p>
          </div>

          <p v-if="!wire.loaded" class="sc-note">Loading the calendar…</p>
          <p v-else-if="!wire.events.ok && !wire.events.items.length" class="sc-note">{{ wire.events.error || 'The calendar is unavailable right now.' }}</p>

          <div v-if="nextHigh" class="sc-card is-brief">
            <div class="sc-card-top">
              <span class="sc-badge is-warn"><span class="sc-dot is-warn is-pulse"></span>Next · high impact</span>
              <span class="mono is-warn">{{ countdownClock(nextHigh) }}</span>
            </div>
            <div class="sc-card-t">{{ nextHigh.currency }} {{ nextHigh.title }}</div>
            <div class="sc-cells">
              <div><span class="sc-k">Forecast</span><span class="mono">{{ nextHigh.forecast || '—' }}</span></div>
              <div><span class="sc-k">Previous</span><span class="mono">{{ nextHigh.previous || '—' }}</span></div>
              <div><span class="sc-k">Actual</span><span class="mono is-dim">{{ nextHigh.actual || 'at ' + clockOf(nextHigh.at) }}</span></div>
            </div>
            <p class="sc-note">Spreads widen and prices jump at news time. {{ nextHighFor }}</p>
          </div>

          <div v-for="dy in fundDays" :key="dy.h" class="sc-day">
            <div class="sc-kicker">{{ dy.h }}</div>
            <div class="sc-list">
              <div v-for="(ev, i) in dy.items" :key="ev.key" class="sc-ev" :class="{ 'has-div': i > 0, 'is-past': ev.past }">
                <span class="sc-ev-t mono">{{ clockOf(ev.at) }}</span>
                <span class="sc-ev-copy">
                  <span class="sc-ev-n">{{ ev.title }}</span>
                  <span class="sc-ev-f">
                    {{ ev.currency }}<template v-if="ev.actual"> · Actual {{ ev.actual }}</template><template v-if="ev.forecast"> · Forecast {{ ev.forecast }}</template><template v-if="ev.previous"> · Previous {{ ev.previous }}</template>
                  </span>
                </span>
                <span class="sc-badge" :class="{ 'is-warn': ev.impact === 'high' }">{{ IMPACT[ev.impact] || 'Low' }}</span>
              </div>
            </div>
          </div>
          <p v-if="wire.events.fetchedAt" class="sc-fine">
            {{ wire.events.source || 'Calendar' }} · updated {{ ageLabel(wire.events.fetchedAt, nowMs) }}{{ wire.events.stale ? ' · may be out of date' : '' }}
          </p>
        </section>

        <!-- ═══ HISTORY ══════════════════════════════════════════════ -->
        <section v-else-if="screen === 'history'" class="sc-step">
          <div class="sc-head">
            <h1 class="sc-h1">Your scans</h1>
            <p class="sc-lede">Scans on this phone, and what you did with each one.</p>
          </div>
          <div class="sc-stats">
            <div><span class="sc-k">Scans</span><span class="sc-stat">{{ history.length }}</span></div>
            <div><span class="sc-k">Buy</span><span class="sc-stat is-buy">{{ histStats.buy }}</span></div>
            <div><span class="sc-k">Sell</span><span class="sc-stat is-sell">{{ histStats.sell }}</span></div>
            <div><span class="sc-k">Sent</span><span class="sc-stat">{{ histStats.sent }}</span></div>
          </div>
          <div v-if="history.length" class="sc-list">
            <button v-for="(h, i) in history" :key="h.id" class="sc-row" :class="{ 'has-div': i > 0 }" @click="openDetail(h.id)">
              <span class="sc-row-main">
                <span class="sc-row-t">{{ h.sym }} · {{ h.style }} · <span :class="toneOf(h.verdict)">{{ h.verdict }} {{ h.confidence ?? '' }}</span></span>
                <span class="sc-row-sub">{{ whenText(h.at) }} · {{ sentText(h) }}</span>
              </span>
              <span class="ms sc-dim">chevron_right</span>
            </button>
          </div>
          <p v-else class="sc-note">No scans yet. Your first one will show up here.</p>
          <p class="sc-fine">
            Wins and losses aren’t joined to scans yet. <button class="sc-link" @click="router.push('/trades')">Trades</button> shows what filled.
          </p>
        </section>

        <section v-else-if="screen === 'hdetail' && histItem" class="sc-step is-tight">
          <div class="sc-head">
            <div class="sc-kicker">{{ whenText(histItem.at) }} · {{ histItem.style }} · {{ histItem.strategy }}</div>
            <h1 class="sc-h1">{{ histItem.sym }} · <span :class="toneOf(histItem.verdict)">{{ histItem.verdict }}</span></h1>
          </div>
          <div class="sc-card is-quiet">
            <div class="sc-kicker">THE ROBOT SAID · {{ histItem.confidence ?? '—' }} CONFIDENCE</div>
            <p class="sc-body">{{ histItem.plan }}</p>
          </div>
          <div class="sc-card" :class="histItem.sent ? (histItem.sent.ok ? 'is-ok' : 'is-bad') : 'is-quiet'">
            <div class="sc-kicker">WHAT YOU DID</div>
            <p class="sc-body">{{ histItem.sent ? histItem.sent.message : histItem.saved ? 'Saved as an idea. Nothing was sent.' : 'Not sent.' }}</p>
          </div>
          <div v-if="histItem.narrative" class="sc-card is-quiet">
            <div class="sc-kicker">THE READ</div>
            <p class="sc-body">{{ histItem.narrative }}</p>
          </div>
        </section>

        <!-- ═══ CLOSED / ERROR ═══════════════════════════════════════ -->
        <section v-else-if="screen === 'closed' || screen === 'error'" class="sc-step is-msg">
          <span class="sc-badge is-lg" :class="{ 'is-warn': screen === 'error' }"><span class="sc-dot" :class="{ 'is-warn': screen === 'error' }"></span>{{ msg.pill }}</span>
          <h1 class="sc-h1 is-xl">{{ msg.title }}</h1>
          <p class="sc-lede">{{ msg.body }}</p>
          <div class="sc-list is-quiet">
            <div v-for="(m, i) in msg.rows" :key="m[0]" class="sc-kv is-tall" :class="{ 'has-div': i > 0 }">
              <span>{{ m[0] }}</span><span class="mono">{{ m[1] }}</span>
            </div>
          </div>
        </section>
      </div>

      <!-- ── Bottom bars ───────────────────────────────────────────── -->
      <div v-if="setupBar" class="sc-bar">
        <button class="sc-cta" :class="{ 'is-off': cta.off }" :disabled="cta.off" @click="cta.go">{{ cta.label }}</button>
        <button class="sc-acct" @click="openAcct">
          <span class="sc-acct-copy">
            <span class="sc-k">Scanning for</span>
            <span class="sc-acct-v">{{ acctLine }}</span>
          </span>
          <span class="ms">expand_more</span>
        </button>
      </div>

      <div v-else-if="screen === 'scan'" class="sc-bar">
        <div class="sc-prog"><span :style="{ width: scanPct + '%' }"></span></div>
        <div class="sc-prog-meta mono"><span>{{ scanClock }}</span><span>usually 10–20 s</span></div>
        <button class="sc-btn is-quiet is-tall sc-beside" @click="cancelScan">Cancel</button>
      </div>

      <div v-else-if="screen === 'result'" class="sc-bar">
        <div
          ref="track"
          class="sc-slide" :class="{ 'is-ok': slideOk }"
        >
          <span class="sc-slide-fill" :style="{ width: (slideX + 52) + 'px', transition: dragging ? 'none' : '' }"></span>
          <span class="sc-slide-l">{{ slideLabel }}</span>
          <span
            class="sc-slide-knob"
            role="button" :tabindex="slideOk ? 0 : -1"
            :aria-disabled="!slideOk" :aria-label="slideLabel"
            :style="{ left: (3 + slideX) + 'px', transition: dragging ? 'none' : '' }"
            @pointerdown="slideDown" @pointermove="slideMove" @pointerup="slideUp" @pointercancel="slideUp"
            @keydown.enter.prevent="slideOk && execute()"
          ><span class="ms">{{ sending ? 'progress_activity' : 'arrow_forward' }}</span></span>
        </div>
        <div class="sc-pair sc-beside">
          <button class="sc-btn is-quiet" @click="saveIdea">Save idea</button>
          <button class="sc-btn is-quiet" @click="scanAgain">Scan again</button>
        </div>
      </div>

      <div v-else-if="screen === 'closed' || screen === 'error'" class="sc-bar">
        <button class="sc-cta" @click="msg.go">{{ msg.cta }}</button>
        <button class="sc-btn is-quiet sc-beside" @click="msg.go2">{{ msg.cta2 }}</button>
      </div>
    </template>

    <!-- ── Sheets ────────────────────────────────────────────────── -->
    <template v-if="sheet">
      <div class="sc-scrim" @click="closeSheet"></div>
      <div class="sc-sheet" role="dialog" aria-modal="true">
        <span class="sc-grab"></span>

        <template v-if="sheet === 'order'">
          <h2 class="sc-h3">Order type</h2>
          <button
            v-for="o in orderOpts" :key="o.k"
            class="sc-opt" :class="{ 'is-on': o.on }" @click="pickOrder(o.k)"
          >
            <span class="sc-opt-copy">
              <span class="sc-opt-t">{{ o.t }} <span v-if="o.mine" class="sc-mine">Scan’s pick</span></span>
              <span class="sc-opt-d">{{ o.d }}</span>
            </span>
            <span class="sc-radio" :class="{ 'is-on': o.on }"><span></span></span>
          </button>
        </template>

        <template v-else-if="sheet === 'acct'">
          <h2 class="sc-h3">Scan for which account?</h2>
          <p class="sc-note">Each robot trades its own broker account. Switching changes the robot the app runs.</p>
          <button
            v-for="ea in robot.eaList" :key="ea.id"
            class="sc-opt" :class="{ 'is-on': ea.id === robot.activeEaId }"
            :disabled="!!robot.busyId" @click="pickRobot(ea.id)"
          >
            <span class="sc-opt-copy">
              <span class="sc-opt-t">{{ ea.name }}</span>
              <span class="sc-opt-d">
                {{ ea.id === robot.activeEaId ? acctLine + ' · ' : '' }}{{ (ea.symbols || []).length }} {{ (ea.symbols || []).length === 1 ? 'symbol' : 'symbols' }}{{ robot.busyId === ea.id ? ' · switching…' : '' }}
              </span>
            </span>
            <span class="sc-radio" :class="{ 'is-on': ea.id === robot.activeEaId }"><span></span></span>
          </button>
          <p v-if="robot.eaList.length <= 1" class="sc-note">Add another robot’s licence in Settings to scan for a second account.</p>
        </template>

        <template v-else-if="sheet === 'placed' && placed">
          <span class="sc-badge is-lg" :class="placed.ok ? 'is-ok' : 'is-bad'">
            <span class="sc-dot" :class="placed.ok ? 'is-ok' : 'is-bad'"></span>{{ placed.ok ? 'Sent to your broker' : 'Not sent' }}
          </span>
          <h2 class="sc-h2">{{ placed.title }}</h2>
          <p class="sc-lede">{{ placed.body }}</p>
          <button class="sc-cta" @click="placed.ok ? doneHome() : closeSheet()">{{ placed.ok ? 'Done' : 'Back to the plan' }}</button>
        </template>
      </div>
    </template>

    <div v-if="toast" class="sc-toast" role="status">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { theme, resolvedScheme } from '../stores/theme.js'
import { session } from '../stores/session.js'
import { robot, selectRobot, refreshFleet, FALLBACK_AVATAR } from '../stores/robot.js'
import { trades } from '../stores/trades.js'
import { loadQuote, loadAccount } from '../services/brokerService.js'
import { authState } from '../services/authService'
import { haptic } from '../composables/useSpeech.js'
import ScannerPaywall from '../components/ScannerPaywall.vue'
import { scanChart, sendPlan, preflight } from '../services/scannerService.js'
import { buildLadder, scoreConfluence, checkGuardrails, MIN_LOT } from '../services/confluence.js'
import { loadRates, convert, signFor } from '../services/fxRates.js'
import { loadPlan, normaliseSymbol } from '../services/symbolPlanService.js'
import { loadWire, ageLabel } from '../services/marketWireService.js'
import {
  STYLES, STRATEGIES, MAX_TRADES, planLegs, verdictOf, bandOf, orderNameOf, nextHighEvent, currenciesOf,
} from '../services/scanPlan.js'
import { loadHistory, recordScan, updateScan } from '../services/scanHistory.js'
// TEMPORARY dev-only scanner unlock. The `import.meta.env.DEV &&` guard at
// its call site below makes Rollup drop this import from any production
// build. Delete this line and the marked usage together.
import { devScannerUnlocked } from '../utils/devGate.js'

const router = useRouter()

/* `analyze-chart` is the real gate. This only decides what to draw. */
const hasScanner = computed(
  () => authState.hasScanner || (import.meta.env.DEV && devScannerUnlocked())
)

const FLOW = ['symbol', 'style', 'strategy', 'size', 'chart', 'scan', 'result']
const BACK = { style: 'symbol', strategy: 'style', size: 'strategy', chart: 'size', result: 'chart', hdetail: 'history', closed: 'symbol', error: 'chart' }
const IMPACT = { high: 'High', medium: 'Medium', low: 'Low' }
const LOT_PRESETS = [0.01, 0.02, 0.05, 0.1, 0.2, 0.5]
const LOT_MAX = 1

const screen = ref('symbol')
/* Fundamentals and History open from more than one place. Back returns there. */
const returnTo = ref('symbol')
const stepIdx = computed(() => FLOW.indexOf(screen.value) + 1)
const topTitle = computed(() => ({
  fund: 'Fundamentals', history: 'Scan history', hdetail: 'Scan', closed: '', error: '',
})[screen.value] ?? (hasScanner.value ? '' : 'Chart Scanner'))

const scroller = ref(null)
watch(screen, () => nextTick(() => { if (scroller.value) scroller.value.scrollTop = 0 }))

/* ── Toast ───────────────────────────────────────────────────────── */
const toast = ref('')
let toastTimer = null
function flash(t) {
  clearTimeout(toastTimer)
  toast.value = t
  toastTimer = setTimeout(() => { toast.value = '' }, 2400)
}

/* ── Clock ───────────────────────────────────────────────────────────
   One ticker for the countdowns. It speeds up while a scan runs so the stage
   list and the progress bar move, and stops on unmount. */
const nowMs = ref(Date.now())
let ticker = null
function tickEvery(ms) {
  clearInterval(ticker)
  ticker = setInterval(() => { nowMs.value = Date.now() }, ms)
}
onMounted(() => tickEvery(15_000))
onUnmounted(() => { clearInterval(ticker); clearTimeout(toastTimer) })

/* ── Choices ─────────────────────────────────────────────────────── */
const symbol = ref('')
const mode = ref('day')
const strategy = ref('SMC')
const lotPer = ref(0.01)
const tradeCount = ref(1)
const lotFromPlan = ref(false)
const riskPct = ref(1)

const styleObj = computed(() => STYLES.find(s => s.id === mode.value) || STYLES[1])
const strategyObj = computed(() => STRATEGIES.find(s => s.id === strategy.value) || STRATEGIES[0])

function setLot(v) {
  lotPer.value = Math.min(LOT_MAX, Math.max(MIN_LOT, Math.round(v * 100) / 100))
  lotFromPlan.value = false
  haptic(15)
}
function setTrades(n) {
  tradeCount.value = Math.min(MAX_TRADES, Math.max(1, n))
  haptic(15)
}
function bumpRisk(d) {
  riskPct.value = Math.min(5, Math.max(0.25, Math.round((riskPct.value + d) * 100) / 100))
  haptic(15)
}

/* A lot the user set on the Trading Symbols screen is a better start than a
   constant, and it keeps the two screens telling the same story. */
watch(symbol, s => {
  const row = s ? loadPlan()[s] : null
  if (row && row.enabled && row.lot > 0) {
    lotPer.value = Math.min(LOT_MAX, Math.max(MIN_LOT, row.lot))
    lotFromPlan.value = true
  } else {
    lotFromPlan.value = false
  }
})
const lotSourceNote = computed(() => (lotFromPlan.value ? 'Lot from your Trading Symbols setup.' : ''))

/* ── Symbols and quotes ──────────────────────────────────────────────
   The robot's own allowance, not a list of majors. broker-execute refuses
   anything outside it, so offering it here would be offering a refusal. */
const symbols = computed(() =>
  [...new Set((robot.allowedSymbols || []).map(x => normaliseSymbol(String(x))).filter(Boolean))].slice(0, 8),
)
const quotes = reactive({})

async function fetchQuote(sym) {
  if (!sym) return
  quotes[sym] = { ok: false, loading: true }
  quotes[sym] = await loadQuote(sym)
}

function pickSymbol(s) {
  if (symbol.value === s) return
  symbol.value = s
  haptic(20)
}

function fmtPrice(q, v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  const d = Number(q && q.digits)
  if (Number.isFinite(d)) return n.toFixed(d)
  return n >= 1000 ? n.toFixed(2) : n.toFixed(n < 10 ? 5 : 3)
}

const symbolRows = computed(() => symbols.value.map(s => {
  const q = quotes[s] || {}
  const ev = nextHighEvent(wire.events.items, s, nowMs.value, 120)
  return {
    sym: s,
    code: q.ok ? (q.broker_symbol || s) : q.loading ? 'pricing…' : (q.code === 'NO_ACCOUNT_LINKED' ? 'no broker linked' : s),
    price: q.ok && q.price != null ? fmtPrice(q, q.price) : '—',
    closed: q.ok && q.tradeable === false,
    badge: ev ? `${shortTitle(ev.title)} ${countdownText(ev)}` : '',
  }
}))

/* ── Account and money ───────────────────────────────────────────────
   The balance is the broker's or an em dash. A sample figure here would size
   every position the user placed. */
const freeMargin = ref(null)
const balance = computed(() => {
  const b = trades.balance
  if (b == null || b === '') return null
  const n = Number(b)
  return Number.isFinite(n) ? n : null
})
const accountCurrency = computed(() => trades.currency || session.brokerCurrency || null)
const fxTable = ref(null)

/** An ACCOUNT-denominated amount, in the account's own currency. */
function money(v) {
  const n = Number(v)
  if (v == null || !Number.isFinite(n)) return '—'
  return signFor(accountCurrency.value || 'USD')
    + Math.abs(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const riskMoney = computed(() => (balance.value == null ? null : (balance.value * riskPct.value) / 100))

const acctLine = computed(() => {
  const server = session.brokerServer ? String(session.brokerServer).split(/[-\s]/)[0] : ''
  const login = session.brokerLogin ? String(session.brokerLogin).slice(-4) : ''
  const parts = [server || robot.name, login ? '···' + login : '', balance.value == null ? 'no balance' : money(balance.value)]
  return parts.filter(Boolean).join(' · ')
})

/* What the chosen size buys BEFORE the scan. The stop is not known yet, so the
   honest figure is the widest stop the risk limit covers at this size. */
const selQuote = computed(() => quotes[symbol.value] || {})
const selPipMoney = computed(() => {
  const q = selQuote.value
  const c = Number(q.contract_size), pip = Number(q.pip_size)
  return q.ok && c > 0 && pip > 0 ? c * pip : null
})
const coverPips = computed(() => {
  if (riskMoney.value == null || selPipMoney.value == null) return null
  const budget = convert(riskMoney.value, accountCurrency.value || selQuote.value.quote_currency, selQuote.value.quote_currency || accountCurrency.value, fxTable.value)
  if (budget == null) return null
  return budget / (lotPer.value * tradeCount.value * selPipMoney.value)
})
const sizeRows = computed(() => [
  { k: 'Total size', v: `${(lotPer.value * tradeCount.value).toFixed(2)} lot` },
  { k: 'Balance', v: balance.value == null ? '—' : money(balance.value) },
  { k: 'Free margin', v: freeMargin.value == null ? '—' : money(freeMargin.value) },
  {
    k: 'Stop this covers',
    v: coverPips.value == null ? '—' : `≈ ${coverPips.value < 10 ? coverPips.value.toFixed(1) : Math.floor(coverPips.value)} pips`,
    tone: coverPips.value != null && coverPips.value < 5 ? 'is-warn' : '',
  },
])
const sizeNote = computed(() => {
  if (balance.value == null) return 'No broker balance yet, so the risk can’t be checked. Link an account to size against it.'
  if (coverPips.value == null) return `The scan finds the stop. I block the plan if it risks more than ${riskPct.value}% of your balance.`
  return `At this size, ${riskPct.value}% covers a stop of about ${Math.floor(coverPips.value)} pips on ${symbol.value}. A wider stop from the scan is blocked until you lower the size.`
})

/* ── Calendar ────────────────────────────────────────────────────── */
const wire = reactive({ loaded: false, events: { ok: false, items: [], error: null, fetchedAt: null } })

function shortTitle(t) {
  const s = String(t || '')
  return s.length > 22 ? s.slice(0, 20).trimEnd() + '…' : s
}
function minsTo(e) { return Math.round((new Date(e.at).getTime() - nowMs.value) / 60000) }
function countdownText(e) {
  const m = minsTo(e)
  if (m <= 0) return 'now'
  return m < 60 ? `in ${m} min` : `in ${Math.floor(m / 60)} h ${m % 60} min`
}
function countdownClock(e) {
  const s = Math.max(0, Math.round((new Date(e.at).getTime() - nowMs.value) / 1000))
  if (s >= 3600) return `${Math.floor(s / 3600)}h ${String(Math.floor((s % 3600) / 60)).padStart(2, '0')}m`
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}
function clockOf(iso) {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', hour12: false })
}

/* Soonest high-impact release for any of the user's symbols in the next hour. */
const newsSoon = computed(() => {
  let best = null
  for (const s of symbols.value) {
    const e = nextHighEvent(wire.events.items, s, nowMs.value, 60)
    if (e && (!best || e.t < best.t)) best = e
  }
  return best
})

/* The user's currencies first. Otherwise the next high-impact row on the wire. */
const nextHigh = computed(() => {
  const mine = new Set(symbols.value.flatMap(currenciesOf))
  const up = wire.events.items
    .filter(e => e.impact === 'high' && new Date(e.at).getTime() > nowMs.value)
    .sort((a, b) => new Date(a.at) - new Date(b.at))
  return up.find(e => mine.has(String(e.currency).toUpperCase())) || up[0] || null
})
const nextHighFor = computed(() => {
  if (!nextHigh.value) return ''
  const hit = symbols.value.filter(s => currenciesOf(s).includes(String(nextHigh.value.currency).toUpperCase()))
  return hit.length ? `Moves ${hit.slice(0, 3).join(', ')}.` : ''
})

const fundDays = computed(() => {
  const today = new Date(nowMs.value); today.setHours(0, 0, 0, 0)
  const dayMs = 86_400_000
  const groups = new Map()
  const items = [...wire.events.items].sort((a, b) => new Date(a.at) - new Date(b.at))
  items.forEach((e, i) => {
    const t = new Date(e.at).getTime()
    if (!Number.isFinite(t) || t < today.getTime()) return
    const off = Math.floor((t - today.getTime()) / dayMs)
    const h = off === 0 ? 'TODAY' : off === 1 ? 'TOMORROW'
      : new Date(t).toLocaleDateString(undefined, { weekday: 'long' }).toUpperCase()
    if (!groups.has(h)) groups.set(h, [])
    groups.get(h).push({ ...e, key: `${e.at}-${i}`, past: t < nowMs.value })
  })
  return [...groups].map(([h, list]) => ({ h, items: list }))
})

function openFund() {
  if (screen.value !== 'fund') returnTo.value = screen.value
  screen.value = 'fund'
}

/* ── Chart ───────────────────────────────────────────────────────── */
const fileInput = ref(null)
const preview = ref(null)
const fileName = ref('')
const imgSize = reactive({ w: 0, h: 0 })
const allowPortrait = ref(false)
const isLandscape = computed(() => imgSize.w > imgSize.h)

const guideRules = computed(() => [
  'Turn your phone sideways before you take the screenshot. Portrait shots cut the price history short.',
  'Show at least 100 candles.',
  'Keep the price scale on the right, and the time axis along the bottom, in view.',
  `One timeframe per shot. ${styleObj.value.tfs[1]} works best for ${styleObj.value.name.toLowerCase()}.`,
])

function pickFile() { fileInput.value?.click() }

function onFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  fileName.value = file.name || 'screenshot'
  allowPortrait.value = false
  const fr = new FileReader()
  fr.onload = ev => {
    const url = ev.target.result
    const img = new Image()
    img.onload = () => {
      imgSize.w = img.naturalWidth
      imgSize.h = img.naturalHeight
      preview.value = url
    }
    img.onerror = () => flash('That file isn’t an image I can read.')
    img.src = url
  }
  fr.readAsDataURL(file)
}

/* ── Scan ────────────────────────────────────────────────────────── */
const reading = ref(null)
const scanError = ref('')
const scanT0 = ref(0)
let scanSeq = 0
let historyId = null
const STAGE_MS = 1700
const EXPECTED_MS = 15_000

const scanStages = computed(() => {
  const el = nowMs.value - scanT0.value
  const list = [
    ['Reading the chart', 'Price axis, candles and your drawings'],
    ['Is the chart current', 'Last candle checked against the clock'],
    ['Higher timeframe trend', `Read on ${styleObj.value.tfs[2]}`],
    [`${strategyObj.value.name} setup`, `Looked for ${strategyObj.value.looks.split(',')[0]}`],
    ['Key levels', 'Support, resistance and touches'],
    ['Stop vs candle size', 'Room for normal noise'],
    ['Your risk budget', `${riskPct.value}% of balance`],
  ]
  return list.map(([l, r], i) => {
    /* The last stage keeps spinning until the answer arrives. Nothing here
       knows a result yet, so a finished stage is "done", not green. */
    const st = el < i * STAGE_MS ? 'wait' : (el < (i + 1) * STAGE_MS || i === list.length - 1) ? 'run' : 'done'
    return { l, r, st, glyph: st === 'wait' ? 'radio_button_unchecked' : st === 'run' ? 'progress_activity' : 'check_circle' }
  })
})
const scanPct = computed(() => Math.min(96, ((nowMs.value - scanT0.value) / EXPECTED_MS) * 100))
const scanClock = computed(() => {
  const s = Math.max(0, Math.floor((nowMs.value - scanT0.value) / 1000))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
})

async function startScan() {
  if (!preview.value) { screen.value = 'chart'; return }
  const mine = ++scanSeq
  reading.value = null
  scanError.value = ''
  orderOverride.value = null
  placed.value = null
  sheet.value = null
  slideX.value = 0
  historyId = null
  scanT0.value = Date.now()
  nowMs.value = scanT0.value
  screen.value = 'scan'
  tickEvery(250)
  haptic(30)
  try {
    const data = await scanChart(preview.value, mode.value, strategy.value)
    if (mine !== scanSeq) return
    reading.value = data
    const scanned = normaliseSymbol(data?.symbol_on_chart || '')
    if (scanned && !quotes[scanned]?.ok) fetchQuote(scanned)
    historyId = recordScan(historyEntry(data))
    history.value = loadHistory()
    screen.value = 'result'
    haptic(40)
  } catch (err) {
    if (mine !== scanSeq) return
    /* The model's own "I could not read this" is more useful than a generic,
       so it is shown as written. */
    scanError.value = err?.message || 'The scan failed. Try a clearer screenshot.'
    screen.value = 'error'
  } finally {
    if (mine === scanSeq) tickEvery(15_000)
  }
}

/* The request cannot be recalled once sent. Cancelling stops the screen from
   waiting for it, and a late answer is dropped. */
function cancelScan() {
  scanSeq++
  tickEvery(15_000)
  screen.value = 'chart'
  flash('Scan cancelled')
}

function scanAgain() {
  preview.value = null
  screen.value = 'chart'
}

/* ── Result ──────────────────────────────────────────────────────── */
/* The chart's instrument wins over the pick. If the model could not read a
   symbol, the user's pick stands, and the price-gap guard still catches a
   chart of something else. */
const pair = computed(() => normaliseSymbol(reading.value?.symbol_on_chart || '') || symbol.value)
const pairQuote = computed(() => quotes[pair.value] || {})
const spec = computed(() => (pairQuote.value.ok ? pairQuote.value : null))
const livePrice = computed(() => (pairQuote.value.ok && Number(pairQuote.value.price) > 0 ? Number(pairQuote.value.price) : null))

const orderOverride = ref(null)
const scanOrderType = computed(() => String(reading.value?.entry_type || 'MARKET').toUpperCase())
const orderType = computed(() => orderOverride.value || scanOrderType.value)

/* Market is offered only while the live price is still inside the bracket.
   Past the stop or the target, a market fill is a different trade. */
const canMarket = computed(() => {
  const r = reading.value, p = livePrice.value
  if (!r || p == null || scanOrderType.value === 'MARKET') return false
  const lo = Math.min(Number(r.sl), Number(r.tp)), hi = Math.max(Number(r.sl), Number(r.tp))
  return p > lo && p < hi
})

const readingEff = computed(() => {
  const r = reading.value
  if (!r) return null
  return orderOverride.value === 'MARKET' && livePrice.value != null ? { ...r, entry: livePrice.value } : r
})

const ladder = computed(() => (readingEff.value
  ? buildLadder(readingEff.value, { allocation: 'balanced', totalLot: lotPer.value * tradeCount.value, spec: spec.value })
  : { valid: false, legs: [] }))
const plan = computed(() => planLegs(ladder.value, lotPer.value, tradeCount.value))
const score = computed(() => (readingEff.value
  ? scoreConfluence(readingEff.value, plan.value)
  : { checks: [] }))

const quoteCurrency = computed(() => plan.value.currency || pairQuote.value.quote_currency || null)
const pipMoney = computed(() => {
  const q = spec.value
  const c = Number(q && q.contract_size), pip = Number(q && q.pip_size)
  return c > 0 && pip > 0 ? c * pip : null
})

/* Both sides of every comparison in the ACCOUNT's currency. Dividing rand by
   dollars-per-pip once sized a gold trade seventeen times too large. */
const sizing = computed(() => {
  const l = plan.value
  if (!l.valid || riskMoney.value == null || pipMoney.value == null || !(balance.value > 0)) return null
  const lots = l.legs.reduce((s, leg) => s + leg.lot, 0)
  const from = quoteCurrency.value || accountCurrency.value
  const to = accountCurrency.value || quoteCurrency.value
  const risk = convert(lots * pipMoney.value * l.slPips, from, to, fxTable.value)
  if (risk == null) return null
  const budgetQ = convert(riskMoney.value, to, from, fxTable.value)
  return {
    risk,
    budget: riskMoney.value,
    share: (risk / balance.value) * 100,
    pct: riskPct.value,
    lots,
    atMinimum: lotPer.value <= MIN_LOT + 1e-9 && tradeCount.value === 1,
    maxStopPips: budgetQ == null ? 0 : budgetQ / (MIN_LOT * pipMoney.value),
    riskText: money(risk),
    balanceText: money(balance.value),
  }
})

/* The calendar can now answer the event rule that `checkGuardrails` reports as
   unknown. A release inside 30 minutes is a warning, not a block. The user may
   be trading the news on purpose. */
const guards = computed(() => {
  const g = checkGuardrails(plan.value, null, readingEff.value, {
    livePrice: livePrice.value, symbol: pair.value, sizing: sizing.value,
  })
  if (!wire.loaded || !wire.events.ok) return g
  const ev = nextHighEvent(wire.events.items, pair.value, nowMs.value, 30)
  const rules = g.rules.map(r => (r.id !== 'event' ? r : ev
    ? { ...r, status: 'WARN', label: 'High-impact release soon', detail: `${ev.currency} ${ev.title} ${countdownText(ev)}` }
    : { ...r, status: 'PASS', detail: 'No high-impact release in the next 30 min' }))
  const blocks = rules.filter(x => x.status === 'BLOCK')
  return { ...g, rules, blocks, blocked: blocks.length > 0 }
})

const verdict = computed(() => verdictOf(reading.value))
const isTrade = computed(() => verdict.value !== 'NO TRADE')
const side = computed(() => String(reading.value?.direction || '').toUpperCase())
const conf = computed(() => {
  const c = Number(reading.value?.confidence)
  return Number.isFinite(c) ? Math.round(c) : null
})
const vTone = computed(() => toneOf(verdict.value))
const vColor = computed(() => (verdict.value === 'BUY' ? 'var(--cc-buy)' : verdict.value === 'SELL' ? 'var(--cc-sell-text)' : 'var(--cc-ink-3)'))
const ringBg = computed(() => `conic-gradient(${vColor.value} 0 ${conf.value ?? 0}%, var(--sc-ring-track) ${conf.value ?? 0}% 100%)`)
const tfText = computed(() => {
  const t = Array.isArray(reading.value?.timeframes) ? reading.value.timeframes.map(x => x.timeframe).filter(Boolean) : []
  return (t.length ? t : [reading.value?.timeframe_on_chart].filter(Boolean)).slice(0, 3).join(' · ')
})

function toneOf(v) { return v === 'BUY' ? 'is-buy' : v === 'SELL' ? 'is-sell' : 'is-dim' }

const fmt = v => fmtPrice(pairQuote.value.ok ? pairQuote.value : null, v)
const orderName = computed(() => orderNameOf(side.value, orderType.value))
const orderWhy = computed(() => {
  if (orderOverride.value === 'MARKET') return `Fills now near ${fmt(livePrice.value)}, not at the scan’s ${fmt(reading.value.entry)}. The stop and target stay where the chart put them.`
  const t = orderType.value
  const base = reading.value?.entry_note || ''
  const how = t === 'LIMIT' ? `Waits for price to come back to ${fmt(plan.value.entry)}.`
    : t === 'STOP' ? `Only fills if price breaks ${fmt(plan.value.entry)}. No break, no trade.`
    : 'Price is at the level now.'
  return [base, how].filter(Boolean).join(' ')
})

const summary = computed(() => {
  const r = reading.value
  if (!r) return ''
  if (!isTrade.value) {
    return `Stay out. It leans ${side.value || 'nowhere'} at ${conf.value ?? '—'}, which is too weak to trade.`
  }
  return `${orderName.value} at ${fmt(plan.value.entry)}. ${strategyObj.value.name} setup on ${r.timeframe_on_chart || styleObj.value.tfs[1]}.`
})

const invalidation = computed(() => {
  const r = reading.value
  if (!r) return ''
  if (!isTrade.value) return `Scan again when ${pair.value} leaves the range or a cleaner setup forms.`
  return `This plan is wrong if price closes ${side.value === 'BUY' ? 'below' : 'above'} ${fmt(r.sl)}${r.timeframe_on_chart ? ' on ' + r.timeframe_on_chart : ''}.`
})

const resultNews = computed(() => (reading.value ? nextHighEvent(wire.events.items, pair.value, nowMs.value, 120) : null))

/* Chart reads come from the confluence checks. Account-side rules (risk, the
   right instrument, a current chart, the calendar) come from the guardrails.
   R:R and stop size appear in both, and the chart's version is kept. */
const ACCOUNT_RULES = ['stale', 'scale', 'risk', 'event']
const lined = computed(() => {
  const good = [], bad = [], unknown = []
  for (const c of score.value.checks || []) {
    if (c.id === 'event') continue
    const row = { l: c.label, r: c.detail }
    if (c.ok === true) good.push(row)
    else if (c.ok === false) bad.push(row)
    else unknown.push(row)
  }
  for (const g of guards.value.rules || []) {
    if (!ACCOUNT_RULES.includes(g.id)) continue
    const row = { l: g.label, r: g.detail, warn: g.status === 'WARN' }
    if (g.status === 'PASS') good.push(row)
    else if (g.status === 'UNKNOWN') unknown.push(row)
    else bad.push(row)
  }
  return { good, bad, unknown }
})

const rrText = computed(() => {
  const p = plan.value
  if (!p.valid) return '—'
  return `1:${p.tp1.rr.toFixed(1)}` + (p.usesSecond ? ` / ${p.tp2.rr.toFixed(1)}` : '')
})

const overBudget = computed(() => guards.value.blocks.some(b => b.id === 'risk'))
const riskText = computed(() => {
  if (sizing.value) return sizing.value.riskText
  const r = plan.value.riskMoney
  if (r == null) return '—'
  const n = convert(r, quoteCurrency.value || accountCurrency.value, accountCurrency.value || quoteCurrency.value, fxTable.value)
  return n == null ? '—' : money(n)
})
const resultSizeNote = computed(() => {
  const p = plan.value
  if (!p.priced) return 'Link a broker to price this plan. Without its contract size the risk can’t be worked out.'
  const reward = p.rewardMoney == null ? null
    : convert(p.rewardMoney, quoteCurrency.value || accountCurrency.value, accountCurrency.value || quoteCurrency.value, fxTable.value)
  if (overBudget.value) return `More than your ${riskPct.value}% limit for this account.`
  if (!sizing.value) return 'No broker balance, so this can’t be checked against your risk limit.'
  return reward == null ? `Within your ${riskPct.value}% limit.` : `Within your ${riskPct.value}% limit. If every target fills, about +${money(reward)}.`
})

/* Ladder drawing, in chart order. Positions are proportional to price, so the
   picture is the plan and not a decoration of it. */
const lad = computed(() => {
  const p = plan.value
  if (!p.valid) return { rows: [], pTop: 0, pH: 0, lTop: 0, lH: 0 }
  const pts = []
  if (p.usesSecond) pts.push({ label: 'TP2', price: p.tp2.price, kind: 'is-tp' })
  pts.push({ label: 'TP1', price: p.tp1.price, kind: p.usesSecond ? 'is-tp is-dash' : 'is-tp' })
  pts.push({ label: orderType.value === 'MARKET' ? 'Entry · now' : 'Entry', price: p.entry, kind: 'is-entry' })
  pts.push({ label: 'Stop', price: p.sl, kind: 'is-sl' })
  if (orderType.value !== 'MARKET' && livePrice.value != null) pts.push({ label: 'Now', price: livePrice.value, kind: 'is-now' })
  const mx = Math.max(...pts.map(x => x.price)), mn = Math.min(...pts.map(x => x.price))
  const span = mx - mn || 1
  const top = v => 8 + ((mx - v) / span) * 84
  const far = p.usesSecond ? p.tp2.price : p.tp1.price
  const a = top(far), b = top(p.entry), z = top(p.sl)
  return {
    rows: pts.map(x => ({ ...x, price: fmt(x.price), top: top(x.price).toFixed(2) })),
    pTop: Math.min(a, b).toFixed(2), pH: Math.abs(a - b).toFixed(2),
    lTop: Math.min(b, z).toFixed(2), lH: Math.abs(b - z).toFixed(2),
  }
})

/* The lot per trade that fits the risk limit on THIS stop, floored to the 0.01
   step. Rounding up would spend risk the user did not allocate. */
function fitLotFor(n) {
  const p = plan.value
  if (!p.valid || riskMoney.value == null || pipMoney.value == null) return null
  const budgetQ = convert(riskMoney.value, accountCurrency.value || quoteCurrency.value, quoteCurrency.value || accountCurrency.value, fxTable.value)
  if (budgetQ == null) return null
  const per = pipMoney.value * p.slPips * n
  return per > 0 ? Number((Math.floor(budgetQ / per / MIN_LOT) * MIN_LOT).toFixed(2)) : null
}

/* Every red item comes with a real way forward, or with none. */
const blocks = computed(() => guards.value.blocks.map(b => {
  const out = { id: b.id, t: b.label, d: [b.detail, b.remedy].filter(Boolean).join(' '), fix: '', run: null }
  if (b.id === 'stale' || b.id === 'scale') {
    out.fix = 'Scan a fresh chart'
    out.run = scanAgain
  } else if (b.id === 'risk') {
    /* Only a fix that actually clears the block is offered. When even 0.01
       on one trade is too much, the honest answer is a tighter setup. */
    const f = fitLotFor(tradeCount.value)
    const f1 = fitLotFor(1)
    if (f != null && f >= MIN_LOT && f < lotPer.value) {
      out.fix = `Use ${f.toFixed(2)} lot`
      out.run = () => { lotPer.value = f; lotFromPlan.value = false; haptic(30) }
    } else if (tradeCount.value > 1 && f1 != null && f1 >= MIN_LOT) {
      out.fix = `Use ${f1.toFixed(2)} lot × 1 trade`
      out.run = () => { tradeCount.value = 1; lotPer.value = Math.min(lotPer.value, f1); lotFromPlan.value = false; haptic(30) }
    } else {
      if (f1 != null && f1 < MIN_LOT) {
        out.d = `${b.detail} Even 0.01 lot on one trade risks more than your ${riskPct.value}% on this stop, so this setup doesn’t fit this account.`
      }
      out.fix = 'Scan again'
      out.run = scanAgain
    }
  } else if (b.id === 'rr') {
    out.fix = 'Scan again'
    out.run = scanAgain
  }
  return out
}))

/* ── Order type sheet ────────────────────────────────────────────── */
const orderOpts = computed(() => {
  const s = side.value === 'SELL' ? 'Sell' : 'Buy'
  const scanType = scanOrderType.value
  const desc = {
    MARKET: 'Fills now at the current price.',
    LIMIT: side.value === 'SELL' ? 'Waits for a rally to a better price.' : 'Waits for a dip to a better price.',
    STOP: 'Fills only if price breaks through.',
  }
  const opts = [{ k: scanType, t: orderNameOf(side.value, scanType), d: desc[scanType], mine: true }]
  if (canMarket.value) opts.push({ k: 'MARKET', t: `${s} at market`, d: desc.MARKET + ' The stop and target stay.', mine: false })
  return opts.map(o => ({ ...o, on: o.k === orderType.value }))
})
function pickOrder(k) {
  orderOverride.value = k === scanOrderType.value ? null : k
  sheet.value = null
  slideX.value = 0
  haptic(20)
}

/* ── Place ───────────────────────────────────────────────────────── */
const sending = ref(false)
const placed = ref(null)

const slideOk = computed(() => isTrade.value && plan.value.valid && !!pair.value && !guards.value.blocked && !sending.value)
const slideLabel = computed(() => {
  if (sending.value) return 'Sending…'
  if (!isTrade.value) return 'No trade to place'
  if (!plan.value.valid) return 'This plan can’t be placed'
  if (guards.value.blocked) return 'Fix the red items first'
  return `Slide to place ${orderName.value}`
})

async function execute() {
  if (!slideOk.value) return
  sending.value = true
  haptic(60)
  const p = plan.value
  const order = {
    pair: pair.value,
    side: side.value,
    sl: p.sl,
    entry: p.entry,
    legs: p.legs,
    totalLot: p.totalLot,
    orderType: orderType.value,
  }
  /* The executor checks licence, allowance, position cap, stops and symbol
     resolution on a dry run before anything real goes out. */
  const pre = await preflight(order, 'ladder')
  if (!pre.ok) {
    finishPlace({ ok: false, title: 'Your broker would refuse this', body: pre.message, message: `Not sent: ${pre.message}` })
    return
  }
  const res = await sendPlan(order, 'ladder')
  const n = res.placed?.length || 0
  finishPlace({
    ok: res.ok,
    title: res.ok ? `${orderName.value} placed` : n ? `${n} of ${p.legs.length} trades placed` : 'Not placed',
    body: res.ok
      ? `${acctLine.value} · ${n} ${n === 1 ? 'trade' : 'trades'} · ${lotPer.value.toFixed(2)} lot each. Entry ${fmt(p.entry)} · stop ${fmt(p.sl)} · TP1 ${fmt(p.tp1.price)}${p.usesSecond ? ' · TP2 ' + fmt(p.tp2.price) : ''}.`
      : res.message,
    message: res.message,
  })
}

function finishPlace(r) {
  sending.value = false
  placed.value = r
  sheet.value = 'placed'
  updateScan(historyId, { sent: { ok: r.ok, message: r.message } })
  history.value = loadHistory()
  if (r.ok) haptic([40, 60, 40])
  else slideX.value = 0
}

/* ── Slide control ───────────────────────────────────────────────── */
const track = ref(null)
const slideX = ref(0)
const dragging = ref(false)
let slideStart = 0
const slideMax = () => Math.max(120, (track.value?.clientWidth || 340) - 58)

function slideDown(e) {
  if (!slideOk.value) return
  e.currentTarget.setPointerCapture?.(e.pointerId)
  slideStart = e.clientX - slideX.value
  dragging.value = true
}
function slideMove(e) {
  if (!dragging.value) return
  slideX.value = Math.max(0, Math.min(slideMax(), e.clientX - slideStart))
}
function slideUp() {
  if (!dragging.value) return
  dragging.value = false
  if (slideX.value > slideMax() * 0.85) {
    slideX.value = slideMax()
    execute()
  } else {
    slideX.value = 0
  }
}

/* ── History ─────────────────────────────────────────────────────── */
const history = ref(loadHistory())
const histId = ref(null)
const histItem = computed(() => history.value.find(h => h.id === histId.value) || null)
const histStats = computed(() => ({
  buy: history.value.filter(h => h.verdict === 'BUY').length,
  sell: history.value.filter(h => h.verdict === 'SELL').length,
  sent: history.value.filter(h => h.sent && h.sent.ok).length,
}))

function historyEntry(r) {
  const v = verdictOf(r)
  const sym = normaliseSymbol(r?.symbol_on_chart || '') || symbol.value
  const f = n => fmtPrice(quotes[sym]?.ok ? quotes[sym] : null, n)
  const planText = v === 'NO TRADE'
    ? `No trade. It leaned ${String(r?.direction || '').toUpperCase()} at ${Math.round(Number(r?.confidence) || 0)}.`
    : `${orderNameOf(r.direction, r.entry_type)} ${f(r.entry)} · Stop ${f(r.sl)} · TP1 ${f(r.tp)}`
  return {
    sym,
    style: styleObj.value.name,
    strategy: strategyObj.value.name,
    verdict: v,
    confidence: Number.isFinite(Number(r?.confidence)) ? Math.round(Number(r.confidence)) : null,
    plan: planText,
    narrative: r?.narrative || '',
  }
}

function openHistory() {
  history.value = loadHistory()
  if (screen.value !== 'history') returnTo.value = screen.value
  screen.value = 'history'
}
function openDetail(id) {
  histId.value = id
  screen.value = 'hdetail'
}
function whenText(iso) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const day = new Date(d); day.setHours(0, 0, 0, 0)
  const diff = Math.round((today - day) / 86_400_000)
  const hm = d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', hour12: false })
  const label = diff === 0 ? 'Today' : diff === 1 ? 'Yesterday' : d.toLocaleDateString(undefined, { weekday: 'short' })
  return `${label} · ${hm}`
}
function sentText(h) {
  if (h.sent) return h.sent.ok ? 'Sent' : 'Refused'
  return h.saved ? 'Saved idea' : 'Not sent'
}

function saveIdea() {
  updateScan(historyId, { saved: true })
  history.value = loadHistory()
  flash('Idea saved to scan history')
}

/* ── Sheets and navigation ───────────────────────────────────────── */
const sheet = ref(null)
function closeSheet() {
  if (sheet.value === 'placed') slideX.value = 0
  sheet.value = null
}
function doneHome() {
  sheet.value = null
  router.push('/home')
}
function openAcct() { sheet.value = 'acct' }

async function pickRobot(id) {
  if (id === robot.activeEaId) { sheet.value = null; return }
  const res = await selectRobot(id)
  if (!res.ok) { flash(res.message || 'That robot could not be switched to.'); return }
  sheet.value = null
  flash(`Scanning for ${robot.name}`)
  symbol.value = ''
  for (const k of Object.keys(quotes)) delete quotes[k]
  await refreshAccount()
  loadSymbolQuotes()
}

const setupBar = computed(() => ['symbol', 'style', 'strategy', 'size', 'chart'].includes(screen.value))
const hasBottomBar = computed(() => setupBar.value || ['scan', 'result', 'closed', 'error'].includes(screen.value))

const cta = computed(() => {
  switch (screen.value) {
    case 'symbol':
      return symbols.value.length
        ? { label: 'Continue', off: !symbol.value, go: continueFromSymbol }
        : { label: 'Set up symbols', off: false, go: () => router.push('/symbols') }
    case 'style': return { label: 'Continue', off: false, go: () => { screen.value = 'strategy' } }
    case 'strategy': return { label: 'Continue', off: false, go: () => { screen.value = 'size' } }
    case 'size': return { label: 'Continue', off: false, go: () => { screen.value = 'chart' } }
    default: {
      const blocked = !preview.value || (!isLandscape.value && !allowPortrait.value)
      return {
        label: !preview.value ? 'Add a screenshot to scan' : blocked ? 'Add a landscape screenshot' : `Scan ${symbol.value}`,
        off: blocked,
        go: startScan,
      }
    }
  }
})

function continueFromSymbol() {
  const q = quotes[symbol.value]
  screen.value = q && q.ok && q.tradeable === false ? 'closed' : 'style'
}

const msg = computed(() => {
  if (screen.value === 'closed') {
    const q = quotes[symbol.value] || {}
    return {
      pill: 'Market closed',
      title: `${symbol.value} isn’t trading right now`,
      body: 'Your broker says this market is shut. A screenshot now shows old candles, and the scan blocks a chart that isn’t current.',
      rows: [['Symbol', q.broker_symbol || symbol.value], ['Last price', q.price != null ? fmtPrice(q, q.price) : '—'], ['Session', sessionLabel()]],
      cta: 'Pick another symbol', go: () => { screen.value = 'symbol' },
      cta2: 'Continue anyway', go2: () => { screen.value = 'style' },
    }
  }
  return {
    pill: 'Couldn’t read the chart',
    title: 'The scan didn’t come back',
    body: scanError.value,
    rows: [['Symbol', symbol.value || '—'], ['Your settings', 'Kept'], ['Screenshot', preview.value ? fileName.value : '—']],
    cta: 'Try again', go: startScan,
    cta2: 'Choose another screenshot', go2: () => { preview.value = null; screen.value = 'chart'; nextTick(pickFile) },
  }
})

function sessionLabel() {
  const d = new Date(nowMs.value)
  const day = d.getUTCDay()
  if (day === 0 || day === 6) return 'Weekend'
  const h = d.getUTCHours() + d.getUTCMinutes() / 60
  const on = []
  if (h < 9) on.push('Tokyo')
  if (h >= 7 && h < 16) on.push('London')
  if (h >= 12 && h < 21) on.push('New York')
  return on.join(' + ') || 'Between sessions'
}

function goBack() {
  if (sheet.value) { closeSheet(); return }
  if (screen.value === 'scan') { cancelScan(); return }
  if (screen.value === 'fund' || screen.value === 'history') { screen.value = returnTo.value || 'symbol'; return }
  const prev = BACK[screen.value]
  if (prev && hasScanner.value) screen.value = prev
  else router.push('/home')
}

const avatar = computed(() => robot.avatarUrl || FALLBACK_AVATAR)

function onUnlocked() { screen.value = 'symbol' }

/* ── Load ────────────────────────────────────────────────────────── */
async function refreshAccount() {
  const acct = await loadAccount()
  if (acct.ok) {
    if (acct.balance != null) trades.balance = Number(acct.balance)
    if (acct.currency) trades.currency = acct.currency
    freeMargin.value = acct.free_margin ?? null
  }
}

function loadSymbolQuotes() {
  symbols.value.forEach(fetchQuote)
  if (!symbol.value && symbols.value.length) symbol.value = symbols.value[0]
}

onMounted(async () => {
  fxTable.value = await loadRates()
  refreshFleet().catch(() => {})
  refreshAccount()
  loadSymbolQuotes()
  const w = await loadWire(symbols.value)
  wire.events = w.events
  wire.loaded = true
})
</script>

<style scoped>
/* ── Shell ───────────────────────────────────────────────────────────
   height, not min-height. `.app-shell` is `height: 100dvh; overflow: hidden`,
   so this view has to scroll inside itself. Installed to the Home screen,
   min-height freezes the page. `.sc-scroll` is the scroll container. */
.sc {
  --sc-font: Geist, var(--cc-font);
  --sc-mono: 'Geist Mono', var(--cc-mono);
  --sc-panel: linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.03));
  --sc-line: rgba(255, 255, 255, 0.09);
  --sc-div: rgba(255, 255, 255, 0.06);
  --sc-well: rgba(0, 0, 0, 0.25);
  --sc-quiet: rgba(255, 255, 255, 0.035);
  --sc-ring-track: rgba(255, 255, 255, 0.07);
  --sc-btn: rgba(255, 255, 255, 0.06);
  position: relative;
  height: 100dvh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--cc-canvas);
  color: var(--cc-ink);
  font-family: var(--sc-font);
  -webkit-font-smoothing: antialiased;
}
.sc[data-scheme='light'] {
  --sc-panel: var(--cc-card);
  --sc-line: var(--cc-border);
  --sc-div: var(--cc-border-soft);
  --sc-well: var(--cc-recess);
  --sc-quiet: var(--cc-card-2);
  --sc-ring-track: var(--cc-track);
  --sc-btn: var(--cc-card-2);
}
.sc-wash {
  position: absolute; inset: 0; pointer-events: none; z-index: 0;
  background:
    radial-gradient(110% 38% at 50% -4%, rgba(var(--cc-accent-rgb), 0.22), transparent 62%),
    radial-gradient(80% 30% at 100% 72%, rgba(var(--cc-accent-rgb), 0.07), transparent 70%);
}
.ms {
  font-family: 'Material Symbols Outlined'; font-weight: 400; font-style: normal;
  font-size: 22px; line-height: 1; letter-spacing: normal; font-feature-settings: 'liga';
  -webkit-font-smoothing: antialiased; user-select: none;
}
.mono { font-family: var(--sc-mono) }
.is-buy { color: var(--cc-buy) }
.is-sell { color: var(--cc-sell-text) }
.is-warn { color: var(--cc-warn) }
.is-dim { color: var(--cc-ink-4) }
.sc-dim { color: var(--cc-ink-4) }
button { font: inherit; color: inherit; -webkit-tap-highlight-color: transparent }

/* ── Top bar ─────────────────────────────────────────────────────── */
.sc-top {
  position: relative; z-index: 5; flex: none;
  display: flex; align-items: center; gap: 12px;
  padding: calc(var(--cc-safe-top) + 8px) 16px 10px;
}
.sc-round {
  width: 44px; height: 44px; flex: none; border-radius: 50%;
  display: grid; place-items: center; cursor: pointer;
  background: var(--sc-btn); border: 1px solid var(--sc-line);
  transition: transform 160ms var(--cc-spring), opacity 160ms;
}
.sc-round:active { transform: scale(0.92) }
.sc-round:disabled { opacity: 0.35; cursor: default }
.sc-round.is-lg { width: 48px; height: 48px }
.sc-segs { flex: 1; display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 4px }
.sc-seg { height: 4px; border-radius: 2px; background: var(--sc-line); transition: background 0.3s }
.sc-seg.is-on { background: var(--cc-accent) }
.sc-seg.is-now { box-shadow: 0 0 12px rgba(var(--cc-accent-rgb), 0.7) }
.sc-top-t { flex: 1; font-size: 15px; font-weight: 600 }
.sc-pill {
  height: 34px; padding: 0 12px 0 10px; border-radius: 17px; flex: none;
  display: flex; align-items: center; gap: 6px; cursor: pointer;
  background: var(--sc-btn); border: 1px solid var(--sc-line);
  font-size: 13px; font-weight: 600;
}
.sc-pill .ms { font-size: 18px }
.sc-paywall { position: relative; z-index: 1; flex: 1; overflow-y: auto }

/* ── Scroll area ─────────────────────────────────────────────────── */
.sc-scroll {
  position: relative; z-index: 1; flex: 1;
  overflow-y: auto; overflow-x: hidden; -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  padding: 10px 20px calc(var(--cc-safe-bottom) + 48px);
}
.sc-scroll::-webkit-scrollbar { display: none }
.sc-scroll.has-bar { padding-bottom: calc(var(--cc-safe-bottom) + 230px) }

.sc-step { display: flex; flex-direction: column; gap: 18px; animation: scUp 0.35s var(--cc-ease-out) both }
.sc-step.is-tight { gap: 12px }
.sc-step.is-center { align-items: center; gap: 18px }
.sc-step.is-msg { gap: 20px; padding-top: 18px }
.sc-head { display: flex; flex-direction: column; gap: 8px }
.sc-kicker { font-family: var(--sc-mono); font-size: 11px; letter-spacing: 0.14em; color: var(--cc-ink-4); text-transform: uppercase }
.sc-kicker.is-split { margin-top: 6px; padding-top: 12px; border-top: 1px solid var(--sc-div) }
.sc-kicker.is-sell { color: var(--cc-sell-text) }
.sc-h1 { margin: 0; font-size: 34px; line-height: 38px; font-weight: 650; letter-spacing: -0.03em; text-wrap: pretty }
.sc-h1.is-xl { font-size: 36px; line-height: 40px }
.sc-h2 { margin: 0; font-size: 28px; line-height: 32px; font-weight: 650; letter-spacing: -0.02em }
.sc-h3 { margin: 0; font-size: 26px; line-height: 30px; font-weight: 650; letter-spacing: -0.02em }
.sc-lede { margin: 0; font-size: 16px; line-height: 24px; color: var(--cc-ink-3); text-wrap: pretty }
.sc-body { margin: 0; font-size: 14.5px; line-height: 21px; color: var(--cc-ink-2); text-wrap: pretty }
.sc-note { margin: 0; font-size: 13.5px; line-height: 19px; color: var(--cc-ink-3); text-wrap: pretty }
.sc-note.is-split { padding-top: 10px; border-top: 1px solid var(--sc-div) }
.sc-fine { margin: 0; padding: 0 4px; font-size: 13px; line-height: 20px; color: var(--cc-ink-4) }
.sc-link { background: none; border: 0; padding: 0; color: var(--cc-ink-2); text-decoration: underline; cursor: pointer; font-size: inherit }
.sc-k { display: block; font-size: 12px; color: var(--cc-ink-4) }

/* ── Lists and rows ──────────────────────────────────────────────── */
.sc-list { border-radius: 24px; overflow: hidden; background: var(--sc-panel); border: 1px solid var(--sc-line) }
.sc-list.is-quiet { background: var(--sc-quiet) }
.has-div { border-top: 1px solid var(--sc-div) }
.sc-row {
  width: 100%; min-height: 72px; padding: 12px 16px; text-align: left;
  display: flex; align-items: center; gap: 12px; cursor: pointer;
  background: transparent; border: 0; border-radius: 0;
  transition: background 0.2s;
}
.sc-row.has-div { border-top: 1px solid var(--sc-div) }
.sc-row.is-on { background: rgba(var(--cc-accent-rgb), 0.12) }
.sc-row-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px }
.sc-row-t { font-size: 16.5px; font-weight: 600 }
.sc-row-sub { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 8px; font-size: 13px; color: var(--cc-ink-4) }
.sc-row-px { font-size: 14.5px; text-align: right }
.sc-radio {
  width: 22px; height: 22px; flex: none; border-radius: 50%;
  border: 2px solid var(--cc-ink-5); display: grid; place-items: center;
}
.sc-radio span { width: 10px; height: 10px; border-radius: 50%; background: transparent }
.sc-radio.is-on { border-color: var(--cc-accent) }
.sc-radio.is-on span { background: var(--cc-accent) }

.sc-badge {
  height: 22px; padding: 0 8px; border-radius: 11px; flex: none; white-space: nowrap;
  display: inline-flex; align-items: center; gap: 5px; align-self: flex-start;
  font-size: 11.5px; font-weight: 600; font-family: var(--sc-font);
  background: var(--sc-btn); border: 1px solid var(--sc-line); color: var(--cc-ink-2);
}
.sc-badge.is-lg { height: 30px; padding: 0 12px; border-radius: 15px; font-size: 13px; font-weight: 500 }
.sc-badge.is-warn { background: rgba(232, 163, 61, 0.12); border-color: rgba(232, 163, 61, 0.42); color: var(--cc-warn) }
.sc-badge.is-ok { background: var(--cc-buy-dim); border-color: rgba(63, 214, 168, 0.4); color: var(--cc-buy) }
.sc-badge.is-bad { background: var(--cc-sell-dim); border-color: rgba(224, 84, 78, 0.45); color: var(--cc-sell-text) }
.sc-dot { width: 6px; height: 6px; border-radius: 50%; flex: none; background: currentColor }
.sc-dot.is-warn { background: var(--cc-warn) }
.sc-dot.is-ok { background: var(--cc-buy) }
.sc-dot.is-bad { background: var(--cc-sell-text) }
.sc-dot.is-pulse { animation: scPulse 1.4s ease-in-out infinite }

.sc-brief, .sc-banner {
  width: 100%; text-align: left; cursor: pointer;
  border-radius: 20px; padding: 12px 14px;
  display: flex; align-items: center; gap: 12px;
  background: rgba(232, 163, 61, 0.08); border: 1px solid rgba(232, 163, 61, 0.32);
}
.sc-brief .ms, .sc-banner .ms { font-size: 20px; color: var(--cc-warn) }
.sc-brief-copy, .sc-banner-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.sc-brief-t, .sc-banner-t { font-size: 14.5px; font-weight: 600; color: var(--cc-warn) }
.sc-brief-s, .sc-banner-s { font-size: 12.5px; line-height: 17px; color: var(--cc-ink-3) }
.sc-banner.is-bad { cursor: default; align-items: flex-start; background: var(--cc-sell-dim); border-color: rgba(224, 84, 78, 0.4); font-size: 14px; line-height: 20px; color: var(--cc-ink-2) }
.sc-banner.is-bad .ms { color: var(--cc-sell-text) }

.sc-empty { display: flex; flex-direction: column; gap: 16px; padding-top: 8px }
.sc-ghosts { border-radius: 24px; border: 1px dashed var(--sc-line) }
.sc-ghost { height: 72px; padding: 0 16px; display: flex; flex-direction: column; justify-content: center; gap: 3px; font-size: 17px; font-weight: 600; color: var(--cc-ink-5) }
.sc-ghost + .sc-ghost { border-top: 1px dashed var(--sc-div) }
.sc-ghost .mono { font-size: 13px; font-weight: 500 }

/* ── Cards ───────────────────────────────────────────────────────── */
.sc-cards { display: flex; flex-direction: column; gap: 10px }
.sc-card {
  border-radius: 24px; padding: 14px 16px; text-align: left;
  display: flex; flex-direction: column; gap: 10px;
  background: var(--sc-panel); border: 1px solid var(--sc-line);
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}
.sc-card.is-pick { cursor: pointer; padding: 16px 16px 14px }
.sc-card.is-pick:active { transform: scale(0.99) }
.sc-card.is-on {
  background: linear-gradient(160deg, rgba(var(--cc-accent-rgb), 0.16), rgba(255, 255, 255, 0.03) 65%);
  border-color: rgba(var(--cc-accent-rgb), 0.6);
  box-shadow: 0 18px 40px -22px rgba(var(--cc-accent-rgb), 0.7);
}
.sc-card.is-quiet { background: var(--sc-quiet); gap: 12px }
.sc-card.is-bad { border-color: rgba(224, 84, 78, 0.45) }
.sc-card.is-ok { border-color: rgba(63, 214, 168, 0.35); background: var(--cc-buy-dim) }
.sc-card.is-blocked { background: var(--cc-sell-dim); border-color: rgba(224, 84, 78, 0.45); box-shadow: 0 0 30px -12px rgba(224, 84, 78, 0.6); gap: 12px }
.sc-card.is-brief {
  gap: 12px; padding: 18px; border-radius: 26px;
  background: linear-gradient(160deg, rgba(232, 163, 61, 0.13), rgba(255, 255, 255, 0.03) 62%);
  border-color: rgba(232, 163, 61, 0.38);
}
.sc-card-top { display: flex; align-items: center; justify-content: space-between; gap: 12px }
.sc-card-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px }
.sc-card-t { font-size: 22px; font-weight: 650; letter-spacing: -0.015em }
.sc-tag { font-size: 10.5px; letter-spacing: 0.12em; color: var(--cc-ink-4) }
.sc-card-d { font-size: 14px; line-height: 20px; color: var(--cc-ink-2); text-wrap: pretty }
.sc-card-f { font-size: 13px; line-height: 19px; color: var(--cc-ink-3); padding-top: 9px; border-top: 1px solid var(--sc-div) }
.sc-card-o { font-size: 13px; color: var(--cc-ink-3) }
.sc-trio { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px }
.sc-v { display: block; margin-top: 3px; font-size: 14px; font-weight: 500; line-height: 18px }
.sc-v.mono { font-size: 13.5px }

.sc-split { padding-top: 12px; border-top: 1px solid var(--sc-div) }
.sc-split .sc-body { margin-top: 6px }
.sc-card.is-quiet .sc-kicker + .sc-body { margin-top: 6px }

/* ── Size ────────────────────────────────────────────────────────── */
.sc-stepper { display: flex; align-items: center; gap: 12px }
.sc-stepper-v { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.sc-big { font-size: 32px; font-weight: 600; line-height: 1.15 }
.sc-presets { display: flex; flex-wrap: wrap; gap: 6px }
.sc-chip {
  height: 34px; padding: 0 12px; border-radius: 17px; cursor: pointer;
  display: inline-flex; align-items: center; font-size: 13px;
  background: var(--sc-btn); border: 1px solid var(--sc-line); color: var(--cc-ink-2);
}
.sc-chip.is-on { background: rgba(var(--cc-accent-rgb), 0.18); border-color: rgba(var(--cc-accent-rgb), 0.6); color: var(--cc-ink) }
.sc-chip.is-static { height: 28px; padding: 0 11px; border-radius: 14px; cursor: default; font-size: 13px; font-weight: 500 }
.sc-chip.is-static.mono { font-size: 12px }
.sc-rule { height: 1px; background: var(--sc-div) }
.sc-well { border-radius: 16px; background: var(--sc-well) }
.sc-kv { min-height: 42px; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; gap: 10px; font-size: 14px }
.sc-kv > span:first-child { color: var(--cc-ink-3) }
.sc-kv.is-tall { height: 56px; padding: 0 16px; font-size: 15px }

/* ── Chart ───────────────────────────────────────────────────────── */
.sc-frames { display: flex; gap: 12px; align-items: center }
.sc-frames > div { display: flex; flex-direction: column; align-items: center; gap: 6px; font-size: 12.5px; font-weight: 600 }
.sc-frame-yes { flex: 1.7 }
.sc-frame-no { flex: 1 }
.sc-frame-yes .sc-frame-box {
  position: relative; width: 100%; aspect-ratio: 2.2 / 1; border-radius: 12px;
  border: 2px solid var(--cc-buy);
  background: repeating-linear-gradient(135deg, rgba(63, 214, 168, 0.06) 0 6px, transparent 6px 12px);
}
.sc-frame-n { position: absolute; left: 8px; top: 6px; font-size: 9px; color: var(--cc-buy) }
.sc-frame-scale {
  position: absolute; right: 0; top: 0; bottom: 0; width: 18%;
  border-left: 1px dashed rgba(63, 214, 168, 0.6);
  display: flex; align-items: center; justify-content: center;
  font-size: 9px; color: var(--cc-buy); writing-mode: vertical-rl;
}
.sc-frame-no .sc-frame-box {
  width: 44px; height: 84px; border-radius: 9px; border: 2px solid var(--cc-sell-text);
  background: repeating-linear-gradient(135deg, rgba(224, 84, 78, 0.07) 0 5px, transparent 5px 10px);
}
.sc-guide { padding: 12px 14px; display: flex; gap: 10px; font-size: 14px; line-height: 20px; color: var(--cc-ink-2) }
.sc-guide .ms { font-size: 18px; line-height: 20px }
.sc-drop {
  height: 132px; border-radius: 20px; cursor: pointer;
  border: 1px dashed var(--cc-border-strong);
  background: repeating-linear-gradient(135deg, rgba(255, 255, 255, 0.03) 0 8px, transparent 8px 16px);
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
}
.sc-drop .ms { font-size: 28px; color: var(--cc-ink-3) }
.sc-drop-t { font-size: 15px; font-weight: 600 }
.sc-drop-s { font-size: 11.5px; color: var(--cc-ink-4) }
.sc-file { border-radius: 20px; padding: 12px 14px; display: flex; align-items: center; gap: 12px; font-size: 13.5px }
.sc-file.is-ok { background: var(--cc-buy-dim); border: 1px solid rgba(63, 214, 168, 0.35) }
.sc-file.is-bad { flex-direction: column; align-items: stretch; gap: 10px; background: var(--cc-sell-dim); border: 1px solid rgba(224, 84, 78, 0.4) }
.sc-file-img { width: 76px; height: 40px; object-fit: cover; border-radius: 8px; flex: none }
.sc-file-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; line-height: 20px; overflow-wrap: anywhere }
.sc-file-acts { display: flex; align-items: center; gap: 16px }

/* ── Scanning ────────────────────────────────────────────────────── */
.sc-core { position: relative; width: 196px; height: 196px; margin-top: 6px }
.sc-core-ring {
  position: absolute; left: 50%; top: 50%; width: 196px; height: 196px; border-radius: 50%;
  border: 1px solid rgba(var(--cc-accent-rgb), 0.6);
  animation: scRing 2.2s ease-out infinite;
}
.sc-core-ring.is-late { animation-delay: 1.1s }
.sc-core-face {
  position: absolute; inset: 0; border-radius: 50%; overflow: hidden;
  border: 1.5px solid rgba(var(--cc-accent-rgb), 0.7);
  box-shadow: 0 0 60px -6px rgba(var(--cc-accent-rgb), 0.7);
}
.sc-core-face img { width: 100%; height: 100%; object-fit: cover; object-position: 50% 22% }
.sc-core-sweep {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, transparent 38%, rgba(var(--cc-accent-rgb), 0.45) 50%, transparent 62%);
  mix-blend-mode: screen; animation: scSweep 1.8s ease-in-out infinite alternate;
}
.sc-core-arc {
  position: absolute; inset: -8px; border-radius: 50%;
  background: conic-gradient(from 0deg, transparent 0 72%, rgba(var(--cc-accent-rgb), 0.85) 96%, transparent 100%);
  -webkit-mask: radial-gradient(circle, transparent 66%, #000 67%, #000 70%, transparent 71%);
  mask: radial-gradient(circle, transparent 66%, #000 67%, #000 70%, transparent 71%);
  animation: scSpin 2.4s linear infinite;
}
.sc-scan-t { text-align: center; display: flex; flex-direction: column; gap: 6px }
.sc-scan-s { margin: 0; font-size: 12.5px; color: var(--cc-ink-4); text-wrap: balance }
.sc-step.is-center .sc-list { width: 100% }
.sc-stage { min-height: 50px; padding: 8px 14px; display: flex; align-items: center; gap: 11px; transition: opacity 0.3s }
.sc-stage.is-wait { opacity: 0.45 }
.sc-stage .ms { font-size: 20px; color: var(--cc-ink-5) }
.sc-stage.is-run .ms { color: var(--cc-accent) }
.sc-stage.is-done .ms { color: var(--cc-ink-3) }
.ms.is-spin { animation: scSpin 1s linear infinite }
.sc-stage-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px }
.sc-stage-l { font-size: 14.5px; font-weight: 500 }
.sc-stage-r { font-size: 12.5px; color: var(--cc-ink-4) }

/* ── Result ──────────────────────────────────────────────────────── */
.sc-verdict { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 10px 0 4px }
.sc-ring { width: 176px; height: 176px; border-radius: 50%; display: grid; place-items: center }
.sc-ring-in {
  width: 150px; height: 150px; border-radius: 50%; background: var(--cc-canvas);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.sc-ring-n { font-size: 56px; font-weight: 650; letter-spacing: -0.04em; line-height: 1 }
.sc-ring-b { font-size: 13px; color: var(--cc-ink-3); margin-top: 4px }
.sc-vtext { font-size: 48px; line-height: 50px; font-weight: 700; letter-spacing: -0.03em }
.sc-chips { display: flex; flex-wrap: wrap; justify-content: center; gap: 6px }
.sc-summary { margin: 0; font-size: 16px; line-height: 24px; color: var(--cc-ink-2); text-align: center; text-wrap: pretty }
.sc-line { display: flex; gap: 10px; align-items: flex-start }
.sc-line .ms { font-size: 19px; line-height: 20px }
.sc-line-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px }
.sc-line-l { font-size: 14.5px; font-weight: 500 }
.sc-line-r { font-size: 13px; line-height: 18px; color: var(--cc-ink-3) }

.sc-ladder { position: relative; margin-top: 2px }
.sc-zone { position: absolute; left: 0; right: 0; border-radius: 6px }
.sc-zone.is-win { background: rgba(63, 214, 168, 0.09) }
.sc-zone.is-loss { background: rgba(224, 84, 78, 0.1) }
.sc-lvl { position: absolute; left: 0; right: 0; transform: translateY(-50%); display: flex; align-items: center; gap: 10px }
.sc-lvl-l { width: 64px; font-size: 12.5px; font-weight: 600 }
.sc-lvl-line { flex: 1; border-top: 1px solid currentColor }
.sc-lvl-px { font-size: 13px }
.sc-lvl.is-tp { color: var(--cc-buy) }
.sc-lvl.is-dash .sc-lvl-line { border-top-style: dashed }
.sc-lvl.is-entry { color: var(--cc-ink) }
.sc-lvl.is-entry .sc-lvl-line { border-top: 2px solid var(--cc-accent) }
.sc-lvl.is-sl { color: var(--cc-sell-text) }
.sc-lvl.is-now { color: var(--cc-ink-3) }
.sc-lvl.is-now .sc-lvl-line { border-top-style: dotted }

.sc-cells { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px }
.sc-cells > div { border-radius: 14px; padding: 9px 10px; background: var(--sc-well); min-width: 0 }
.sc-cells .mono { display: block; margin-top: 3px; font-size: 15.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.sc-block { display: flex; flex-direction: column; gap: 6px; align-items: flex-start }
.sc-block-t { font-size: 16px; font-weight: 600; color: var(--cc-sell-text) }

/* ── Fundamentals / history ──────────────────────────────────────── */
.sc-day { display: flex; flex-direction: column; gap: 8px }
.sc-day > .sc-kicker { padding: 0 4px }
.sc-ev { min-height: 64px; padding: 10px 16px; display: flex; align-items: center; gap: 12px }
.sc-ev.is-past { opacity: 0.5 }
.sc-ev-t { width: 46px; flex: none; font-size: 14px; color: var(--cc-ink-2) }
.sc-ev-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px }
.sc-ev-n { font-size: 15px; font-weight: 600 }
.sc-ev-f { font-size: 12.5px; color: var(--cc-ink-4) }
.sc-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px }
.sc-stats > div { border-radius: 16px; padding: 10px; background: var(--sc-quiet); border: 1px solid var(--sc-line) }
.sc-stat { display: block; margin-top: 2px; font-size: 20px; font-weight: 650 }

/* ── Bottom bars ─────────────────────────────────────────────────────
   The app's floating Screens button sits bottom-right (54px, 18px in, 20px
   up). The bottom row of every bar stops short of it (`.sc-beside`); the
   full-width control sits above it. */
.sc-bar {
  position: absolute; left: 0; right: 0; bottom: 0; z-index: 6;
  padding: 40px 14px calc(var(--cc-safe-bottom) + 20px);
  display: flex; flex-direction: column; gap: 12px;
  background: linear-gradient(180deg, rgba(var(--cc-tone-rgb, 14, 14, 17), 0), rgba(var(--cc-tone-rgb, 14, 14, 17), 0.92) 34%, var(--cc-canvas));
}
.sc[data-scheme='light'] .sc-bar { background: linear-gradient(180deg, transparent, var(--cc-canvas) 34%) }
.sc-beside { margin-right: 66px }
.sc-cta {
  height: 58px; margin: 0 6px; border-radius: 29px; border: 0; cursor: pointer;
  font-size: 17px; font-weight: 600;
  background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink);
  box-shadow: 0 0 34px -6px rgba(var(--cc-accent-rgb), 0.75);
  transition: filter 0.2s, background 0.25s;
}
.sc-sheet .sc-cta { margin: 0; align-self: stretch }
.sc-cta:active { filter: brightness(1.08) }
.sc-cta.is-off { background: var(--sc-btn); color: var(--cc-ink-5); box-shadow: none; cursor: default }
.sc-acct {
  margin-right: 66px; border-radius: 26px; padding: 11px 14px 11px 18px; cursor: pointer; text-align: left;
  display: flex; align-items: center; gap: 12px;
  background: var(--cc-glass-fill); -webkit-backdrop-filter: blur(26px) saturate(140%); backdrop-filter: blur(26px) saturate(140%);
  border: 1px solid var(--sc-line);
  box-shadow: 0 14px 34px -12px rgba(0, 0, 0, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
.sc-acct-copy { flex: 1; min-width: 0 }
.sc-acct-v { display: block; margin-top: 3px; font-size: 15.5px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.sc-acct .ms { color: var(--cc-ink-3) }
.sc-btn {
  height: 46px; padding: 0 16px; border-radius: 23px; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 15px; font-weight: 600;
  background: var(--sc-btn); border: 1px solid var(--sc-line);
}
.sc-btn.is-tall { height: 54px; border-radius: 27px; font-size: 16px }
.sc-btn.is-sm { height: 36px; padding: 0 14px; border-radius: 18px; font-size: 14px }
.sc-btn.is-xs { height: 32px; padding: 0 13px; border-radius: 16px; font-size: 13.5px }
.sc-pair { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px }
.sc-prog { height: 4px; border-radius: 2px; background: var(--sc-line); overflow: hidden }
.sc-prog span { display: block; height: 100%; background: var(--cc-accent); box-shadow: 0 0 12px var(--cc-accent); transition: width 0.25s linear }
.sc-prog-meta { display: flex; justify-content: space-between; font-size: 12px; color: var(--cc-ink-4) }

.sc-slide {
  position: relative; height: 58px; border-radius: 29px; overflow: hidden;
  background: var(--sc-quiet); border: 1px solid var(--sc-line);
  user-select: none; touch-action: none;
}
.sc-slide.is-ok { background: rgba(255, 255, 255, 0.07); border-color: rgba(var(--cc-accent-rgb), 0.5) }
.sc[data-scheme='light'] .sc-slide.is-ok { background: var(--cc-card) }
.sc-slide-fill { position: absolute; left: 0; top: 0; bottom: 0; width: 52px; background: transparent; transition: width 0.3s ease }
.sc-slide.is-ok .sc-slide-fill { background: rgba(var(--cc-accent-rgb), 0.4) }
.sc-slide-l {
  position: absolute; inset: 0; padding: 0 16px 0 60px;
  display: flex; align-items: center; justify-content: center; text-align: center;
  font-size: 15.5px; font-weight: 600; color: var(--cc-ink-5);
}
.sc-slide.is-ok .sc-slide-l { color: var(--cc-ink) }
.sc-slide-knob {
  position: absolute; top: 3px; width: 50px; height: 50px; border-radius: 25px;
  display: grid; place-items: center; touch-action: none; cursor: default;
  background: var(--sc-btn); color: var(--cc-ink-5);
  transition: left 0.3s ease;
}
.sc-slide.is-ok .sc-slide-knob {
  cursor: grab; background: var(--cc-accent-fill); color: var(--cc-accent-fill-ink);
  box-shadow: 0 0 20px rgba(var(--cc-accent-rgb), 0.7);
}
.sc-slide-knob:focus-visible { outline: 2px solid var(--cc-ink); outline-offset: 2px }

/* ── Sheets ──────────────────────────────────────────────────────── */
.sc-scrim { position: absolute; inset: 0; z-index: 9100; background: rgba(0, 0, 0, 0.55); animation: scFade 0.2s ease both }
.sc-sheet {
  position: absolute; left: 8px; right: 8px; bottom: calc(var(--cc-safe-bottom) + 8px); z-index: 9101;
  max-height: 82dvh; overflow-y: auto;
  border-radius: 36px; padding: 10px 18px 20px;
  display: flex; flex-direction: column; gap: 12px;
  background: var(--cc-glass-fill); -webkit-backdrop-filter: blur(30px) saturate(160%); backdrop-filter: blur(30px) saturate(160%);
  border: 1px solid var(--sc-line); box-shadow: var(--cc-shadow-sheet);
  animation: scSheet 0.3s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}
.sc-grab { align-self: center; width: 38px; height: 5px; border-radius: 3px; background: var(--cc-border-strong) }
.sc-opt {
  width: 100%; min-height: 68px; padding: 12px 16px; border-radius: 22px; cursor: pointer; text-align: left;
  display: flex; align-items: center; gap: 12px;
  background: var(--sc-btn); border: 1px solid var(--sc-line);
}
.sc-opt.is-on { background: rgba(var(--cc-accent-rgb), 0.12); border-color: rgba(var(--cc-accent-rgb), 0.55) }
.sc-opt:disabled { opacity: 0.6 }
.sc-opt-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px }
.sc-opt-t { font-size: 17px; font-weight: 600 }
.sc-opt-d { font-size: 13.5px; color: var(--cc-ink-3) }
.sc-mine { margin-left: 6px; font-size: 11.5px; font-weight: 600; color: var(--cc-accent-text) }

.sc-toast {
  position: absolute; left: 20px; right: 20px; top: calc(var(--cc-safe-top) + 60px); z-index: 9200;
  padding: 13px 16px; border-radius: 18px;
  background: var(--cc-card-2); border: 1px solid var(--sc-line); box-shadow: var(--cc-shadow-float);
  font-size: 14.5px; font-weight: 500; animation: scUp 0.25s ease both;
}

@keyframes scSpin { to { transform: rotate(360deg) } }
@keyframes scPulse { 0%, 100% { opacity: 0.35 } 50% { opacity: 1 } }
@keyframes scRing { 0% { transform: translate(-50%, -50%) scale(0.75); opacity: 0.7 } 100% { transform: translate(-50%, -50%) scale(1.6); opacity: 0 } }
@keyframes scSweep { 0% { transform: translateY(-120%) } 100% { transform: translateY(120%) } }
@keyframes scUp { from { opacity: 0; transform: translateY(10px) } to { opacity: 1; transform: none } }
@keyframes scFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes scSheet { from { transform: translateY(100%) } to { transform: none } }

.sc[data-motion='off'] *,
.sc[data-motion='off'] *::before { animation: none !important; transition: none !important }
@media (prefers-reduced-motion: reduce) {
  .sc * { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important }
}
</style>
