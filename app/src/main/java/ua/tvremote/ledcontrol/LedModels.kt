package ua.tvremote.ledcontrol

/** sysfs-база для всіх атрибутів meson-vfd */
const val VFD_BASE = "/sys/devices/platform/meson-vfd/attr"

/** Головний LED / дисплей керується тим самим атрибутом "led",
 *  але вимкнення робиться спеціальним значенням "tcd1" і вимагає перезапуску приставки. */
const val DISPLAY_ATTR = "led"
const val DISPLAY_OFF_VALUE = "tcd1"

enum class LedId(val attr: String, val nameRes: Int) {
    GREEN("greenled", R.string.led_green),
    WLAN("wlanled", R.string.led_wlan),
    ETHERNET("ethernetled", R.string.led_ethernet),
    USB("usbled", R.string.led_usb),
    CARD("cardled", R.string.led_card),
    APP("appled", R.string.led_app),
    AGING("agingled", R.string.led_aging);

    val sysfsPath: String get() = "$VFD_BASE/$attr"
}

enum class LedMode {
    OFF, ON, APP_ACTIVE, SCRIPT, TIME
}

enum class ScriptType {
    INTERNET,   // світиться, якщо є інтернет
    SDCARD,     // світиться, якщо вставлена SD-карта / зовнішній накопичувач
    LAUNCHER    // світиться, якщо зараз активний головний лаунчер (Apps)
}

data class LedConfig(
    val id: LedId,
    var mode: LedMode = LedMode.OFF,
    var selectedApps: MutableSet<String> = mutableSetOf(), // package name-и для APP_ACTIVE
    var scriptType: ScriptType = ScriptType.INTERNET,
    var timeFromMinutes: Int = 0,   // хвилини з півночі
    var timeToMinutes: Int = 0,
    var lastAppliedOn: Boolean? = null // фізичний стан, який востаннє застосовано (кеш, щоб не спамити su)
)
