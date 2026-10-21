package cn.qingheng.app

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.os.*
import android.provider.Settings
import java.time.Instant
import java.time.ZoneId

/** Optional phone sensor counter; it never reads Mi Fitness private data or claims to match a wearable. */
class PhoneStepsService : Service(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var store: Store
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private var registered = false
    private var lastNotice = 0L
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        worker = HandlerThread("phone-steps").apply { start() }; handler = Handler(worker.looper)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("phone_steps", "手机本机计步", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!prefs.getBoolean("phone_steps_enabled", false) || (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)) {
            prefs.edit().putBoolean("phone_steps_enabled", false).apply()
            stopSelf(); return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(71, notice("计步已开启 · 从启用后记录"), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            else startForeground(71, notice("计步已开启 · 从启用后记录"))
            if (!registered) {
                val sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                registered = sensor != null && sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
                if (!registered) error("step_counter_unavailable")
            }
        } catch (e: Exception) {
            prefs.edit().putBoolean("phone_steps_enabled", false).putString("phone_steps_error", "本机计步未能启动（${e.javaClass.simpleName}），请检查身体活动权限、传感器和系统后台设置。").apply()
            stopSelf(); return START_NOT_STICKY
        }
        prefs.edit().remove("phone_steps_error").apply()
        return START_STICKY
    }
    private fun notice(message: String): Notification {
        val open = PendingIntent.getActivity(this, 71, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "phone_steps").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("轻衡 · 手机本机计步")
            .setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).build()
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("phone_steps_enabled", false)) return
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = event.values.firstOrNull()?.toDouble() ?: return
        if (!raw.isFinite() || raw < 0 || raw >= Long.MAX_VALUE.toDouble()) return
        val elapsed = (SystemClock.elapsedRealtimeNanos() - event.timestamp).coerceAtLeast(0)
        val instant = Instant.now().minusNanos(elapsed)
        val day = instant.atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val boot = try { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) } catch (_: Exception) { -1 }
        val count = try { store.recordPhoneCounter(PhoneCounter(raw.toLong(), day, boot), System.currentTimeMillis()) }
        catch (e: Exception) {
            getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("phone_steps_enabled", false)
                .putString("phone_steps_error", "本机计步保存失败（${e.javaClass.simpleName}），已停止；请检查存储空间后重新开启。").apply()
            stopSelf(); return
        }
        if (SystemClock.elapsedRealtime() - lastNotice >= 60000 || lastNotice == 0L) {
            getSystemService(NotificationManager::class.java).notify(71, notice("$day 已记录 $count 步 · 不是小米/手环总步数"))
            lastNotice = SystemClock.elapsedRealtime()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onDestroy() {
        sensors.unregisterListener(this)
        handler.post { store.close(); worker.quitSafely() }
        super.onDestroy()
    }
}
