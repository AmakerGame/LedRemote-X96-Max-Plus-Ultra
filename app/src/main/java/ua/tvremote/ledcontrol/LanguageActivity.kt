package ua.tvremote.ledcontrol

import android.content.Intent
import android.os.Bundle
import ua.tvremote.ledcontrol.databinding.ActivityLanguageBinding

class LanguageActivity : BaseActivity() {

    private lateinit var binding: ActivityLanguageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUk.setOnClickListener { setLang("uk") }
        binding.btnRu.setOnClickListener { setLang("ru") }
        binding.btnEn.setOnClickListener { setLang("en") }
    }

    private fun setLang(code: String) {
        LedApp.setLocale(this, code)
        // BUG FIX: CLEAR_TOP alone can just bring an existing MainActivity instance back to
        // front via onNewIntent() instead of recreating it — and attachBaseContext() (where
        // the new locale is applied) only runs once, at Activity creation. CLEAR_TASK forces
        // the whole task to be torn down and MainActivity to be freshly created, so the new
        // language actually takes effect immediately instead of requiring a manual app restart.
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
