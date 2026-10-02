package ua.tvremote.ledcontrol

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ua.tvremote.ledcontrol.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.about_title)

        binding.txtAppName.text = getString(R.string.app_name)
        binding.txtVersionValue.text = versionLabel()
        binding.txtBuildValue.text = getString(R.string.about_build_value)
        binding.txtDeveloperValue.text = getString(R.string.about_developer_value)
        binding.txtGithubLink.text = GITHUB_URL

        binding.txtGithubLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        }
    }

    /** Version is read directly from the APK (PackageInfo), never hardcoded in code. */
    private fun versionLabel(): String {
        return try {
            val pm = packageManager
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
            else @Suppress("DEPRECATION") pi.versionCode.toLong()
            "${pi.versionName} ($code)"
        } catch (e: Exception) {
            getString(R.string.about_version_label)
        }
    }

    companion object {
        const val GITHUB_URL = "https://github.com/AmakerGame/LedRemote-X96-Max-Plus-Ultra"
    }
}
