package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class StepsPolicyTest {
    @Test fun emptyAggregationDoesNotOverwriteCacheWithZero() {
        assertNull(StepsRead(null, emptySet()).availableCount)
        assertNull(StepsRead(0L, emptySet()).availableCount)
        assertEquals(0L, StepsRead(0L, setOf("writer")).availableCount)
        assertEquals(900L, StepsRead(900L, emptySet()).availableCount)
    }
    @Test fun selectedSourceIsDistinctFromSystemTotalAndManual() {
        assertEquals("health_connect", StepsPolicy.source(null))
        assertEquals("health_connect", StepsPolicy.source(" "))
        assertEquals("health_connect:com.xiaomi.wearable", StepsPolicy.source(" com.xiaomi.wearable "))
        assertTrue(StepsPolicy.isHealthSource("health_connect"))
        assertTrue(StepsPolicy.isHealthSource("health_connect:com.mi.health"))
        assertFalse(StepsPolicy.isHealthSource("manual"))
        assertFalse(StepsPolicy.isHealthSource("phone_sensor"))
    }
    @Test fun paginationStopsForBothTerminalTokensAndRepeatedTokens() {
        assertNull(StepsPolicy.nextPage(null, emptySet()))
        assertNull(StepsPolicy.nextPage("", emptySet()))
        assertNull(StepsPolicy.nextPage("A", setOf("A")))
        assertEquals("B", StepsPolicy.nextPage("B", setOf("A")))
    }
}
