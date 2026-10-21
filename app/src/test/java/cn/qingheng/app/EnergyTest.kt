package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class EnergyTest {
    @Test fun deficitIncludesExerciseOnce() {
        val entries = listOf(Entry(date="2026-10-20", kind="food", name="午餐", kcal=800.0), Entry(date="2026-10-20", kind="food", name="晚餐", kcal=1000.0), Entry(date="2026-10-20", kind="exercise", name="跑步", kcal=300.0, minutes=40))
        val t = Energy.totals(entries, 2000.0)
        assertEquals(1800.0, t.intake, 0.001)
        assertEquals(500.0, t.deficit!!, 0.001)
        assertEquals(-200.0, Energy.totals(entries, 1300.0).deficit!!, 0.001)
    }
    @Test fun missingBaselineIsNotZero() { assertNull(Energy.totals(emptyList(), null).deficit) }
    @Test fun invalidNumbersAreRejected() {
        listOf("", "0", "-1", "NaN", "Infinity", "50001", "abc").forEach { assertNull(Energy.number(it, 50000.0)) }
        assertEquals(125.5, Energy.number(" 125.5 ", 50000.0)!!, 0.001)
    }
    @Test fun csvEscapesQuotesAndCommas() { assertEquals("\"餐,\"\"一\"\"\"", Energy.csv("餐,\"一\"")) }
}
