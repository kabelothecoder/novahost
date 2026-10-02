package com.novahost.app

import com.novahost.app.ui.scanner.AllocationPreset
import com.novahost.app.ui.scanner.Bias
import com.novahost.app.ui.scanner.Direction
import com.novahost.app.ui.scanner.GuardrailConfig
import com.novahost.app.ui.scanner.Guardrails
import com.novahost.app.ui.scanner.Instrument
import com.novahost.app.ui.scanner.RuleSeverity
import com.novahost.app.ui.scanner.ScanMode
import com.novahost.app.ui.scanner.ScanReading
import com.novahost.app.ui.scanner.TradePlan
import com.novahost.app.ui.scanner.TradePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays the 2026-10-02 gold scan that preceded a R1,008 account being wiped:
 * XAUUSD BUY at 4180.92, stop 4165.00, sized at 1% of the balance.
 *
 * The plan screen showed three 0.01-lot orders risking R784 -- 78% of the
 * account -- with no rule comparing the two. These pin the three things that
 * had to change: the split no longer multiplies the minimum lot, the budget
 * rule blocks, and a chart of the wrong instrument blocks.
 */
class ScannerRiskTest {

    private val zarPerUsd = 16.73
    private val balanceUsd = 1008.15 / zarPerUsd

    private fun gold(price: Double = 4180.92) = Instrument(
        symbol = "XAUUSD",
        displayName = "Gold",
        broker = "test",
        price = price,
        changePercent = 0.0,
        changePips = 0.0,
        spreadPips = -1.0,
        atrPips = 40.0,
        session = "Tokyo",
        sessionOpen = true
    )

    private fun reading(entry: Double = 4180.92, stop: Double = 4165.00) = ScanReading(
        direction = Direction.BUY,
        entry = entry,
        stop = stop,
        pattern = "Ascending Triangle",
        trendTimeframe = "H4",
        trendBias = Bias.BULLISH,
        chartTimeframe = "M15",
        keyLevel = entry,
        keyLevelTouches = 3,
        timeframes = emptyList(),
        events = emptyList(),
        narrative = "",
        chartAgeMinutes = 0
    )

    private fun plan(
        instrument: Instrument = gold(),
        balance: Double = balanceUsd,
        riskPercent: Double = 1.0,
        r: ScanReading = reading()
    ): TradePlan = TradePlanner.build(
        instrument = instrument,
        reading = r,
        balance = balance,
        riskPercent = riskPercent,
        preset = AllocationPreset.BALANCED,
        mode = ScanMode.DAY
    )!!

    private fun evaluate(p: TradePlan, r: ScanReading = reading(), standIn: Boolean = false) =
        Guardrails.evaluate(
            plan = p,
            reading = r,
            config = GuardrailConfig.Defaults,
            consecutiveLosses = 0,
            calendarAvailable = true,
            balanceIsStandIn = standIn
        )

    @Test fun `the minimum lot is one order, not three`() {
        val p = plan()
        assertEquals(0.01, p.totalLots, 1e-9)
        assertEquals(listOf(0.01, 0.0, 0.0), p.legs.map { it.lots })
        assertEquals(1, p.fundedLegs.size)
        // What is sent is what the header says.
        assertEquals(p.totalLots, p.fundedLegs.sumOf { it.lots }, 1e-9)
    }

    @Test fun `legs always sum to the sized position`() {
        for (balance in listOf(500.0, 2_000.0, 6_000.0, 25_000.0)) {
            for (preset in AllocationPreset.values()) {
                val p = TradePlanner.build(gold(), reading(), balance, 1.0, preset, ScanMode.DAY)!!
                assertEquals("$balance $preset", p.totalLots, p.legs.sumOf { it.lots }, 1e-9)
            }
        }
    }

    @Test fun `a plan risking a quarter of the account is blocked`() {
        val report = evaluate(plan())
        val budget = report.outcomes.first { it.title.startsWith("Risk <=") }
        assertEquals(RuleSeverity.BLOCK, budget.severity)
        assertTrue(report.blocked)
        // 0.01 lots on a 159.2-pip gold stop is $15.92 of a ~$60 account.
        // Formatted in the device locale, so a South African handset prints 26,4%.
        assertEquals("26.4%", budget.reading.replace(',', '.'))
        assertFalse(report.blockers.first().retunable)
    }

    @Test fun `a plan inside the budget passes the budget rule`() {
        val report = evaluate(plan(balance = 10_000.0))
        val budget = report.outcomes.first { it.title.startsWith("Risk <=") }
        assertEquals(RuleSeverity.PASS, budget.severity)
    }

    @Test fun `a stand-in balance blocks rather than sizing a real order`() {
        val report = evaluate(plan(balance = 10_000.0), standIn = true)
        val budget = report.outcomes.first { it.title.startsWith("Risk <=") }
        assertEquals(RuleSeverity.BLOCK, budget.severity)
    }

    @Test fun `a gold chart scanned as NAS100 is blocked`() {
        val nas = gold(price = 25_000.0).copy(symbol = "NAS100", displayName = "Nasdaq")
        val r = reading(entry = 4180.4, stop = 4183.5).copy(direction = Direction.SELL)
        val report = evaluate(plan(instrument = nas, balance = 10_000.0, r = r), r)
        val scale = report.outcomes.first { it.title == "Chart is NAS100" }
        assertEquals(RuleSeverity.BLOCK, scale.severity)
        assertTrue(scale.voidsPlan)
    }

    @Test fun `a current gold chart matches the gold quote`() {
        val report = evaluate(plan(balance = 10_000.0))
        val scale = report.outcomes.first { it.title == "Chart is XAUUSD" }
        assertEquals(RuleSeverity.PASS, scale.severity)
    }
}
