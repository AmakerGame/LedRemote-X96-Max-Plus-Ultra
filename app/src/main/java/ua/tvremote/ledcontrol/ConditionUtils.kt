package ua.tvremote.ledcontrol

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.storage.StorageManager
import java.util.Calendar

object ConditionUtils {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Package of the app that is actually open on screen right now (not just in the background).
     *
     * BUG FIX: this used to only look at events from the last 15 seconds. Android only logs a
     * MOVE_TO_FOREGROUND/ACTIVITY_RESUMED event the MOMENT focus changes — not continuously
     * while an app stays open. So as soon as an app had been open for more than ~15s, its
     * opening event fell out of that window and this function started returning null, turning
     * the LED off even though the app was still clearly on screen. Fixed by scanning a much
     * wider window (24h) and keeping the MOST RECENT foreground-transition event found in it —
     * that event's package is the current foreground app, no matter how long ago it fired.
     */
    fun currentForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 24 * 60 * 60 * 1000L
        val events = usm.queryEvents(begin, end)
        var lastPkg: String? = null
        var lastTimestamp = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                if (event.timeStamp >= lastTimestamp) {
                    lastTimestamp = event.timeStamp
                    lastPkg = event.packageName
                }
            }
        }
        return lastPkg
    }

    // The three helpers below back Condition mode (see shouldBeOn's CONDITION branch) — all
    // of them read standard Android APIs and need NO root, so they're safe to call on every
    // single MonitorService tick and react live to real events (e.g. plugging in an SD card),
    // unlike Script mode's arbitrary su-requiring command, which is only ever checked once.

    fun defaultLauncherPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = context.packageManager.resolveActivity(intent, 0)
        return resolve?.activityInfo?.packageName
    }

    /**
     * BUG FIX (round 2): dropping NET_CAPABILITY_VALIDATED alone wasn't enough — on this
     * device's firmware, ConnectivityManager's own NetworkCapabilities snapshot for the active
     * network can itself get stuck stale after a reboot (still reporting "no internet" even
     * though the box is clearly online), and nothing short of toggling Wi-Fi forces it to
     * refresh. Trusting ConnectivityManager's cached state at all is therefore not reliable
     * here. This now does a real, direct reachability probe instead — a raw TCP connect to a
     * well-known public host — which reflects actual reality regardless of what the OS's
     * connectivity cache believes. No root needed, but it IS blocking network I/O, so it must
     * run off the main thread (MonitorService now ticks on its own background thread for
     * exactly this reason).
     */
    fun hasInternet(context: Context): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 1500)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** True if any removable volume (SD card or USB OTG storage — Android reports both the
     *  same way) is currently mounted. No root needed, safe to poll continuously. */
    fun hasRemovableStorage(context: Context): Boolean {
        return try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.any { it.isRemovable && it.state == android.os.Environment.MEDIA_MOUNTED }
        } catch (e: Exception) {
            false
        }
    }

    fun isWithinTimeRange(fromMinutes: Int, toMinutes: Int): Boolean {
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (fromMinutes <= toMinutes) {
            nowMinutes in fromMinutes..toMinutes
        } else {
            // Range wraps past midnight, e.g. 22:00-06:00
            nowMinutes >= fromMinutes || nowMinutes <= toMinutes
        }
    }

    /** Whether a given LED should be lit right now, according to its configuration. */
    fun shouldBeOn(context: Context, cfg: LedConfig): Boolean {
        return when (cfg.mode) {
            LedMode.OFF -> false
            LedMode.ON -> true
            LedMode.APP_ACTIVE -> {
                val fg = currentForegroundPackage(context)
                fg != null && cfg.selectedApps.contains(fg)
            }
            // No root needed — safe to re-check every tick, reacts live to real events.
            LedMode.CONDITION -> when (cfg.condition) {
                BuiltInCondition.INTERNET -> hasInternet(context)
                BuiltInCondition.REMOVABLE_STORAGE -> hasRemovableStorage(context)
                BuiltInCondition.LAUNCHER_APP -> {
                    val fg = currentForegroundPackage(context)
                    val home = defaultLauncherPackage(context)
                    fg != null && home != null && fg == home
                }
            }
            // Each LED has its own independent script. The command runs via su;
            // exit code 0 means "on", anything else means "off".
            LedMode.SCRIPT -> {
                if (cfg.scriptCommand.isBlank()) false
                else Shell.exec(cfg.scriptCommand).ok
            }
            LedMode.TIME -> isWithinTimeRange(cfg.timeFromMinutes, cfg.timeToMinutes)
        }
    }
}
