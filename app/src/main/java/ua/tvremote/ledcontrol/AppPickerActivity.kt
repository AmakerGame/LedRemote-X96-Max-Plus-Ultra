package ua.tvremote.ledcontrol

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import ua.tvremote.ledcontrol.databinding.ActivityAppPickerBinding

/**
 * Full app picker: unlike the old inline list (which only showed apps with a launcher icon),
 * this lists EVERY installed app via PackageManager.getInstalledApplications(), split into
 * "User apps" / "System apps" tabs. The selection (shared MutableSet) persists across tab
 * switches, and the current selection is always returned on both Save and back-press.
 */
class AppPickerActivity : BaseActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var adapter: AppSelectAdapter
    private val selected = mutableSetOf<String>()
    private var userApps: List<AppEntry> = emptyList()
    private var systemApps: List<AppEntry> = emptyList()
    private var showingSystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.select_apps_title)

        selected.addAll(intent.getStringArrayListExtra(EXTRA_SELECTED) ?: emptyList())
        loadApps()

        adapter = AppSelectAdapter(userApps, selected)
        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerApps.adapter = adapter

        binding.btnTabUser.setOnClickListener { switchTab(false) }
        binding.btnTabSystem.setOnClickListener { switchTab(true) }
        binding.btnDone.setOnClickListener { finishWithResult() }
        updateTabButtons()
    }

    override fun onBackPressed() {
        finishWithResult()
    }

    private fun loadApps() {
        val pm = packageManager
        val entries = pm.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .map { ai ->
                AppEntry(
                    packageName = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
        userApps = entries.filter { !it.isSystem }
        systemApps = entries.filter { it.isSystem }
    }

    private fun switchTab(system: Boolean) {
        showingSystem = system
        adapter.updateList(if (system) systemApps else userApps)
        updateTabButtons()
    }

    private fun updateTabButtons() {
        binding.btnTabUser.isSelected = !showingSystem
        binding.btnTabSystem.isSelected = showingSystem
        binding.btnTabUser.alpha = if (showingSystem) 0.55f else 1f
        binding.btnTabSystem.alpha = if (showingSystem) 1f else 0.55f
    }

    private fun finishWithResult() {
        val data = Intent()
        data.putStringArrayListExtra(EXTRA_SELECTED, ArrayList(selected))
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_SELECTED = "extra_selected"
    }
}
