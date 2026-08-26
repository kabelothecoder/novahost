package com.novahost.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.novahost.app.R
import com.novahost.app.navigation.Routes
import com.novahost.app.sdk.NotificationHelper
import com.novahost.app.service.NovaHostPulseService
import com.novahost.app.ui.components.LightStatusBarEffect
import com.novahost.app.ui.components.NovaCtaButton
import com.novahost.app.ui.components.NovaGhostButton
import com.novahost.app.ui.components.StaggerIn
import com.novahost.app.ui.theme.InterFamily
import com.novahost.app.ui.theme.NovaAccentDeep
import com.novahost.app.ui.theme.NovaArtChip
import com.novahost.app.ui.theme.NovaArtScreen
import com.novahost.app.ui.theme.NovaArtScreenAlt
import com.novahost.app.ui.theme.NovaArtSkeleton
import com.novahost.app.ui.theme.NovaArtSkeletonLo
import com.novahost.app.ui.theme.NovaCanvas
import com.novahost.app.ui.theme.NovaDangerSoft
import com.novahost.app.ui.theme.NovaElevation.novaCard
import com.novahost.app.ui.theme.NovaMotion
import com.novahost.app.ui.theme.NovaPlaceholderFill
import com.novahost.app.ui.theme.NovaSuccessSoft
import com.novahost.app.ui.theme.NovaSurface
import com.novahost.app.ui.theme.NovaTextDisabled
import com.novahost.app.ui.theme.NovaTextMuted
import com.novahost.app.ui.theme.NovaTextOnInk
import com.novahost.app.ui.theme.NovaTextPrimary
import com.novahost.app.ui.theme.NovaToggleTrack
import com.novahost.app.ui.theme.NovaType
import com.novahost.app.ui.theme.SoftLightBlue
import com.novahost.app.ui.theme.SoftLightPurple

/**
 * The two grants NovaHost needs, asked for one screen at a time.
 *
 * This runs immediately after licence activation, which is the only point in
 * the flow where either request has a reason attached: the overlay grant is
 * what lets the Pulse bubble exist, and the bubble is the product. Asking at
 * cold start -- which is what the app used to do -- put a system dialog in
 * front of someone who had not yet seen what it was for, and a denial there
 * was permanent in practice.
 *
 * Two steps, then a confirmation:
 *
 *  1. **Overlay** (`SYSTEM_ALERT_WINDOW`). Required, so there is no skip.
 *     Android has no dialog for this one -- it hands out the grant on its own
 *     full settings screen -- which is why the step spends its space showing
 *     the row the user is about to be dropped in front of.
 *  2. **Notifications** (`POST_NOTIFICATIONS`). Optional, keeps a quiet
 *     "Not now": trade alerts are worth having but nothing breaks without
 *     them, and a forced ask here costs the grant permanently.
 *
 * The design (NovaHost Permissions v3) mocks Android's own permission sheet so
 * the prototype can be walked end to end. That mock is deliberately **not**
 * reproduced here -- the real system UI appears instead. A convincing fake of
 * a system permission dialog is the exact shape of a phishing screen, and it
 * would also drift from whatever the OEM actually shows.
 */
