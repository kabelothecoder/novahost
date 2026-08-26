package com.novahost.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.novahost.app.ui.theme.DeepPurple
import com.novahost.app.ui.theme.Charcoal

/**
 * GlobalVideoBackground
 *
 * @description Plays a looping ExoPlayer video as the full-screen background layer.
 * Lifecycle-aware: pauses on ON_PAUSE, resumes on ON_RESUME, releases on DisposableEffect.
 * Uses a ColorMatrix overlay to modulate saturation dynamically based on bot run state.
 *
 * @param saturationMultiplier 1.5f = vivid (running), 0.0f = grayscale (stopped), 1.0f = normal
 * @param videoUrl Remote URL to stream; null = falls back to local res/raw/bg_motion_loop.mp4
 */
@Composable
fun GlobalVideoBackground(
    saturationMultiplier: Float = 1f,
    videoUrl: String? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Build player once; swap media when videoUrl changes
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            volume = 0f
        }
    }

    LaunchedEffect(videoUrl) {
        val uri = if (!videoUrl.isNullOrBlank()) {
            Uri.parse(videoUrl)
        } else {
            Uri.parse("android.resource://${context.packageName}/raw/bg_motion_loop")
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Lifecycle observer — pause/resume playback to prevent battery drain
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_PAUSE  -> exoPlayer.playWhenReady = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // Build saturation ColorMatrix for the overlay
    val colorMatrix = remember(saturationMultiplier) {
        ColorMatrix().apply { setToSaturation(saturationMultiplier) }
    }

    Box(modifier = modifier) {
        // Fallback gradient when no video is loaded yet
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(colors = listOf(DeepPurple, Charcoal))
                )
        )

        // ExoPlayer surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Saturation scrim overlay — rendered OVER the PlayerView surface using BlendMode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    if (saturationMultiplier != 1f) {
                        // A white rect with a saturation-mapped color filter creates
                        // the correct visual effect on top of the hardware-rendered video
                        drawRect(
                            color = Color.White.copy(alpha = 0.001f),
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                            blendMode = BlendMode.Saturation
                        )
                    }
                }
        )
    }
}
