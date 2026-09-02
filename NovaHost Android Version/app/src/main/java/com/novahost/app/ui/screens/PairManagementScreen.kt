package com.novahost.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.novahost.app.sdk.EconomicEvent
import com.novahost.app.sdk.MarketSession
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.sdk.SymbolConfig
import com.novahost.app.sdk.SymbolPlan
import com.novahost.app.sdk.SymbolPlanStore
import com.novahost.app.ui.scanner.ScanCard
import com.novahost.app.ui.scanner.readableInk
import com.novahost.app.ui.theme.*
import com.novahost.app.ui.viewmodels.MarketsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Trading Symbols -- the robot's allowance, the user's selection, the size of
 * each trade, and the market context that decides whether to let it run.
 *
 * One scroll rather than the four tabs this screen used to carry. The order is
 * the decision the user is actually making, top to bottom: what am I *allowed*
 * to trade, which of those do I *want*, how big, and what is about to hit the
 * wire. Tabs put a click between every step of that and hid the sizing controls
 * behind a label ("SYMBOLS") that did not mention them.
 *
 * ## The bug this screen replaces
 *
 * The old version wrote the user's ticks back into `allowed_symbols` -- the key
 * that holds the *robot's* allowance and the key the grid is built from. So
 * unticking a symbol deleted it from the mentor's allowance, it disappeared from
 * the screen on the next visit, and there was no way to get it back. Untick all
 * of them and the app would never trade again. The two permissions are separate
 * now; see [SymbolPlanStore].
 */
