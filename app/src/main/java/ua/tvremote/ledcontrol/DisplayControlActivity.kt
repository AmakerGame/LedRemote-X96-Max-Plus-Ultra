package ua.tvremote.ledcontrol

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ua.tvremote.ledcontrol.databinding.ActivityDisplayControlBinding

/**
 * Дисплей (VFD) керується тим самим "led" атрибутом, але вимкнення робиться
 * спеціальним значенням "tcd1" і після цього дисплей сам не повертається —
 * потрібен реальний перезапуск приставки. Тому тут лише дві дії: Увімкнути / Вимкнути,
 * без режимів App Active / Script / Time, які є у решти LED.
 */
class DisplayControlActivity : AppCompatActivity() {

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
        if (wasOff) {
            // Реальне повернення зображення на VFD можливе лише після повного reboot,
            // просто записом атрибута дисплей назад не вмикається.
            Toast.makeText(this, R.string.restart_required, Toast.LENGTH_LONG).show()
        }
        refreshStatus()
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
        binding.txtStatus.text = if (on) getString(R.string.display_status_on)
        else getString(R.string.display_status_off)
        binding.btnRebootNow.isEnabled = !on
    }
}
