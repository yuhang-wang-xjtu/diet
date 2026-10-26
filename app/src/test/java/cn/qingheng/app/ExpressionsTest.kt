package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test

class ExpressionsTest {
    @Test fun supportsFoodEnergyConversionAndOperatorPrecedence() {
        assertEquals(17.0 / 4.18, Expressions.evaluate("17/4.18")!!, 1e-12)
        assertEquals(14.0, Expressions.evaluate("2+3*4")!!, 0.0)
        assertEquals(20.0, Expressions.evaluate("(2+3)*4")!!, 0.0)
        assertEquals(180.0, Expressions.evaluate("（120＋30）× 1.2")!!, 1e-12)
        assertEquals(-2.0, Expressions.evaluate("-6 ÷ 3")!!, 0.0)
    }
    @Test fun supportsPercentScientificNotationAndUnarySigns() {
        assertEquals(25.0, Expressions.evaluate("200*12.5%")!!, 0.0)
        assertEquals(1000.05, Expressions.evaluate("1e3+5E-2")!!, 1e-10)
        assertEquals(4.0, Expressions.evaluate("2--2")!!, 0.0)
    }
    @Test fun rejectsIncompleteUnknownTextAndNonFiniteResults() {
        for (raw in listOf("", "17/", "1/0", "0/0", "2+abc", "100克", "2(3)", "(1+2", "NaN", "Infinity", "1e999", "1,000", "2%%", "alert(1)", "2^3")) assertNull(raw, Expressions.evaluate(raw))
        assertNull(Expressions.evaluate("(".repeat(100) + "1" + ")".repeat(100)))
        assertNull(Expressions.evaluate("1+".repeat(200) + "1"))
    }
    @Test fun appliesEachFieldsRangeAfterEvaluation() {
        assertEquals(50.0, Energy.number("100/2", 100.0)!!, 0.0)
        assertNull(Energy.number("1-1", 100.0))
        assertNull(FoodMath.nonNegative("1-2", 100.0))
        assertNull(Nutrition.grams("5000+1"))
        assertEquals(0.0, Nutrition.grams("1-1")!!, 0.0)
        assertEquals(30L, Expressions.integer("60/2"))
        assertNull(Expressions.integer("5/2"))
    }
    @Test fun truncatesPresentationWithoutRoundingStoredValue() {
        val value = 17.0 / 4.18
        assertEquals("4.06", DecimalText.display(value))
        assertEquals("120", DecimalText.display(120.0))
        assertEquals("-4.06", DecimalText.display(-value))
        assertEquals(value, Expressions.evaluate("17/4.18")!!, 0.0)
        assertEquals("0", DecimalText.display(-0.0))
    }
}
