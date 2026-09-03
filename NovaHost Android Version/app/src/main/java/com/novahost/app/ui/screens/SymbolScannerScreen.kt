package com.novahost.app.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.novahost.app.navigation.Routes
import com.novahost.app.sdk.ForexRepository
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.ui.components.PaywallOverlay
import com.novahost.app.ui.components.SCANNER_PRICE_LABEL
import com.novahost.app.ui.components.ScannerPaywallSheet
import com.novahost.app.ui.home.onArtFloor
import com.novahost.app.ui.scanner.*
import com.novahost.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * The AI Chart Scanner, as five states over one result.
 *
 * The design presents scan input, confluence score, trade plan, context and the
 * blocked case as five artboards. They are one flow: each of the middle states
 * ends in a down-arrow affordance that advances to the next, and the blocked
 * state is the context state when a guardrail vetoes rather than a separate
 * destination. Splitting them into routes would have put the back stack between
 * a user and their own trade plan.
 *
 * What the design shows and this does not:
 *
 * - **A mock status bar and bottom tab bar.** Artboard furniture; see the note
 *   in `ScannerKit.kt`.
 * - **"14 scans left today."** There is no scan quota in this app. Printing a
 *   made-up allowance is the one kind of lie a paid product cannot afford, so
 *   that line carries the true statement instead.
 * - **An optional chart.** The design marks the dropzone optional. The only
 *   reading source this codebase has is the vision pass, which needs an image,
 *   so the scan button stays disarmed until one is attached and says why. The
 *   layout is unchanged.
 */
enum class ScanStage { INPUT, SCANNING, SCORE, PLAN, CONTEXT }

/**
 * Where a running scan actually is.
 *
 * These are the pipeline's real phases, not a loading animation's script. The
 * scanning screen used to cycle four labels on a 4.2-second loop regardless of
 * what the app was doing, which on a slow network meant it claimed to be
 * "scoring confluence" while it was still waiting on the first response -- and
 * looped back to "reading the chart" on a scan that had already finished
 * reading.
 *
 * Two of the four are network calls and two are local arithmetic that completes
 * in microseconds. That is honest: the wait really is almost all [READING], and
 * a progress display that pretends the last two steps take time is the same
 * class of lie as a confluence check that scores data it never received.
 */
enum class ScanPhase(val label: String, val detail: String) {
    PRICING("Pricing the symbol", "asking your broker where it is trading"),
    READING("Reading the chart", "the model is looking at your screenshot"),
    SIZING("Measuring the stop", "turning the entry and stop into lots"),
    SCORING("Scoring confluence", "running the five checks on this device")
}

