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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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

    // The robot's own symbol list is the watchlist. Falling back to a fixed set
    // rather than an empty rail: a scanner with nothing to scan is a dead end,
    // and an unactivated install has no robot yet.
    val symbols = remember(branding.allowedSymbols) {
        branding.allowedSymbols.takeIf { it.isNotEmpty() } ?: listOf("EUR/USD", "XAU/USD", "GBP/JPY")
    }

    var stage by remember { mutableStateOf(ScanStage.INPUT) }
    var symbol by remember { mutableStateOf(symbols.first()) }
    var mode by remember { mutableStateOf(ScanMode.DAY) }
    var preset by remember { mutableStateOf(AllocationPreset.Default) }
    var riskPercent by remember { mutableStateOf(1.0) }
    var chartUri by remember { mutableStateOf<Uri?>(null) }
    var isSample by remember { mutableStateOf(false) }
    var instrument by remember { mutableStateOf<Instrument?>(null) }
    var reading by remember { mutableStateOf<ScanReading?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var executionNote by remember { mutableStateOf<String?>(null) }

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
    val plan = remember(reading, instrument, preset, balance, riskPercent) {
        val r = reading
        val i = instrument
        if (r == null || i == null) null
        else TradePlanner.build(
            instrument = i,
            reading = r,
            // An unconnected terminal reports zero. Sizing off zero yields a
            // zero position and a plan of blanks, so the preview balance stands
            // in and the input state labels it.
            balance = if (balance > 0.0) balance else SAMPLE_BALANCE,
            riskPercent = riskPercent,
            preset = preset
        )
    }
    val confluence = remember(plan, reading, instrument, mode) {
        val r = reading
        val i = instrument
        val p = plan
        if (r == null || i == null || p == null) null
        else ConfluenceEngine.score(i, r, p, mode)
    }
    val guardrails = remember(plan, reading) {
        val r = reading
        val p = plan
        if (r == null || p == null) null
        else Guardrails.evaluate(p, r, GuardrailConfig(), consecutiveLosses = 0)
    }

    fun resetToInput() {
        stage = ScanStage.INPUT
        reading = null
        instrument = null
        isSample = false
        errorMessage = null
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
        errorMessage = null
        scope.launch {
            val encoded = encodeChart(context, uri)
            if (encoded == null) {
                errorMessage = "That image could not be read. Try another screenshot."
                stage = ScanStage.INPUT
                return@launch
            }
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
                atrPips = null
            )
            ScanSource.analyzeChart(encoded)
                .onSuccess { verdict ->
                    val events = ScanSource.buildEvents(symbol, calendar)
                    instrument = built
                    reading = ScanSource.toReading(verdict, built, events)
                    isSample = false
                    stage = ScanStage.SCORE
                }
                .onFailure { failure ->
                    errorMessage = failure.message ?: "The scan did not complete."
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
                    balance = balance,
                    onRisk = { riskPercent = it },
                    scannerUnlocked = hasScanner,
                    chartAttached = chartUri != null,
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

                ScanStage.SCANNING -> ScanRunningState(accent = accent)

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
                                onBack = { stage = ScanStage.PLAN },
                                onExecute = { showConfirm = true }
                            )
                        }
                    }
                }
            }
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
                                tp = leg.price
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
    balance: Double,
    onRisk: (Double) -> Unit,
    scannerUnlocked: Boolean,
    chartAttached: Boolean,
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

            // Balance, risk and trade count in one place. This replaced a pair
            // of read-only tiles whose risk figure could only be cycled through
            // 0.5 / 1.0 / 1.5 / 2.0 by tapping it.
            TradeCalculatorCard(
                accent = accent,
                terminalBalance = balance,
                onPerTradeRiskPercent = onRisk
            )

            DashedCard(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAttach),
                background = if (chartAttached) accent.copy(alpha = 0.06f) else ScanSurfaceGlass,
                borderColor = if (chartAttached) accent.copy(alpha = 0.5f) else ScanBorderStrong
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ScanWell),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (chartAttached) Icons.Rounded.CheckCircle else Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = if (chartAttached) accent.onArtFloor() else HomeTextDim,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (chartAttached) "Chart attached" else "Attach your chart",
                        color = HomeTextValue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (chartAttached) {
                            "Tap to swap it for a different screenshot."
                        } else {
                            "Your drawings and levels get folded into the score."
                        },
                        color = HomeTextFaint,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
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
private fun ScanRunningState(accent: Color) {
    val steps = listOf(
        "Reading the chart",
        "Finding structure",
        "Measuring the stop",
        "Scoring confluence"
    )
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = steps.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "scanStep"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScannerGutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = accent.onArtFloor(), strokeWidth = 2.dp, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(28.dp))
        steps.forEachIndexed { index, step ->
            val reached = progress >= index
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (reached) accent.onArtFloor() else HomeBorderStrong)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    step,
                    color = if (reached) HomeTextPrimary else HomeTextFaint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
                    "+$" + String.format("%,.0f", leg.estimatedPl),
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
                "Risk $" + String.format("%,.0f", plan.actualRisk) +
                    " · Reward $" + String.format("%,.0f", plan.totalReward),
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
    onBack: () -> Unit,
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
                Icon(Icons.Rounded.Tune, null, tint = HomeTextDim, modifier = Modifier.size(20.dp))
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
                        "No calendar events for these currencies today.",
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
            GuardrailPanel(report)

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
private fun GuardrailPanel(report: GuardrailReport) {
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
                    .border(1.dp, (if (clear) ScanBuy else ScanWarn).copy(alpha = 0.22f), CircleShape),
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
                plan.legs.size.toString() + " orders on " + plan.instrument.symbol + " · " +
                    plan.direction.label + " · " + String.format("%.2f", plan.totalLots) + " lots total",
                color = HomeTextDim,
                fontSize = 12.sp
            )

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
                        "+$" + String.format("%,.0f", leg.estimatedPl),
                        color = ScanBuy,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            ScanNote(
                icon = Icons.Rounded.GppMaybe,
                text = "Worst case on this plan is $" + String.format("%,.0f", plan.actualRisk) +
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
 * Reads the picked screenshot into the data URL the edge function expects.
 *
 * Capped rather than unbounded: a modern phone screenshot is a couple of
 * megabytes, base64 inflates it by a third, and an OOM on a 108MP camera roll
 * pick would take the whole app down on the one screen users pay for.
 */
private suspend fun encodeChart(context: android.content.Context, uri: Uri): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > MAX_CHART_BYTES) return@withContext null
                "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            android.util.Log.e("NovaHost", "[Scanner] could not read chart", e)
            null
        }
    }

private const val MAX_CHART_BYTES = 8 * 1024 * 1024
