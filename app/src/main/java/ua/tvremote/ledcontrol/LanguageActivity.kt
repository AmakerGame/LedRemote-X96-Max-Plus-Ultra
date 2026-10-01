package ua.tvremote.ledcontrol

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ua.tvremote.ledcontrol.databinding.ActivityLanguageBinding

class LanguageActivity : AppCompatActivity() {

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
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}
