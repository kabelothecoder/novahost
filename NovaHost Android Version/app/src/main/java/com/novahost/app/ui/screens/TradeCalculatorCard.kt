package com.novahost.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novahost.app.ui.scanner.ScanCard
import com.novahost.app.ui.scanner.ScanSectionLabel
import com.novahost.app.ui.theme.HomeBorderSubtle
import com.novahost.app.ui.theme.HomeTextDim
import com.novahost.app.ui.theme.HomeTextFaint
import com.novahost.app.ui.theme.ScanSurfaceRaised
import com.novahost.app.ui.theme.ScanTextBright
import com.novahost.app.ui.theme.ScanTextTrace
import com.novahost.app.ui.theme.ScanWell
import java.util.Locale

/**
 * The trade calculator, on the scanner where the number it produces is used.
 *
 * It moved off the home screen because the figure it computes -- risk per trade
 * -- is an input to the scan, not a dashboard readout. Sitting on Home it was a
 * calculator the user had to copy out by hand; here its answer is what
 * `TradePlanner` sizes the position with.
 *
 * Two changes from the home widget it replaces:
 *
 *  - **Risk is typed, not picked.** The old widget offered Conservative /
 *    Moderate / Aggressive at 1 / 2 / 5%. Three presets cannot express "0.75%",
 *    which is what a funded-account rule actually says, so the preset rail is
 *    gone and the field takes any number.
 *  - **Trade count is an input.** Risk budgets are per-session, not per-order.
 *    Someone risking 2% across four setups is risking 0.5% each, and the old
 *    widget would have sized every one of them at the full 2%.
 *
 * [onPerTradeRiskPercent] reports total ÷ trades, so everything downstream keeps
 * working in the per-trade terms it already speaks.
 */
@Composable
fun TradeCalculatorCard(
    accent: Color,
    terminalBalance: Double,
    onPerTradeRiskPercent: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE) }

    // The terminal's balance is the default, not the law: a user sizing for an
    // account they have not linked yet still needs the calculator to work.
    var balanceText by remember {
        mutableStateOf(
            prefs.getString("calc_balance", null)
                ?: terminalBalance.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.0f", it) }
                ?: ""
        )
    }
    var riskText by remember { mutableStateOf(prefs.getString("calc_risk_pct", "1") ?: "1") }
    var tradesText by remember { mutableStateOf(prefs.getString("calc_trades", "1") ?: "1") }

    val balance = balanceText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: terminalBalance
    val totalRiskPercent = riskText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
    // Zero trades is not a plan, and dividing by it is not a number.
    val trades = tradesText.toIntOrNull()?.coerceIn(1, 20) ?: 1

    val perTradePercent = if (trades > 0) totalRiskPercent / trades else totalRiskPercent
    val totalRiskAmount = balance * totalRiskPercent / 100.0
    val perTradeAmount = totalRiskAmount / trades

    // A 20-pip stop and a $10 standard-lot pip. Named on screen rather than
    // hidden, because a lot size quoted off an assumption the user cannot see
    // is a number they will size a real position with and not know why.
    val stopPips = 20.0
    val pipValuePerLot = 10.0
    val suggestedLot = if (perTradeAmount > 0.0) {
        maxOf(0.01, perTradeAmount / (stopPips * pipValuePerLot))
    } else 0.0

    LaunchedEffect(balanceText, riskText, tradesText, perTradePercent, suggestedLot) {
        prefs.edit().apply {
            putString("calc_balance", balanceText)
            putString("calc_risk_pct", riskText)
            putString("calc_trades", tradesText)
            putFloat("smart_lot_size", suggestedLot.toFloat())
            putFloat("smart_risk_pct", perTradePercent.toFloat())
        }.apply()
        onPerTradeRiskPercent(perTradePercent)
    }

    ScanCard(
        modifier = modifier.fillMaxWidth(),
        background = ScanSurfaceRaised,
        borderColor = HomeBorderSubtle,
        contentPadding = 14.dp
    ) {
        ScanSectionLabel(
            "TRADE CALCULATOR",
            trailing = {
                Text("sizes every scan", color = ScanTextTrace, fontSize = 9.sp)
            }
        )

        Spacer(Modifier.height(12.dp))

        CalcField(
            label = "ACCOUNT BALANCE ($)",
            value = balanceText,
            onValue = { balanceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
            placeholder = if (terminalBalance > 0.0) {
                String.format(Locale.US, "%.0f", terminalBalance)
            } else "10000",
            hint = if (terminalBalance > 0.0) "from your linked terminal" else "no terminal linked",
            accent = accent
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcField(
                label = "TOTAL RISK (%)",
                value = riskText,
                onValue = { riskText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                placeholder = "1",
                hint = "across all trades",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            CalcField(
                label = "HOW MANY TRADES",
                value = tradesText,
                onValue = { tradesText = it.filter { ch -> ch.isDigit() }.take(2) },
                placeholder = "1",
                hint = "splits the budget",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcReadout(
                label = "RISK / TRADE",
                value = String.format(Locale.US, "%.2f%%", perTradePercent),
                sub = "$" + String.format(Locale.US, "%,.0f", perTradeAmount),
                valueColor = accent,
                modifier = Modifier.weight(1f)
            )
            CalcReadout(
                label = "TOTAL AT RISK",
                value = "$" + String.format(Locale.US, "%,.0f", totalRiskAmount),
                sub = String.format(Locale.US, "%.2f%% of %,.0f", totalRiskPercent, balance),
                valueColor = ScanTextBright,
                modifier = Modifier.weight(1f)
            )
            CalcReadout(
                label = "SUGGESTED LOT",
                value = String.format(Locale.US, "%.2f", suggestedLot),
                sub = "at a " + stopPips.toInt() + "-pip stop",
                valueColor = ScanTextBright,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CalcField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    hint: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            color = HomeTextFaint,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ScanWell)
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    color = ScanTextTrace,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = ScanTextBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(hint, color = ScanTextTrace, fontSize = 8.5.sp)
    }
}

@Composable
private fun CalcReadout(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ScanWell)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = HomeTextFaint,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(2.dp))
        Text(sub, color = HomeTextDim, fontSize = 8.5.sp)
    }
}
