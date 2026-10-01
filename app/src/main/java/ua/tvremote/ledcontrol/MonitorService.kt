package ua.tvremote.ledcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Живе у фоні, стежить за динамічними режимами LED (App Active / Script / Time)
 * та застосовує збережений стан при старті (у т.ч. після перезавантаження приставки).
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: LedRepository
    private val periodMs = 5_000L

    private val tick = object : Runnable {
        override fun run() {
            applyAll()
            handler.postDelayed(this, periodMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = LedRepository(this)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyDisplayStateOnBoot()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    /** Викликається один раз при старті сервісу: звіряє бажаний стан дисплея з реальністю. */
    private fun applyDisplayStateOnBoot() {
        // sysfs скидається на дефолт (увімкнено) після кожного reboot.
        // Якщо користувач раніше хотів дисплей вимкненим — знову подаємо команду вимкнення.
        if (!repo.isDisplayDesiredOn()) {
            Shell.writeAttr("${VFD_BASE}/$DISPLAY_ATTR", DISPLAY_OFF_VALUE)
        }
        // Якщо бажаний стан ON — нічого не робимо, бо після reboot дисплей і так увімкнений,
        // і чекати перезапуску більше не потрібно.
        if (repo.isDisplayDesiredOn()) {
            repo.setDisplayAwaitingRestart(false)
        }
    }

    private fun applyAll() {
        if (!Shell.hasRoot()) return
        // Якщо дисплей повністю вимкнений — інші LED все одно фізично не видно,
        // але команди все одно шлемо, щоб стан зберігався коректно на момент увімкнення.
        for (cfg in repo.allConfigs()) {
            val shouldBeOn = ConditionUtils.shouldBeOn(this, cfg)
            if (cfg.lastAppliedOn != shouldBeOn) {
                Shell.writeAttr(cfg.id.sysfsPath, if (shouldBeOn) "1" else "0")
                repo.setLastApplied(cfg.id, shouldBeOn)
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "led_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                getString(R.string.monitor_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            mgr.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.monitor_running))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIF_ID = 42
    }
}
