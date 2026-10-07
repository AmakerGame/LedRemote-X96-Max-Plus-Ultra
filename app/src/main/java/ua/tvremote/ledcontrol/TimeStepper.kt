package ua.tvremote.ledcontrol

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Our own time-value input, built from scratch instead of relying on stock Android widgets:
 * an Up button, a large number display, and a Down button, stacked vertically. Every part is
 * a plain, independently focusable View, so D-pad Up/Down/Center always works reliably on an
 * Android TV remote — unlike:
 *  - android.widget.TimePicker, which can render as a touch-only analog clock face on some TV
 *    builds (dragging clock hands doesn't work with a remote at all), and
 *  - android.widget.NumberPicker, whose scroll/fling gesture is designed for touch, not for
 *    discrete remote Up/Down clicks.
 *
 * Wraps around at the configured bounds (e.g. hour 23 -> 0, minute 59 -> 0) and exposes a plain
 * `value` property plus an optional change listener.
 */
class TimeStepper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var minValue: Int = 0
    var maxValue: Int = 59

    var value: Int = 0
        set(newValue) {
            field = newValue.coerceIn(minValue, maxValue)
            txtValue.text = String.format("%02d", field)
        }

    private val txtValue: TextView
    private val btnUp: Button
    private val btnDown: Button
    private var onValueChanged: ((Int) -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        View.inflate(context, R.layout.view_time_stepper, this)

        btnUp = findViewById(R.id.btnStepUp)
        txtValue = findViewById(R.id.txtStepValue)
        btnDown = findViewById(R.id.btnStepDown)

        btnUp.setOnClickListener { step(1) }
        btnDown.setOnClickListener { step(-1) }

        txtValue.text = String.format("%02d", value)
    }

    private fun step(delta: Int) {
        var next = value + delta
        if (next > maxValue) next = minValue
        if (next < minValue) next = maxValue
        value = next
        onValueChanged?.invoke(value)
    }

    fun setOnValueChangedListener(listener: (Int) -> Unit) {
        onValueChanged = listener
    }
}
