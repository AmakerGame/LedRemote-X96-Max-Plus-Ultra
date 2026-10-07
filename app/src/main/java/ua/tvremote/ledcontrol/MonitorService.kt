package ua.tvremote.ledcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Runs in the background, watches the dynamic LED modes (App Active / Condition / Time) and
 * restores the saved state on start (including after the box reboots).
 *
 * Condition mode (Internet / removable storage / launcher app) is deliberately included in the
 * regular tick below, unlike Script mode: it reads plain Android APIs and needs no root to
 * check, so it's safe to poll every 5s and reacts live to real events (e.g. plugging in an SD
 * card) — the one-shot limitation described for Script mode does not apply to it.
 *
 * IMPORTANT about root: su is only ever invoked in these situations —
 *  1) on a REAL device reboot, to resync state after sysfs resets to defaults;
 *  2) when a LED's desired state actually changed (a real echo command into sysfs);
 *  3) once per Script-mode LED, also only on that same real reboot (see applyScriptModesOnce) —
 *     never on the repeating tick.
 * The App Active / Time checks never touch root at all — they only read Android APIs
 * (UsageStats, the clock).
 *
 * BUG FIX (script mode hammering su): Script mode runs a user-supplied shell command to decide
 * on/off, which genuinely requires su to evaluate — unlike every other mode. It used to be
 * evaluated inside the same 5s tick as everything else, which meant su was invoked in an
 * infinite loop for every LED using Script mode (visible as constant superuser activity /
 * repeated root grants in Magisk). Script-mode LEDs are now excluded from the tick entirely and
 * are only (re)evaluated once: right after a real reboot, and immediately when the user saves a
 * script in LedConfigActivity. Between those two moments nothing touches root for them at all.
 *
 * BUG FIX: this service can also be (re)started by MainActivity every time the app is opened in
 * the foreground — that is NOT a device reboot. Re-issuing the "disable display" command,
 * clearing the "awaiting restart" flag, or re-running scripts on every such start used to
 * happen unconditionally and was wrong (extra needless su calls). It is now gated strictly
 * behind [LedRepository.consumeBootResyncNeeded], which only becomes true on a genuine
 * BOOT_COMPLETED broadcast handled by [BootReceiver].
 */
class MonitorService : Service() {

    // BUG FIX: the tick used to run on the main Looper. Checking "is there real internet" now
    // does an actual network probe (see ConditionUtils.hasInternet), which is blocking I/O and
    // would throw NetworkOnMainThreadException there — and the su calls this loop already made
    // were blocking the main thread the whole time anyway, which is simply the wrong thread for
    // any of this. Everything here now runs on its own background HandlerThread instead.
    private val handlerThread = HandlerThread("LedMonitorThread").apply { start() }
    private val handler = Handler(handlerThread.looper)
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
            applyScriptModesOnce()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handlerThread.quitSafely()
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
     * Evaluates every Script-mode LED's command exactly ONCE (one su call each) and writes the
     * result. Only called right after a real reboot — never from the repeating tick. A user can
     * also trigger this same one-shot evaluation on demand for a single LED from
     * LedConfigActivity's "Check now" button when saving a script.
     */
    private fun applyScriptModesOnce() {
        if (!repo.isDisplayDesiredOn()) return
        for (cfg in repo.allConfigs()) {
            if (cfg.mode != LedMode.SCRIPT) continue
            val shouldBeOn = ConditionUtils.shouldBeOn(this, cfg)
            Shell.writeAttr(cfg.id.sysfsPath, if (shouldBeOn) "1" else "0")
            repo.setLastApplied(cfg.id, shouldBeOn)
        }
    }

    /**
     * Runs every 5s, but su is only executed when a state actually needs to change.
     * Script-mode LEDs are skipped here entirely (see class doc) — only App Active / Time are
     * evaluated on this loop, and neither of those needs root just to check its condition.
     * While the VFD display itself is off, nothing on the box is visible anyway, so the
     * secondary LEDs are left untouched entirely (saves root calls); they get resynced
     * automatically on the next real reboot via [reapplyDisplayStateAfterReboot] plus the
     * forced cache resync above.
     */
    private fun applyAll() {
        if (!repo.isDisplayDesiredOn()) return

        for (cfg in repo.allConfigs()) {
            if (cfg.mode == LedMode.SCRIPT) continue
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
