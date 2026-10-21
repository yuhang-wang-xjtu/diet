package cn.qingheng.app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StepsClient(private val context: Context) {
    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    fun status(): Int = if (Build.VERSION.SDK_INT < 28) HealthConnectClient.SDK_UNAVAILABLE else HealthConnectClient.getSdkStatus(context)
    fun available() = status() == HealthConnectClient.SDK_AVAILABLE
    private fun client() = HealthConnectClient.getOrCreate(context)
    suspend fun permitted() = available() && client().permissionController.getGrantedPermissions().containsAll(permissions)
    suspend fun read(date: LocalDate, origin: String? = null): StepsRead {
        check(permitted()) { "步数读取未授权" }
        val range = ExerciseMath.dayRange(date, Instant.now(), ZoneId.systemDefault()) ?: return StepsRead(null, emptySet())
        // Single-source selection is explicit. Never sum raw records or combine it with the deduplicated total.
        val filter = StepsPolicy.origin(origin)?.let { setOf(DataOrigin(it)) } ?: emptySet()
        val result = client().aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(range.first, range.second), dataOriginFilter = filter))
        return StepsRead(result[StepsRecord.COUNT_TOTAL], result.dataOrigins.map { it.packageName }.toSet())
    }
    /** Raw records are used only to discover writers, not to calculate steps. Bounded for quota safety. */
    suspend fun sources(date: LocalDate): StepsSources {
        check(permitted()) { "步数读取未授权" }
        val range = ExerciseMath.dayRange(date, Instant.now(), ZoneId.systemDefault()) ?: return StepsSources(emptySet(), true)
        val start = date.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val packages = mutableSetOf<String>(); val seen = mutableSetOf<String>()
        var token: String? = null
        repeat(10) {
            val page = client().readRecords(ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(start, range.second), pageSize = 1000, pageToken = token))
            packages.addAll(page.records.map { it.metadata.dataOrigin.packageName })
            val next = StepsPolicy.nextPage(page.pageToken, seen)
            if (next == null) return StepsSources(packages, page.pageToken.isNullOrEmpty())
            seen.add(next); token = next
        }
        return StepsSources(packages, false)
    }
    fun settingsIntent() = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
}
