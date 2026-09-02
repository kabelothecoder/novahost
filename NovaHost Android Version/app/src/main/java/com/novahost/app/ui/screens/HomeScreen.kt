package com.novahost.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.novahost.app.R
import com.novahost.app.ui.components.*
import com.novahost.app.ui.theme.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign

/**
 * @description Strips legacy prefixes (ALGO_CORE ::) and underscores from raw robot names,
 * then formats the result in clean Title Case for display.
 * Example: "ALGO_CORE :: apex_scalper_v2" → "Apex Scalper V2"
 */
fun sanitizeRobotName(raw: String): String {
    val cleaned = raw
        .replace(Regex("(?i)ALGO_CORE\\s*::\\s*"), "")
        .trim()

    if (cleaned.isBlank()) return "NO ROBOT SELECTED"

    // Only reformat legacy SNAKE_CASE identifiers. A mentor's display_name is
    // their branding and is shown exactly as they typed it -- title-casing it
    // turned "WAKANDA WEALTH EA" into "Wakanda Wealth Ea".
    if (!cleaned.contains('_')) return cleaned

    return cleaned
        .replace("_", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

@Composable
fun EA_InventoryList(
    licenses: List<com.novahost.app.sdk.LicenseRecord> = emptyList(),
    /** The key in `metahost_prefs` -- the one trades actually execute under. */
    activeLicenseKey: String = "",
    /** The key currently being switched to, if any. */
    switchingKey: String? = null,
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    onAddKeyClick: () -> Unit = {},
    onRobotSelected: (com.novahost.app.sdk.LicenseRecord) -> Unit = {}
) {
    val themeState = LocalNovaHostTheme.current
    val primaryColor = themeState.primaryColor
    val secondaryColor = themeState.secondaryColor
    val borderBrush = if(themeState.isGlossTheme) Brush.linearGradient(listOf(primaryColor, secondaryColor)) else Brush.linearGradient(listOf(primaryColor, primaryColor))

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // No heading here. The only caller is the Asset Hub sheet, which already
        // prints "CONNECTED ROBOTS" in the accent as its own title -- the two
        // stacked twelve dp apart and read as a rendering fault.

        // Three different empty screens, because they need three different
        // sentences. The old code had one -- "no active licenses found" -- and
        // showed it for a broken query, an unreachable server and a genuinely
        // empty account alike, which is how a 400 on a misnamed column read for
        // weeks as "you own nothing".
        if (licenses.isEmpty() && loading) {
            Text("Reading your licences…", color = Color(0xFFEDEDED), fontSize = 12.sp)
        } else if (licenses.isEmpty() && error != null) {
            Text(error, color = Color(0xFFFFB4A9), fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "TRY AGAIN",
                color = primaryColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRetry() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else if (licenses.isEmpty()) {
            Text("No licences on this device yet. Tap [+] to add your NovaHost key.", color = Color(0xFFEDEDED), fontSize = 12.sp)
        } else {
            licenses.forEach { license ->
                // Matched on the key, not the robot name. Several keys routinely
                // sit behind one robot -- `expert_advisors` is joined through
                // `ea_id`, so those rows carry identical names and every one of
                // them used to light up as active at once.
                val isActive = license.license_key != null &&
                    license.license_key.equals(activeLicenseKey, ignoreCase = true)
                val isSwitching = license.license_key != null &&
                    license.license_key == switchingKey
                val busy = switchingKey != null

                val cardModifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !busy && !isActive) { onRobotSelected(license) }
                    .then(
                        if (isActive) Modifier.border(1.dp, primaryColor, RoundedCornerShape(16.dp)) else Modifier
                    )
                    .alpha(if (busy && !isSwitching) 0.45f else 1f)

                val content: @Composable ColumnScope.() -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (license.avatar_url != null) {
                            AsyncImage(
                                model = license.avatar_url,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.new_avatar),
                                error = painterResource(id = R.drawable.new_avatar)
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = if (isActive) primaryColor else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                (license.display_name ?: "ROBOT").uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            // The key, not just the status. Two rows showing the
                            // same robot name are otherwise indistinguishable,
                            // and the user has no way to tell which key they are
                            // about to switch to.
                            Text(
                                license.license_key ?: (license.status?.uppercase() ?: "ACTIVE"),
                                color = if (isActive) primaryColor.copy(alpha = 0.85f) else Color(0xFFBDBDBD),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        when {
                            isSwitching -> CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = primaryColor
                            )
                            isActive -> Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (themeState.isGlossTheme) primaryColor else Color.Green)
                            )
                        }
                    }
                }

                GlassDepthCard(modifier = cardModifier, content = content)
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Add Keys Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(CircleShape)
                .background(Color.Transparent) // Transparent center
                .border(2.dp, borderBrush, CircleShape) // Thicker themed border
                .clickable { onAddKeyClick() },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).border(1.dp, borderBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add", tint = primaryColor, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("ADD KEYS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Have a valid License Key", color = Color(0xFFEDEDED), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        // Powered by NovaHost
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(0.6f)) {
            Text("Powered by ", color = Color.White, fontSize = 12.sp)
            Text("NovaHost", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}


@Composable
fun DraggableFloatingAvatar(robotAvatarUrl: String?, onClick: () -> Unit) {
    val themeState = LocalNovaHostTheme.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Safe zone padding
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(72.dp)
                .shadow(16.dp, CircleShape, spotColor = themeState.primaryColor)
                .clip(CircleShape)
                .background(Charcoal)
                .border(2.dp, themeState.primaryColor, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (robotAvatarUrl != null) {
                AsyncImage(
                    model = robotAvatarUrl,
                    contentDescription = "Robot Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.novahost_mark),
                    contentDescription = "Default Robot",
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Pulsing inner ring to show it's active
            val transition = rememberInfiniteTransition(label = "avatar_pulse")
            val pulseAlpha by transition.animateFloat(
                initialValue = 0.2f, targetValue = 0.6f,
                animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "avatar_pulse_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(4.dp, themeState.primaryColor.copy(alpha = pulseAlpha), CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotActiveDialog(
    robotName: String,
    adminName: String,
    onDismiss: () -> Unit, 
    onStop: () -> Unit
) {
    val themeState = LocalNovaHostTheme.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ACTIVE ROBOT", 
                    color = themeState.primaryColor, 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Details
            Surface(
                color = Color.Black.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Robot:", color = Color.Gray, fontSize = 14.sp)
                        Text(robotName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Account:", color = Color.Gray, fontSize = 14.sp)
                        Text(adminName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status:", color = Color.Gray, fontSize = 14.sp)
                        Text("Running", color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Powered by ", color = Color(0xFFEDEDED), fontSize = 10.sp)
                Text("NovaHost", color = themeState.primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    onStop()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeState.primaryColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("STOP ROBOT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}
