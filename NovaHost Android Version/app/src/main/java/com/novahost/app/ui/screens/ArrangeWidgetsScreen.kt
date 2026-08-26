package com.novahost.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.novahost.app.ui.home.HomeWidget
import com.novahost.app.ui.home.onArtFloor
import com.novahost.app.ui.theme.HomeBorder
import com.novahost.app.ui.theme.HomeCanvas
import com.novahost.app.ui.theme.HomeSurface
import com.novahost.app.ui.theme.HomeSurfaceRaised
import com.novahost.app.ui.theme.HomeTextBright
import com.novahost.app.ui.theme.HomeTextDim
import com.novahost.app.ui.theme.HomeTextFaint
import com.novahost.app.ui.theme.HomeTextMuted
import com.novahost.app.ui.theme.LocalNovaHostTheme
import com.novahost.app.ui.viewmodels.HomeViewModel
import kotlin.math.roundToInt

/** Every reorderable row is this tall, which is what makes drop-index arithmetic possible. */
private val ROW_HEIGHT = 64.dp
private val ROW_GAP = 8.dp

/**
 * Order and visibility, saved per layout.
 *
 * Reordering gets its own screen rather than a section on the picker: with all
 * five layouts reorderable, drag is the feature, and it needs the room.
 */
@Composable
fun ArrangeWidgetsScreen(
    navController: NavController,
    homeViewModel: HomeViewModel
) {
    val customization by homeViewModel.customization.collectAsState()
    val layout = customization.layout
    val arrangement = customization.active
    val accent = LocalNovaHostTheme.current.primaryColor.onArtFloor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeCanvas)
            .verticalScroll(rememberScrollState())
    ) {
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
            Text("Arrange Widgets", color = HomeTextBright, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Restore,
                contentDescription = "Reset arrangement",
                tint = HomeTextDim,
                modifier = Modifier.size(20.dp).clickable { homeViewModel.resetArrangements() }
            )
        }

        Text(
            "Order and visibility save per layout. You are editing ${layout.displayName}. Hero and ignition are pinned in all five.",
            color = HomeTextDim,
            fontSize = 11.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 16.dp)
        )

        SectionLabel("PINNED", Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP)
        ) {
            HomeWidget.pinnedWidgets.forEach { PinnedRow(it) }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("HOLD A HANDLE TO REORDER", Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(8.dp))

        ReorderableList(
            widgets = arrangement.order,
            hidden = arrangement.hidden,
            accent = accent,
            modifier = Modifier.padding(horizontal = 22.dp),
            onMove = { widget, index -> homeViewModel.moveWidget(layout, widget, index) },
            onToggle = { homeViewModel.toggleWidgetVisible(layout, it) }
        )

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HomeSurface)
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = HomeTextFaint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Prefs are local. Reinstalling the APK clears your arrangement and colours.",
                color = HomeTextMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = HomeTextFaint,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier
    )
}

@Composable
private fun PinnedRow(widget: HomeWidget) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(HomeSurface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = "Pinned", tint = HomeTextFaint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(widget.displayName, color = HomeTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(widget.subtitle, color = HomeTextFaint, fontSize = 10.sp)
        }
    }
}

/**
 * Drag-to-reorder over a fixed-height list.
 *
 * Rows are a known height, so the drop index is the drag distance divided by
 * the row pitch -- no per-item bounds tracking and no measurement pass. The
 * dragged row draws at its finger offset above the others while every other row
 * shifts by exactly one slot, which is what makes the drop position legible
 * before the finger lifts.
 */
@Composable
private fun ReorderableList(
    widgets: List<HomeWidget>,
    hidden: Set<HomeWidget>,
    accent: Color,
    modifier: Modifier = Modifier,
    onMove: (HomeWidget, Int) -> Unit,
    onToggle: (HomeWidget) -> Unit
) {
    val density = LocalDensity.current
    val pitchPx = with(density) { (ROW_HEIGHT + ROW_GAP).toPx() }
    val haptics = LocalHapticFeedback.current

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val fromIndex = draggingIndex
    val slotShift = if (fromIndex != null) (dragOffset / pitchPx).roundToInt() else 0
    val targetIndex = if (fromIndex != null) {
        (fromIndex + slotShift).coerceIn(0, widgets.lastIndex.coerceAtLeast(0))
    } else {
        null
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        widgets.forEachIndexed { index, widget ->
            val isDragging = index == fromIndex

            // Rows between the source and the target slide one slot to make room.
            val displacement = when {
                fromIndex == null || targetIndex == null || isDragging -> 0f
                index in (fromIndex + 1)..targetIndex -> -pitchPx
                index in targetIndex until fromIndex -> pitchPx
                else -> 0f
            }

            WidgetRow(
                widget = widget,
                visible = widget !in hidden,
                accent = accent,
                isDragging = isDragging,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = if (isDragging) dragOffset.roundToInt() else displacement.roundToInt()
                        )
                    },
                onToggle = { onToggle(widget) },
                dragHandleModifier = Modifier.pointerInput(widgets, index) {
                    // Long-press first, not an immediate drag. This list sits
                    // inside a vertically scrolling screen, and an immediate
                    // drag races the page scroll for the same touch slop --
                    // sometimes reordering, sometimes scrolling the page. The
                    // press also gives the row somewhere to announce itself
                    // (haptic + accent border) before anything moves.
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingIndex = index
                            dragOffset = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                        },
                        onDragEnd = {
                            val from = draggingIndex
                            val to = (from ?: 0) + (dragOffset / pitchPx).roundToInt()
                            draggingIndex = null
                            dragOffset = 0f
                            if (from != null && to != from) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMove(widget, to)
                            }
                        },
                        onDragCancel = {
                            draggingIndex = null
                            dragOffset = 0f
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun WidgetRow(
    widget: HomeWidget,
    visible: Boolean,
    accent: Color,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    dragHandleModifier: Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clip(shape)
            .background(if (isDragging) HomeSurfaceRaised else HomeSurface)
            .border(1.dp, if (isDragging) accent else HomeBorder, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The handle's touch target is the 40dp box, not the 18dp glyph -- an
        // icon-sized drag target is the difference between reordering working
        // and the list feeling broken.
        Box(
            modifier = Modifier.size(40.dp).then(dragHandleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.DragIndicator,
                contentDescription = "Reorder ${widget.displayName}",
                tint = if (isDragging) accent else HomeTextFaint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                widget.displayName,
                color = if (visible) HomeTextBright else HomeTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(widget.subtitle, color = HomeTextFaint, fontSize = 10.sp)
        }
        VisibilitySwitch(checked = visible, accent = accent, onToggle = onToggle)
    }
}

/**
 * A compact switch. Material3's Switch is 52dp wide with its own touch padding,
 * which does not fit next to a drag handle in a 64dp row.
 */
@Composable
private fun VisibilitySwitch(checked: Boolean, accent: Color, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(CircleShape)
            .background(if (checked) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (checked) accent else HomeBorder, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) Color.Black.copy(alpha = 0.85f) else HomeTextFaint)
        )
    }
}