@Composable
fun SymbolScannerScreen(
    navController: NavController,
    mainViewModel: com.novahost.app.ui.viewmodels.MainViewModel
) {
    val themeState = LocalNovaHostTheme.current
    val branding = LocalRobotBranding.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accent = themeState.primaryColor
    val glow = themeState.glow
    val isPremium by mainViewModel.isPremium.collectAsState()
    val hasScanner by mainViewModel.hasScanner.collectAsState()
    val balance by MetaAPIManager.balance.collectAsState()
    val livePrices by ForexRepository.livePrices.collectAsState()
    val calendar by ForexRepository.economicCalendar.collectAsState()
    // Whether the calendar was read at all, not whether it came back empty. The
    // event check and the event guardrail both need the difference -- see
    // ConfluenceEngine.eventCheck.
    val calendarAvailable by ForexRepository.calendarAvailable.collectAsState()

    /**
     * The robot's symbols, narrowed to the ones this subscriber actually trades.
     *
     * Three sources, most specific first:
     *
     *  1. `SymbolPlanStore.load().selected` -- the symbols ticked on Trading
     *     Symbols. Scanning one the user switched off is a dead end: the
     *     executor answers SYMBOL_DISABLED and the scan is spent.
     *  2. `robotAllowance` -- the mentor's allowance, for an install that has
     *     not opened the Trading Symbols screen yet.
     *  3. A fixed pair, only when there is no robot at all.
     *
     * It used to read `branding.allowedSymbols`, which is hydrated once in
     * MainActivity from a raw prefs string and never refreshed -- so the rail
     * showed EUR/USD, XAU/USD and GBP/JPY on installs whose licence actually
     * permits NAS100, XAUUSD, US30 and EURUSD. Three of those four are not
     * tradeable on any licence in the system, so a scan of them could only ever
     * end in a rejection.
     */
    val symbols = remember(branding.allowedSymbols) {
        val plan = com.novahost.app.sdk.SymbolPlanStore.load(context)
        val enabled = plan.selected.map { it.symbol }.filter { it.isNotBlank() }
        when {
            enabled.isNotEmpty() -> enabled
            else -> com.novahost.app.sdk.SymbolPlanStore.robotAllowance(context)
                .takeIf { it.isNotEmpty() }
                ?: branding.allowedSymbols.takeIf { it.isNotEmpty() }
                ?: listOf("XAUUSD")
        }
    }

    var stage by remember { mutableStateOf(ScanStage.INPUT) }
    var symbol by remember { mutableStateOf(symbols.first()) }
    var mode by remember { mutableStateOf(ScanMode.DAY) }
    // Remembered across scans: a trader reads one way, and re-picking it on
    // every scan would be the app forgetting who they are.
    var strategy by remember {
        mutableStateOf(
            ScanStrategy.from(
                context.getSharedPreferences("nova_appearance", android.content.Context.MODE_PRIVATE)
                    .getString("scan_strategy", null)
            )
        )
    }
    var preset by remember { mutableStateOf(AllocationPreset.Default) }
    var riskPercent by remember { mutableStateOf(1.0) }
    /**
     * The balance the plan is sized against, reported by the calculator card.
     *
     * Zero until the card first reports, which it does on composition.
     */
    var sizingBalance by remember { mutableStateOf(0.0) }
    /** Display currency and USD->currency rate, reported by the calculator. */
    var displayCurrency by remember { mutableStateOf("USD") }
    var displayRate by remember { mutableStateOf(1.0) }
    var chartUri by remember { mutableStateOf<Uri?>(null) }
    var isSample by remember { mutableStateOf(false) }
    var instrument by remember { mutableStateOf<Instrument?>(null) }
    var reading by remember { mutableStateOf<ScanReading?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var executionNote by remember { mutableStateOf<String?>(null) }
    /**
     * What the chart disagrees with the app about.
     *
     * A list rather than a field per check: both of these say "the screenshot is
     * not what you told the app it was", they are read together, and stacking a
     * separate amber panel per check would turn two useful warnings into
     * wallpaper.
     */
    var scanWarnings by remember { mutableStateOf<List<String>>(emptyList()) }

    /**
     * The user's own guardrail limits, loaded once and edited through the sheet.
     *
     * Was `GuardrailConfig()` constructed inline on every recomposition, which
     * made "your rules" and the EDIT MY RULES button both untrue.
     */
    var guardrailConfig by remember { mutableStateOf(GuardrailStore.load(context)) }
    var showGuardrailSheet by remember { mutableStateOf(false) }

    /** Losses in a row on the linked account. Null means it could not be read. */
    var lossStreak by remember { mutableStateOf<Int?>(null) }
    /** Which real phase a running scan is in. See [ScanPhase]. */
    var scanPhase by remember { mutableStateOf(ScanPhase.PRICING) }

    // The scanner is a separate once-off purchase from app access. The screen
    // stays fully visible either way -- the gate is on the scan, not on the
    // page, so the user can see exactly what they would be buying.
    var showScannerGate by remember { mutableStateOf(false) }

    val chartPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        chartUri = uri
        errorMessage = null
    }

    // The plan and everything scored off it are derived, never stored. The
    // allocation rail on the plan state rebalances live, and a stored plan would
    // have meant three copies of the same figures drifting apart on every tap.
    // What the plan is actually sized against, in the order the user would
    // expect: the figure they typed into the calculator, then the linked
    // terminal's, then the stand-in.
    //
    // The typed figure used to be dropped on the floor -- the card reported only
    // a risk percent -- so the plan sized off the terminal or off $10,000 while
    // the card above it did its arithmetic on something else entirely.
    val effectiveBalance = when {
        sizingBalance > 0.0 -> sizingBalance
        balance > 0.0 -> balance
        else -> SAMPLE_BALANCE
    }
    /** True when neither the user nor a terminal supplied a balance. */
    val balanceIsStandIn = sizingBalance <= 0.0 && balance <= 0.0

    val plan = remember(reading, instrument, preset, effectiveBalance, riskPercent, mode, displayCurrency, displayRate) {
        val r = reading
        val i = instrument
        if (r == null || i == null) null
        else TradePlanner.build(
            instrument = i,
            reading = r,
            balance = effectiveBalance,
            riskPercent = riskPercent,
            preset = preset,
            mode = mode,
            displayCurrency = displayCurrency,
            displayRate = displayRate
        )
    }
    val confluence = remember(plan, reading, instrument, mode, calendarAvailable) {
        val r = reading
        val i = instrument
        val p = plan
        if (r == null || i == null || p == null) null
        else ConfluenceEngine.score(i, r, p, mode, calendarAvailable)
    }
    // Re-evaluated when the rules change, so saving in the sheet re-scores the
    // plan behind it immediately -- including flipping it to BLOCKED.
    val guardrails = remember(plan, reading, calendarAvailable, guardrailConfig, lossStreak) {
        val r = reading
        val p = plan
        if (r == null || p == null) null
        else Guardrails.evaluate(
            plan = p,
            reading = r,
            config = guardrailConfig,
            // Read from the broker's closed trades by `broker-history`, and null
            // when that could not be established. Null warns; it does not pass.
            consecutiveLosses = lossStreak,
            calendarAvailable = calendarAvailable
        )
    }

    fun resetToInput() {
        stage = ScanStage.INPUT
        reading = null
        instrument = null
        isSample = false
        errorMessage = null
        scanWarnings = emptyList()
    }

    fun runSample() {
        instrument = SampleScan.instrument
        reading = SampleScan.reading
        isSample = true
        stage = ScanStage.SCORE
    }

    fun runScan() {
        if (!hasScanner) {
            showScannerGate = true
            return
        }
        val uri = chartUri ?: return
        stage = ScanStage.SCANNING
        scanPhase = ScanPhase.PRICING
        errorMessage = null
        scope.launch {
            val encoded = encodeChart(context, uri)
            if (encoded == null) {
                errorMessage = "That image could not be read. Try another screenshot."
                stage = ScanStage.INPUT
                return@launch
            }
            // Asked before the chart is read, and allowed to come back null.
            // A quote improves the instrument; it is not a precondition for
            // reading a chart, so a disconnected broker costs precision rather
            // than the whole scan.
            val quote = ScanSource.brokerQuote(context, symbol)

            // Same broker, same phase, so it rides along with the quote rather
            // than adding a round trip the user waits through.
            lossStreak = ScanSource.lossStreak(context)

            // Broker answered (or did not). Either way the pricing phase is over.
            scanPhase = ScanPhase.READING

            val built = ScanSource.buildInstrument(
                symbol = symbol,
                livePrice = livePrices[ScanSource.feedKey(symbol)],
                broker = branding.productCode,
                session = ForexRepository.marketSessions.value
                    .filter { it.isOpen }
                    .joinToString(" + ") { it.name }
                    .ifBlank { "Closed" },
                sessionOpen = ForexRepository.marketSessions.value.any { it.isOpen },
                spreadPips = null,
                atrPips = null,
                quote = quote
            )
            ScanSource.analyzeChart(
                imageBase64 = encoded,
                pair = symbol,
                mode = mode,
                strategy = strategy,
                email = mainViewModel.userEmail.value.ifEmpty {
                    com.novahost.app.sdk.Entitlements.savedEmail(context)
                },
                deviceId = com.novahost.app.sdk.DeviceSecurityHelper.getDeviceId(context)
            )
                .onSuccess { verdict ->
                    // The two remaining phases are local and finish in microseconds.
                    // Reported anyway so the list is a record of what ran, not a
                    // countdown invented to fill the wait.
                    scanPhase = ScanPhase.SIZING
                    val events = ScanSource.buildEvents(symbol, calendar)
                    // The ATR arrives with the reading, not before it, so the
                    // instrument is completed here rather than at build time.
                    val priced = ScanSource.withMeasuredAtr(built, verdict)
                    instrument = priced
                    reading = ScanSource.toReading(verdict, priced, events)
                    // Surfaced, not blocked. Everything downstream keys off the
                    // SELECTED symbol and the SELECTED mode -- the scan cannot
                    // adopt what the chart says -- so a disagreement has to be
                    // shown rather than silently resolved either way.
                    scanWarnings = listOfNotNull(
                        ScanSource.symbolMismatch(verdict, symbol)?.let { onChart ->
                            "This chart is labelled " + onChart + ", but " + symbol +
                                " is selected. The plan below sizes and sends " + symbol +
                                ". Go back and pick the right pair."
                        },
                        ScanSource.timeframeMismatch(verdict, mode)?.let { tf ->
                            "This is a " + tf + " chart scanned in " + mode.label +
                                " mode, which reads " + mode.timeframes +
                                ". The stop and the volatility score are measured against the " +
                                "wrong horizon."
                        }
                    )
                    scanPhase = ScanPhase.SCORING
                    isSample = false
                    stage = ScanStage.SCORE
                }
                .onFailure { failure ->
                    errorMessage = failure.message ?: "The scan did not complete."
                    // A locked scanner is a purchase prompt, not an error line.
                    if ((failure as? ScanRefused)?.code == "SCANNER_LOCKED") {
                        showScannerGate = true
                    }
                    stage = ScanStage.INPUT
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScannerBackground(
            artUrl = branding.avatarUrl,
            accent = accent,
            glow = glow,
            // Art behind the input and the score; a flat ground under the ladder
            // and the rule lists, which are dense enough already.
            showArt = stage == ScanStage.INPUT || stage == ScanStage.SCANNING || stage == ScanStage.SCORE
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = ScannerTopInset)
                .then(if (!isPremium) Modifier.blur(15.dp) else Modifier)
        ) {
            when (stage) {
                ScanStage.INPUT -> ScanInputState(
                    accent = accent,
                    glow = glow,
                    symbols = symbols,
                    symbol = symbol,
                    onSymbol = { symbol = it },
                    mode = mode,
                    onMode = { mode = it },
                    strategy = strategy,
                    onStrategy = {
                        strategy = it
                        context.getSharedPreferences("nova_appearance", android.content.Context.MODE_PRIVATE)
                            .edit().putString("scan_strategy", it.name).apply()
                    },
                    balance = balance,
                    onRisk = { riskPercent = it },
                    onBalance = { sizingBalance = it },
                    onCurrency = { code, rate -> displayCurrency = code; displayRate = rate },
                    scannerUnlocked = hasScanner,
                    chartAttached = chartUri != null,
                    chartUri = chartUri,
                    onAttach = { chartPicker.launch("image/*") },
                    robotName = branding.name.ifBlank { "NO ROBOT" },
                    robotAvatar = branding.avatarUrl,
                    livePrice = livePrices[ScanSource.feedKey(symbol)],
                    errorMessage = errorMessage,
                    onScan = ::runScan,
                    onPreview = ::runSample,
                    onBack = { navController.popBackStack() },
                    onAddKeys = { navController.navigate(Routes.ACTIVATE) }
                )

                ScanStage.SCANNING -> ScanRunningState(
                    accent = accent,
                    chartUri = chartUri,
                    symbol = symbol,
                    strategy = strategy,
                    phase = scanPhase
                )

                ScanStage.SCORE -> {
                    val c = confluence
                    val r = reading
                    val i = instrument
                    if (c != null && r != null && i != null) {
                        ScanScoreState(
                            instrument = i,
                            reading = r,
                            confluence = c,
                            mode = mode,
                            glow = glow,
                            isSample = isSample,
                            warnings = scanWarnings,
                            onBack = ::resetToInput,
                            onAdvance = { stage = ScanStage.PLAN }
                        )
                    }
                }

                ScanStage.PLAN -> {
                    val p = plan
                    val c = confluence
                    if (p != null && c != null) {
                        ScanPlanState(
                            plan = p,
                            confluence = c,
                            accent = accent,
                            preset = preset,
                            balanceIsStandIn = balanceIsStandIn,
                            onPreset = { preset = it },
                            onBack = { stage = ScanStage.SCORE },
                            onAdvance = { stage = ScanStage.CONTEXT }
                        )
                    }
                }

                ScanStage.CONTEXT -> {
                    val p = plan
                    val r = reading
                    val c = confluence
                    val g = guardrails
                    if (p != null && r != null && c != null && g != null) {
                        if (g.blocked) {
                            ScanBlockedState(
                                plan = p,
                                confluence = c,
                                report = g,
                                accent = accent,
                                glow = glow,
                                onBack = { stage = ScanStage.PLAN },
                                onRetune = { stage = ScanStage.PLAN }
                            )
                        } else {
                            ScanContextState(
                                plan = p,
                                reading = r,
                                confluence = c,
                                report = g,
                                accent = accent,
                                isSample = isSample,
                                executionNote = executionNote,
                                calendarAvailable = calendarAvailable,
                                onBack = { stage = ScanStage.PLAN },
                                onEditRules = { showGuardrailSheet = true },
                                onExecute = { showConfirm = true }
                            )
                        }
                    }
                }
            }
        }

        // Opened from EDIT MY RULES on the guardrail panel and from the tune
        // icon in the Context header -- both of which were decoration until now.
        if (showGuardrailSheet) {
            GuardrailSheet(
                config = guardrailConfig,
                accent = accent,
                onDismiss = { showGuardrailSheet = false },
                onSave = { updated ->
                    guardrailConfig = updated
                    GuardrailStore.save(context, updated)
                    showGuardrailSheet = false
                }
            )
        }

        if (showConfirm && plan != null) {
            ExecuteReviewSheet(
                plan = plan,
                accent = accent,
                onDismiss = { showConfirm = false },
                onConfirm = {
                    showConfirm = false
                    val confirmed = plan
                    scope.launch {
                        executionNote = "Sending..."
                        val results = confirmed.legs.map { leg ->
                            MetaAPIManager.executeTrade(
                                context = context,
                                pair = confirmed.instrument.symbol.replace("/", ""),
                                side = confirmed.direction.label,
                                volume = leg.lots,
                                sl = confirmed.stop,
                                tp = leg.price,
                                // Market, or a pending order waiting at the
                                // entry. Without these the executor sends
                                // openPrice 0 -- MetaCopier's market flag -- and
                                // a retest entry fills wherever price happens to
                                // be, with a stop and targets built around a
                                // level that was never traded.
                                orderType = confirmed.entryType.name,
                                openPrice = confirmed.openPriceForBroker,
                                // The broker cancels it if price never comes back,
                                // so a stale setup cannot fire days later.
                                pendingExpirySeconds = confirmed.pendingExpirySeconds
                            )
                        }
                        val failed = results.count { it.isFailure }
                        executionNote = when {
                            failed == 0 -> "All " + results.size + " orders accepted."
                            failed == results.size ->
                                "No orders were accepted. " +
                                    (results.first().exceptionOrNull()?.message ?: "")
                            // Partial fills are the case worth naming loudly:
                            // the user now has a live position that does not
                            // match the plan they approved.
                            else -> (results.size - failed).toString() + " of " + results.size +
                                " orders were accepted. Check your terminal before adding more."
                        }
                    }
                }
            )
        }

        // The app lock outranks the scanner upsell: there is no point selling a
        // R349 add-on to someone who cannot open the app it lives in.
        if (!isPremium) {
            val appGate by mainViewModel.appGate.collectAsState()
            val savedEmail by mainViewModel.userEmail.collectAsState()

            PaywallOverlay(
                gate = appGate,
                initialEmail = savedEmail,
                onCheckAccess = { email -> mainViewModel.checkAppAccess(email) },
                onBuy = { email -> mainViewModel.buyAppAccess(email) },
                onSubmitMoveCode = { code -> mainViewModel.submitMoveCode(code) },
                onCancelMove = { mainViewModel.cancelDeviceMove() },
                onGranted = {
                    mainViewModel.unlockApp()
                    navController.navigate(Routes.HOME) { popUpTo(0) }
                },
                onCheckoutOpened = { mainViewModel.resetAppGate() }
            )
        } else if (showScannerGate) {
            val scannerGate by mainViewModel.scannerGate.collectAsState()

            ScannerPaywallSheet(
                gate = scannerGate,
                deviceLabel = rememberDeviceLabel(),
                onDismiss = {
                    showScannerGate = false
                    mainViewModel.resetScannerGate()
                },
                onUnlock = { mainViewModel.buyScanner() },
                onRestore = { mainViewModel.checkScannerAccess() },
                onGranted = {
                    showScannerGate = false
                    mainViewModel.resetScannerGate()
                },
                onCheckoutOpened = { mainViewModel.resetScannerGate() }
            )
        }
    }
}

/**
 * The short handset id shown on the scanner sheet's binding pill.
 *
 * The full Android id is 16 hex characters and means nothing to anyone; the
 * design shows a stub, which is enough for a user to tell two of their own
 * devices apart when contacting support.
 */
@Composable
private fun rememberDeviceLabel(): String {
    val context = LocalContext.current
    return remember {
        val id = com.novahost.app.sdk.DeviceSecurityHelper.getDeviceId(context)
        "DEVICE " + id.takeLast(6).uppercase()
    }
}

/** Stands in for a real account balance so the plan has something to size against. */
private const val SAMPLE_BALANCE = 10_000.0

// ── 01 · Scan input ────────────────────────────────────────────────────────

@Composable
private fun ScanInputState(
    accent: Color,
    glow: NovaGlow,
    symbols: List<String>,
    symbol: String,
    onSymbol: (String) -> Unit,
    mode: ScanMode,
    onMode: (ScanMode) -> Unit,
    strategy: ScanStrategy,
    onStrategy: (ScanStrategy) -> Unit,
    balance: Double,
    onRisk: (Double) -> Unit,
    onBalance: (Double) -> Unit,
    onCurrency: (String, Double) -> Unit,
    scannerUnlocked: Boolean,
    chartAttached: Boolean,
    /** The picked screenshot, so the card can show it rather than describe it. */
    chartUri: Uri?,
    onAttach: () -> Unit,
    robotName: String,
    robotAvatar: String?,
    livePrice: Double?,
    errorMessage: String?,
    onScan: () -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
    onAddKeys: () -> Unit
) {

    Column(modifier = Modifier.fillMaxSize()) {
        ScannerHeader(
            title = "AI Chart Scanner",
            subtitle = "Confluence engine · runs on device",
            onBack = onBack,
            trailing = {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "Scan history",
                    tint = HomeTextDim,
                    modifier = Modifier.size(22.dp)
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScannerGutter)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                symbols.forEach { candidate ->
                    SymbolChip(
                        label = candidate,
                        selected = candidate == symbol,
                        accent = accent,
                        onClick = { onSymbol(candidate) }
                    )
                }
            }

            QuoteCard(symbol = symbol, livePrice = livePrice)

            Column {
                ScanSectionLabel("SCAN MODE")
                Spacer(Modifier.height(8.dp))
                SegmentedRail(
                    options = ScanMode.entries.toList(),
                    selected = mode,
                    accent = accent,
                    onSelect = onMode,
                    label = { it.label },
                    caption = { it.timeframes }
                )
            }

            // How to read the chart, as opposed to how long to hold it. The old
            // endpoint hardcoded an SMC prompt, so every scan was an SMC read
            // whether or not that is how the user trades.
            Column {
                ScanSectionLabel("STRATEGY")
                Spacer(Modifier.height(8.dp))
                SegmentedGrid(
                    options = ScanStrategy.entries.toList(),
                    selected = strategy,
                    accent = accent,
                    onSelect = onStrategy,
                    label = { it.label },
                    caption = { it.caption }
                )
            }

            // Balance, risk and trade count in one place. This replaced a pair
            // of read-only tiles whose risk figure could only be cycled through
            // 0.5 / 1.0 / 1.5 / 2.0 by tapping it.
            TradeCalculatorCard(
                accent = accent,
                terminalBalance = balance,
                onPerTradeRiskPercent = onRisk,
                onBalance = onBalance,
                onCurrency = onCurrency
            )

            // Attached: the screenshot itself, at a size you can actually read.
            // A tick and the words "Chart attached" asked the user to take it on
            // faith that the right image went up -- and the one mistake this
            // screen cannot recover from is scanning yesterday's screenshot,
            // which looks identical to scanning today's until the plan is wrong.
            if (chartUri != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(ScanSurfaceGlass)
                        .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                        .clickable(onClick = onAttach)
                ) {
                    AsyncImage(
                        model = chartUri,
                        contentDescription = "The chart you attached",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .background(ScanWell)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = accent.onArtFloor(),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Chart attached",
                            color = HomeTextValue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "TAP TO SWAP",
                            color = HomeTextFaint,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else {
                DashedCard(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAttach),
                    background = ScanSurfaceGlass,
                    borderColor = ScanBorderStrong
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ScanWell),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            tint = HomeTextDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Attach your chart",
                            color = HomeTextValue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Your drawings and levels get folded into the score.",
                            color = HomeTextFaint,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            if (errorMessage != null) {
                ScanNote(
                    icon = Icons.Rounded.GppMaybe,
                    text = errorMessage,
                    tint = ScanSell,
                    background = ScanSell.copy(alpha = 0.05f),
                    borderColor = ScanSell.copy(alpha = 0.32f),
                    textColor = ScanSellText
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ScanSectionLabel(
                    "SCANNING ROBOT",
                    trailing = {
                        Text("reads the chart for you", color = ScanTextTrace, fontSize = 9.sp)
                    }
                )

                RobotRail(
                    accent = accent,
                    robotName = robotName,
                    robotAvatar = robotAvatar,
                    onAddKeys = onAddKeys
                )

                // Locked, the button stays live and opens the paygate: a dead
                // control teaches the user nothing about why it is dead.
                ScanPrimaryCta(
                    label = if (scannerUnlocked) {
                        "SCAN WITH " + robotName.uppercase()
                    } else {
                        "UNLOCK AI VISION — " + SCANNER_PRICE_LABEL
                    },
                    icon = if (scannerUnlocked) Icons.Rounded.Radar else Icons.Rounded.Lock,
                    fill = accent,
                    enabled = scannerUnlocked.not() || chartAttached,
                    glow = glow,
                    height = 54.dp,
                    onClick = onScan
                )

                ScanFootnote(
                    when {
                        !scannerUnlocked -> "The AI chart scanner is a one-time unlock, separate from app access."
                        chartAttached -> "Scored on this device. Only the screenshot leaves it."
                        else -> "Attach a chart screenshot to run a scan."
                    }
                )

                // The preview is how the five states get reviewed without a
                // chart or a live account. It is labelled everywhere it leads.
                Text(
                    "Preview the flow with sample data",
                    color = accent.onArtFloor(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPreview)
                        .padding(vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun QuoteCard(symbol: String, livePrice: Double?) {
    ScanCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        background = ScanSurfaceGlass,
        borderColor = HomeBorderSubtle,
        contentPadding = 16.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    symbol,
                    color = ScanTextBright,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (livePrice != null) "Streaming" else "Waiting for a price",
                    color = HomeTextDim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                livePrice?.let { String.format("%.5f", it) } ?: "--",
                color = if (livePrice != null) Color.White else HomeTextFaint,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Spread and ATR are not on the trade stream this app subscribes to.
            // Shown as unavailable rather than filled with a plausible number:
            // the volatility check scores off ATR, so a decorative figure here
            // would become twenty decorative points two screens later.
            ScanWellTile(label = "SPREAD", value = "--", modifier = Modifier.weight(1f))
            ScanWellTile(label = "ATR", value = "--", modifier = Modifier.weight(1f))
            ScanWellTile(
                label = "SESSION",
                modifier = Modifier.weight(1.5f),
                valueContent = {
                    val sessions by ForexRepository.marketSessions.collectAsState()
                    val open = sessions.filter { it.isOpen }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (open.isNotEmpty()) ScanBuy else HomeTextFaint)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (open.isEmpty()) "Closed" else open.joinToString(" + ") { it.name },
                            color = HomeTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun RobotRail(
    accent: Color,
    robotName: String,
    robotAvatar: String?,
    onAddKeys: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, accent.copy(alpha = 0.52f), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box {
                coil.compose.AsyncImage(
                    model = robotAvatar,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    placeholder = androidx.compose.ui.res.painterResource(com.novahost.app.R.drawable.new_avatar),
                    error = androidx.compose.ui.res.painterResource(com.novahost.app.R.drawable.new_avatar),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, accent, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(ScanBuy)
                        .border(2.dp, ScanCanvas, CircleShape)
                )
            }
            Text(
                robotName.uppercase(),
                color = ScanTextBright,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                "SELECTED",
                color = accent.onArtFloor(),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(ScanSurfaceGlass)
                .border(1.dp, ScanBorderStrong, RoundedCornerShape(16.dp))
                .clickable(onClick = onAddKeys)
                .padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ScanWell)
                    .border(1.dp, HomeBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = HomeTextDim,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                "ADD KEYS",
                color = HomeTextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "LICENCE",
                color = ScanTextTrace,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }
}

// ── Scanning ───────────────────────────────────────────────────────────────

/**
 * The wait.
 *
 * Not in the design, which has no loading artboard, but the vision round trip
 * is measured in seconds and a screen that simply freezes on the input state
 * reads as a dropped tap. The checklist is the one the previous scanner used,
 * kept because it sets the right expectation about what is happening.
 */
@Composable
private fun ScanRunningState(
    accent: Color,
    chartUri: Uri?,
    symbol: String,
    strategy: ScanStrategy,
    phase: ScanPhase
) {
    val steps = ScanPhase.entries.toList()
    val transition = rememberInfiniteTransition(label = "scan")

    // One pass of the reticle down the chart, and the step list is derived from
    // the same clock -- so the words under the chart describe the band the user
    // is watching rather than running on a timer of their own.
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep"
    )
    // A slow pulse on the phase that is actually running. It carries "still
    // working" without implying progress the app cannot measure -- the previous
    // build animated a fake position through the list, which on a slow network
    // reached "scoring confluence" while the first request was still in flight.
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val ink = accent.onArtFloor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScannerGutter)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                "SCANNING " + symbol.uppercase() + " · " + strategy.label.uppercase(),
                color = ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        // The chart being scanned, with the reticle drawn over it. Showing the
        // real screenshot is the point: a spinner over a blank screen asks the
        // user to believe work is happening, and this lets them watch it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ScanWell)
                .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(20.dp))
        ) {
            if (chartUri != null) {
                AsyncImage(
                    model = chartUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // A scrim so the overlay reads against a bright chart, lifted
                // where the band is passing.
                drawRect(color = Color.Black.copy(alpha = 0.34f))

                // Analysis grid. Faint -- it frames the chart, it does not
                // compete with the candles underneath.
                val cells = 6
                repeat(cells - 1) { i ->
                    val f = (i + 1f) / cells
                    drawLine(
                        color = accent.copy(alpha = 0.10f),
                        start = Offset(w * f, 0f),
                        end = Offset(w * f, h),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = accent.copy(alpha = 0.10f),
                        start = Offset(0f, h * f),
                        end = Offset(w, h * f),
                        strokeWidth = 1f
                    )
                }

                // The sweep: a soft band with a hot line at its leading edge.
                val y = h * sweep
                val band = h * 0.18f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.28f),
                            accent.copy(alpha = 0.06f)
                        ),
                        startY = y - band,
                        endY = y
                    ),
                    topLeft = Offset(0f, (y - band).coerceAtLeast(0f)),
                    size = Size(w, band.coerceAtMost(y).coerceAtLeast(0f))
                )
                drawLine(
                    color = ink,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 2.5f
                )

                // Corner brackets. The camera-focus idiom, which is what makes
                // the frame read as "being examined" rather than "being shown".
                val arm = 26f
                val inset = 12f
                val bracket = accent.copy(alpha = 0.85f)
                listOf(
                    Triple(Offset(inset, inset), Offset(inset + arm, inset), Offset(inset, inset + arm)),
                    Triple(Offset(w - inset, inset), Offset(w - inset - arm, inset), Offset(w - inset, inset + arm)),
                    Triple(Offset(inset, h - inset), Offset(inset + arm, h - inset), Offset(inset, h - inset - arm)),
                    Triple(Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), Offset(w - inset, h - inset - arm))
                ).forEach { (corner, horizontal, vertical) ->
                    drawLine(bracket, corner, horizontal, strokeWidth = 3f)
                    drawLine(bracket, corner, vertical, strokeWidth = 3f)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        steps.forEach { step ->
            val done = step.ordinal < phase.ordinal
            val active = step == phase
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> ink.copy(alpha = pulse)
                                done -> ink
                                else -> HomeBorderStrong
                            }
                        )
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        step.label,
                        color = when {
                            active -> ScanTextBright
                            done -> HomeTextPrimary
                            else -> HomeTextFaint
                        },
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
                    )
                    // Only the running step explains itself. Four permanent
                    // subtitles would be a wall of text on a screen the user is
                    // waiting through.
                    if (active) {
                        Text(
                            step.detail,
                            color = HomeTextFaint,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                if (done) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = ink.copy(alpha = 0.65f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── 02 · Confluence score ──────────────────────────────────────────────────

@Composable
private fun ScanScoreState(
    instrument: Instrument,
    reading: ScanReading,
    confluence: ConfluenceResult,
    mode: ScanMode,
    glow: NovaGlow,
    isSample: Boolean,
    warnings: List<String>,
    onBack: () -> Unit,
    onAdvance: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScannerHeader(
            title = instrument.symbol,
            onBack = onBack,
            divider = true,
            inlineDetail = {
                Text(
                    instrument.formatPrice(instrument.price),
                    color = ScanBuy,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(mode.label, color = HomeTextFaint, fontSize = 10.sp)
            },
            trailing = {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ScanSurfaceRaised)
                        .border(1.dp, HomeBorderSubtle, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Refresh, null, tint = HomeTextDim, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("now", color = HomeTextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScannerGutter)
        ) {
            Spacer(Modifier.height(18.dp))

            if (isSample) SampleBadge(Modifier.padding(bottom = 14.dp))

            // Above the score, because these qualify everything under them: the
            // plan sizes and sends the SELECTED symbol on the SELECTED horizon
            // whatever the chart turned out to be.
            //
            // One panel however many warnings there are. Two stacked amber
            // blocks read as decoration; one block with two lines reads as a
            // list of things to fix.
            if (warnings.isNotEmpty()) {
                ScanNote(
                    icon = Icons.Rounded.GppMaybe,
                    text = warnings.joinToString("\n\n"),
                    tint = ScanWarn,
                    background = ScanWarn.copy(alpha = 0.06f),
                    borderColor = ScanWarn.copy(alpha = 0.32f),
                    textColor = ScanWarnText
                )
                Spacer(Modifier.height(14.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScoreRing(
                    score = confluence.score,
                    conviction = confluence.conviction,
                    glow = glow
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            confluence.score.toString(),
                            color = Color.White,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "/100",
                            color = HomeTextFaint,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        "CONFLUENCE",
                        color = HomeTextDim,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DirectionPill(reading.direction)
                    ConvictionChip(confluence.conviction)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    reading.narrative,
                    color = ScanTextSoft,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))
            ScanSectionLabel(
                "SCORE BREAKDOWN",
                trailing = {
                    Text(
                        confluence.score.toString() + " of 100 pts",
                        color = ScanTextTrace,
                        fontSize = 9.sp
                    )
                }
            )

            Spacer(Modifier.height(10.dp))
            confluence.checks.forEach { check ->
                ConfluenceRow(check)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(6.dp))
            ScanNote(
                icon = Icons.Rounded.PhonelinkLock,
                text = "Scored on your device. Nothing about your account leaves the phone.",
                tint = ScanTextTrace,
                textColor = ScanTextTrace
            )
            Spacer(Modifier.height(20.dp))
        }

        Column(
            modifier = Modifier
                .padding(horizontal = ScannerGutter)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            ScanAdvanceCta(
                label = "SEE TRADE PLAN",
                icon = Icons.Rounded.ArrowDownward,
                onClick = onAdvance
            )
        }
    }
}

@Composable
private fun SampleBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScanWarn.copy(alpha = 0.08f))
            .border(1.dp, ScanWarn.copy(alpha = 0.34f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = ScanWarn, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            "Sample data — not a live read. Execution stays locked.",
            color = ScanWarnText,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

// ── 03 · Multi-TP splitter ─────────────────────────────────────────────────

@Composable
private fun ScanPlanState(
    plan: TradePlan,
    confluence: ConfluenceResult,
    accent: Color,
    preset: AllocationPreset,
    balanceIsStandIn: Boolean,
    onPreset: (AllocationPreset) -> Unit,
    onBack: () -> Unit,
    onAdvance: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScannerHeader(
            title = "Trade Plan",
            onBack = onBack,
            divider = true,
            inlineDetail = {
                Text(
                    plan.instrument.symbol + " · " + plan.direction.label + " · " + confluence.score,
                    color = HomeTextFaint,
                    fontSize = 10.sp
                )
            },
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ScanBuy.copy(alpha = 0.14f))
                        .border(1.dp, ScanBuy.copy(alpha = 0.34f), CircleShape)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        plan.legs.size.toString() + " ORDERS",
                        color = ScanBuy,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScannerGutter)
        ) {
            Spacer(Modifier.height(16.dp))

            // Every figure below this line is a fraction of the balance. When
            // that balance is a placeholder, so are the lots and so is the P/L,
            // and the screen has to say which -- an unlabelled $10,000 plan
            // reads as the user's own account.
            if (balanceIsStandIn) {
                ScanNote(
                    icon = Icons.Rounded.GppMaybe,
                    text = "Sized against a stand-in $" +
                        String.format("%,.0f", SAMPLE_BALANCE) +
                        " balance. Link a terminal, or type your balance on the scan screen, " +
                        "and these lots will be yours.",
                    tint = ScanWarn,
                    background = ScanWarn.copy(alpha = 0.06f),
                    borderColor = ScanWarn.copy(alpha = 0.30f),
                    textColor = ScanWarnText
                )
                Spacer(Modifier.height(14.dp))
            }

            PriceLadder(plan)

            Spacer(Modifier.height(16.dp))
            ScanSectionLabel(
                "ALLOCATION",
                trailing = { Text("tap to rebalance", color = ScanTextTrace, fontSize = 9.sp) }
            )

            Spacer(Modifier.height(9.dp))
            SegmentedRail(
                options = AllocationPreset.entries.toList(),
                selected = preset,
                accent = accent,
                onSelect = onPreset,
                label = { it.label },
                caption = { it.caption }
            )

            Spacer(Modifier.height(11.dp))
            AllocationBar(plan)

            Spacer(Modifier.height(16.dp))
            PlanTable(plan)

            Spacer(Modifier.height(14.dp))
            ScanNote(
                icon = Icons.AutoMirrored.Rounded.CallSplit,
                text = "Split on device, then sent one order per target. Each leg carries the same stop.",
                tint = accent.onArtFloor(),
                background = accent.copy(alpha = 0.05f),
                borderColor = HomeBorderFaint
            )
            Spacer(Modifier.height(20.dp))
        }

        Column(
            modifier = Modifier
                .padding(horizontal = ScannerGutter)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            ScanAdvanceCta(
                label = "CHECK GUARDRAILS",
                icon = Icons.Rounded.ArrowDownward,
                onClick = onAdvance
            )
        }
    }
}

@Composable
private fun PlanTable(plan: TradePlan) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ScanSurface)
            .border(1.dp, HomeBorderFaint, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScanSurfaceSunken)
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            TableCell("LEVEL", 1.1f, header = true)
            TableCell("R:R", 0.8f, header = true)
            TableCell("ALLOC", 0.9f, header = true)
            TableCell("LOTS", 0.9f, header = true, end = true)
            TableCell("EST P/L", 1.2f, header = true, end = true)
        }
        plan.legs.forEachIndexed { index, leg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1.1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(LadderTints[index])
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(leg.name, color = HomeTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TableCell("1:" + trimmed(leg.rMultiple), 0.8f)
                TableCell((leg.allocation * 100).toInt().toString() + "%", 0.9f)
                TableCell(String.format("%.2f", leg.lots), 0.9f, end = true)
                TableCell(
                    "+" + plan.money(leg.estimatedPl),
                    1.2f,
                    end = true,
                    tint = ScanBuy
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScanSurfaceSunken)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Risk " + plan.money(plan.actualRisk) +
                    " · Reward " + plan.money(plan.totalReward),
                color = HomeTextDim,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Blended " + plan.rewardLabel,
                color = HomeTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
    text: String,
    weight: Float,
    header: Boolean = false,
    end: Boolean = false,
    tint: Color? = null
) {
    Text(
        text = text,
        color = tint ?: if (header) HomeTextFaint else HomeTextPrimary,
        fontSize = if (header) 8.sp else 11.sp,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        fontFamily = if (header) FontFamily.Default else FontFamily.Monospace,
        letterSpacing = if (header) 1.sp else 0.sp,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = Modifier.weight(weight)
    )
}

// ── 04 · Context + execute ─────────────────────────────────────────────────

@Composable
private fun ScanContextState(
    plan: TradePlan,
    reading: ScanReading,
    confluence: ConfluenceResult,
    report: GuardrailReport,
    accent: Color,
    isSample: Boolean,
    executionNote: String?,
    /** Whether the economic feed answered. See ConfluenceEngine.eventCheck. */
    calendarAvailable: Boolean,
    onBack: () -> Unit,
    onEditRules: () -> Unit,
    onExecute: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScannerHeader(
            title = "Context",
            onBack = onBack,
            divider = true,
            inlineDetail = {
                Text(
                    plan.instrument.symbol + " · " + plan.direction.label + " · " + confluence.score,
                    color = HomeTextFaint,
                    fontSize = 10.sp
                )
            },
            trailing = {
                // The other half of the same feature as EDIT MY RULES below.
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = "Edit my rules",
                    tint = HomeTextDim,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onEditRules)
                        .padding(4.dp)
                        .size(20.dp)
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScannerGutter)
        ) {
            Spacer(Modifier.height(14.dp))
            ScanSectionLabel("TIMEFRAME ALIGNMENT")
            Spacer(Modifier.height(9.dp))

            // A grid when the reading carries several timeframes, a single card
            // when it carries one. Padding one read out into a 2x2 of blanks
            // would look like analysis that did not happen.
            reading.timeframes.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { read ->
                        TimeframeCard(read, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(10.dp))
            ScanSectionLabel(
                "EVENT RADAR",
                trailing = {
                    val high = reading.events.count { it.highImpact }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (high > 0) ScanWarn.copy(alpha = 0.12f) else ScanBuy.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (high > 0) high.toString() + " HIGH" else "CLEAR",
                            color = if (high > 0) ScanWarn else ScanBuy,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            )

            Spacer(Modifier.height(9.dp))
            if (reading.events.isEmpty()) {
                ScanCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        // An empty radar and an unread calendar are different
                        // facts, and this card used to state the first while the
                        // guardrail below it correctly reported the second --
                        // two panels on one screen contradicting each other.
                        if (calendarAvailable) {
                            "No calendar events for these currencies today."
                        } else {
                            "The economic calendar could not be read. Check it yourself before sending."
                        },
                        color = HomeTextDim,
                        fontSize = 11.sp
                    )
                }
            } else {
                reading.events.forEach { event ->
                    EventCard(event)
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            GuardrailPanel(report, onEditRules = onEditRules)

            if (executionNote != null) {
                Spacer(Modifier.height(12.dp))
                ScanNote(
                    icon = Icons.Rounded.Bolt,
                    text = executionNote,
                    tint = accent.onArtFloor(),
                    background = accent.copy(alpha = 0.05f),
                    borderColor = HomeBorderFaint,
                    textColor = HomeTextValue
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        Column(
            modifier = Modifier
                .padding(horizontal = ScannerGutter)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            ScanPrimaryCta(
                label = if (isSample) "PREVIEW ONLY" else
                    "EXECUTE " + plan.legs.size + " ORDERS · " + String.format("%.2f", plan.totalLots),
                icon = if (isSample) Icons.Rounded.Lock else Icons.Rounded.Bolt,
                fill = ScanBuy,
                enabled = !isSample,
                onClick = onExecute
            )
            Spacer(Modifier.height(9.dp))
            ScanFootnote(
                if (isSample) {
                    "Sample plans never reach a broker."
                } else {
                    "Review sheet before anything reaches your broker"
                }
            )
        }
    }
}

@Composable
private fun EventCard(event: MarketEvent) {
    val high = event.highImpact
    ScanCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        background = if (high) ScanWarn.copy(alpha = 0.05f) else ScanSurface,
        borderColor = if (high) ScanWarn.copy(alpha = 0.30f) else HomeBorderFaint
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (high) ScanWarn else HomeBorderStrong)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    event.currency,
                    color = if (high) ScanWarnInk else HomeTextValue,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                event.title,
                color = HomeTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                humanDuration(event.minutesAway),
                color = if (high) ScanWarn else HomeTextDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (event.forecast != null || event.prior != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                event.forecast?.let { EventFigure("FORECAST", it) }
                event.prior?.let { EventFigure("PRIOR", it) }
            }
        }

        if (event.advice.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            ScanNote(
                icon = Icons.Rounded.AutoAwesome,
                text = event.advice,
                tint = if (high) ScanWarn else HomeTextDim,
                textColor = if (high) ScanWarnText else HomeTextDim
            )
        }
    }
}

@Composable
private fun EventFigure(label: String, value: String) {
    Column {
        Text(label, color = HomeTextFaint, fontSize = 8.sp, letterSpacing = 0.8.sp)
        Text(
            value,
            color = HomeTextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun GuardrailPanel(report: GuardrailReport, onEditRules: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val clear = report.allClear
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (clear) ScanBuy.copy(alpha = 0.05f) else ScanWarn.copy(alpha = 0.05f))
            .border(1.dp, (if (clear) ScanBuy else ScanWarn).copy(alpha = 0.30f), shape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Shield,
                null,
                tint = if (clear) ScanBuy else ScanWarn,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (clear) "Guardrails passed" else "Guardrails passed with warnings",
                    color = HomeTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    report.passes.size.toString() + " of " + report.outcomes.size +
                        " rules · checked on device",
                    color = if (clear) ScanBuyMeta else ScanWarnText,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp)
                .padding(bottom = 10.dp)
        ) {
            report.outcomes.forEach { outcome -> GuardrailRow(outcome) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, (if (clear) ScanBuy else ScanWarn).copy(alpha = 0.22f), CircleShape)
                    // It looked like a button for as long as it was not one.
                    .clickable(onClick = onEditRules),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Edit, null, tint = HomeTextDim, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "EDIT MY RULES",
                    color = HomeTextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ── 05 · Blocked by guardrails ─────────────────────────────────────────────

@Composable
private fun ScanBlockedState(
    plan: TradePlan,
    confluence: ConfluenceResult,
    report: GuardrailReport,
    accent: Color,
    glow: NovaGlow,
    onBack: () -> Unit,
    onRetune: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScannerHeader(
            title = plan.instrument.symbol,
            onBack = onBack,
            divider = true,
            inlineDetail = {
                Text(
                    plan.instrument.formatPrice(plan.instrument.price),
                    color = ScanSell,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScannerGutter)
        ) {
            Spacer(Modifier.height(16.dp))

            ScanCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = 14.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(
                        score = confluence.score,
                        conviction = confluence.conviction,
                        diameter = 92.dp,
                        thickness = 9.dp,
                        glow = glow
                    ) {
                        Text(
                            confluence.score.toString(),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "SCORE",
                            color = HomeTextDim,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(ScanSell.copy(alpha = 0.16f))
                                    .border(1.dp, ScanSell.copy(alpha = 0.40f), CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    plan.direction.label,
                                    color = ScanSell,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                confluence.conviction.label,
                                color = convictionColor(confluence.conviction),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            blockedSummary(report),
                            color = HomeTextDim,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockedStat("BLENDED R:R", plan.rewardLabel, ScanSell, Modifier.weight(1f))
                BlockedStat("STOP LOSS", trimmed(plan.stopPips) + "p", ScanWarn, Modifier.weight(1f))
                BlockedStat("LOTS", String.format("%.2f", plan.totalLots), HomeTextPrimary, Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(ScanSell.copy(alpha = 0.05f))
                    .border(1.dp, ScanSell.copy(alpha = 0.34f), shape)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.GppMaybe, null, tint = ScanSell, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Execution blocked",
                            color = ScanTextBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            report.blockers.size.toString() + " blocker · " +
                                report.warnings.size + " warnings · " +
                                report.passes.size + " passed",
                            color = ScanSellText,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 13.dp).padding(bottom = 12.dp)) {
                    // Blockers first, then warnings, then passes -- the order the
                    // user needs, not the order the rules happen to be declared.
                    (report.blockers + report.warnings + report.passes).forEach { outcome ->
                        GuardrailRow(outcome, showBadge = true)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            ScanCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                background = accent.copy(alpha = 0.05f)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Lightbulb,
                        null,
                        tint = accent.onArtFloor(),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            "One way forward",
                            color = HomeTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Rebalance toward the further targets to lift the blended reward past your minimum.",
                            color = HomeTextDim,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Spacer(Modifier.height(9.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.16f))
                                .border(1.dp, accent.copy(alpha = 0.44f), CircleShape)
                                .clickable(onClick = onRetune)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Retune targets",
                                color = HomeTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        Column(
            modifier = Modifier
                .padding(horizontal = ScannerGutter)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            ScanPrimaryCta(
                label = "BLOCKED BY " + report.blockers.size +
                    (if (report.blockers.size == 1) " RULE" else " RULES"),
                icon = Icons.Rounded.Lock,
                fill = ScanBuy,
                enabled = false,
                onClick = {}
            )
            Spacer(Modifier.height(10.dp))
            ScanFootnote("Only you can change your rules, in Guardrail settings")
        }
    }
}

private fun blockedSummary(report: GuardrailReport): String {
    val failed = report.blockers.size + report.warnings.size
    return if (failed == 1) {
        "One check failed. " + report.blockers.firstOrNull()?.detail.orEmpty()
    } else {
        failed.toString() + " checks failed. The setup may be directionally valid, " +
            "but it does not clear the rules you set."
    }
}

@Composable
private fun BlockedStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    ScanCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = 12.dp
    ) {
        Text(
            label,
            color = HomeTextFaint,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(value, color = tint, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

// ── Execution review ───────────────────────────────────────────────────────

/**
 * The sheet the footnote promises.
 *
 * Every leg is listed with the volume and the stop it will carry, because this
 * is the last screen before real orders reach a real account. The design's
 * "Review sheet before anything reaches your broker" is a commitment, and a
 * confirm dialog that only repeats the total would not honour it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExecuteReviewSheet(
    plan: TradePlan,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HomeCanvas,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 28.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Review before sending",
                color = ScanTextBright,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                plan.legs.size.toString() + " " + plan.entryType.label.uppercase() + " orders on " +
                    plan.instrument.symbol + " · " + plan.direction.label + " · " +
                    String.format("%.2f", plan.totalLots) + " lots total",
                color = HomeTextDim,
                fontSize = 12.sp
            )

            // What kind of order this is, in words, above the legs. A pending
            // order does not fill today and a market order fills immediately --
            // the user is approving two very different things and the sheet used
            // to describe both the same way.
            Spacer(Modifier.height(12.dp))
            ScanNote(
                icon = if (plan.entryType == EntryType.MARKET) Icons.Rounded.Bolt else Icons.Rounded.History,
                text = when (plan.entryType) {
                    EntryType.MARKET ->
                        "Fills now at the market, around " + plan.instrument.formatPrice(plan.entry) + "."
                    // A pending order has a lifetime, and approving one without
                    // knowing it is approving an order that could fire days from
                    // now into a market that has moved on.
                    EntryType.LIMIT ->
                        "Waits for price to come back to " + plan.instrument.formatPrice(plan.entry) +
                            ". Nothing opens until it does, and your broker cancels it after " +
                            humanDuration(plan.pendingExpirySeconds / 60) + "."
                    EntryType.STOP ->
                        "Waits for price to break " + plan.instrument.formatPrice(plan.entry) +
                            ". Nothing opens until it does, and your broker cancels it after " +
                            humanDuration(plan.pendingExpirySeconds / 60) + "."
                },
                tint = accent.onArtFloor(),
                background = accent.copy(alpha = 0.05f),
                borderColor = HomeBorderFaint,
                textColor = HomeTextValue
            )

            // The screenshot is a moment ago; the order is now. If price has
            // crossed the entry since the scan, the order the app is about to
            // place is the opposite kind to the one the setup described.
            if (plan.entryDrifted) {
                Spacer(Modifier.height(8.dp))
                ScanNote(
                    icon = Icons.Rounded.GppMaybe,
                    text = "Price has moved since this chart was captured, so this is now a " +
                        plan.entryType.label.lowercase() + " order rather than what the scan read. " +
                        "Re-scan if the setup has changed.",
                    tint = ScanWarn,
                    background = ScanWarn.copy(alpha = 0.06f),
                    borderColor = ScanWarn.copy(alpha = 0.30f),
                    textColor = ScanWarnText
                )
            }

            Spacer(Modifier.height(18.dp))
            plan.legs.forEachIndexed { index, leg ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(LadderTints[index])
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        leg.name,
                        color = HomeTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(38.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            String.format("%.2f", leg.lots) + " lots → " +
                                plan.instrument.formatPrice(leg.price),
                            color = HomeTextValue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "stop " + plan.instrument.formatPrice(plan.stop),
                            color = HomeTextFaint,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        "+" + plan.money(leg.estimatedPl),
                        color = ScanBuy,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            ScanNote(
                icon = Icons.Rounded.GppMaybe,
                text = "Worst case on this plan is " + plan.money(plan.actualRisk) +
                    " if the stop fills on all " + plan.legs.size + " legs.",
                tint = ScanSell,
                background = ScanSell.copy(alpha = 0.05f),
                borderColor = ScanSell.copy(alpha = 0.28f),
                textColor = ScanSellText
            )

            Spacer(Modifier.height(20.dp))
            ScanPrimaryCta(
                label = "SEND " + plan.legs.size + " ORDERS",
                icon = Icons.Rounded.Bolt,
                fill = ScanBuy,
                onClick = onConfirm
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Cancel",
                color = HomeTextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp)
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

/**
 * Reads the picked screenshot, shrinks it, and returns the data URL the edge
 * function expects.
 *
 * The previous version base64'd the file's raw bytes. A phone screenshot is
 * commonly 2-8 MB, base64 inflates it by a third, so the request body reached
 * ~10 MB and was uploaded over whatever mobile connection the user happened to
 * have. On a typical link that upload takes well over a minute, the socket
 * stalls, and Ktor surfaces it as `Connect timeout has expired` against
 * analyze-chart -- which reads as "the server is down" when it is really "we
 * asked the phone to upload ten megabytes".
 *
 * Downscaling is free accuracy-wise: the vision model resizes anything larger
 * than [MAX_EDGE] on its long edge before reading it, so pixels above that are
 * paid for in upload time and discarded on arrival. 1568px keeps every candle
 * and price label legible while turning a 10 MB body into a few hundred KB.
 *
 * Decoding is two-pass -- bounds first, then a subsampled decode -- so the full
 * bitmap is never held in memory. That is what makes a 108MP camera-roll pick
 * safe rather than an OOM on the one screen users pay for.
 *
 * It also makes the MIME type honest. The data URL has always claimed
 * `image/jpeg`; screenshots are PNG, so the label was a lie the vision endpoint
 * had to tolerate. Re-encoding to JPEG here means the declared type is now what
 * is actually sent.
 */
private suspend fun encodeChart(context: android.content.Context, uri: Uri): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Pass 1: dimensions only, no pixels allocated.
            val bounds = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                android.util.Log.e("NovaHost", "[Scanner] not a decodable image")
                return@withContext null
            }

            // Pass 2: decode subsampled. inSampleSize only honours powers of two,
            // so aim within 2x of the target and let the exact scale below
            // finish the job.
            var sample = 1
            while (bounds.outWidth / sample > MAX_EDGE * 2 ||
                   bounds.outHeight / sample > MAX_EDGE * 2) {
                sample *= 2
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(
                    stream,
                    null,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                )
            } ?: return@withContext null

            val longEdge = maxOf(decoded.width, decoded.height)
            val scaled = if (longEdge > MAX_EDGE) {
                val ratio = MAX_EDGE.toFloat() / longEdge
                android.graphics.Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }

            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            scaled.recycle()

            val bytes = out.toByteArray()
            android.util.Log.i(
                "NovaHost",
                "[Scanner] chart ${bounds.outWidth}x${bounds.outHeight} -> " +
                    "${bytes.size / 1024}KB JPEG (sample=$sample)"
            )
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: OutOfMemoryError) {
            // Subsampling makes this unlikely, but a pathological image should
            // fail as a refused scan, not as a dead app.
            android.util.Log.e("NovaHost", "[Scanner] out of memory decoding chart", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("NovaHost", "[Scanner] could not read chart", e)
            null
        }
    }

/**
 * Long-edge cap, in pixels. Matches the size the vision model downsamples to
 * internally, so anything larger costs upload time and buys no detail.
 */
private const val MAX_EDGE = 1568

/** High enough that thin candle wicks and axis labels survive re-encoding. */
private const val JPEG_QUALITY = 85
