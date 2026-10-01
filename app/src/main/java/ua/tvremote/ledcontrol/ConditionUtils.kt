package ua.tvremote.ledcontrol

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    /** Пакет застосунку, що зараз реально відкритий на екрані (не у фоні). */
    fun currentForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 15_000
        val events = usm.queryEvents(begin, end)
        var lastPkg: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    fun defaultLauncherPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = context.packageManager.resolveActivity(intent, 0)
        return resolve?.activityInfo?.packageName
    }

    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun hasSdCard(context: Context): Boolean {
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
            // діапазон через північ, напр. 22:00-06:00
            nowMinutes >= fromMinutes || nowMinutes <= toMinutes
        }
    }

    /** Чи має світитися конкретний LED зараз, згідно з його налаштуваннями. */
    fun shouldBeOn(context: Context, cfg: LedConfig): Boolean {
        return when (cfg.mode) {
            LedMode.OFF -> false
            LedMode.ON -> true
            LedMode.APP_ACTIVE -> {
                val fg = currentForegroundPackage(context)
                fg != null && cfg.selectedApps.contains(fg)
            }
            LedMode.SCRIPT -> when (cfg.scriptType) {
                ScriptType.INTERNET -> hasInternet(context)
                ScriptType.SDCARD -> hasSdCard(context)
                ScriptType.LAUNCHER -> {
                    val fg = currentForegroundPackage(context)
                    val home = defaultLauncherPackage(context)
                    fg != null && home != null && fg == home
                }
            }
            LedMode.TIME -> isWithinTimeRange(cfg.timeFromMinutes, cfg.timeToMinutes)
        }
    }
}
