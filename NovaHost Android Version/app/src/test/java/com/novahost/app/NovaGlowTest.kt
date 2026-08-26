package com.novahost.app

import com.novahost.app.ui.theme.NovaGlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glow is persisted as an ordinal in SharedPreferences, so it can come back
 * stale or out of range after an update that reorders or removes a level.
 * A bad stored value must fall back, never crash.
 */
class NovaGlowTest {

    @Test fun `valid ordinals map to their level`() {
        assertEquals(NovaGlow.OFF, NovaGlow.fromOrdinalOrDefault(0))
        assertEquals(NovaGlow.SOFT, NovaGlow.fromOrdinalOrDefault(1))
        assertEquals(NovaGlow.MEDIUM, NovaGlow.fromOrdinalOrDefault(2))
        assertEquals(NovaGlow.INTENSE, NovaGlow.fromOrdinalOrDefault(3))
    }

    @Test fun `out of range falls back to default`() {
        assertEquals(NovaGlow.Default, NovaGlow.fromOrdinalOrDefault(4))
        assertEquals(NovaGlow.Default, NovaGlow.fromOrdinalOrDefault(99))
        assertEquals(NovaGlow.Default, NovaGlow.fromOrdinalOrDefault(-1))
    }

    @Test fun `OFF keeps the crisp edge but removes all bloom`() {
        // OFF is a supported state, not a degraded one: no bloom, no haze,
        // but novaRim still draws the 1px border so shapes stay readable.
        assertEquals(0f, NovaGlow.OFF.bloomAlpha, 0.0001f)
        assertEquals(0f, NovaGlow.OFF.innerAlpha, 0.0001f)
    }

    @Test fun `intensity increases monotonically`() {
        val levels = listOf(NovaGlow.OFF, NovaGlow.SOFT, NovaGlow.MEDIUM, NovaGlow.INTENSE)
        levels.zipWithNext { a, b ->
            assertTrue("${b.name} must bloom more than ${a.name}", b.bloomAlpha > a.bloomAlpha)
            assertTrue("${b.name} must blur more than ${a.name}", b.blur > a.blur)
        }
    }

    @Test fun `default is medium`() {
        assertEquals(NovaGlow.MEDIUM, NovaGlow.Default)
    }
}