@Composable
fun PermissionsScreen(navController: NavController) {
    val context = LocalContext.current
    LightStatusBarEffect()

    var step by rememberSaveable { mutableStateOf(PermissionStep.OVERLAY) }
    var overlayGranted by remember { mutableStateOf(canDrawOverlays(context)) }
    var notifGranted by remember { mutableStateOf(notificationsEnabled(context)) }

    // Set once the OS has said it will no longer show its own dialog. From
    // that point the only route left is the app's notification settings, and
    // the copy has to stop promising a prompt that will never appear.
    var notifAskExhausted by rememberSaveable { mutableStateOf(false) }

    // Both grants can change outside the app, and the overlay one always does:
    // Android grants it on a settings screen, not in a dialog. Re-reading on
    // resume rather than trusting the launcher result is deliberate -- several
    // OEM settings apps return RESULT_CANCELED after a successful flip, so the
    // result alone would leave the screen showing "off" for a granted
    // permission.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = canDrawOverlays(context)
                notifGranted = notificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // MainActivity only tries to start the bubble at cold start, so without
    // this the grant made on this screen would produce nothing visible until
    // the next launch -- which reads as the permission not having worked.
    LaunchedEffect(overlayGranted) {
        if (overlayGranted) startPulseService(context)
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { overlayGranted = canDrawOverlays(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted || notificationsEnabled(context)
        if (!granted) notifAskExhausted = !canStillShowNotifDialog(context)
    }

    // Step 2 goes back to step 1. Step 1 does not intercept: activation was
    // popped when this screen opened, so there is nothing behind it, and
    // swallowing back there would trap the user in a flow they cannot leave.
    BackHandler(enabled = step == PermissionStep.NOTIFICATIONS) {
        step = PermissionStep.OVERLAY
    }

    val finish = {
        markPermissionsSeen(context)
        // Register the channel now rather than at first alert: a channel
        // created lazily lands after the notification that needed it.
        NotificationHelper.init(context)
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.PERMISSIONS) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovaCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            if (step != PermissionStep.DONE) {
                StepRail(
                    step = step,
                    overlayGranted = overlayGranted,
                    onBack = { step = PermissionStep.OVERLAY }
                )
            }

            Crossfade(
                targetState = step,
                animationSpec = tween(NovaMotion.COPY_MS, easing = NovaMotion.Emphasized),
                label = "permissionStep",
                modifier = Modifier.weight(1f)
            ) { current ->
                when (current) {
                    PermissionStep.OVERLAY -> OverlayStep(
                        granted = overlayGranted,
                        onPrimary = {
                            if (overlayGranted) step = PermissionStep.NOTIFICATIONS
                            else openOverlaySettings(context, overlayLauncher::launch)
                        }
                    )

                    PermissionStep.NOTIFICATIONS -> NotificationStep(
                        granted = notifGranted,
                        askExhausted = notifAskExhausted,
                        onPrimary = {
                            when {
                                notifGranted -> step = PermissionStep.DONE
                                // Below API 33 there is no runtime permission to
                                // request -- notifications are on at install and
                                // "off" can only mean the user switched them off,
                                // which only their settings screen can undo.
                                notifAskExhausted || Build.VERSION.SDK_INT < 33 ->
                                    openAppNotificationSettings(context)

                                else -> notifLauncher.launch(POST_NOTIFICATIONS)
                            }
                        },
                        onSkip = { step = PermissionStep.DONE }
                    )

                    PermissionStep.DONE -> DoneStep(
                        notifGranted = notifGranted,
                        onFinish = finish
                    )
                }
            }
        }
    }
}

private enum class PermissionStep { OVERLAY, NOTIFICATIONS, DONE }

// ─────────────────────────────────────────────────────────────────────────────
// Steps
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverlayStep(granted: Boolean, onPrimary: () -> Unit) {
    StepBody(
        stepKey = PermissionStep.OVERLAY,
        title = "Let the robot sit\non top of your screen",
        hero = { OverlayHero() },
        bubble = "It draws a bubble, nothing more. NovaHost never reads what's on your screen.",
        row = {
            SettingsRowMock(label = "Display over other apps", on = granted) {
                AppMark(Modifier.size(38.dp), corner = 12.dp)
            }
        },
        footer = if (granted) {
            "Granted. The circle appears the moment the robot starts trading."
        } else {
            "Android opens its own list — find NovaHost, flip the switch, then come back here."
        },
        cta = {
            NovaCtaButton(
                label = if (granted) "Overlay is on · continue" else "Open Android settings",
                onClick = onPrimary,
                height = 56.dp,
                leading = if (granted) {
                    { CtaCheckBadge() }
                } else null
            )
        },
        // No skip: the bubble is the product, and without this grant the
        // service that draws it cannot start at all.
        below = { Spacer(Modifier.height(26.dp)) }
    )
}

@Composable
private fun NotificationStep(
    granted: Boolean,
    askExhausted: Boolean,
    onPrimary: () -> Unit,
    onSkip: () -> Unit
) {
    StepBody(
        stepKey = PermissionStep.NOTIFICATIONS,
        title = "Hear about fills\nas they happen",
        hero = { NotificationHero() },
        bubble = "Trade events only. No marketing, no daily digests, ever.",
        row = {
            SettingsRowMock(label = "Allow notifications", on = granted) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NovaPlaceholderFill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsNone,
                        contentDescription = null,
                        tint = NovaAccentDeep,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        footer = when {
            granted -> "Done. Mute any category later in Settings."
            // The design has one un-granted line, written for a prompt that is
            // still coming. Once Android has stopped offering the prompt that
            // sentence is a lie, so this state gets its own.
            askExhausted ->
                "Android won't ask again — switch NovaHost on in its notification settings."

            else -> "Optional, but drawdown warnings only help if they reach you in time."
        },
        cta = {
            NovaCtaButton(
                label = when {
                    granted -> "Continue"
                    askExhausted || Build.VERSION.SDK_INT < 33 -> "Open notification settings"
                    else -> "Allow notifications"
                },
                onClick = onPrimary,
                height = 56.dp
            )
        },
        below = {
            // The slot keeps its height whether or not the trigger is in it,
            // so granting does not shift the CTA down the screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!granted) NovaGhostButton(label = "Not now", onClick = onSkip)
            }
            Spacer(Modifier.height(8.dp))
        }
    )
}

