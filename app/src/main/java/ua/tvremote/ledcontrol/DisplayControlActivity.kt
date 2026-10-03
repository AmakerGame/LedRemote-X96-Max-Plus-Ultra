package ua.tvremote.ledcontrol

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import ua.tvremote.ledcontrol.databinding.ActivityDisplayControlBinding

/**
 * The display (VFD) is driven by the same "led" attribute as the main LED, but disabling it
 * uses the special value "tcd1", after which the display does not come back on its own —
 * a real device reboot is required. So this screen only exposes two actions: Enable / Disable,
 * without the App Active / Script / Time modes that the other LEDs have.
 */
class DisplayControlActivity : BaseActivity() {

    private lateinit var binding: ActivityDisplayControlBinding
    private lateinit var repo: LedRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisplayControlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = LedRepository(this)

        binding.btnEnable.setOnClickListener { onEnableClicked() }
        binding.btnDisable.setOnClickListener { onDisableClicked() }
        binding.btnRebootNow.setOnClickListener { onRebootClicked() }

        refreshStatus()
    }

    private fun onEnableClicked() {
        val wasOff = !repo.isDisplayDesiredOn()
        repo.setDisplayDesiredOn(true)
        refreshStatus()

        if (wasOff) {
            // The VFD only actually comes back on after a full reboot — simply writing
            // the attribute back does not re-enable it. We keep "awaiting restart" set
            // (it only gets cleared by a confirmed real reboot in MonitorService), so the
            // status text above honestly reflects "enabled, but pending restart" instead
            // of falsely claiming the display is already on. Offer to restart right away,
            // or let the user restart later — either way the pending state is preserved
            // and will resync automatically on the next real reboot.
            AlertDialog.Builder(this)
                .setTitle(R.string.restart_apply_title)
                .setMessage(R.string.restart_apply_message)
                .setPositiveButton(R.string.restart_now_yes) { _, _ ->
                    Thread { Shell.exec("reboot") }.start()
                }
                .setNegativeButton(R.string.restart_later, null)
                .setCancelable(false)
                .show()
        }
    }

    private fun onDisableClicked() {
        Thread {
            val result = Shell.writeAttr("$VFD_BASE/$DISPLAY_ATTR", DISPLAY_OFF_VALUE)
            runOnUiThread {
                repo.setDisplayDesiredOn(false)
                repo.setDisplayAwaitingRestart(true)
                refreshStatus()
                if (!result.ok) {
                    Toast.makeText(this, R.string.command_failed, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun onRebootClicked() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reboot_title)
            .setMessage(R.string.reboot_message)
            .setPositiveButton(R.string.reboot_confirm) { _, _ ->
                Thread { Shell.exec("reboot") }.start()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshStatus() {
        val on = repo.isDisplayDesiredOn()
        val pendingRestart = on && repo.isDisplayAwaitingRestart()
        binding.txtStatus.text = when {
            pendingRestart -> getString(R.string.display_status_pending)
            on -> getString(R.string.display_status_on)
            else -> getString(R.string.display_status_off)
        }
        binding.btnRebootNow.isEnabled = !on || pendingRestart
    }
}
