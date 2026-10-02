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

            val svc = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        }
    }
}
