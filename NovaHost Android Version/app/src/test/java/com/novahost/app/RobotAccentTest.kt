package com.novahost.app

import androidx.compose.ui.graphics.Color
import com.novahost.app.ui.theme.SoftLightBlue
import com.novahost.app.ui.theme.parseRobotAccent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The accent comes from a mentor typing a value into the portal, so it will
 * eventually arrive blank, malformed or with odd casing. Every one of those
 * must degrade to the system blue rather than crash or render black.
 */
class RobotAccentTest {

    @Test fun `parses six digit hex with leading hash`() {
        assertEquals(Color(0xFFC9A227), parseRobotAccent("#C9A227"))
    }

    @Test fun `parses six digit hex without hash`() {
        assertEquals(Color(0xFFC9A227), parseRobotAccent("C9A227"))
    }

    @Test fun `parses lowercase and surrounding whitespace`() {
        assertEquals(Color(0xFFC9A227), parseRobotAccent("  #c9a227  "))
    }

    @Test fun `parses eight digit hex with explicit alpha`() {
        assertEquals(Color(0x80C9A227), parseRobotAccent("#80C9A227"))
    }

    @Test fun `null falls back to system blue`() {
        assertEquals(SoftLightBlue, parseRobotAccent(null))
    }

    @Test fun `blank falls back to system blue`() {
        assertEquals(SoftLightBlue, parseRobotAccent("   "))
    }

    @Test fun `colour name falls back to system blue`() {
        assertEquals(SoftLightBlue, parseRobotAccent("gold"))
    }

    @Test fun `wrong length falls back to system blue`() {
        assertEquals(SoftLightBlue, parseRobotAccent("#FFF"))
    }

    @Test fun `non hex characters fall back to system blue`() {
        assertEquals(SoftLightBlue, parseRobotAccent("#ZZZZZZ"))
    }
}