@Composable
private fun DoneStep(notifGranted: Boolean, onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Holds the place the step rail occupies on the other two, so the
        // hero does not jump upward on the last frame of the crossfade.
        Spacer(Modifier.height(40.dp))

        StaggerIn(delayMs = 0, durationMs = 520, rise = 14.dp) { ClearedMark() }
        Spacer(Modifier.height(26.dp))
        StaggerIn(delayMs = 140, durationMs = 460, rise = 14.dp) {
            Text(
                text = "The robot is cleared to run",
                style = PermissionTitleStyle,
                color = NovaTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
        StaggerIn(delayMs = 200, durationMs = 460, rise = 14.dp) {
            Text(
                text = if (notifGranted) {
                    "Overlay and alerts are both on. Change either one any time in Settings."
                } else {
                    "Overlay is on. Turn alerts on later in Settings if you want fills pushed to you."
                },
                style = NovaType.Body,
                color = NovaTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.weight(1f))
        NovaCtaButton(label = "Open the dashboard", onClick = onFinish, height = 56.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared step layout
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Title, hero, bubble, mock row -- then the footer and CTA pinned to the
 * bottom edge.
 *
 * The upper block scrolls and the lower one does not. The design distributes
 * the slack with a flex spacer, which works because it is drawn at one fixed
 * 390x844. On a real range of devices that spacer collapses first and then the
 * hero starts clipping, so the part that can afford to scroll does, and the
 * CTA stays where the thumb expects it regardless of screen height.
 */
@Composable
private fun StepBody(
    stepKey: Any,
    title: String,
    hero: @Composable () -> Unit,
    bubble: String,
    row: @Composable () -> Unit,
    footer: String,
    cta: @Composable () -> Unit,
    below: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            StaggerIn(delayMs = 0, key = stepKey, durationMs = 480, rise = 14.dp) {
                Text(
                    text = title,
                    style = PermissionTitleStyle,
                    color = NovaTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(20.dp))
            StaggerIn(delayMs = 80, key = stepKey, durationMs = 520, rise = 14.dp) { hero() }
            Spacer(Modifier.height(18.dp))
            StaggerIn(delayMs = 180, key = stepKey, durationMs = 460, rise = 10.dp) {
                ReassuranceBubble(bubble)
            }
            // 17dp in the design, less the 9dp the tail takes up here -- it is
            // laid out below the bubble rather than absolutely positioned into
            // the gap beneath it.
            Spacer(Modifier.height(8.dp))
            StaggerIn(delayMs = 240, key = stepKey, durationMs = 460, rise = 14.dp) { row() }
            Spacer(Modifier.height(14.dp))
        }

        Text(
            text = footer,
            style = PermissionFooterStyle,
            color = NovaTextDisabled,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(14.dp))
        cta()
        below()
    }
}

/**
 * Back slot and the two step segments.
 *
 * The arrow keeps its 32dp of width on step 1 even though it is not drawn
 * there -- the segments would otherwise be a different length on each step,
 * which reads as the progress bar itself moving.
 */
@Composable
private fun StepRail(step: PermissionStep, overlayGranted: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 8.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (step == PermissionStep.NOTIFICATIONS) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(width = 11.dp, height = 18.dp)) {
                        val sx = size.width / 11f
                        val sy = size.height / 18f
                        val chevron = Path().apply {
                            moveTo(9f * sx, 1.6f * sy)
                            lineTo(2f * sx, 9f * sy)
                            lineTo(9f * sx, 16.4f * sy)
                        }
                        drawPath(
                            path = chevron,
                            color = NovaTextPrimary,
                            style = Stroke(
                                width = 2.1f * sx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        RailSegment(
            filled = overlayGranted || step == PermissionStep.OVERLAY,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        RailSegment(
            filled = step == PermissionStep.NOTIFICATIONS || step == PermissionStep.DONE,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RailSegment(filled: Boolean, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(
        targetValue = if (filled) NovaTextPrimary else NovaToggleTrack,
        animationSpec = tween(NovaMotion.RAIL_MS, easing = NovaMotion.Emphasized),
        label = "railSegment"
    )
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(fill)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Pieces
// ─────────────────────────────────────────────────────────────────────────────

/** The dark bubble that answers the objection, tail pointing at the row below. */
@Composable
private fun ReassuranceBubble(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NovaTextPrimary)
                .padding(horizontal = 18.dp, vertical = 15.dp)
        ) {
            Text(
                text = text,
                style = BubbleCopyStyle,
                color = NovaTextOnInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Canvas(Modifier.size(width = 18.dp, height = 9.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, NovaTextPrimary)
        }
    }
}

/**
 * A mock of the exact Android row the user is about to be shown.
 *
 * It is a picture, not a control -- tapping it does nothing, because the only
 * thing that can flip this switch is Android itself. It exists so that when
 * the settings list opens, the row is already familiar.
 */
@Composable
private fun SettingsRowMock(label: String, on: Boolean, icon: @Composable () -> Unit) {
    // Flattened to one label for TalkBack. Left as-is it announces a switch,
    // which would send a screen-reader user hunting for a control that is a
    // drawing -- so the semantics say what it actually is.
    val spoken = if (on) "$label: already on" else "$label: preview of the Android setting"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken }
            .novaCard(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(NovaSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(13.dp))
        Text(
            text = label,
            style = RowLabelStyle,
            color = NovaTextPrimary,
            modifier = Modifier.weight(1f)
        )
        AndroidToggle(on)
    }
}

/** Android's switch, at Android's proportions: 46x28, 22dp knob, 18dp of travel. */
@Composable
private fun AndroidToggle(on: Boolean) {
    val track by animateColorAsState(
        targetValue = if (on) NovaSuccessSoft else NovaToggleTrack,
        animationSpec = tween(260, easing = NovaMotion.Emphasized),
        label = "toggleTrack"
    )
    val knobX by animateDpAsState(
        targetValue = if (on) 18.dp else 0.dp,
        animationSpec = tween(260, easing = NovaMotion.Emphasized),
        label = "toggleKnob"
    )
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobX)
                .size(22.dp)
                .shadow(
                    elevation = 3.dp,
                    shape = CircleShape,
                    ambientColor = NovaTextPrimary.copy(alpha = 0.22f),
                    spotColor = NovaTextPrimary.copy(alpha = 0.22f)
                )
                .clip(CircleShape)
                .background(NovaSurface)
        )
    }
}

/** The check that slides in beside the CTA label once the grant has landed. */
@Composable
private fun CtaCheckBadge() {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(width = 12.dp, height = 10.dp)) {
            val sx = size.width / 16f
            val sy = size.height / 14f
            val tick = Path().apply {
                moveTo(2.6f * sx, 7.2f * sy)
                lineTo(6f * sx, 10.4f * sy)
                lineTo(13.2f * sx, 3f * sy)
            }
            drawPath(
                path = tick,
                color = Color.White,
                style = Stroke(width = 2.8f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero art
// ─────────────────────────────────────────────────────────────────────────────

/** Step 1: the bubble already sitting over a chart, which is the outcome being asked for. */
@Composable
private fun OverlayHero() {
    HeroFrame(halo = SoftLightBlue.copy(alpha = 0.22f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .background(NovaArtScreen)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .alpha(0.5f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SkeletonBar(fraction = 0.56f, color = NovaArtSkeleton)
                SkeletonBar(fraction = 0.78f, color = NovaArtSkeletonLo)
            }

            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 34.dp)
                    .size(width = 154.dp, height = 86.dp)
                    .alpha(0.75f)
            ) {
                val sx = size.width / 154f
                val sy = size.height / 86f
                val points = listOf(
                    4f to 66f, 22f to 52f, 38f to 60f, 56f to 34f, 74f to 44f,
                    92f to 22f, 110f to 32f, 128f to 14f, 150f to 24f
                )
                val line = Path().apply {
                    moveTo(points.first().first * sx, points.first().second * sy)
                    points.drop(1).forEach { (x, y) -> lineTo(x * sx, y * sy) }
                }
                drawPath(
                    path = line,
                    color = SoftLightBlue,
                    style = Stroke(width = 2.2f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .size(width = 30.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(NovaToggleTrack)
                )
                Box(
                    Modifier
                        .size(width = 18.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(NovaArtChip)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 44.dp)
                    .size(52.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            ambientColor = NovaTextPrimary.copy(alpha = 0.22f),
                            spotColor = NovaTextPrimary.copy(alpha = 0.22f)
                        )
                        .clip(CircleShape)
                        .background(NovaSurface)
                ) {
                    RobotFace(Modifier.fillMaxSize())
                }
                // Live dot, ringed in the screen colour so it reads as cut out
                // of the bubble rather than stuck on top of it.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(NovaArtScreen)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(NovaSuccessSoft)
                )
            }
        }
    }
}

/** Step 2: the alerts themselves, as they arrive. */
@Composable
private fun NotificationHero() {
    HeroFrame(halo = SoftLightBlue.copy(alpha = 0.20f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .background(NovaArtScreenAlt)
                .padding(horizontal = 9.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotifCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppMark(Modifier.size(14.dp), corner = 4.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("NOVAHOST", style = NotifChannelStyle, color = NovaTextMuted)
                }
                Spacer(Modifier.height(5.dp))
                Text("Buy EURUSD filled", style = NotifTitleStyle, color = NovaTextPrimary)
                Spacer(Modifier.height(2.dp))
                Text("0.40 lot at 1.0842", style = NotifBodyStyle, color = NovaTextMuted)
            }
            NotifCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(NovaDangerSoft.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(NovaDangerSoft)
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text("DRAWDOWN", style = NotifChannelStyle, color = NovaTextMuted)
                }
                Spacer(Modifier.height(5.dp))
                Text("Daily loss at 3.1%", style = NotifTitleStyle, color = NovaTextPrimary)
                Spacer(Modifier.height(2.dp))
                Text("Limit is 4% — robot still on", style = NotifBodyStyle, color = NovaTextMuted)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.55f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NovaSurface)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "Session closed · +1.8%",
                    style = NotifTitleStyle.copy(fontSize = 9.sp),
                    color = NovaTextPrimary
                )
            }
        }
    }
}

@Composable
private fun NotifCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .novaCard(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(NovaSurface)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) { content() }
}

/** The 172x206 phone every hero is drawn inside, with its breathing halo. */
@Composable
private fun HeroFrame(halo: Color, screen: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.TopCenter) {
        HeroHalo(
            colors = listOf(halo, SoftLightPurple.copy(alpha = 0.06f), Color.Transparent),
            width = 220.dp,
            height = 150.dp,
            modifier = Modifier.padding(top = 14.dp)
        )
        Box(
            modifier = Modifier
                .size(width = 172.dp, height = 206.dp)
                .novaCard(RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(NovaSurface)
                .padding(9.dp)
        ) { screen() }
    }
}

@Composable
private fun HeroHalo(
    colors: List<Color>,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val breathe = rememberInfiniteTransition(label = "halo")
    val scale by breathe.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )
    val fade by breathe.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .scale(scale)
            .alpha(fade)
            .background(
                Brush.radialGradient(
                    0f to colors[0],
                    0.58f to colors[1],
                    0.74f to colors[2]
                )
            )
    )
}

/** The confirmation mark: the robot's face, checked off. */
@Composable
private fun ClearedMark() {
    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        HeroHalo(
            colors = listOf(
                NovaSuccessSoft.copy(alpha = 0.16f),
                NovaSuccessSoft.copy(alpha = 0.05f),
                Color.Transparent
            ),
            width = 150.dp,
            height = 150.dp
        )
        Box(
            modifier = Modifier
                .size(118.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    ambientColor = NovaTextPrimary.copy(alpha = 0.16f),
                    spotColor = NovaTextPrimary.copy(alpha = 0.16f)
                )
                .clip(CircleShape)
                .background(NovaSurface)
        ) {
            RobotFace(Modifier.fillMaxSize())
        }

        var play by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { play = true }
        val drawn by animateFloatAsState(
            targetValue = if (play) 1f else 0f,
            animationSpec = tween(440, delayMillis = 300, easing = NovaMotion.Emphasized),
            label = "checkDraw"
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-12).dp, y = (-14).dp)
                .size(38.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = NovaTextPrimary.copy(alpha = 0.16f),
                    spotColor = NovaTextPrimary.copy(alpha = 0.16f)
                )
                .clip(CircleShape)
                .background(NovaSurface),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(width = 21.dp, height = 18.dp)) {
                val sx = size.width / 24f
                val sy = size.height / 20f
                val tick = Path().apply {
                    moveTo(3.6f * sx, 10.4f * sy)
                    lineTo(9f * sx, 15.4f * sy)
                    lineTo(20f * sx, 3.8f * sy)
                }
                // The stroke is revealed rather than faded in: one dash the
                // length of the whole path, walked into view by its phase.
                val span = 32f * sx
                drawPath(
                    path = tick,
                    color = NovaSuccessSoft,
                    style = Stroke(
                        width = 3.2f * sx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(span, span),
                            phase = span * (1f - drawn)
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun SkeletonBar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * The robot's face, from the portal when the licence carried one.
 *
 * Same rule as licence activation: the bundled drawable is a fallback, not the
 * default. Someone who has just been shown their mentor's robot should see the
 * same face here, not a stock one.
 */
@Composable
private fun RobotFace(modifier: Modifier) {
    val context = LocalContext.current
    val avatarUrl = remember {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("avatar_url", null)
            ?.takeIf { it.isNotBlank() }
    }
    // 50% 34% in the design: the crop is biased upward so the face, not the
    // body, is what the circle holds.
    val crop = BiasAlignment(horizontalBias = 0f, verticalBias = -0.32f)
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Robot avatar",
            modifier = modifier,
            contentScale = ContentScale.Crop,
            alignment = crop
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.new_avatar),
            contentDescription = "Robot avatar",
            modifier = modifier,
            contentScale = ContentScale.Crop,
            alignment = crop
        )
    }
}

