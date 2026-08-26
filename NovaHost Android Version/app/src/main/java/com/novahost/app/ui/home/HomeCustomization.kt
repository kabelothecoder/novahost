package com.novahost.app.ui.home

import android.content.Context
import com.novahost.app.sdk.NovaPrefs

/**
 * The five home interfaces.
 *
 * All five read the same Home state. Layout is presentation only -- a running
 * bot survives a layout switch, which is why the run state lives in
 * [com.novahost.app.ui.viewmodels.HomeViewModel] and not in a `remember` inside
 * whichever layout happens to be composed.
 */
enum class HomeLayout(
    val displayName: String,
    val blurb: String,
    /** Shown as a warning chip in the picker when the layout needs data the app cannot supply yet. */
    val needsPlumbing: Boolean = false
) {
    CLASSIC_CORE(
        displayName = "Classic Core",
        blurb = "Today's arrangement, cleaned up. Avatar, name, ignition, widgets below."
    ),
    FOCUS_ENGINE(
        displayName = "Focus Engine",
        blurb = "Ignition and name. Nothing else. Your most screenshot-able screen."
    ),
    FULL_BLEED_ART(
        displayName = "Full-Bleed Art",
        blurb = "Mentor art edge to edge. Controls float on a scrim."
    ),
    GLASS_STACK(
        displayName = "Glass Stack",
        blurb = "Frosted panels, mentor art reading through. The competitor look."
    ),
    SIGNAL_FEED(
        displayName = "Signal Feed",
        blurb = "Mentor signals + fleet status. Replaces Trader Pro — no MetaAPI equity yet.",
        needsPlumbing = true
    );

    companion object {
        /**
         * The marketing screen ships first: art dominant, robot name legible at
         * thumbnail size, one obvious glowing control.
         */
        val Default = FULL_BLEED_ART

        fun fromNameOrDefault(value: String?): HomeLayout =
            entries.firstOrNull { it.name == value } ?: Default
    }
}

/**
 * How much of the screen the mentor's art is allowed to take, per layout.
 *
 * Stored per layout rather than globally: Full-Bleed Art with [AVATAR] is a
 * different screen from Classic Core with [AVATAR], and a user who tunes one
 * has not asked to change the other.
 */
enum class HomeArtMode(val displayName: String) {
    /** A circular crop in the hero. The art never leaves its container. */
    AVATAR("Avatar"),
    /** Art fills a band at the top of the screen, content stacks below it. */
    FRAMED("Framed"),
    /** Edge to edge behind everything, with a scrim carrying the contrast floor. */
    FULL("Full");

    companion object {
        fun fromNameOrNull(value: String?): HomeArtMode? =
            entries.firstOrNull { it.name == value }
    }
}

/**
 * The blocks a layout can stack, in the order Arrange Widgets presents them.
 *
 * [ROBOT_HERO] and [IGNITION_POD] are pinned in all five layouts -- a home
 * screen with no robot identity and no way to start is not a layout, it is a
 * bug the user configured for themselves.
 */
enum class HomeWidget(
    val displayName: String,
    val subtitle: String,
    val pinned: Boolean = false
) {
    ROBOT_HERO("Robot Hero", "Art, name, mentor credit", pinned = true),
    IGNITION_POD("Ignition Pod", "Quotes · Start/Stop · Asset Hub", pinned = true),
    CONNECTED_ROBOTS("Connected Robots", "Licence-bound robot list"),
    CHART_SCANNER("AI Chart Scanner", "Screenshot dropzone"),
    ACTIVITY_LOG("Terminal Activity Log", "Pulse service events");

    companion object {
        fun fromNameOrNull(value: String?): HomeWidget? =
            entries.firstOrNull { it.name == value }

        /** Everything the user can drag or hide, in default order. */
        val reorderable: List<HomeWidget> get() = entries.filter { !it.pinned }

        val pinnedWidgets: List<HomeWidget> get() = entries.filter { it.pinned }
    }
}

/**
 * One layout's arrangement: which optional widgets it shows and in what order.
 *
 * [order] holds every reorderable widget, hidden ones included, so toggling a
 * widget back on returns it to where the user last dragged it rather than to
 * the bottom of the list.
 */
data class LayoutArrangement(
    val artMode: HomeArtMode,
    val order: List<HomeWidget>,
    val hidden: Set<HomeWidget>
) {
    val visibleWidgets: List<HomeWidget> get() = order.filter { it !in hidden }
    val shownCount: Int get() = order.size - hidden.size
    val hiddenCount: Int get() = hidden.size
}

/**
 * The whole home customization: the active layout plus every layout's own
 * arrangement.
 */
data class HomeCustomization(
    val layout: HomeLayout = HomeLayout.Default,
    val arrangements: Map<HomeLayout, LayoutArrangement> = emptyMap()
) {
    fun arrangementFor(layout: HomeLayout): LayoutArrangement =
        arrangements[layout] ?: defaultArrangementFor(layout)

    val active: LayoutArrangement get() = arrangementFor(layout)
}

