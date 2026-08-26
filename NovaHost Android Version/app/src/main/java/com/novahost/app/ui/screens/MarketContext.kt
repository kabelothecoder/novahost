package com.novahost.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novahost.app.sdk.EconomicEvent
import com.novahost.app.ui.components.TradingViewWidget
import com.novahost.app.ui.theme.ActiveGrey

/**
 * The two TradingView embeds [PairManagementScreen] hangs off its NEWS and
 * EVENTS sections.
 *
 * These used to be tabs on a Markets screen of their own, alongside a quotes
 * list and an "Insights" tab that was a second copy of a TradingView widget.
 * Markets is gone: it duplicated the symbol list that already lives on Trading
 * Symbols, and its context panels are worth more next to the symbols they
 * describe than behind a separate destination nobody opened twice.
 *
 * The sessions panel that used to live here went with the tabs. Trading Symbols
 * draws its own session tiles from [com.novahost.app.sdk.MarketSession] so the
 * four windows sit in one row of the scroll rather than a grid on a tab of their
 * own -- there is nothing left here for it to share.
 *
 * Each panel is self-contained so the screen can host them without knowing what
 * a TradingView embed is.
 */

// ── News ───────────────────────────────────────────────────────────────────

/** The rolling market newsfeed. */
@Composable
fun NewsPanel(modifier: Modifier = Modifier) {
    val widgetHtml = """
        <div class="tradingview-widget-container">
          <div class="tradingview-widget-container__widget"></div>
          <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-timeline.js" async>
          {
          "feedMode": "all_symbols",
          "colorTheme": "dark",
          "isTransparent": true,
          "displayMode": "regular",
          "width": "100vw",
          "height": "100%",
          "locale": "en"
        }
          </script>
        </div>
    """.trimIndent()

    Box(modifier = modifier.fillMaxSize()) {
        TradingViewWidget(widgetHtml = widgetHtml)
    }
}

// ── Hot news calendar ──────────────────────────────────────────────────────

/**
 * The economic calendar, filtered to the releases that actually move price.
 *
 * `importanceFilter` is "0,1" rather than the "-1,0,1" the Markets screen used:
 * low-impact prints are most of the feed by volume and none of the reason a
 * trader opens a calendar. [events] is accepted so a future offline fallback has
 * somewhere to render from.
 */
@Composable
fun HotNewsCalendarPanel(
    events: List<EconomicEvent>,
    modifier: Modifier = Modifier
) {
    val widgetHtml = """
        <div class="tradingview-widget-container">
          <div class="tradingview-widget-container__widget"></div>
          <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-events.js" async>
          {
          "colorTheme": "dark",
          "isTransparent": true,
          "width": "100vw",
          "height": "100%",
          "locale": "en",
          "importanceFilter": "0,1"
        }
          </script>
        </div>
    """.trimIndent()

    Column(modifier = modifier.fillMaxSize()) {
        if (events.isNotEmpty()) {
            Text(
                events.size.toString() + " releases on the wire today",
                color = ActiveGrey,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            TradingViewWidget(widgetHtml = widgetHtml)
        }
    }
}
