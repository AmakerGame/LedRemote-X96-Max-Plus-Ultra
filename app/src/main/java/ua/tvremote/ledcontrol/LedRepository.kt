package ua.tvremote.ledcontrol

import android.content.Context

/**
 * Зберігає бажані налаштування кожного LED та бажаний стан дисплея.
 * Після перезапуску приставки все скидається на заводські значення (sysfs),
 * тому MonitorService при старті звіряє збережений стан з фактичним і перезастосовує його.
 */
class LedRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("led_state", Context.MODE_PRIVATE)

    // ---------- Другорядні LED ----------

    fun getConfig(id: LedId): LedConfig {
        val p = "led_${id.name}_"
        val mode = runCatching { LedMode.valueOf(prefs.getString(p + "mode", LedMode.OFF.name)!!) }
            .getOrDefault(LedMode.OFF)
        val script = runCatching { ScriptType.valueOf(prefs.getString(p + "script", ScriptType.INTERNET.name)!!) }
            .getOrDefault(ScriptType.INTERNET)
        val apps = prefs.getStringSet(p + "apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        val from = prefs.getInt(p + "time_from", 0)
        val to = prefs.getInt(p + "time_to", 0)
        val lastAppliedRaw = prefs.getInt(p + "last_applied", -1)
        val lastApplied = when (lastAppliedRaw) { 1 -> true; 0 -> false; else -> null }
        return LedConfig(id, mode, apps, script, from, to, lastApplied)
    }

    fun saveConfig(cfg: LedConfig) {
        val p = "led_${cfg.id.name}_"
        prefs.edit()
            .putString(p + "mode", cfg.mode.name)
            .putString(p + "script", cfg.scriptType.name)
            .putStringSet(p + "apps", cfg.selectedApps)
            .putInt(p + "time_from", cfg.timeFromMinutes)
            .putInt(p + "time_to", cfg.timeToMinutes)
            .apply()
    }

    fun setLastApplied(id: LedId, on: Boolean) {
        prefs.edit().putInt("led_${id.name}_last_applied", if (on) 1 else 0).apply()
    }

    fun allConfigs(): List<LedConfig> = LedId.entries.map { getConfig(it) }

    // ---------- Дисплей (головний led / tcd1) ----------

    /** Бажаний стан дисплея, що вибрав користувач: true = увімкнено, false = вимкнено. */
    fun isDisplayDesiredOn(): Boolean = prefs.getBoolean("display_desired_on", true)

    fun setDisplayDesiredOn(on: Boolean) {
        prefs.edit().putBoolean("display_desired_on", on).apply()
    }

    /** Чи очікує застосована команда вимкнення дисплея перезапуску, щоб знову стало ON. */
    fun isDisplayAwaitingRestart(): Boolean = prefs.getBoolean("display_awaiting_restart", false)

    fun setDisplayAwaitingRestart(v: Boolean) {
        prefs.edit().putBoolean("display_awaiting_restart", v).apply()
    }
}
