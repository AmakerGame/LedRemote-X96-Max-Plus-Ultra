package ua.tvremote.ledcontrol

/** sysfs base path shared by all meson-vfd attributes. */
const val VFD_BASE = "/sys/devices/platform/meson-vfd/attr"

/** The main LED / display is driven by the same "led" attribute, but disabling it uses
 *  the special value "tcd1" and requires a device reboot to turn back on. */
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

/**
 * Starter script template shown (pre-filled, editable) for each LED's Script mode.
 * Every LED gets its own distinct example tied to what it typically represents — the
 * user is free to edit or replace it entirely. Exit code 0 means "turn the LED on".
 */
fun LedId.defaultScriptTemplate(): String = when (this) {
    LedId.GREEN -> "getprop sys.boot_completed | grep -q 1   # device fully booted"
    LedId.WLAN -> "ping -c1 -W1 8.8.8.8                      # Wi-Fi has internet"
    LedId.ETHERNET -> "cat /sys/class/net/eth0/operstate | grep -q up   # cable link up"
    LedId.USB -> "ls /storage/usbotg* >/dev/null 2>&1        # USB storage mounted"
    LedId.CARD -> "ls /storage/sdcard1 >/dev/null 2>&1       # SD card mounted"
    LedId.APP -> "dumpsys activity activities | grep -q mResumedActivity   # an app is on screen"
    LedId.AGING -> "true                                     # always on (test mode)"
}

data class LedConfig(
    val id: LedId,
    var mode: LedMode = LedMode.OFF,
    var selectedApps: MutableSet<String> = mutableSetOf(), // package names for APP_ACTIVE
    var timeFromMinutes: Int = 0,   // minutes since midnight
    var timeToMinutes: Int = 0,
    /** Script mode: its own independent shell script for EACH LED.
     *  Exit code 0 = turn the LED on, anything else = off. Runs via su. */
    var scriptCommand: String = "",
    var lastAppliedOn: Boolean? = null // last physically applied state (cache, to avoid spamming su)
)
