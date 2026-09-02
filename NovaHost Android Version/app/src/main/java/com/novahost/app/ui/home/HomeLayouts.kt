package com.novahost.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novahost.app.R
import com.novahost.app.sdk.LicenseRecord
import com.novahost.app.ui.theme.HomeBorderFaint
import com.novahost.app.ui.theme.HomeBorderSubtle
import com.novahost.app.ui.theme.HomeCanvasArt
import com.novahost.app.ui.theme.HomeCanvasFeed
import com.novahost.app.ui.theme.HomeCanvasFocus
import com.novahost.app.ui.theme.HomeCanvasGlass
import com.novahost.app.ui.theme.HomeLive
import com.novahost.app.ui.theme.HomeSell
import com.novahost.app.ui.theme.HomeSurfaceSunken
import com.novahost.app.ui.theme.HomeSurfaceWell
import com.novahost.app.ui.theme.HomeTextBright
import com.novahost.app.ui.theme.HomeTextDim
import com.novahost.app.ui.theme.HomeTextFaint
import com.novahost.app.ui.theme.HomeTextMuted
import com.novahost.app.ui.theme.HomeTextValue
import com.novahost.app.ui.theme.NovaGlow

/**
 * Everything the five layouts read. One Home state, five presentations -- a
 * layout switch changes which composable renders this, never what is in it.
 */
data class HomeUiState(
    val isRunning: Boolean,
    val isConnecting: Boolean,
    val brokerConnected: Boolean,
    /**
     * True while the broker link is still being checked against the server.
     *
     * [brokerConnected] alone cannot express "we do not know yet", and its
     * default is false -- so on a cold start the header used to assert NOT
     * LINKED about an account it had not asked about. This makes the unknown
     * state visible instead of guessing at it.
     */
    val linkChecking: Boolean = false,
    /** Already sanitized for display. */
    val robotName: String,
    /** The key this robot is running under. Blank until one is activated. */
    val licenseKey: String = "",
    val mentorName: String,
    val tagline: String,
    val artUrl: String?,
    val accent: Color,
    val glow: NovaGlow,
    val licenses: List<LicenseRecord> = emptyList(),
    val activityLog: List<String> = emptyList(),
    val licenceSummary: String = ""
) {
    /**
     * What the status pill says. Three states, not two.
     *
     * Checking outranks connected: a stale true from a previous session should
     * not read as a fresh confirmation while the probe is still in flight.
     */
    val linkLabel: String get() = when {
        linkChecking -> "CHECKING"
        brokerConnected -> "LINKED"
        else -> "NOT LINKED"
    }
}

/** What the user can do from any layout. Layouts never navigate on their own. */
data class HomeActions(
    val onToggleRun: () -> Unit,
    val onQuotes: () -> Unit,
    val onAssetHub: () -> Unit,
    val onSettings: () -> Unit,
    val onScanner: () -> Unit,
    /**
     * The MetaTrader connection screen.
     *
     * Only the nav drawer reached it before, which put the one screen that
     * fixes an unlinked broker behind a menu the user has no reason to open
     * while the header is telling them the broker is not linked.
     */
    val onTerminal: () -> Unit,
    val onAddKey: () -> Unit,
    val onRobotSelected: (LicenseRecord) -> Unit
)

/**
 * Renders the chosen interface.
 *
 * The `when` is exhaustive on purpose: adding a [HomeLayout] without giving it
 * a body should fail the build, not fall through to a default that silently
 * ships the wrong screen.
 */
