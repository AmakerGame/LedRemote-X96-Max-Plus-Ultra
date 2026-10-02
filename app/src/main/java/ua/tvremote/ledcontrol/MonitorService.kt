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
 * Runs in the background, watches the dynamic LED modes (App Active / Script / Time)
 * and restores the saved state on start (including after the box reboots).
 *
 * IMPORTANT about root: su is only ever invoked in two situations —
 *  1) on a REAL device reboot, to resync state after sysfs resets to defaults;
 *  2) when a LED's desired state actually changed (a real echo command into sysfs).
 * The monitoring loop itself (checking App Active / Script / Time conditions) never
 * touches root — it only reads Android APIs (UsageStats, time), so it does not spawn
 * su every tick and does not trigger repeated root prompts.
 *
 * BUG FIX: this service can also be (re)started by MainActivity every time the app is
 * opened in the foreground — that is NOT a device reboot. Re-issuing the "disable
 * display" command or clearing the "awaiting restart" flag on every such start used to
 * happen unconditionally and was wrong (extra needless su calls, and the pending-restart
 * status could silently reset just by reopening the app). It is now gated strictly behind
 * [LedRepository.consumeBootResyncNeeded], which only becomes true on a genuine
 * BOOT_COMPLETED broadcast handled by [BootReceiver].
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
        // Only touch su / reset caches when this start was triggered by a real reboot.
        // A plain app-foreground restart of the service must NOT spend any root calls.
        if (repo.consumeBootResyncNeeded()) {
            repo.clearAllLastApplied()
            reapplyDisplayStateAfterReboot()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    /** Single su call, only after a confirmed real reboot: reconciles the display state. */
    private fun reapplyDisplayStateAfterReboot() {
        // sysfs resets to its default (display on) after every reboot.
        // If the user had previously disabled the display, re-issue the disable command.
        if (!repo.isDisplayDesiredOn()) {
            Shell.writeAttr("$VFD_BASE/$DISPLAY_ATTR", DISPLAY_OFF_VALUE)
        } else {
            // Desired state is ON — after a reboot the display is on by default,
            // so the "awaiting restart" pending status can finally be cleared.
            repo.setDisplayAwaitingRestart(false)
        }
    }

    /**
     * Runs every 5s, but su is only executed when a state actually needs to change.
     * While the VFD display itself is off, nothing on the box is visible anyway, so the
     * secondary LEDs are left untouched entirely (saves root calls); they get resynced
     * automatically on the next real reboot via [reapplyDisplayStateAfterReboot] plus the
     * forced cache resync above.
     */
    private fun applyAll() {
        if (!repo.isDisplayDesiredOn()) return

        for (cfg in repo.allConfigs()) {
            val shouldBeOn = ConditionUtils.shouldBeOn(this, cfg)
            if (cfg.lastAppliedOn != shouldBeOn) {
                Shell.writeAttr(cfg.id.sysfsPath, if (shouldBeOn) "1" else "0")
                repo.setLastApplied(cfg.id, shouldBeOn)
            }
        }
    }

    // NOTE: previously this method also called Shell.hasRoot() on every single tick
    // (every 5s), which spawned a new su session constantly and was the root cause of
    // root-access prompts firing in a loop. That check has been removed — su is now only
    // invoked above, and only when a write is actually needed.

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
