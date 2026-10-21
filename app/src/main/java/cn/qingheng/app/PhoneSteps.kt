package cn.qingheng.app

data class PhoneCounter(val raw: Long, val date: String, val boot: Int)
object PhoneSteps {
    fun increment(previous: PhoneCounter?, current: PhoneCounter): Long {
        if (previous == null || previous.boot != current.boot || previous.date != current.date || current.raw < previous.raw) return 0
        return (current.raw - previous.raw).takeIf { it in 0..300000 } ?: 0
    }
}