@Composable
fun HomeLayoutHost(
    layout: HomeLayout,
    arrangement: LayoutArrangement,
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    when (layout) {
        HomeLayout.CLASSIC_CORE -> ClassicCoreLayout(arrangement, state, actions, modifier)
        HomeLayout.FOCUS_ENGINE -> FocusEngineLayout(arrangement, state, actions, modifier)
        HomeLayout.FULL_BLEED_ART -> FullBleedArtLayout(arrangement, state, actions, modifier)
        HomeLayout.GLASS_STACK -> GlassStackLayout(arrangement, state, actions, modifier)
        HomeLayout.SIGNAL_FEED -> SignalFeedLayout(arrangement, state, actions, modifier)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 1 · CLASSIC CORE — today's arrangement, cleaned up
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ClassicCoreLayout(
    arrangement: LayoutArrangement,
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        MentorArtBackground(
            artUrl = state.artUrl,
            artMode = arrangement.artMode,
            ground = HomeCanvasArt,
            accent = state.accent,
            glow = state.glow
        )

        // Measured from the container, not from LocalConfiguration: the app
        // draws edge to edge, so the configuration height and the height left
        // for content after system-bar insets are two different numbers.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
        ) {
            val viewportHeight = maxHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Claim at least one viewport so the weighted spacers below
                    // have something to divide. Without a min height the scroll
                    // container hands this column an infinite maxHeight, every
                    // weight resolves to zero, and a hero whose widgets are all
                    // hidden stacks against the status bar with half the screen
                    // left empty underneath it.
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = HomeTopChromeInset - HomeGutter, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeStatusPill(
                        text = if (state.brokerConnected) "BROKER LINKED" else state.linkLabel,
                        connected = state.brokerConnected
                    )
                    Spacer(Modifier.weight(1f))
                    HomeSettingsButton(onClick = actions.onSettings)
                }

                Spacer(Modifier.height(28.dp))

                // Centres the hero in whatever room the widget stack leaves it.
                // The pair collapses to zero the moment the content is taller
                // than the viewport, so a full widget stack scrolls exactly as
                // it did before -- this only moves the hero when there is dead
                // space to absorb.
                Spacer(Modifier.weight(1f))

                // Pinned: robot hero.
                HeroAvatar(state = state, artMode = arrangement.artMode, size = 132.dp)

                Spacer(Modifier.height(18.dp))

                MentorCredit(state.mentorName, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.robotName,
                    color = HomeTextBright,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(28.dp))

                // Pinned: ignition.
                IgnitionRow(
                    isRunning = state.isRunning,
                    isConnecting = state.isConnecting,
                    accent = state.accent,
                    glow = state.glow,
                    onToggleRun = actions.onToggleRun,
                    onQuotes = actions.onQuotes,
                    onAssetHub = actions.onAssetHub
                )

                Spacer(Modifier.height(16.dp))
                PoweredByFooter()
                Spacer(Modifier.height(28.dp))

                Spacer(Modifier.weight(1f))

                WidgetStack(arrangement = arrangement, state = state, actions = actions, glass = false)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 2 · FOCUS ENGINE — ignition and name, nothing else
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FocusEngineLayout(
    arrangement: LayoutArrangement,
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Focus Engine's art is deliberately pushed back -- it is atmosphere,
        // not subject. The ignition is the only thing allowed to be bright.
        Box(modifier = Modifier.fillMaxSize().background(HomeCanvasFocus)) {
            if (arrangement.artMode != HomeArtMode.AVATAR) {
                Box(modifier = Modifier.fillMaxSize().alpha(0.22f)) {
                    MentorArtBackground(
                        artUrl = state.artUrl,
                        artMode = HomeArtMode.FULL,
                        ground = HomeCanvasFocus,
                        accent = state.accent,
                        glow = NovaGlow.OFF
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // The ignition sits in the optical centre of what is actually
            // visible, so this measures the container rather than the screen.
            val viewportHeight = maxHeight

            Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp).clickable(onClick = actions.onSettings)
                )
            }

            // The allowance is the chrome this column has to leave room for:
            // ~30.dp for the settings row above it, and ~101.dp for the
            // captioned nav row and its bottom padding below. Under-count it and
            // Focus Engine -- a screen whose whole argument is that it fits on
            // one screen -- starts scrolling.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight - 140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MentorCredit(state.mentorName, align = TextAlign.Center)

                Spacer(Modifier.height(14.dp))
                Text(
                    text = state.robotName,
                    color = Color.White,
                    fontSize = 34.sp,
                    // Thin and wide: at this size weight would fight the glow.
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = 8.sp,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(58.dp))

                IgnitionCircle(
                    isRunning = state.isRunning,
                    isConnecting = state.isConnecting,
                    accent = state.accent,
                    glow = state.glow,
                    onClick = actions.onToggleRun
                )

                Spacer(Modifier.height(52.dp))

                HomeStatusPill(
                    text = if (state.isRunning) {
                        "RUNNING · BROKER LINKED"
                    } else {
                        "STANDBY · ${if (state.brokerConnected) "BROKER LINKED" else state.linkLabel}"
                    },
                    connected = state.brokerConnected
                )
            }

            // Settings is already the gear in the top-right corner of this same
            // screen, so it is not repeated here -- two identical gears one
            // above the other told the user nothing about either. Its slot goes
            // to Symbols, which is otherwise reachable only from the Quotes
            // button on the ignition row that this layout does not draw.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 46.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top
            ) {
                QuietIcon(Icons.Rounded.QueryStats, "Symbols", actions.onQuotes)
                QuietIcon(Icons.Rounded.DocumentScanner, "Scanner", actions.onScanner)
                QuietIcon(Icons.Rounded.SyncAlt, "MetaTrader", actions.onTerminal)
                QuietIcon(Icons.Rounded.Memory, "Robots", actions.onAssetHub)
            }

            // Every optional widget is hidden by default here, but the user may
            // have turned some back on. Honour that rather than hardcoding none.
            if (arrangement.visibleWidgets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                WidgetStack(arrangement = arrangement, state = state, actions = actions, glass = false)
                Spacer(Modifier.height(32.dp))
            }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 3 · FULL-BLEED ART — the marketing screen
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FullBleedArtLayout(
    arrangement: LayoutArrangement,
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        MentorArtBackground(
            artUrl = state.artUrl,
            artMode = arrangement.artMode,
            ground = HomeCanvasArt,
            accent = state.accent,
            glow = state.glow
        )

        // The viewport height has to come from the measured container, not from
        // LocalConfiguration: the app draws edge to edge, so the configuration
        // height and the height actually left for content after system-bar
        // insets are two different numbers.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
        ) {
            val viewportHeight = maxHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
            // The marketing screen is judged above the fold, so the hero block
            // claims exactly one viewport and the widgets begin below it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = HomeTopChromeInset, end = HomeGutter)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeStatusPill(
                        text = state.linkLabel,
                        connected = state.brokerConnected
                    )
                    Spacer(Modifier.weight(1f))
                    HomeSettingsButton(onClick = actions.onSettings)
                }

                Spacer(Modifier.weight(1f))

                Column(modifier = Modifier.padding(start = 26.dp, end = 26.dp, bottom = 40.dp)) {
                    MentorCredit(state.mentorName)

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.robotName,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        lineHeight = 36.sp
                    )

                    if (state.tagline.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            text = state.tagline,
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.fillMaxWidth(0.72f)
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    IgnitionRow(
                        isRunning = state.isRunning,
                        isConnecting = state.isConnecting,
                        accent = state.accent,
                        glow = state.glow,
                        onToggleRun = actions.onToggleRun,
                        onQuotes = actions.onQuotes,
                        onAssetHub = actions.onAssetHub
                    )

                    Spacer(Modifier.height(20.dp))
                    PoweredByFooter()
                }
            }

            if (arrangement.visibleWidgets.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = HomeGutter).padding(bottom = 32.dp)) {
                    WidgetStack(arrangement = arrangement, state = state, actions = actions, glass = true)
                }
            }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 4 · GLASS STACK — frosted panels, art reading through
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun GlassStackLayout(
    arrangement: LayoutArrangement,
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        MentorArtBackground(
            artUrl = state.artUrl,
            artMode = arrangement.artMode,
            ground = HomeCanvasGlass,
            accent = state.accent,
            glow = state.glow
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = HomeTopChromeInset - 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MentorCredit(state.mentorName)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp).clickable(onClick = actions.onSettings)
                )
            }

            // Art occupies the top 56%; the panel stack starts below it.
            Spacer(Modifier.height((LocalConfiguration.current.screenHeightDp * 0.40f).dp))

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "ACTIVE ROBOT",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = state.robotName,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(color = if (state.brokerConnected) HomeLive else HomeTextFaint, animate = state.brokerConnected)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.licenceSummary.ifBlank {
                            if (state.isRunning) "Running" else "Standby"
                        },
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeStatTile(
                    label = "STATUS",
                    value = if (state.isRunning) "RUNNING" else "STANDBY",
                    modifier = Modifier.weight(1f),
                    valueColor = if (state.isRunning) HomeLive else Color.White,
                    glass = true
                )
                HomeStatTile(
                    label = "BROKER",
                    value = if (state.linkChecking) "CHECKING" else if (state.brokerConnected) "LINKED" else "OFFLINE",
                    modifier = Modifier.weight(1f),
                    valueColor = when {
                        state.linkChecking -> HomeTextMuted
                        state.brokerConnected -> HomeLive
                        else -> HomeSell
                    },
                    glass = true
                )
            }

            Spacer(Modifier.height(12.dp))

            WidgetStack(arrangement = arrangement, state = state, actions = actions, glass = true)

            Spacer(Modifier.height(16.dp))

            IgnitionRow(
                isRunning = state.isRunning,
                isConnecting = state.isConnecting,
                accent = state.accent,
                glow = state.glow,
                onToggleRun = actions.onToggleRun,
                onQuotes = actions.onQuotes,
                onAssetHub = actions.onAssetHub,
                shape = RoundedCornerShape(22.dp),
                controlHeight = 68.dp
            )

            Spacer(Modifier.height(34.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 5 · SIGNAL FEED — mentor signals + fleet status
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SignalFeedLayout(
    arrangement: LayoutArrangement,
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(HomeCanvasFeed)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = HomeTopChromeInset, end = HomeGutter).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroAvatar(state = state, artMode = HomeArtMode.AVATAR, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.robotName, color = HomeTextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (state.mentorName.isNotBlank()) {
                        Text(state.mentorName, color = HomeTextDim, fontSize = 10.sp)
                    }
                }
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = HomeTextMuted,
                    modifier = Modifier.size(22.dp).clickable(onClick = actions.onSettings)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeStatTile("STATE", if (state.isRunning) "LIVE" else "IDLE", Modifier.weight(1f),
                    valueColor = if (state.isRunning) HomeLive else HomeTextValue)
                HomeStatTile("LINK", if (state.brokerConnected) "OK" else "OFF", Modifier.weight(1f),
                    valueColor = if (state.brokerConnected) HomeLive else HomeSell)
                HomeStatTile("BOTS", "${state.licenses.count { it.status.equals("active", true) }}/${state.licenses.size}", Modifier.weight(1f))
                HomeStatTile("SIGS", "${state.licenses.size}", Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                HomeSectionLabel(
                    text = "MENTOR SIGNALS",
                    color = HomeTextMuted,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulsingDot(color = state.accent, animate = state.isRunning)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.isRunning) "LIVE" else "PAUSED",
                                color = state.accent.onArtFloor(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                // No signal store exists on the device yet -- the pulse service
                // acts on inserts and does not keep them. Rather than invent
                // rows, the feed shows what the app genuinely has: the terminal
                // log. The picker flags this layout as NEEDS PLUMBING so the
                // gap is visible in the product, not just in the code.
                SignalFeedEmptyNotice(accent = state.accent)

                Spacer(Modifier.height(12.dp))
                // The terminal log is a widget like any other here, so a user
                // who hides it in Arrange Widgets actually gets it hidden.
                WidgetStack(arrangement = arrangement, state = state, actions = actions, glass = false)

                Spacer(Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HomeCanvasFeed.copy(alpha = 0.9f))
                    .padding(horizontal = 22.dp)
                    .padding(top = 16.dp, bottom = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SatelliteButton(Icons.Rounded.DocumentScanner, "Scanner", actions.onScanner, size = 52.dp)
                IgnitionCta(
                    isRunning = state.isRunning,
                    isConnecting = state.isConnecting,
                    accent = state.accent,
                    glow = state.glow,
                    onClick = actions.onToggleRun,
                    modifier = Modifier.weight(1f),
                    height = 56.dp
                )
                SatelliteButton(Icons.Rounded.Memory, "Asset Hub", actions.onAssetHub, size = 52.dp)
            }
        }
    }
}

@Composable
private fun SignalFeedEmptyNotice(accent: Color) {
    HomeCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = accent.copy(alpha = 0.26f),
        background = accent.copy(alpha = 0.05f)
    ) {
        Text(
            "No signal history on this device",
            color = HomeTextValue,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Incoming mentor signals are executed by the pulse service as they arrive. Persisting them for this feed needs a store the app does not have yet.",
            color = HomeTextMuted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Shared pieces
// ═══════════════════════════════════════════════════════════════════════════

/** The mentor credit line. Drops out entirely when no mentor name is set. */
@Composable
private fun MentorCredit(
    mentorName: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start
) {
    if (mentorName.isBlank()) return
    Text(
        text = "MENTOR · ${mentorName.uppercase()}",
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
        textAlign = align,
        modifier = modifier
    )
}

/** The circular crop of the mentor's art, used where the layout does not go full-bleed. */
@Composable
private fun HeroAvatar(
    state: HomeUiState,
    artMode: HomeArtMode,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val shape = if (artMode == HomeArtMode.FRAMED) RoundedCornerShape(22.dp) else CircleShape
    Box(
        modifier = modifier
            .then(if (size > 60.dp) Modifier.fillMaxWidth() else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .border(1.5.dp, state.accent.onArtFloor().copy(alpha = 0.8f), shape)
        ) {
            if (state.artUrl != null) {
                AsyncImage(
                    model = state.artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(id = R.drawable.new_avatar),
                    error = painterResource(id = R.drawable.new_avatar)
                )
            } else {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.new_avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * One destination in Focus Engine's bottom row.
 *
 * Captioned, and on a 48.dp tap target rather than the 22.dp glyph itself. Four
 * unlabelled low-alpha glyphs are not a navigation bar -- the user has to tap
 * one to find out where it goes, which is how a duplicate settings gear sat
 * here unnoticed. The caption stays faint enough not to compete with the
 * ignition, which is still the only bright thing on the screen.
 */
@Composable
private fun QuietIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The optional widgets, in the order this layout's arrangement puts them.
 *
 * Order and visibility are the user's; which widgets exist is not. A widget the
 * app cannot render is simply absent rather than shown as an error card.
 */
@Composable
private fun WidgetStack(
    arrangement: LayoutArrangement,
    state: HomeUiState,
    actions: HomeActions,
    glass: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        arrangement.visibleWidgets.forEach { widget ->
            when (widget) {
                HomeWidget.ROBOT_HERO, HomeWidget.IGNITION_POD -> Unit // pinned, drawn by the layout
                HomeWidget.CONNECTED_ROBOTS -> ConnectedRobotsWidget(
                    licenses = state.licenses,
                    activeLicenseKey = state.licenseKey,
                    accent = state.accent,
                    glass = glass,
                    onRobotSelected = actions.onRobotSelected,
                    onAddKey = actions.onAddKey
                )
                HomeWidget.CHART_SCANNER -> ChartScannerWidget(accent = state.accent, glass = glass, onClick = actions.onScanner)
                HomeWidget.ACTIVITY_LOG -> ActivityLogWidget(state.activityLog)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConnectedRobotsWidget(
    licenses: List<LicenseRecord>,
    /**
     * The active key, not the active robot name.
     *
     * Names are joined from `expert_advisors` through `ea_id`, so two keys for
     * one robot produce two rows with the same name and both were marked ACTIVE
     * at once. Two keys on one robot is the common case in this database.
     */
    activeLicenseKey: String,
    accent: Color,
    glass: Boolean,
    onRobotSelected: (LicenseRecord) -> Unit,
    onAddKey: () -> Unit
) {
    WidgetShell(title = "CONNECTED ROBOTS", glass = glass) {
        if (licenses.isEmpty()) {
            Text(
                "No active licences on this device.",
                color = HomeTextMuted,
                fontSize = 11.sp
            )
        } else {
            licenses.forEach { license ->
                val name = license.display_name ?: "TRADING BOT"
                val isActive = license.license_key != null &&
                    license.license_key.equals(activeLicenseKey, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRobotSelected(license) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(HomeSurfaceSunken),
                        contentAlignment = Alignment.Center
                    ) {
                        if (license.avatar_url != null) {
                            AsyncImage(
                                model = license.avatar_url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = painterResource(id = R.drawable.new_avatar),
                                error = painterResource(id = R.drawable.new_avatar)
                            )
                        } else {
                            Icon(Icons.Rounded.Memory, null, tint = HomeTextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = HomeTextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        // The key, so identical robot names stay tellable apart.
                        Text(
                            license.license_key
                                ?: license.status?.replaceFirstChar { it.uppercase() }
                                ?: "Active",
                            color = HomeTextDim,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    if (isActive) {
                        Text(
                            "ACTIVE",
                            color = accent.onArtFloor(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .border(1.dp, accent.onArtFloor().copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onAddKey)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, tint = accent.onArtFloor(), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("ADD LICENCE KEY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ChartScannerWidget(accent: Color, glass: Boolean, onClick: () -> Unit) {
    WidgetShell(title = "AI CHART SCANNER", glass = glass) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.onArtFloor().copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DocumentScanner, null, tint = accent.onArtFloor(), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Scan a chart screenshot", color = HomeTextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Pattern, entry, SL and TP", color = HomeTextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ActivityLogWidget(entries: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(HomeSurfaceWell)
            .border(1.dp, HomeBorderFaint, RoundedCornerShape(13.dp))
            .padding(14.dp)
    ) {
        Text("TERMINAL", color = HomeTextMuted, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        // Newest first, capped: the log is a reassurance widget, not a console.
        entries.asReversed().take(6).forEach { line ->
            Text(
                text = line,
                color = HomeTextFaint,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 19.sp
            )
        }
        if (entries.isEmpty()) {
            Text(
                "No service events yet.",
                color = HomeTextFaint,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** The container a widget sits in. Glass over art, flat card otherwise. */
@Composable
private fun WidgetShell(
    title: String,
    glass: Boolean,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        HomeSectionLabel(text = title, color = if (glass) Color.White.copy(alpha = 0.48f) else HomeTextMuted)
        Spacer(Modifier.height(10.dp))
        content()
    }

    if (glass) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), content = body)
    } else {
        HomeCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            borderColor = HomeBorderSubtle,
            content = body
        )
    }
}
