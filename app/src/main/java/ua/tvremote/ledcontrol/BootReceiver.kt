package ua.tvremote.ledcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // A real device boot — sysfs has definitely been reset to its defaults.
            // Mark that MonitorService must forcibly reapply the saved state, ignoring
            // the stale "lastApplied" cache.
            LedRepository(context).markBootResyncNeeded()
            startMonitorServiceReliably(context)
        }
    }

    /**
     * Starts MonitorService using the normal Android API, AND — since this app already
     * requires root anyway — also issues a root "am" command as a fallback a moment later.
     *
     * Some custom Android TV firmwares (including SlimBoxTV-style builds) impose background
     * service start restrictions that can make a plain BOOT_COMPLETED → startForegroundService()
     * silently fail to actually bring the service to a running foreground state, even though no
     * exception is thrown. Launching it again via `am start-foreground-service` through su
     * bypasses those OS-level background-start restrictions entirely, since it's not subject to
     * the same caller-process rules a BroadcastReceiver is. Calling start twice is harmless —
     * MonitorService.onStartCommand() is idempotent (it just (re)schedules its own tick loop).
     */
    private fun startMonitorServiceReliably(context: Context) {
        val svc = Intent(context, MonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (e: Exception) {
            // Ignore — the root fallback below covers this case too.
        }

        Thread {
            Shell.exec("am start-foreground-service -n ${context.packageName}/.MonitorService")
        }.start()
    }
}
