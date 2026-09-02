package com.novahost.app.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novahost.app.ui.home.onArtFloor
import com.novahost.app.ui.theme.HomeBorderSubtle
import com.novahost.app.ui.theme.HomeCanvas
import com.novahost.app.ui.theme.HomeTextDim
import com.novahost.app.ui.theme.HomeTextFaint
import com.novahost.app.ui.theme.HomeTextValue
import com.novahost.app.ui.theme.ScanTextBright
import com.novahost.app.ui.theme.ScanTextTrace
import com.novahost.app.ui.theme.ScanWarn
import com.novahost.app.ui.theme.ScanWell

/**
 * The screen behind EDIT MY RULES.
 *
 * Four numbers, each with the sentence that explains what breaking it costs.
 * The panel that opens this sheet has always called them "your rules"; this is
 * what makes that true.
 *
 * ## Why the copy carries the severity
 *
 * Two of these rules BLOCK a plan and two only WARN, and which is which is not
 * guessable from the number. A user who lowers their R:R minimum is changing
 * the one rule that can stop a trade outright; a user who raises the stop limit
 * is changing a rule that has never stopped anything. Each row says which,
 * because a limit whose consequence is invisible is a limit nobody sets
 * deliberately.
 *
 * ## Why edits are held locally until SAVE
 *
 * The plan behind this sheet re-scores the moment the config changes. Writing on
 * every keystroke would have the guardrail panel flickering between BLOCKED and
 * clear while a half-typed "1" is briefly a minimum R:R of 1. Committing on save
 * keeps the sheet a decision rather than a live wire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardrailSheet(
    config: GuardrailConfig,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (GuardrailConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Held as text, not as numbers. A number field that reparses on every
    // keystroke deletes the character someone just typed the moment it is not
    // yet a valid figure -- "1." being the obvious one.
    var rrText by remember { mutableStateOf(trimmed(config.minBlendedRR)) }
    var stopText by remember { mutableStateOf(trimmed(config.maxStopPips)) }
    var lossText by remember { mutableStateOf(config.maxConsecutiveLosses.toString()) }
    var eventText by remember { mutableStateOf(config.minEventClearanceMinutes.toString()) }

    fun edited() = GuardrailConfig(
        minBlendedRR = rrText.toDoubleOrNull() ?: config.minBlendedRR,
        maxStopPips = stopText.toDoubleOrNull() ?: config.maxStopPips,
        maxConsecutiveLosses = lossText.toIntOrNull() ?: config.maxConsecutiveLosses,
        minEventClearanceMinutes = eventText.toIntOrNull() ?: config.minEventClearanceMinutes
    ).sanitised()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HomeCanvas,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 28.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = accent.onArtFloor(),
                    modifier = Modifier.width(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "My rules",
                    color = ScanTextBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Checked on this device before every plan. Nothing here is sent anywhere.",
                color = HomeTextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(20.dp))

            RuleField(
                label = "MINIMUM BLENDED R:R",
                value = rrText,
                onValue = { rrText = it.filter { c -> c.isDigit() || c == '.' } },
                suffix = ": 1",
                accent = accent,
                blocking = true,
                help = "A plan whose reward-weighted return falls below this is blocked, not warned. " +
                    "Taking part of the position at 1:1 pulls the blended figure down, so this is " +
                    "the rule the three-target ladder has to satisfy."
            )

            Spacer(Modifier.height(14.dp))

            RuleField(
                label = "MAXIMUM STOP",
                value = stopText,
                onValue = { stopText = it.filter { c -> c.isDigit() || c == '.' } },
                suffix = "pips",
                accent = accent,
                blocking = false,
                help = "A warning, not a block. A wider stop is already paid for by the smaller " +
                    "position the risk sizing hands back, so this is information rather than a veto."
            )

            Spacer(Modifier.height(14.dp))

            RuleField(
                label = "CONSECUTIVE LOSSES",
                value = lossText,
                onValue = { lossText = it.filter { c -> c.isDigit() }.take(2) },
                suffix = "in a row locks the engine",
                accent = accent,
                blocking = true,
                help = "Counted from the closed trades on your linked broker account. Reaching it " +
                    "blocks new plans until a win breaks the streak."
            )

            Spacer(Modifier.height(14.dp))

            RuleField(
                label = "EVENT CLEARANCE",
                value = eventText,
                onValue = { eventText = it.filter { c -> c.isDigit() }.take(3) },
                suffix = "minutes",
                accent = accent,
                blocking = false,
                help = "Warns when a high-impact release lands inside this window. Needs a working " +
                    "economic calendar -- while that feed is down the rule reports 'unknown' rather " +
                    "than a false all-clear."
            )

            Spacer(Modifier.height(22.dp))

            ScanPrimaryCta(
                label = "SAVE MY RULES",
                icon = Icons.Rounded.Shield,
                fill = accent,
                onClick = { onSave(edited()) }
            )

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Reset to defaults",
                    color = HomeTextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val d = GuardrailConfig.Defaults
                            rrText = trimmed(d.minBlendedRR)
                            stopText = trimmed(d.maxStopPips)
                            lossText = d.maxConsecutiveLosses.toString()
                            eventText = d.minEventClearanceMinutes.toString()
                        }
                        .padding(vertical = 10.dp)
                )
                Text(
                    "Cancel",
                    color = HomeTextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * One rule: its number, its unit, and what breaking it does.
 *
 * [blocking] is rendered rather than merely stored. The difference between a
 * rule that stops a trade and one that comments on it is the whole reason to
 * set either, and it is not inferable from the figure.
 */
@Composable
private fun RuleField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    suffix: String,
    accent: Color,
    blocking: Boolean,
    help: String
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ScanWell)
            .border(1.dp, HomeBorderSubtle, shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = ScanTextTrace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (blocking) ScanWarn.copy(alpha = 0.14f)
                        else HomeBorderSubtle.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    if (blocking) "BLOCKS" else "WARNS",
                    color = if (blocking) ScanWarn else HomeTextFaint,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }

        Spacer(Modifier.height(9.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(
                    color = ScanTextBright,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(accent.onArtFloor()),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp)
            )
            Text(
                suffix,
                color = HomeTextValue,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(help, color = HomeTextFaint, fontSize = 10.5.sp, lineHeight = 15.sp)
    }
}
