package ua.tvremote.ledcontrol

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TimePicker
import ua.tvremote.ledcontrol.databinding.ActivityLedConfigBinding

class LedConfigActivity : BaseActivity() {

    private lateinit var binding: ActivityLedConfigBinding
    private lateinit var repo: LedRepository
    private lateinit var ledId: LedId
    private lateinit var cfg: LedConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLedConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = LedRepository(this)

        ledId = LedId.valueOf(intent.getStringExtra(EXTRA_LED_ID) ?: LedId.GREEN.name)
        cfg = repo.getConfig(ledId)
        title = getString(ledId.nameRes)

        setupModeGroup()
        setupTimePickers()
        applyConfigToUi()
        updateVisibility()
        updateSelectedAppsSummary()

        binding.btnSelectApps.setOnClickListener { openAppPicker() }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun setupModeGroup() {
        binding.radioOff.text = getString(R.string.mode_off)
        binding.radioOn.text = getString(R.string.mode_on)
        binding.radioAppActive.text = getString(R.string.mode_app_active)
        binding.radioScript.text = getString(R.string.mode_script)
        binding.radioTime.text = getString(R.string.mode_time)
        binding.radioGroupMode.setOnCheckedChangeListener { _, _ -> updateVisibility() }
    }

    private fun openAppPicker() {
        val intent = Intent(this, AppPickerActivity::class.java)
        intent.putStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED, ArrayList(cfg.selectedApps))
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_PICK_APPS)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_APPS && resultCode == Activity.RESULT_OK) {
            val picked = data?.getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED) ?: arrayListOf()
            cfg.selectedApps = picked.toMutableSet()
            updateSelectedAppsSummary()
        }
    }

    private fun updateSelectedAppsSummary() {
        binding.txtSelectedAppsSummary.text = if (cfg.selectedApps.isEmpty()) {
            getString(R.string.no_apps_selected)
        } else {
            resources.getQuantityString(
                R.plurals.apps_selected_count,
                cfg.selectedApps.size,
                cfg.selectedApps.size
            )
        }
    }

    private fun setupTimePickers() {
        binding.timeFrom.setIs24HourView(true)
        binding.timeTo.setIs24HourView(true)
    }

    private fun applyConfigToUi() {
        when (cfg.mode) {
            LedMode.OFF -> binding.radioOff.isChecked = true
            LedMode.ON -> binding.radioOn.isChecked = true
            LedMode.APP_ACTIVE -> binding.radioAppActive.isChecked = true
            LedMode.SCRIPT -> binding.radioScript.isChecked = true
            LedMode.TIME -> binding.radioTime.isChecked = true
        }
        // Each LED gets its own starter template pre-filled when no script was saved yet.
        binding.editScript.setText(cfg.scriptCommand.ifBlank { ledId.defaultScriptTemplate() })
        setTimePicker(binding.timeFrom, cfg.timeFromMinutes)
        setTimePicker(binding.timeTo, cfg.timeToMinutes)
    }

    private fun setTimePicker(picker: TimePicker, minutes: Int) {
        picker.hour = minutes / 60
        picker.minute = minutes % 60
    }

    private fun minutesOf(picker: TimePicker): Int = picker.hour * 60 + picker.minute

    private fun updateVisibility() {
        binding.groupApps.visibility =
            if (binding.radioAppActive.isChecked) View.VISIBLE else View.GONE
        binding.groupScript.visibility =
            if (binding.radioScript.isChecked) View.VISIBLE else View.GONE
        binding.groupTime.visibility =
            if (binding.radioTime.isChecked) View.VISIBLE else View.GONE
    }

    private fun save() {
        cfg.mode = when (binding.radioGroupMode.checkedRadioButtonId) {
            binding.radioOn.id -> LedMode.ON
            binding.radioAppActive.id -> LedMode.APP_ACTIVE
            binding.radioScript.id -> LedMode.SCRIPT
            binding.radioTime.id -> LedMode.TIME
            else -> LedMode.OFF
        }
        cfg.scriptCommand = binding.editScript.text?.toString()?.trim() ?: ""
        cfg.timeFromMinutes = minutesOf(binding.timeFrom)
        cfg.timeToMinutes = minutesOf(binding.timeTo)
        repo.saveConfig(cfg)
        finish()
    }

    companion object {
        const val EXTRA_LED_ID = "extra_led_id"
        private const val REQ_PICK_APPS = 100
    }
}
