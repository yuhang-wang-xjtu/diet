package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class PhoneStepsTest {
    private val first = PhoneCounter(12345L, "2026-10-20", 9)
    @Test fun firstSampleNeverImportsAllStepsSinceBoot() {
        assertEquals(0L, PhoneSteps.increment(null, first))
    }
    @Test fun countsOnlyDeltaAndRepeatedEventsAddNothing() {
        assertEquals(15L, PhoneSteps.increment(first, first.copy(raw = 12360L)))
        assertEquals(0L, PhoneSteps.increment(first, first))
    }
    @Test fun rebootAndCounterResetEstablishNewBaseline() {
        assertEquals(0L, PhoneSteps.increment(first, first.copy(raw = 13000, boot = 10)))
        assertEquals(0L, PhoneSteps.increment(first, first.copy(raw = 100)))
    }
    @Test fun midnightGapIsNotAssignedToWrongDay() {
        assertEquals(0L, PhoneSteps.increment(first, first.copy(raw = 12500, date = "2026-10-21")))
        assertEquals(0L, PhoneSteps.increment(first, first.copy(raw = 12500, date = "2026-10-19")))
    }
    @Test fun rejectsImplausibleJumpInsteadOfInventingDailyTotal() {
        assertEquals(0L, PhoneSteps.increment(first, first.copy(raw = 999999)))
        assertEquals(300000L, PhoneSteps.increment(first, first.copy(raw = first.raw + 300000)))
    }
}