@Composable
fun PairManagementScreen(
    navController: NavController,
    viewModel: MarketsViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val accent = LocalNovaHostTheme.current.primaryColor
    val robotName = LocalRobotBranding.current.name

    // Re-read when the mentor changes what the robot may trade.
    //
    // `remember { }` with no key read the allowance exactly once and then never
    // again, so a symbol added by the mentor mid-session stayed invisible until
    // the app was restarted -- on top of the allowance itself never refreshing
    // at all. The signal poll now applies the server's allowance and bumps
    // [SymbolPlanStore.allowanceRevision]; this follows it.
    val revision by SymbolPlanStore.allowanceRevision.collectAsState()

    val allowance = remember(revision) { SymbolPlanStore.robotAllowance(context) }
    var plan by remember { mutableStateOf(SymbolPlanStore.load(context)) }

    // Reload the plan when the allowance moves. `load` reconciles against the
    // current allowance and preserves whatever the user set for symbols that
    // survived, so a new symbol appears with defaults and nothing already
    // configured is disturbed. Skipped on first composition -- the plan above is
    // already current, and reloading it would discard nothing but wastes a read.
    LaunchedEffect(revision) {
        if (revision > 0) plan = SymbolPlanStore.load(context)
    }
    var active by remember {
        mutableStateOf(plan.selected.firstOrNull()?.symbol ?: allowance.firstOrNull())
    }

    val terminalBalance by MetaAPIManager.balance.collectAsState()
    val sessions by viewModel.marketSessions.collectAsState()
    val events by viewModel.economicCalendar.collectAsState()

    // The calculator's answer is derived, never stored by hand: every input that
    // feeds it is already on the plan, so recomputing keeps the cached figure
    // and the numbers on screen from ever disagreeing.
    val suggestedLot = remember(plan.balance, plan.riskPercent, plan.riskTrades, plan.fxRate) {
        SymbolPlanStore.suggestedLot(
            balance = plan.balance,
            riskPercent = plan.riskPercent,
            trades = plan.riskTrades,
            fxRate = plan.fxRate
        )
    }

    // The rate the sizing is done against. Refreshed when the user changes
    // account currency, cached by FxRates so a flat network still sizes.
    var fxQuote by remember { mutableStateOf<com.novahost.app.sdk.FxRates.Quote?>(null) }
    LaunchedEffect(plan.currency) {
        val quote = com.novahost.app.sdk.FxRates.usdTo(context, plan.currency)
        fxQuote = quote
        if (quote.rate > 0.0 && quote.rate != plan.fxRate) {
            plan = plan.copy(fxRate = quote.rate)
        }
    }

    // Seed the balance from the linked terminal, once, and only when the user
    // has not typed one. Their figure outranks the broker's.
    LaunchedEffect(terminalBalance) {
        if (plan.balance <= 0.0 && terminalBalance > 0.0) {
            plan = plan.copy(balance = terminalBalance)
        }
    }

    // Persist locally on every change; push to the server on a pause. Debounced
    // because a lot stepper held down would otherwise fire a request per tap,
    // and the server only ever needs the value the user stopped on.
    var syncState by remember { mutableStateOf<SyncState>(SyncState.Idle) }

    /**
     * The last plan an explicit APPLY already pushed.
     *
     * Without it, tapping APPLY fires the immediate sync and then the debounce
     * below fires a second, identical one 900ms later -- and if that redundant
     * call is the one that hits a dead spot, the pip flips to OFFLINE moments
     * after the toast said "saved". Confirming a save and then contradicting it
     * is worse than either alone.
     */
    var pushedPlan by remember { mutableStateOf<SymbolPlan?>(null) }

    LaunchedEffect(plan, suggestedLot) {
        val current = plan.copy(smartLotSize = suggestedLot)
        SymbolPlanStore.save(context, current)
        delay(900)
        if (current == pushedPlan) return@LaunchedEffect
        syncState = SyncState.Syncing
        syncState = SymbolPlanStore.sync(context).fold(
            onSuccess = { pushedPlan = current; SyncState.Synced },
            onFailure = { SyncState.Failed(it.message ?: "Could not reach the server") }
        )
    }

    fun update(symbol: String, transform: (SymbolConfig) -> SymbolConfig) {
        plan = plan.copy(
            symbols = plan.symbols.map { if (it.symbol == symbol) transform(it) else it }
        )
    }

    // ---- Broker symbol discovery --------------------------------------------
    //
    // Kept out of the normal save path on purpose: this queries the user's live
    // broker, which is slow and can fail for reasons that have nothing to do
    // with the plan. It is an explicit action with its own result line, not
    // something that happens silently behind a stepper.
    var discovering by remember { mutableStateOf(false) }
    var discoveryNote by remember { mutableStateOf<String?>(null) }

    fun discoverBrokerSymbols() {
        if (discovering) return
        discovering = true
        discoveryNote = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        scope.launch {
            SymbolPlanStore.discover(context).fold(
                onSuccess = { found ->
                    // discover() saves what it resolved, so the screen re-reads
                    // rather than trying to merge the same answer twice.
                    plan = SymbolPlanStore.load(context)
                    discoveryNote = when {
                        found.matched.isEmpty() ->
                            "Your broker lists none of these under a name we recognise. " +
                                "Type the exact names from MetaTrader Market Watch below."
                        found.unmatched.isEmpty() ->
                            "Matched all ${found.matched.size} to your broker."
                        else ->
                            "Matched ${found.matched.size}. Not on your broker: " +
                                found.unmatched.joinToString(", ")
                    }
                },
                onFailure = {
                    discoveryNote = it.message ?: "Could not reach your broker."
                }
            )
            discovering = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ScanCanvas)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // The global nav overlay floats a 48dp menu button at top=48dp,
                // start=24dp on this route. The header clears it rather than
                // drawing a second, competing back affordance underneath it.
                .padding(top = 48.dp, bottom = 40.dp)
        ) {
            SymbolsHeader(
                robotName = robotName,
                selectedCount = plan.selected.size,
                total = allowance.size,
                syncState = syncState
            )

            Column(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {

                RobotAllowanceSection(
                    allowance = allowance,
                    smartLotSize = suggestedLot,
                    plan = plan,
                    accent = accent,
                    robotName = robotName,
                    onToggle = { sym ->
                        val wasOn = plan.configFor(sym)?.enabled == true
                        update(sym) { it.copy(enabled = !wasOn) }
                        active = if (wasOn) {
                            plan.selected.firstOrNull { it.symbol != sym }?.symbol
                        } else sym
                    }
                )

                BrokerSymbolSection(
                    allowance = allowance,
                    plan = plan,
                    accent = accent,
                    discovering = discovering,
                    note = discoveryNote,
                    onDiscover = ::discoverBrokerSymbols,
                    onBrokerSymbol = { sym, value ->
                        update(sym) { it.copy(brokerSymbol = value) }
                    }
                )

                SelectionSection(
                    plan = plan,
                    active = active,
                    accent = accent,
                    smartLotSize = suggestedLot,
                    perTradeAmount = perTradeAmount(plan),
                    onPick = { active = it },
                    onLotStep = { delta ->
                        active?.let { sym ->
                            update(sym) {
                                val stepped = (it.lot + delta)
                                    .coerceIn(SymbolPlanStore.MIN_LOT, SymbolPlanStore.MAX_LOT)
                                it.copy(
                                    // Stepping the lot is the user taking the
                                    // wheel, so it turns smart lot off -- otherwise
                                    // the number they just set would be ignored in
                                    // favour of the calculator's.
                                    smartLot = false,
                                    lot = Math.round(stepped * 100.0) / 100.0
                                )
                            }
                        }
                    },
                    onTradesStep = { delta ->
                        active?.let { sym ->
                            update(sym) {
                                it.copy(
                                    maxTrades = (it.maxTrades + delta)
                                        .coerceIn(SymbolPlanStore.MIN_TRADES, SymbolPlanStore.MAX_TRADES)
                                )
                            }
                        }
                    },
                    onToggleSmart = {
                        active?.let { sym -> update(sym) { it.copy(smartLot = !it.smartLot) } }
                    }
                )

                CalculatorSection(
                    plan = plan,
                    accent = accent,
                    suggestedLot = suggestedLot,
                    activeSymbol = active,
                    terminalBalance = terminalBalance,
                    fxQuote = fxQuote,
                    onCurrency = { plan = plan.copy(currency = it) },
                    onBalance = { plan = plan.copy(balance = it) },
                    onRisk = { plan = plan.copy(riskPercent = it) },
                    onTrades = { plan = plan.copy(riskTrades = it) },
                    onApply = {
                        val sym = active
                        if (sym != null && suggestedLot > 0.0) {
                            // Fires before the network call, not after. The tap
                            // is confirmed by the phone the instant it lands;
                            // whether the server agreed is a separate answer,
                            // and the toast below is what carries that.
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                            val applied = plan.copy(
                                symbols = plan.symbols.map {
                                    if (it.symbol == sym) it.copy(smartLot = true) else it
                                },
                                smartLotSize = suggestedLot
                            )
                            plan = applied

                            // Pushed immediately rather than waiting out the
                            // debounce. APPLY is an explicit instruction, and a
                            // user who taps it and leaves the screen inside a
                            // second had their change saved locally and never
                            // sent -- the executor went on sizing from the old
                            // numbers with nothing on screen to say so.
                            scope.launch {
                                SymbolPlanStore.save(context, applied)
                                syncState = SyncState.Syncing
                                syncState = SymbolPlanStore.sync(context).fold(
                                    onSuccess = {
                                        pushedPlan = applied
                                        toast(
                                            context,
                                            String.format(
                                                Locale.US,
                                                "%.2f lot strategy saved for %s",
                                                suggestedLot,
                                                sym
                                            )
                                        )
                                        SyncState.Synced
                                    },
                                    onFailure = {
                                        // Says what is and is not true. The plan
                                        // did save on the handset; what failed
                                        // is server-side enforcement, and
                                        // claiming a clean save would be a lie.
                                        toast(
                                            context,
                                            "Saved on this device — could not reach the hosting server."
                                        )
                                        SyncState.Failed(it.message ?: "Could not reach the server")
                                    }
                                )
                            }
                        }
                    }
                )

                NewsSection()

                SessionsSection(sessions = sessions, accent = accent)

                EventsSection(events = events)
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────

/**
 * A short, non-blocking confirmation.
 *
 * The only feedback APPLY used to give was a 12sp "SAVED" pip in the header,
 * at the far end of a long scroll from the button and arriving 900ms after the
 * tap. A user pressing a button at the bottom of the screen does not see a
 * caption at the top of it, so the change read as having done nothing.
 */
private fun toast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

private sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data object Synced : SyncState
    data class Failed(val reason: String) : SyncState
}

@Composable
private fun SymbolsHeader(
    robotName: String,
    selectedCount: Int,
    total: Int,
    syncState: SyncState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            // Clears the floating nav button: 24dp start + 48dp button + gap.
            .padding(start = 84.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "TRADING SYMBOLS",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Text(
                "$robotName · $selectedCount of $total in use",
                color = HomeTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        SyncPip(syncState)
    }
    Spacer(Modifier.height(14.dp))
}

/**
 * Whether the plan has reached the server.
 *
 * Worth its own indicator because a plan that saved locally but never synced
 * still *looks* applied, and the difference only shows up as a trade sized off
 * numbers the user thought they had changed.
 */
@Composable
private fun SyncPip(state: SyncState) {
    val (dot, label) = when (state) {
        SyncState.Idle -> HomeTextFaint to "READY"
        SyncState.Syncing -> ScanWarn to "SAVING"
        SyncState.Synced -> ScanBuy to "SAVED"
        is SyncState.Failed -> ScanSell to "OFFLINE"
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(dot.copy(alpha = 0.09f))
            .border(1.dp, dot.copy(alpha = 0.32f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
        Text(
            label,
            color = dot,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}

// ── 1 · Robot allowance ────────────────────────────────────────────────────

/**
 * A rough asset class from the symbol name.
 *
 * Inferred rather than fetched: the licence carries symbol names and nothing
 * else, and the label is a reading aid on a card the user is scanning, not a
 * value anything trades on. Anything unrecognised falls through to FOREX rather
 * than inventing a class for it.
 */
private fun categoryOf(symbol: String): String {
    val s = symbol.uppercase().replace("/", "").replace("-", "")
    return when {
        s.startsWith("XAU") || s.startsWith("XAG") || s.startsWith("XPT") -> "METALS"
        s.startsWith("BTC") || s.startsWith("ETH") || s.contains("USDT") -> "CRYPTO"
        s.startsWith("US30") || s.startsWith("NAS") || s.startsWith("SPX") ||
            s.startsWith("GER") || s.startsWith("UK100") || s.startsWith("JP225") -> "INDEX"
        s.startsWith("USOIL") || s.startsWith("UKOIL") || s.startsWith("WTI") -> "ENERGY"
        else -> "FOREX"
    }
}

@Composable
private fun RobotAllowanceSection(
    allowance: List<String>,
    plan: SymbolPlan,
    accent: Color,
    robotName: String,
    smartLotSize: Double,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(
            title = "ALLOWED BY THE ROBOT",
            subtitle = if (allowance.isEmpty()) {
                "This licence carried no symbols"
            } else {
                "$robotName permits ${allowance.size} " +
                    if (allowance.size == 1) "symbol" else "symbols"
            },
            trailing = {
                Text(
                    "TICK TO USE",
                    color = accent,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier
                        .border(1.dp, accent.copy(alpha = 0.32f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        )

        if (allowance.isEmpty()) {
            EmptyNote("No symbols yet. Activate a robot licence and its pairs appear here.")
            return@Column
        }

        // Chunked rather than a LazyVerticalGrid: this sits inside the screen's
        // vertical scroll, and nesting a lazy scroller in a scroller does not
        // measure.
        allowance.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { sym ->
                    AllowanceCard(
                        symbol = sym,
                        config = plan.configFor(sym),
                        accent = accent,
                        smartLotSize = smartLotSize,
                        onToggle = { onToggle(sym) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keeps a short final row aligned to the grid instead of
                // stretching two cards across three columns.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AllowanceCard(
    symbol: String,
    config: SymbolConfig?,
    accent: Color,
    smartLotSize: Double,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val on = config?.enabled == true
    val shape = RoundedCornerShape(17.dp)

    Column(
        modifier = modifier
            .height(104.dp)
            .clip(shape)
            .background(if (on) accent.copy(alpha = 0.10f) else ScanSurfaceRaised)
            .border(
                if (on) 1.5.dp else 1.dp,
                if (on) accent else HomeBorderSubtle,
                shape
            )
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                categoryOf(symbol),
                color = HomeTextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) accent else Color.Transparent)
                    .border(
                        1.dp,
                        if (on) accent else ScanBorderStrong,
                        RoundedCornerShape(7.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (on) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = if (on) "In use" else "Tap to use",
                    tint = if (on) accent.readableInk() else ScanTextTrace,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                symbol,
                color = if (on) Color.White else HomeTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (config != null && on) {
                    String.format(Locale.US, "%.2f × %d", effectiveLot(config, smartLotSize), config.maxTrades)
                } else "tap to use",
                color = HomeTextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** What this symbol would actually send: the calculator's figure, or the stepper's. */
private fun effectiveLot(config: SymbolConfig, smartLotSize: Double): Double =
    if (config.smartLot && smartLotSize > 0.0) smartLotSize else config.lot

// ── 2 · Selection and per-symbol sizing ────────────────────────────────────

@Composable
private fun SelectionSection(
    plan: SymbolPlan,
    active: String?,
    accent: Color,
    smartLotSize: Double,
    perTradeAmount: Double,
    onPick: (String) -> Unit,
    onLotStep: (Double) -> Unit,
    onTradesStep: (Int) -> Unit,
    onToggleSmart: () -> Unit
) {
    val selected = plan.selected
    val activeCfg = active?.let { plan.configFor(it) }?.takeIf { it.enabled }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(
            title = "YOUR SELECTION",
            subtitle = "Pick one on the left to size it",
            trailing = {
                Text(
                    "${selected.size} selected",
                    color = HomeTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            // Left rail
            Column(
                modifier = Modifier.width(96.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (selected.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, HomeBorderSubtle, RoundedCornerShape(14.dp))
                            .padding(vertical = 14.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tick a symbol above",
                            color = HomeTextFaint,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    selected.forEach { cfg ->
                        RailItem(
                            config = cfg,
                            isActive = cfg.symbol == active,
                            accent = accent,
                            smartLotSize = smartLotSize,
                            onClick = { onPick(cfg.symbol) }
                        )
                    }
                }
            }

            // Config panel
            ScanCard(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                background = ScanSurfaceGlass,
                borderColor = HomeBorderFaint,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            active ?: "No symbol",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                activeCfg == null -> "Tick a robot symbol to configure"
                                activeCfg.smartLot -> "${categoryOf(activeCfg.symbol)} · smart lot on"
                                else -> "${categoryOf(activeCfg.symbol)} · manual lot"
                            },
                            color = HomeTextMuted,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (activeCfg != null) ArmedChip()
                }

                Spacer(Modifier.height(13.dp))

                StepperRow(
                    label = "LOT SIZE",
                    hint = "per trade",
                    accent = accent,
                    enabled = activeCfg != null,
                    onDown = { onLotStep(-SymbolPlanStore.LOT_STEP) },
                    onUp = { onLotStep(SymbolPlanStore.LOT_STEP) }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            String.format(
                                Locale.US, "%.2f",
                                activeCfg?.let { effectiveLot(it, smartLotSize) } ?: 0.0
                            ),
                            color = accent,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            when {
                                activeCfg == null -> "—"
                                activeCfg.smartLot -> "from calculator"
                                else -> "manual · 0.01 steps"
                            },
                            color = HomeTextDim,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))

                StepperRow(
                    label = "HOW MANY TRADES",
                    hint = "open at once",
                    accent = accent,
                    enabled = activeCfg != null,
                    onDown = { onTradesStep(-1) },
                    onUp = { onTradesStep(1) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        (1..SymbolPlanStore.MAX_TRADES).forEach { i ->
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height((10 + i * 2).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (i <= (activeCfg?.maxTrades ?: 0)) accent
                                        else ScanTrack
                                    )
                            )
                        }
                        Text(
                            (activeCfg?.maxTrades ?: 0).toString(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(11.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "EXPOSURE",
                            color = HomeTextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            if (activeCfg == null) "—" else {
                                val lot = effectiveLot(activeCfg, smartLotSize)
                                String.format(
                                    Locale.US, "%.2f lots · $%,.0f at risk",
                                    lot * activeCfg.maxTrades,
                                    perTradeAmount * activeCfg.maxTrades
                                )
                            },
                            color = HomeTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    SmartLotChip(
                        on = activeCfg?.smartLot == true,
                        accent = accent,
                        enabled = activeCfg != null,
                        onClick = onToggleSmart
                    )
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    config: SymbolConfig,
    isActive: Boolean,
    accent: Color,
    smartLotSize: Double,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (isActive) accent.copy(alpha = 0.10f) else ScanSurfaceRaised)
            .border(1.dp, if (isActive) accent.copy(alpha = 0.42f) else HomeBorderSubtle, shape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(vertical = 11.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(if (isActive) accent else ScanBorderStrong)
        )
        Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp)) {
            Text(
                config.symbol,
                color = if (isActive) Color.White else HomeTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                String.format(
                    Locale.US, "%.2f × %d",
                    effectiveLot(config, smartLotSize), config.maxTrades
                ),
                color = HomeTextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun ArmedChip() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(ScanBuy.copy(alpha = 0.09f))
            .border(1.dp, ScanBuy.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(ScanBuy))
        Text(
            "ARMED",
            color = ScanBuy,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun SmartLotChip(on: Boolean, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    val tint = when {
        !enabled -> HomeTextFaint
        on -> accent
        else -> HomeTextMuted
    }
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (on && enabled) accent.copy(alpha = 0.14f) else ScanSurfaceRaised)
            .border(1.dp, if (on && enabled) accent.copy(alpha = 0.45f) else ScanBorderStrong, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text("SMART LOT", color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepperRow(
    label: String,
    hint: String,
    accent: Color,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    readout: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(ScanWell)
            .border(1.dp, HomeBorderSubtle, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = HomeTextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(hint, color = HomeTextFaint, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StepButton(Icons.Rounded.Remove, "Decrease", accent, filled = false, enabled = enabled, onClick = onDown)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { readout() }
            StepButton(Icons.Rounded.Add, "Increase", accent, filled = true, enabled = enabled, onClick = onUp)
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(if (filled && enabled) accent.copy(alpha = 0.12f) else ScanSurfaceRaised)
            .border(1.dp, if (filled && enabled) accent.copy(alpha = 0.4f) else ScanBorderStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> HomeTextFaint
                filled -> accent
                else -> HomeTextValue
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── 3 · Trade calculator ───────────────────────────────────────────────────

private fun perTradeAmount(plan: SymbolPlan): Double {
    val n = plan.riskTrades.coerceAtLeast(1)
    return plan.balance * plan.riskPercent.coerceIn(0.0, 100.0) / 100.0 / n
}

@Composable
private fun CalculatorSection(
    plan: SymbolPlan,
    accent: Color,
    suggestedLot: Double,
    activeSymbol: String?,
    terminalBalance: Double,
    fxQuote: com.novahost.app.sdk.FxRates.Quote?,
    onCurrency: (String) -> Unit,
    onBalance: (Double) -> Unit,
    onRisk: (Double) -> Unit,
    onTrades: (Int) -> Unit,
    onApply: () -> Unit
) {
    val perAmt = perTradeAmount(plan)
    val totalAmt = plan.balance * plan.riskPercent.coerceIn(0.0, 100.0) / 100.0
    val perPct = SymbolPlanStore.perTradeRiskPercent(plan)
    val sym = com.novahost.app.sdk.FxRates.symbolFor(plan.currency)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(
            title = "TRADE CALCULATOR",
            subtitle = "Splits one risk budget across your trades"
        )

        ScanCard(
            shape = RoundedCornerShape(20.dp),
            background = ScanSurface,
            borderColor = HomeBorderFaint,
            contentPadding = 14.dp
        ) {
            // Account currency first: it changes what every figure below means.
            // A rand balance sized against a dollar pip value produced positions
            // roughly eighteen times too large, so this is not a display option.
            CurrencyRail(
                selected = plan.currency,
                accent = accent,
                onSelect = onCurrency
            )

            Spacer(Modifier.height(11.dp))

            NumberField(
                label = "ACCOUNT BALANCE ($sym)",
                value = if (plan.balance > 0.0) {
                    String.format(Locale.US, "%.0f", plan.balance)
                } else "",
                placeholder = "10000",
                hint = if (terminalBalance > 0.0) "from your linked terminal" else "no terminal linked",
                accent = accent,
                fontSize = 20.sp,
                onValue = { onBalance(it.toDoubleOrNull() ?: 0.0) }
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(
                    label = "TOTAL RISK (%)",
                    value = trimNumber(plan.riskPercent),
                    placeholder = "1",
                    hint = "across all trades",
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onValue = { onRisk(it.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0) }
                )
                NumberField(
                    label = "HOW MANY TRADES",
                    value = plan.riskTrades.toString(),
                    placeholder = "1",
                    hint = "splits the budget",
                    accent = accent,
                    digitsOnly = true,
                    modifier = Modifier.weight(1f),
                    onValue = { onTrades(it.toIntOrNull()?.coerceIn(1, 20) ?: 1) }
                )
            }

            Spacer(Modifier.height(11.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Readout(
                    label = "RISK / TRADE",
                    value = String.format(Locale.US, "%.2f%%", perPct),
                    sub = sym + String.format(Locale.US, "%,.0f", perAmt),
                    valueColor = accent,
                    tinted = accent,
                    modifier = Modifier.weight(1f)
                )
                Readout(
                    label = "TOTAL AT RISK",
                    value = sym + String.format(Locale.US, "%,.0f", totalAmt),
                    sub = String.format(
                        Locale.US, "%.2f%% of %,.0f",
                        plan.riskPercent, plan.balance
                    ),
                    valueColor = ScanTextBright,
                    modifier = Modifier.weight(1f)
                )
                Readout(
                    label = "SUGGESTED LOT",
                    value = String.format(Locale.US, "%.2f", suggestedLot),
                    sub = "${SymbolPlanStore.STOP_PIPS.toInt()}-pip stop",
                    valueColor = ScanTextBright,
                    modifier = Modifier.weight(1f)
                )
            }

            // What the lot was actually sized against. A conversion the user
            // cannot see is a conversion they cannot check, and this one decides
            // how much money each position risks.
            if (plan.currency != "USD") {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = when {
                        fxQuote == null -> "Fetching the USD/${plan.currency} rate..."
                        fxQuote.isEstimate ->
                            "Sized at an estimated 1 USD = ${String.format(Locale.US, "%.2f", fxQuote.rate)} ${plan.currency} " +
                                "— no live rate available, check your connection."
                        else ->
                            "Sized at 1 USD = ${String.format(Locale.US, "%.2f", fxQuote.rate)} ${plan.currency}, " +
                                fxRateAge(fxQuote.ageMillis) + "."
                    },
                    color = if (fxQuote?.isEstimate == true) ScanWarnText else ScanTextTrace,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(Modifier.height(11.dp))

            val canApply = activeSymbol != null && suggestedLot > 0.0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canApply) accent else ScanDisabledFill)
                    .clickable(enabled = canApply, onClick = onApply),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = if (canApply) accent.readableInk() else HomeTextFaint,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (canApply) {
                        String.format(Locale.US, "APPLY %.2f TO %s", suggestedLot, activeSymbol)
                    } else "PICK A SYMBOL TO APPLY",
                    color = if (canApply) accent.readableInk() else HomeTextFaint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
            }
        }
    }
}

/** Drops a trailing `.0` so a whole percentage does not read as a decimal. */
private fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.US, "%.2f", v)

@Composable
private fun NumberField(
    label: String,
    value: String,
    placeholder: String,
    hint: String,
    accent: Color,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    digitsOnly: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp
) {
    // Local text state so a partial entry survives.
    //
    // Keying this on [value] would defeat it: every keystroke parses to a Double
    // and renders back, so typing "1." produces a value of "1", the key changes,
    // and the decimal point is deleted from under the user's finger. Instead the
    // field only accepts an outside value when it differs *numerically* from
    // what is typed -- which lets the terminal balance seed the field, but never
    // lets a round-trip rewrite a half-finished number.
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value.toDoubleOrNull() != text.toDoubleOrNull()) text = value
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ScanWell)
            .border(1.dp, HomeBorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = HomeTextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(5.dp))
        Box {
            if (text.isEmpty()) {
                Text(
                    placeholder,
                    color = ScanTextTrace,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val cleaned = if (digitsOnly) {
                        raw.filter { it.isDigit() }.take(2)
                    } else {
                        raw.filter { it.isDigit() || it == '.' }
                    }
                    text = cleaned
                    onValue(cleaned)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (digitsOnly) KeyboardType.Number else KeyboardType.Decimal
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(hint, color = HomeTextFaint, fontSize = 9.sp)
    }
}

@Composable
private fun Readout(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    tinted: Color? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tinted?.copy(alpha = 0.07f) ?: Color.White.copy(alpha = 0.035f))
            .border(
                1.dp,
                tinted?.copy(alpha = 0.22f) ?: HomeBorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .padding(10.dp)
    ) {
        Text(
            label,
            color = HomeTextMuted,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.9.sp
        )
        Text(
            value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 5.dp)
        )
        Text(
            sub,
            color = HomeTextDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// ── 4 · News ───────────────────────────────────────────────────────────────

@Composable
private fun NewsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(
            title = "NEWS",
            subtitle = "Wire on your selected symbols",
            trailing = {
                Text("live feed", color = HomeTextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Fixed height on purpose: the feed is a WebView, and a WebView
                // inside a vertical scroll has no intrinsic height to measure.
                .height(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ScanSurface)
                .border(1.dp, HomeBorderFaint, RoundedCornerShape(20.dp))
        ) {
            NewsPanel()
        }
    }
}

// ── 5 · Sessions ───────────────────────────────────────────────────────────

@Composable
private fun SessionsSection(sessions: List<MarketSession>, accent: Color) {
    val openNames = sessions.filter { it.isOpen }.joinToString(" + ") { it.name.uppercase() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(
            title = "SESSIONS",
            subtitle = "Local time liquidity windows",
            trailing = {
                Text(
                    if (openNames.isBlank()) "ALL CLOSED" else "$openNames OPEN",
                    color = if (openNames.isBlank()) HomeTextDim else accent,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        )

        ScanCard(
            shape = RoundedCornerShape(20.dp),
            background = ScanSurface,
            borderColor = HomeBorderFaint,
            contentPadding = 14.dp
        ) {
            if (sessions.isEmpty()) {
                EmptyNote("Session times are still loading.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sessions.take(4).forEach { session ->
                        SessionTile(session, accent, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTile(session: MarketSession, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (session.isOpen) accent.copy(alpha = 0.09f) else ScanWell)
            .border(1.dp, if (session.isOpen) accent.copy(alpha = 0.4f) else HomeBorderSubtle, shape)
            .padding(horizontal = 8.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (session.isOpen) ScanBuy else ScanBorderStrong)
            )
            Text(
                shortSessionName(session.name),
                color = if (session.isOpen) Color.White else HomeTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "${session.openTime}–${session.closeTime}",
            color = if (session.isOpen) accent else HomeTextFaint,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

/** Four tiles across a 412dp phone leaves no room for "London" spelled out. */
private fun shortSessionName(name: String): String = when (name.uppercase()) {
    "SYDNEY" -> "SYD"
    "TOKYO" -> "TKY"
    "LONDON" -> "LDN"
    "NEW YORK", "NEWYORK" -> "NY"
    else -> name.take(3).uppercase()
}

// ── 6 · Events ─────────────────────────────────────────────────────────────

@Composable
private fun EventsSection(events: List<EconomicEvent>) {
    // The calendar is mostly low-impact prints by volume and none of the reason
    // a trader opens one. Same filter the retired HOT NEWS tab applied.
    val high = remember(events) {
        events.filter { it.impact.equals("High", true) || it.impact.equals("1", true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(
            title = "EVENTS",
            subtitle = if (high.isEmpty()) {
                "High impact only"
            } else {
                "High impact only · ${high.size} on the wire today"
            }
        )

        if (high.isEmpty()) {
            // No high-impact rows to render is not the same as no calendar --
            // fall through to the live widget rather than showing an empty card.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ScanSurface)
                    .border(1.dp, HomeBorderFaint, RoundedCornerShape(20.dp))
            ) {
                HotNewsCalendarPanel(events = events)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ScanSurface)
                    .border(1.dp, HomeBorderFaint, RoundedCornerShape(20.dp))
            ) {
                high.take(6).forEachIndexed { index, event ->
                    EventRow(event, isLast = index == high.take(6).lastIndex)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(ScanWarn.copy(alpha = 0.07f))
                .border(1.dp, ScanWarn.copy(alpha = 0.28f), RoundedCornerShape(15.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                Icons.Rounded.PauseCircle,
                contentDescription = null,
                tint = ScanWarn,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "High-impact prints move the symbols above. Check the wire before you start the robot.",
                color = ScanWarnText,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EventRow(event: EconomicEvent, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text(
            eventTime(event.date),
            color = HomeTextValue,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(42.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.event,
                color = HomeTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                buildString {
                    append(event.currency.ifBlank { event.country })
                    event.estimate?.let { append(" · forecast $it") }
                    event.previous?.let { append(" · prev $it") }
                },
                color = HomeTextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ScanSell)
                )
            }
        }
    }
    if (!isLast) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HomeBorderFaint)
        )
    }
}

/** FMP hands back `2026-08-21 14:30:00`; only the clock time fits the row. */
private fun eventTime(raw: String): String {
    val parts = raw.trim().split(" ")
    val time = parts.getOrNull(1) ?: return "--:--"
    return time.split(":").take(2).joinToString(":")
}

// ── Shared bits ────────────────────────────────────────────────────────────

@Composable
private fun SectionHead(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = ScanTextBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )
            Text(
                subtitle,
                color = HomeTextDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (trailing != null) trailing()
    }
}

/**
 * What this user's broker calls each of the robot's instruments.
 *
 * ## Why a whole section exists for spelling
 *
 * NovaHost trades canonical names -- XAUUSD, NAS100 -- because that is what the
 * mentor picks and what the licence allows. Brokers do not agree, and the
 * disagreement is not cosmetic: an order naming a symbol the broker does not
 * carry is rejected outright, so a robot on a mismatched book receives every
 * signal, sends every order, and fills none of them.
 *
 * This is not hypothetical. A live Trade245 account lists gold as `Gold`, the
 * Nasdaq as `.USTECH.` and the Dow as `.US30.`. Nothing about a leading dot is
 * guessable, and every NAS100 and US30 signal on that account failed silently.
 *
 * ## Why it is a button and not a form
 *
 * The obvious design is a text box per symbol, and it is the wrong one: it asks
 * a subscriber to know something only their terminal knows, and punishes a typo
 * with a robot that quietly stops trading. MetaCopier can be asked for the
 * account's real Market Watch instead, so the normal path is one tap and no
 * typing.
 *
 * The fields stay, editable, for the account whose spelling nothing predicts.
 * They are the escape hatch, not the route.
 */
@Composable
private fun BrokerSymbolSection(
    allowance: List<String>,
    plan: SymbolPlan,
    accent: Color,
    discovering: Boolean,
    note: String?,
    onDiscover: () -> Unit,
    onBrokerSymbol: (String, String) -> Unit
) {
    if (allowance.isEmpty()) return

    val mapped = plan.symbols.count { it.brokerSymbol.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(
            title = "YOUR BROKER'S NAMES",
            subtitle = if (mapped == 0) {
                "Not matched yet — tap to read them from your account"
            } else {
                "$mapped of ${allowance.size} matched to your broker"
            },
            trailing = {
                // Pill, per the component rules. Disabled while in flight rather
                // than hidden, so the control does not move under a finger that
                // is already on its way to it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, accent.copy(alpha = if (discovering) 0.18f else 0.45f), CircleShape)
                        .clickable(enabled = !discovering, onClick = onDiscover)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = accent.copy(alpha = if (discovering) 0.4f else 1f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        if (discovering) "READING…" else "MATCH",
                        color = accent.copy(alpha = if (discovering) 0.4f else 1f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.7.sp
                    )
                }
            }
        )

        if (note != null) {
            Text(
                note,
                color = HomeTextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScanWell)
                    .padding(horizontal = 13.dp, vertical = 10.dp)
            )
        }

        allowance.forEach { sym ->
            BrokerSymbolRow(
                symbol = sym,
                brokerSymbol = plan.configFor(sym)?.brokerSymbol.orEmpty(),
                accent = accent,
                onValue = { onBrokerSymbol(sym, it) }
            )
        }
    }
}

/** One canonical symbol and the name it goes to the broker under. */
@Composable
private fun BrokerSymbolRow(
    symbol: String,
    brokerSymbol: String,
    accent: Color,
    onValue: (String) -> Unit
) {
    // Local text state so an in-progress entry is not rewritten by the plan
    // round-tripping back through recomposition.
    var text by remember(symbol) { mutableStateOf(brokerSymbol) }
    LaunchedEffect(brokerSymbol) {
        // Accepts an outside value only when it genuinely differs -- which is
        // what lets MATCH fill the field without fighting someone mid-type.
        if (brokerSymbol != text) text = brokerSymbol
    }

    val filled = text.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ScanWell)
            .border(
                1.dp,
                if (filled) accent.copy(alpha = 0.22f) else HomeBorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(
            symbol,
            color = ScanTextBright,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.6.sp,
            modifier = Modifier.width(74.dp)
        )

        Text(
            "→",
            color = HomeTextFaint,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 10.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(
                    "same as $symbol",
                    color = ScanTextTrace,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    // Only what MetaTrader permits inside a symbol name. Filtered
                    // here rather than on save so the user sees immediately that a
                    // space or slash is not going to be part of it.
                    val cleaned = raw.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
                        .take(24)
                    text = cleaned
                    onValue(cleaned)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (filled) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, HomeBorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = HomeTextFaint,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** The account-currency picker. Three, because those are the accounts we see. */
@Composable
private fun CurrencyRail(
    selected: String,
    accent: Color,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            "ACCOUNT CURRENCY",
            color = HomeTextFaint,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            com.novahost.app.sdk.FxRates.SUPPORTED.forEach { code ->
                val isOn = code == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isOn) accent.copy(alpha = 0.20f) else ScanWell)
                        .border(
                            1.dp,
                            if (isOn) accent.copy(alpha = 0.55f) else HomeBorderFaint,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable { onSelect(code) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        com.novahost.app.sdk.FxRates.symbolFor(code) + "  " + code,
                        color = if (isOn) Color.White else HomeTextDim,
                        fontSize = 11.sp,
                        fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/** "updated 12 minutes ago", for the rate the sizing used. */
private fun fxRateAge(ageMillis: Long): String {
    val minutes = ageMillis / 60_000
    return when {
        minutes < 1L -> "just updated"
        minutes < 60L -> "updated " + minutes + "m ago"
        else -> "updated " + (minutes / 60) + "h ago"
    }
}
