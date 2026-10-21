package cn.qingheng.app

data class StepsRead(val count: Long?, val origins: Set<String>) {
    // Some providers return zero without a contributing origin when no record exists.
    val availableCount: Long? get() = count?.takeUnless { it == 0L && origins.isEmpty() }
}
object StepsPolicy {
    fun origin(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
    fun source(raw: String?) = origin(raw)?.let { "health_connect:$it" } ?: "health_connect"
    fun isHealthSource(source: String) = source == "health_connect" || source.startsWith("health_connect:")
    fun nextPage(token: String?, seen: Set<String>): String? = token?.takeIf { it.isNotEmpty() && it !in seen }
}
data class StepsSources(val packages: Set<String>, val complete: Boolean)
