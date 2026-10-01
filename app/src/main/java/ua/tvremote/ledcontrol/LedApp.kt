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

        fun applyLocale(context: Context): Context {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lang = prefs.getString(KEY_LANG, Locale.getDefault().language.ifBlank { "uk" }) ?: "uk"
            val locale = Locale(lang)
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
