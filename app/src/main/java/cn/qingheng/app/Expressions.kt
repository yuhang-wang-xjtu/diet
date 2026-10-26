package cn.qingheng.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer

/** Arithmetic only: no scripting, function calls, units, or silently discarded characters. */
object Expressions {
    fun evaluate(raw: String): Double? = try {
        val text = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).replace('×', '*').replace('÷', '/').replace('−', '-')
        require(text.isNotEmpty() && text.length <= 256)
        Parser(text).parse().takeIf { it.isFinite() }
    } catch (_: IllegalArgumentException) { null }
    private class Parser(val text: String) {
        var index = 0
        fun skip() { while (index < text.length && text[index].isWhitespace()) index++ }
        fun take(c: Char): Boolean { skip(); return if (index < text.length && text[index] == c) { index++; true } else false }
        fun parse(): Double { val n = sum(0); skip(); require(index == text.length); return n }
        fun sum(depth: Int): Double {
            var n = product(depth)
            while (true) n = when { take('+') -> n + product(depth); take('-') -> n - product(depth); else -> return n }
        }
        fun product(depth: Int): Double {
            var n = atom(depth)
            while (true) n = when {
                take('*') -> n * atom(depth)
                take('/') -> { val divisor = atom(depth); require(divisor != 0.0); n / divisor }
                else -> return n
            }
        }
        fun atom(depth: Int): Double {
            require(depth < 32)
            var n = when {
                take('+') -> atom(depth + 1)
                take('-') -> -atom(depth + 1)
                take('(') -> sum(depth + 1).also { require(take(')')) }
                else -> {
                    skip(); val start = index
                    while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
                    if (index < text.length && text[index] in "eE") {
                        index++; if (index < text.length && text[index] in "+-") index++
                        while (index < text.length && text[index].isDigit()) index++
                    }
                    text.substring(start, index).toDoubleOrNull()?.also { require(it.isFinite()) } ?: throw IllegalArgumentException()
                }
            }
            if (take('%')) n /= 100
            require(n.isFinite()); return n
        }
    }
    fun integer(raw: String): Long? = evaluate(raw)?.takeIf { it >= 0 && it <= 1_000_000_000 && it == kotlin.math.floor(it) }?.toLong()
}
object DecimalText {
    fun display(value: Double): String = if (!value.isFinite()) "—" else BigDecimal.valueOf(value).setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
}
