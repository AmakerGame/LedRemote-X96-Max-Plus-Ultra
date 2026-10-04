package ua.tvremote.ledcontrol

import android.content.Context

/**
 * Stores the desired configuration of each LED and the desired display state.
 * After the box reboots everything resets to factory sysfs values, so MonitorService
 * reconciles the saved state against reality on start and reapplies it
 * (see markBootResyncNeeded / consumeBootResyncNeeded / clearAllLastApplied).
 */
class LedRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("led_state", Context.MODE_PRIVATE)

    // ---------- Secondary LEDs ----------

    fun getConfig(id: LedId): LedConfig {
        val p = "led_${id.name}_"
        val mode = runCatching { LedMode.valueOf(prefs.getString(p + "mode", LedMode.OFF.name)!!) }
            .getOrDefault(LedMode.OFF)
        val apps = prefs.getStringSet(p + "apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        val from = prefs.getInt(p + "time_from", 0)
        val to = prefs.getInt(p + "time_to", 0)
        val condition = runCatching {
            BuiltInCondition.valueOf(prefs.getString(p + "condition", BuiltInCondition.INTERNET.name)!!)
        }.getOrDefault(BuiltInCondition.INTERNET)
        val scriptCmd = prefs.getString(p + "script_cmd", "") ?: ""
        val lastAppliedRaw = prefs.getInt(p + "last_applied", -1)
        val lastApplied = when (lastAppliedRaw) { 1 -> true; 0 -> false; else -> null }
        return LedConfig(id, mode, apps, from, to, condition, scriptCmd, lastApplied)
    }

    fun saveConfig(cfg: LedConfig) {
        val p = "led_${cfg.id.name}_"
        prefs.edit()
            .putString(p + "mode", cfg.mode.name)
            .putStringSet(p + "apps", cfg.selectedApps)
            .putInt(p + "time_from", cfg.timeFromMinutes)
            .putInt(p + "time_to", cfg.timeToMinutes)
            .putString(p + "condition", cfg.condition.name)
            .putString(p + "script_cmd", cfg.scriptCommand)
            .apply()
    }

    fun setLastApplied(id: LedId, on: Boolean) {
        prefs.edit().putInt("led_${id.name}_last_applied", if (on) 1 else 0).apply()
    }

    /** Clears the "last applied" cache for ALL LEDs (call only right after a real reboot). */
    fun clearAllLastApplied() {
        val editor = prefs.edit()
        LedId.entries.forEach { editor.remove("led_${it.name}_last_applied") }
        editor.apply()
    }

    fun allConfigs(): List<LedConfig> = LedId.entries.map { getConfig(it) }

    // ---------- Display (main led / tcd1) ----------

    /** The display state the user wants: true = enabled, false = disabled. */
    fun isDisplayDesiredOn(): Boolean = prefs.getBoolean("display_desired_on", true)

    fun setDisplayDesiredOn(on: Boolean) {
        prefs.edit().putBoolean("display_desired_on", on).apply()
    }

    /** Whether the display-disable command is waiting on a reboot to turn back ON. */
    fun isDisplayAwaitingRestart(): Boolean = prefs.getBoolean("display_awaiting_restart", false)

    fun setDisplayAwaitingRestart(v: Boolean) {
        prefs.edit().putBoolean("display_awaiting_restart", v).apply()
    }

    // ---------- Resync after a real device reboot ----------

    /** Set by BootReceiver on every genuine BOOT_COMPLETED broadcast. */
    fun markBootResyncNeeded() {
        prefs.edit().putBoolean("boot_resync_needed", true).apply()
    }

    /** Reads and immediately clears the flag — fires exactly once per real reboot. */
    fun consumeBootResyncNeeded(): Boolean {
        val needed = prefs.getBoolean("boot_resync_needed", false)
        if (needed) prefs.edit().putBoolean("boot_resync_needed", false).apply()
        return needed
    }
}
