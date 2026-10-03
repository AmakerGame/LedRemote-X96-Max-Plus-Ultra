package ua.tvremote.ledcontrol

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class LedApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        applyLocale(this)
    }

    companion object {
        const val PREFS = "led_remote_prefs"
        const val KEY_LANG = "app_language" // "uk" | "ru" | "en"
        val SUPPORTED_LANGS = setOf("uk", "ru", "en")

        /**
         * Resolves which language to use: the explicitly chosen one if the user picked one
         * in Settings, otherwise the device's current language if it's one we support —
         * and English as the fallback for any other (unsupported) device language, instead
         * of silently falling back to the base `values/` resources (Ukrainian).
         */
        fun resolveLanguage(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val chosen = prefs.getString(KEY_LANG, null)
            if (chosen != null) return chosen
            val deviceLang = Locale.getDefault().language
            return if (deviceLang in SUPPORTED_LANGS) deviceLang else "en"
        }

        fun applyLocale(context: Context): Context {
            val locale = Locale(resolveLanguage(context))
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }

        fun setLocale(context: Context, lang: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANG, lang).apply()
        }
    }
}
