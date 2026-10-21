package cn.qingheng.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object MealReminders {
    const val CHANNEL = "meal_reminders"
    const val OPEN = "cn.qingheng.app.OPEN_MEAL"
    const val DAILY = "cn.qingheng.app.MEAL_DAILY"
    const val LATER = "cn.qingheng.app.MEAL_LATER"
    const val SNOOZE = "cn.qingheng.app.MEAL_SNOOZE"
    const val SKIP = "cn.qingheng.app.MEAL_SKIP"
    private fun prefs(c: Context) = c.getSharedPreferences("meal_reminders", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", true)
    fun minutes(c: Context, meal: Meal) = prefs(c).getInt("time_${meal.key}", meal.defaultMinutes ?: 0).coerceIn(0, 1439)
    fun timeText(c: Context, meal: Meal) = minutes(c, meal).let { String.format(java.util.Locale.US, "%02d:%02d", it / 60, it % 60) }
    fun channel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "三餐记餐提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "早餐、午餐和晚餐的食物记录提醒，零食不提醒"
        })
    }
    fun notificationsAllowed(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val nm = c.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled() && nm.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun exactAllowed(c: Context) = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun setEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("enabled", value).commit()
        reschedule(c)
        if (!value) c.getSystemService(NotificationManager::class.java).cancelAll()
    }
    fun setTime(c: Context, meal: Meal, minutes: Int) {
        require(meal.remindable && minutes in 0..1439)
        prefs(c).edit().putInt("time_${meal.key}", minutes).commit()
        scheduleDaily(c, meal)
    }
    private fun alarmIntent(c: Context, meal: Meal, action: String, date: String = "", due: Long = 0): PendingIntent = PendingIntent.getBroadcast(
        c, 0, Intent(c, MealReminderReceiver::class.java).apply {
            this.action = action; data = Uri.parse("qingheng://alarm/$action/${meal.key}")
            putExtra("meal", meal.key); putExtra("date", date); putExtra("due", due)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun actionIntent(c: Context, meal: Meal, date: String, action: String): PendingIntent = PendingIntent.getBroadcast(
        c, 0, Intent(c, MealReminderReceiver::class.java).apply {
            this.action = action; data = Uri.parse("qingheng://action/$action/$date/${meal.key}")
            putExtra("meal", meal.key); putExtra("date", date)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(c: Context, at: Long, pending: PendingIntent) {
        val alarms = c.getSystemService(AlarmManager::class.java)
        if (exactAllowed(c)) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); return }
            catch (_: SecurityException) { /* Permission can be revoked between checking and scheduling. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
    fun scheduleDaily(c: Context, meal: Meal) {
        if (!enabled(c) || !meal.remindable) return
        val zone = ZoneId.systemDefault()
        val next = MealTiming.next(Instant.now(), zone, minutes(c, meal))
        val date = next.atZone(zone).toLocalDate().toString()
        schedule(c, next.toEpochMilli(), alarmIntent(c, meal, DAILY, date, next.toEpochMilli()))
    }
    /** Called after launch, boot, package replacement, time/zone changes and alarm permission grants. */
    fun reschedule(c: Context) {
        channel(c)
        val alarms = c.getSystemService(AlarmManager::class.java)
        Meal.regular.forEach { meal ->
            alarms.cancel(alarmIntent(c, meal, DAILY))
            alarms.cancel(alarmIntent(c, meal, LATER))
            if (!enabled(c)) clearSnooze(c, meal)
            else {
                scheduleDaily(c, meal)
                val p = prefs(c); val date = p.getString("date_${meal.key}", null)
                val due = p.getLong("due_${meal.key}", 0)
                if (date != null && due > 0) {
                    val resolved = Store(c).use { it.resolved(date, meal) }
                    if (resolved) clearSnooze(c, meal)
                    else schedule(c, due.coerceAtLeast(System.currentTimeMillis() + 1000), alarmIntent(c, meal, LATER, date, due))
                }
            }
        }
    }
    private fun clearSnooze(c: Context, meal: Meal) {
        c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c, meal, LATER))
        prefs(c).edit().remove("date_${meal.key}").remove("due_${meal.key}").commit()
    }
    fun resolve(c: Context, date: String, meal: Meal) {
        c.getSystemService(NotificationManager::class.java).cancel("$date:${meal.key}", 1)
        if (prefs(c).getString("date_${meal.key}", null) == date) clearSnooze(c, meal)
    }
    fun snooze(c: Context, date: String, meal: Meal) {
        if (!enabled(c) || !meal.remindable) return
        resolve(c, date, meal)
        if (Store(c).use { it.resolved(date, meal) }) return
        val due = MealTiming.snooze(Instant.now()).toEpochMilli()
        prefs(c).edit().putString("date_${meal.key}", date).putLong("due_${meal.key}", due).commit()
        schedule(c, due, alarmIntent(c, meal, LATER, date, due))
    }
    @SuppressLint("MissingPermission")
    fun show(c: Context, date: String, meal: Meal) {
        channel(c)
        if (!enabled(c) || !meal.remindable || !notificationsAllowed(c)) return
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java).apply {
            action = OPEN; data = Uri.parse("qingheng://record/$date/${meal.key}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("date", date); putExtra("meal", meal.key)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(c, CHANNEL)
            .setSmallIcon(cn.qingheng.app.R.drawable.ic_meal_notification)
            .setContentTitle("该记录${meal.label}啦")
            .setContentText("$date · 点击输入这一餐吃的食物")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setTimeoutAfter(24 * 60 * 60 * 1000L)
            .addAction(Notification.Action.Builder(null, "推迟30分钟", actionIntent(c, meal, date, SNOOZE)).build())
            .addAction(Notification.Action.Builder(null, "这一餐没吃", actionIntent(c, meal, date, SKIP)).build())
            .build()
        c.getSystemService(NotificationManager::class.java).notify("$date:${meal.key}", 1, notification)
    }
    fun notificationSettings(c: Context) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName); putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
    }
}

class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val meal = Meal.from(intent.getStringExtra("meal"))
        if (!meal.remindable) return
        val date = try { LocalDate.parse(intent.getStringExtra("date")).toString() } catch (_: Exception) { return }
        when (intent.action) {
            MealReminders.DAILY -> {
                MealReminders.scheduleDaily(context, meal)
                // Delayed alarms from a previous day do not ask the user to log the wrong day.
                if (date != LocalDate.now().toString()) return
            }
            MealReminders.LATER -> {
                val p = context.getSharedPreferences("meal_reminders", Context.MODE_PRIVATE)
                if (p.getString("date_${meal.key}", null) != date || p.getLong("due_${meal.key}", 0) != intent.getLongExtra("due", -1)) return
                MealReminders.resolve(context, date, meal)
            }
            MealReminders.SNOOZE -> { MealReminders.snooze(context, date, meal); return }
            MealReminders.SKIP -> {
                Store(context).use { it.skip(date, meal) }
                MealReminders.resolve(context, date, meal)
                return
            }
            else -> return
        }
        if (!Store(context).use { it.resolved(date, meal) }) MealReminders.show(context, date, meal)
    }
}

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) MealReminders.reschedule(context)
    }
}