/** The launcher icon as Android's own lists draw it. */
@Composable
private fun AppMark(modifier: Modifier, corner: Dp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(NovaCanvas)
    ) {
        Image(
            painter = painterResource(id = R.drawable.novahost_mark),
            contentDescription = null,
            // The mark sits inside generous padding in the source asset; the
            // scale is what crops that away so it fills the tile.
            modifier = Modifier.fillMaxSize().scale(1.6f),
            contentScale = ContentScale.Crop
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Type
// ─────────────────────────────────────────────────────────────────────────────

private val PermissionTitleStyle = NovaType.StepTitle.copy(
    lineHeight = 34.sp,
    letterSpacing = (-0.7).sp
)

private val PermissionFooterStyle = TextStyle(
    fontFamily = InterFamily,
    fontSize = 13.5.sp,
    lineHeight = 20.sp
)

private val BubbleCopyStyle = TextStyle(
    fontFamily = InterFamily,
    fontSize = 14.sp,
    lineHeight = 21.sp
)

private val RowLabelStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp
)

private val NotifChannelStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 8.sp,
    letterSpacing = 0.16.sp
)

private val NotifTitleStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 9.5.sp
)

private val NotifBodyStyle = TextStyle(
    fontFamily = InterFamily,
    fontSize = 8.5.sp
)

