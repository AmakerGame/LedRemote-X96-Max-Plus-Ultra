package ua.tvremote.ledcontrol

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import ua.tvremote.ledcontrol.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: LedRepository
    private var rootGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = LedRepository(this)

        binding.btnRetryRoot.setOnClickListener { checkRootAndInit() }
        binding.btnLanguage.setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
        }
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        binding.btnUsageAccess.setOnClickListener {
            ConditionUtils.openUsageAccessSettings(this)
        }

        binding.recyclerLeds.layoutManager = LinearLayoutManager(this)

        checkRootAndInit()
    }

    override fun onResume() {
        super.onResume()
        // Usage-access status needs no root at all, so it must be checked unconditionally —
        // it used to be gated behind `rootGranted`, which is still false on the very first
        // resume (the root check thread hasn't returned yet), so the button could stay stuck
        // in its initial state until the user backgrounded and reopened the app once more.
        refreshUsageAccessButton()
        if (rootGranted) {
            updateDisplayStatusText()
            binding.recyclerLeds.adapter?.notifyDataSetChanged()
        }
    }

    private fun refreshUsageAccessButton() {
        binding.btnUsageAccess.visibility =
            if (ConditionUtils.hasUsageAccess(this)) View.GONE else View.VISIBLE
    }

    private fun checkRootAndInit() {
        binding.rootBlock.visibility = View.VISIBLE
        binding.mainContent.visibility = View.GONE
        binding.txtRootStatus.text = getString(R.string.checking_root)

        Thread {
            val granted = Shell.hasRoot()
            runOnUiThread {
                rootGranted = granted
                if (granted) {
                    binding.rootBlock.visibility = View.GONE
                    binding.mainContent.visibility = View.VISIBLE
                    setupLedList()
                    updateDisplayStatusText()
                    refreshUsageAccessButton()
                    startMonitorService()
                } else {
                    binding.rootBlock.visibility = View.VISIBLE
                    binding.mainContent.visibility = View.GONE
                    binding.txtRootStatus.text = getString(R.string.grant_root)
                }
            }
        }.start()
    }

    private fun startMonitorService() {
        val svc = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }

    private fun setupLedList() {
        val items = mutableListOf<LedListItem>()
        items.add(LedListItem.Display)
        LedId.entries.forEach { items.add(LedListItem.Secondary(it)) }

        binding.recyclerLeds.adapter = LedListAdapter(items, repo) { item ->
            when (item) {
                is LedListItem.Display -> startActivity(Intent(this, DisplayControlActivity::class.java))
                is LedListItem.Secondary -> onSecondaryLedClicked(item.id)
            }
        }
    }

    private fun onSecondaryLedClicked(id: LedId) {
        if (!repo.isDisplayDesiredOn()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.display_off_title)
                .setMessage(R.string.display_off_message)
                .setPositiveButton(R.string.go_to_display) { _, _ ->
                    startActivity(Intent(this, DisplayControlActivity::class.java))
                }
                .setNegativeButton(R.string.configure_anyway) { _, _ ->
                    openLedConfig(id)
                }
                .show()
        } else {
            openLedConfig(id)
        }
    }

    private fun openLedConfig(id: LedId) {
        val intent = Intent(this, LedConfigActivity::class.java)
        intent.putExtra(LedConfigActivity.EXTRA_LED_ID, id.name)
        startActivity(intent)
    }

    private fun updateDisplayStatusText() {
        binding.txtDisplayStatus.text = if (repo.isDisplayDesiredOn()) {
            getString(R.string.display_status_on)
        } else {
            getString(R.string.display_status_off)
        }
    }
}

sealed class LedListItem {
    object Display : LedListItem()
    data class Secondary(val id: LedId) : LedListItem()
}
