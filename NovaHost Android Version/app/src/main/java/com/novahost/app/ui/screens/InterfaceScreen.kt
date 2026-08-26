package com.novahost.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.novahost.app.R
import com.novahost.app.navigation.Routes
import com.novahost.app.ui.home.HomeArtMode
import com.novahost.app.ui.home.HomeLayout
import com.novahost.app.ui.home.onArtFloor
import com.novahost.app.ui.theme.HomeBorder
import com.novahost.app.ui.theme.HomeBorderStrong
import com.novahost.app.ui.theme.HomeCanvas
import com.novahost.app.ui.theme.HomeSurface
import com.novahost.app.ui.theme.HomeTextBright
import com.novahost.app.ui.theme.HomeTextDim
import com.novahost.app.ui.theme.HomeTextFaint
import com.novahost.app.ui.theme.HomeTextMuted
import com.novahost.app.ui.theme.HomeAccentAmber
import com.novahost.app.ui.theme.LocalNovaHostTheme
import com.novahost.app.ui.theme.LocalRobotBranding
import com.novahost.app.ui.viewmodels.HomeViewModel

/**
 * The interface picker: tap a layout to apply it instantly.
 *
 * Art mode sits on each layout's own card rather than in a separate list. The
 * five per-layout settings are the only thing this screen is about, and pulling
 * them out into their own section is what turns a picker into a buried menu.
 */
@Composable
fun InterfaceScreen(
    navController: NavController,
    homeViewModel: HomeViewModel
) {
    val customization by homeViewModel.customization.collectAsState()
    val themeState = LocalNovaHostTheme.current
    val branding = LocalRobotBranding.current
    val accent = themeState.primaryColor.onArtFloor()

    Column(modifier = Modifier.fillMaxSize().background(HomeCanvas)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = HomeTextMuted,
                modifier = Modifier.size(22.dp).clickable { navController.popBackStack() }
            )
            Spacer(Modifier.width(14.dp))
            Text("Interface", color = HomeTextBright, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        Text(
            "Tap a layout to apply it instantly. Art mode is per layout.",
            color = HomeTextDim,
            fontSize = 11.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 10.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 22.dp, end = 22.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(HomeLayout.entries) { layout ->
                val arrangement = customization.arrangementFor(layout)
                LayoutCard(
                    layout = layout,
                    isActive = layout == customization.layout,
                    artMode = arrangement.artMode,
                    accent = accent,
                    artUrl = branding.avatarUrl,
                    onSelect = { homeViewModel.selectLayout(layout) },
                    onArtMode = { homeViewModel.setArtMode(layout, it) }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                val active = customization.active
                ArrangeWidgetsRow(
                    summary = "${active.shownCount} shown · ${active.hiddenCount} hidden",
                    accent = accent,
                    onClick = { navController.navigate(Routes.ARRANGE_WIDGETS) }
                )
            }
        }
    }
}

@Composable
private fun LayoutCard(
    layout: HomeLayout,
    isActive: Boolean,
    artMode: HomeArtMode,
    accent: Color,
    artUrl: String?,
    onSelect: () -> Unit,
    onArtMode: (HomeArtMode) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isActive) accent.copy(alpha = 0.07f) else HomeSurface)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) accent else HomeBorder,
                shape = shape
            )
            .clickable(onClick = onSelect)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        LayoutThumbnail(layout = layout, artMode = artMode, accent = accent, artUrl = artUrl)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    layout.displayName,
                    color = HomeTextBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(7.dp))
                if (isActive) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = accent, modifier = Modifier.size(16.dp))
                }
                if (layout.needsPlumbing) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "NEEDS PLUMBING",
                        color = HomeAccentAmber,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HomeAccentAmber.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(layout.blurb, color = HomeTextMuted, fontSize = 11.sp, lineHeight = 17.sp)

            Spacer(Modifier.height(8.dp))
            Text(
                "ART MODE",
                color = HomeTextFaint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HomeArtMode.entries.forEach { mode ->
                    ArtModeChip(
                        label = mode.displayName,
                        selected = mode == artMode,
                        accent = accent,
                        onClick = { onArtMode(mode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtModeChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else HomeTextMuted,
        fontSize = 10.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent else Color.Transparent)
            .border(1.dp, if (selected) accent else HomeBorderStrong, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

/**
 * A schematic of the layout, not a screenshot.
 *
 * A real render would need the whole home state to compose five times on a
 * settings screen. The schematic shows the one thing the user is choosing
 * between: where the art sits and where the glowing control lands.
 */
@Composable
private fun LayoutThumbnail(
    layout: HomeLayout,
    artMode: HomeArtMode,
    accent: Color,
    artUrl: String?
) {
    Box(
        modifier = Modifier
            .size(width = 62.dp, height = 100.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF111111))
    ) {
        // Art extent follows the art mode, so the chips visibly change the card.
        val artFraction = when (artMode) {
            HomeArtMode.AVATAR -> 0f
            HomeArtMode.FRAMED -> 0.56f
            HomeArtMode.FULL -> 1f
        }

        if (artFraction > 0f) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeightFraction(artFraction)) {
                ThumbnailArt(artUrl)
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                    )
                )
            }
        }

        when (layout) {
            HomeLayout.FOCUS_ENGINE -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, accent, CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }

            HomeLayout.SIGNAL_FEED -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(9.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.55f))
                    )
                }
            }

            HomeLayout.GLASS_STACK -> {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.55f))
                    )
                }
            }

            else -> {
                // Classic Core and Full-Bleed Art both end on a wide glowing pill.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .height(11.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.55f))
                )
            }
        }
    }
}

@Composable
private fun ThumbnailArt(artUrl: String?) {
    if (artUrl != null) {
        AsyncImage(
            model = artUrl,
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

/** `fillMaxHeight` rejects a 0f fraction, which the AVATAR case would otherwise pass. */
private fun Modifier.fillMaxHeightFraction(fraction: Float): Modifier =
    if (fraction >= 1f) this.then(Modifier.fillMaxSize())
    else this.then(Modifier.fillMaxHeight(fraction.coerceIn(0.01f, 1f)))

@Composable
private fun ArrangeWidgetsRow(summary: String, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HomeSurface)
            .border(1.dp, HomeBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Arrange Widgets", color = HomeTextBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, color = HomeTextMuted, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = HomeTextDim, modifier = Modifier.size(20.dp))
    }
}
