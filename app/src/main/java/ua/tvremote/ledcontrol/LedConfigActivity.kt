package ua.tvremote.ledcontrol

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
        binding.btnCheckScriptNow.setOnClickListener { checkScriptNow() }
        binding.btnSave.setOnClickListener { save() }
    }

    /**
     * Script mode is never polled in the background (see MonitorService) — su for a custom
     * command only runs here, once, when the user explicitly asks to check it, and once more
     * automatically right after Save. This button lets them see the result immediately instead
     * of waiting for the next reboot.
     */
    private fun checkScriptNow() {
        val command = binding.editScript.text?.toString()?.trim().orEmpty()
        if (command.isBlank()) return
        binding.btnCheckScriptNow.isEnabled = false
        Thread {
            val on = Shell.exec(command).ok
            runOnUiThread {
                binding.btnCheckScriptNow.isEnabled = true
                Toast.makeText(
                    this,
                    if (on) R.string.script_check_on else R.string.script_check_off,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun setupModeGroup() {
        binding.radioOff.text = getString(R.string.mode_off)
        binding.radioOn.text = getString(R.string.mode_on)
        binding.radioAppActive.text = getString(R.string.mode_app_active)
        binding.radioCondition.text = getString(R.string.mode_condition)
        binding.radioScript.text = getString(R.string.mode_script)
        binding.radioTime.text = getString(R.string.mode_time)
        binding.radioGroupMode.setOnCheckedChangeListener { _, _ -> updateVisibility() }

        binding.radioConditionInternet.text = getString(R.string.condition_internet)
        binding.radioConditionStorage.text = getString(R.string.condition_storage)
        binding.radioConditionLauncher.text = getString(R.string.condition_launcher)
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

    /**
     * UX: a fully custom time input (see TimeStepper.kt) instead of any stock Android widget —
     * Up/Down buttons around a big number, each independently focusable so the TV remote's
     * D-pad always works reliably.
     */
    private fun setupTimePickers() {
        binding.stepperFromHour.maxValue = 23
        binding.stepperToHour.maxValue = 23
        binding.stepperFromMinute.maxValue = 59
        binding.stepperToMinute.maxValue = 59
    }

    private fun applyConfigToUi() {
        when (cfg.mode) {
            LedMode.OFF -> binding.radioOff.isChecked = true
            LedMode.ON -> binding.radioOn.isChecked = true
            LedMode.APP_ACTIVE -> binding.radioAppActive.isChecked = true
            LedMode.CONDITION -> binding.radioCondition.isChecked = true
            LedMode.SCRIPT -> binding.radioScript.isChecked = true
            LedMode.TIME -> binding.radioTime.isChecked = true
        }
        when (cfg.condition) {
            BuiltInCondition.INTERNET -> binding.radioConditionInternet.isChecked = true
            BuiltInCondition.REMOVABLE_STORAGE -> binding.radioConditionStorage.isChecked = true
            BuiltInCondition.LAUNCHER_APP -> binding.radioConditionLauncher.isChecked = true
        }
        // Each LED gets its own starter template pre-filled when no script was saved yet.
        binding.editScript.setText(cfg.scriptCommand.ifBlank { ledId.defaultScriptTemplate() })
        binding.stepperFromHour.value = cfg.timeFromMinutes / 60
        binding.stepperFromMinute.value = cfg.timeFromMinutes % 60
        binding.stepperToHour.value = cfg.timeToMinutes / 60
        binding.stepperToMinute.value = cfg.timeToMinutes % 60
    }

    private fun updateVisibility() {
        binding.groupApps.visibility =
            if (binding.radioAppActive.isChecked) View.VISIBLE else View.GONE
        binding.groupCondition.visibility =
            if (binding.radioCondition.isChecked) View.VISIBLE else View.GONE
        binding.groupScript.visibility =
            if (binding.radioScript.isChecked) View.VISIBLE else View.GONE
        binding.groupTime.visibility =
            if (binding.radioTime.isChecked) View.VISIBLE else View.GONE
    }

    private fun save() {
        cfg.mode = when (binding.radioGroupMode.checkedRadioButtonId) {
            binding.radioOn.id -> LedMode.ON
            binding.radioAppActive.id -> LedMode.APP_ACTIVE
            binding.radioCondition.id -> LedMode.CONDITION
            binding.radioScript.id -> LedMode.SCRIPT
            binding.radioTime.id -> LedMode.TIME
            else -> LedMode.OFF
        }
        cfg.condition = when (binding.radioGroupCondition.checkedRadioButtonId) {
            binding.radioConditionStorage.id -> BuiltInCondition.REMOVABLE_STORAGE
            binding.radioConditionLauncher.id -> BuiltInCondition.LAUNCHER_APP
            else -> BuiltInCondition.INTERNET
        }
        cfg.scriptCommand = binding.editScript.text?.toString()?.trim() ?: ""
        cfg.timeFromMinutes = binding.stepperFromHour.value * 60 + binding.stepperFromMinute.value
        cfg.timeToMinutes = binding.stepperToHour.value * 60 + binding.stepperToMinute.value
        repo.saveConfig(cfg)

        // One single su call right now to apply the new script immediately — MonitorService
        // will NOT pick this LED up on its own repeating tick (Script mode is excluded from
        // it by design, to avoid spamming su in a loop); it only gets re-evaluated again on
        // the next real reboot.
        if (cfg.mode == LedMode.SCRIPT) {
            val toApply = cfg
            Thread {
                val on = ConditionUtils.shouldBeOn(this, toApply)
                Shell.writeAttr(toApply.id.sysfsPath, if (on) "1" else "0")
                repo.setLastApplied(toApply.id, on)
            }.start()
        }

        finish()
    }

    companion object {
        const val EXTRA_LED_ID = "extra_led_id"
        private const val REQ_PICK_APPS = 100
    }
}