// ─────────────────────────────────────────────────────────────────────────────
// Platform
// ─────────────────────────────────────────────────────────────────────────────

private const val PREFS = "metahost_prefs"
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

/**
 * Whether the permissions flow has been run to the end on this install.
 *
 * Read by the splash routing so a cold start mid-setup lands back here rather
 * than in a Home whose bubble cannot draw.
 */
fun hasSeenPermissions(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean("permissions_complete", false)

private fun markPermissionsSeen(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("permissions_complete", true)
        .apply()
}

private fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * True while Android is still willing to put its own dialog in front of the
 * user. Only meaningful after a refusal: before the first ask it is false for
 * a permission that has simply never been requested.
 */
private fun canStillShowNotifDialog(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, POST_NOTIFICATIONS)
}

/**
 * Opens the overlay grant screen, scoped to this app where the OEM honours it.
 *
 * The package Uri is what takes the user straight to NovaHost's own row rather
 * than to the full app list. Several skins ignore it and a few reject the
 * intent outright, so a failure falls back to the unscoped screen instead of
 * leaving the CTA doing nothing.
 */
private fun openOverlaySettings(context: Context, launch: (Intent) -> Unit) {
    val scoped = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    try {
        launch(scoped)
    } catch (e: Exception) {
        try {
            launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        } catch (e2: Exception) {
            // Nothing on this device can show it. The step stays put and the
            // toggle stays off, which is the honest reading.
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        } catch (e2: Exception) {
            // Same as above: no route, so no state change.
        }
    }
}

private fun startPulseService(context: Context) {
    try {
        context.startForegroundService(Intent(context, NovaHostPulseService::class.java))
    } catch (e: Exception) {
        // A start refused because the app slipped into the background is not
        // worth a crash -- MainActivity retries it on the next cold start.
    }
}

private fun Context.findActivity(): android.app.Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

