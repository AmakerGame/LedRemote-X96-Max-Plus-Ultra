package ua.tvremote.ledcontrol

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

/**
 * All activities in the app extend this instead of AppCompatActivity directly.
 *
 * BUG FIX: applying the chosen locale only in LedApp (the Application class) is NOT enough —
 * each Activity resolves its own strings/layouts through its own base Context, independent
 * of the Application's configuration override. Without this, picking a language in
 * LanguageActivity had no visible effect anywhere. Overriding attachBaseContext() here makes
 * every screen pick up the chosen language correctly.
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LedApp.applyLocale(newBase))
    }
}
