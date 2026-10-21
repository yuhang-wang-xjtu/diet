package cn.qingheng.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class FormulaSex(val label: String, val offset: Double) { MALE("男", 5.0), FEMALE("女", -161.0) }
enum class ActivityLevel(val label: String, val pal: Double) {
    LOW("较少活动 · PAL 1.4", 1.4), LIGHT("轻度活动 · PAL 1.6", 1.6),
    MODERATE("中度活动 · PAL 1.8", 1.8), HIGH("较多活动 · PAL 2.0", 2.0)
}
data class Profile(val date: String, val age: Int, val sex: FormulaSex, val heightCm: Double, val activity: ActivityLevel)
data class BodyEstimate(val resting: Double, val expenditure: Double, val bmi: Double) {
    val protein get() = expenditure * 0.20 / 4
    val carbs get() = expenditure * 0.50 / 4
    val fat get() = expenditure * 0.30 / 9
}
object ProfileMath {
    fun at(profiles: List<Profile>, date: String) = profiles.filter { it.date <= date }.maxByOrNull { it.date }
    // Without a birthday the anniversary of entering age is used; editing the profile corrects this approximation.
    fun ageAt(profile: Profile, date: String): Int = profile.age + ChronoUnit.YEARS.between(LocalDate.parse(profile.date), LocalDate.parse(date)).toInt()
    fun estimate(profile: Profile?, weights: List<Weight>, date: String): BodyEstimate? {
        if (profile == null || profile.date > date) return null
        val age = ageAt(profile, date)
        val weight = ExerciseMath.weightAt(weights, date) ?: return null
        if (age !in 19..78 || !profile.heightCm.isFinite() || profile.heightCm !in 100.0..250.0 || !weight.isFinite() || weight !in 20.0..500.0) return null
        val resting = 10 * weight + 6.25 * profile.heightCm - 5 * age + profile.sex.offset
        return if (resting <= 0) null else BodyEstimate(resting, resting * profile.activity.pal, weight / (profile.heightCm / 100).let { it * it })
    }
}