/**
 * What each layout looks like before the user touches it.
 *
 * These are not all the same on purpose. Focus Engine's whole argument is that
 * nothing competes with the ignition, so it ships with every optional widget
 * hidden; Signal Feed leads with the log because that is the screen's subject.
 */
fun defaultArrangementFor(layout: HomeLayout): LayoutArrangement = when (layout) {
    HomeLayout.CLASSIC_CORE -> LayoutArrangement(
        artMode = HomeArtMode.AVATAR,
        order = listOf(
            HomeWidget.CONNECTED_ROBOTS,
            HomeWidget.CHART_SCANNER,
            HomeWidget.ACTIVITY_LOG
        ),
        hidden = setOf(HomeWidget.CHART_SCANNER, HomeWidget.ACTIVITY_LOG)
    )

    HomeLayout.FOCUS_ENGINE -> LayoutArrangement(
        artMode = HomeArtMode.FULL,
        order = HomeWidget.reorderable,
        // "Ignition and name. Nothing else."
        hidden = HomeWidget.reorderable.toSet()
    )

    HomeLayout.FULL_BLEED_ART -> LayoutArrangement(
        artMode = HomeArtMode.FULL,
        order = listOf(
            HomeWidget.CONNECTED_ROBOTS,
            HomeWidget.CHART_SCANNER,
            HomeWidget.ACTIVITY_LOG
        ),
        // The marketing screen is judged above the fold. Widgets exist, they
        // just start below the scroll rather than on top of the art.
        hidden = setOf(HomeWidget.ACTIVITY_LOG)
    )

    HomeLayout.GLASS_STACK -> LayoutArrangement(
        artMode = HomeArtMode.FRAMED,
        order = listOf(
            HomeWidget.CONNECTED_ROBOTS,
            HomeWidget.CHART_SCANNER,
            HomeWidget.ACTIVITY_LOG
        ),
        hidden = setOf(HomeWidget.CHART_SCANNER)
    )

    HomeLayout.SIGNAL_FEED -> LayoutArrangement(
        artMode = HomeArtMode.AVATAR,
        order = listOf(
            HomeWidget.ACTIVITY_LOG,
            HomeWidget.CONNECTED_ROBOTS,
            HomeWidget.CHART_SCANNER
        ),
        hidden = setOf(HomeWidget.CHART_SCANNER)
    )
}

// ── Persistence ────────────────────────────────────────────────────────────
// Device-local, via NovaPrefs. Names that no longer resolve to an enum entry
// are dropped and the default fills the gap, so renaming a layout or widget in
// a later build degrades quietly instead of throwing on the first read.

fun loadHomeCustomization(context: Context): HomeCustomization {
    val layout = HomeLayout.fromNameOrDefault(NovaPrefs.getHomeLayout(context))
    val arrangements = HomeLayout.entries.associateWith { loadArrangement(context, it) }
    return HomeCustomization(layout = layout, arrangements = arrangements)
}

private fun loadArrangement(context: Context, layout: HomeLayout): LayoutArrangement {
    val fallback = defaultArrangementFor(layout)

    val artMode = HomeArtMode.fromNameOrNull(NovaPrefs.getArtMode(context, layout.name))
        ?: fallback.artMode

    // The raw stored order is what says whether this layout has ever been
    // arranged. Read it once: deciding that from the *parsed* list would treat
    // a saved order of renamed widgets as "never arranged" and quietly restore
    // the defaults over the user's hidden set.
    val rawOrder = NovaPrefs.getWidgetOrder(context, layout.name)
    if (rawOrder.isEmpty()) {
        return LayoutArrangement(artMode = artMode, order = fallback.order, hidden = fallback.hidden)
    }

    val storedOrder = rawOrder.mapNotNull { HomeWidget.fromNameOrNull(it) }.filter { !it.pinned }
    // A widget added in a later build has no entry in the stored order. Append
    // it rather than dropping it -- otherwise the user can never reach it.
    val order = storedOrder + fallback.order.filter { it !in storedOrder }

    val hidden = NovaPrefs.getHiddenWidgets(context, layout.name)
        .mapNotNull { HomeWidget.fromNameOrNull(it) }
        .toSet()

    return LayoutArrangement(artMode = artMode, order = order, hidden = hidden)
}

fun saveActiveLayout(context: Context, layout: HomeLayout) {
    NovaPrefs.setHomeLayout(context, layout.name)
}

fun saveArrangement(context: Context, layout: HomeLayout, arrangement: LayoutArrangement) {
    NovaPrefs.setArtMode(context, layout.name, arrangement.artMode.name)
    NovaPrefs.setWidgetOrder(context, layout.name, arrangement.order.map { it.name })
    NovaPrefs.setHiddenWidgets(context, layout.name, arrangement.hidden.map { it.name }.toSet())
}
