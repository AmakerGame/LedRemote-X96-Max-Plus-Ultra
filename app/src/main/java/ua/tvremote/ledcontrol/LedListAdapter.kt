package ua.tvremote.ledcontrol

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ua.tvremote.ledcontrol.databinding.ItemLedBinding

class LedListAdapter(
    private val items: List<LedListItem>,
    private val repo: LedRepository,
    private val onClick: (LedListItem) -> Unit
) : RecyclerView.Adapter<LedListAdapter.VH>() {

    inner class VH(val binding: ItemLedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        val displayOn = repo.isDisplayDesiredOn()
        var active: Boolean

        when (item) {
            is LedListItem.Display -> {
                val pending = displayOn && repo.isDisplayAwaitingRestart()
                active = displayOn
                holder.binding.txtLedName.text = ctx.getString(R.string.led_display)
                holder.binding.txtLedHint.text = when {
                    pending -> ctx.getString(R.string.display_status_pending)
                    displayOn -> ctx.getString(R.string.display_status_on)
                    else -> ctx.getString(R.string.display_status_off)
                }
                holder.itemView.alpha = 1f
            }
            is LedListItem.Secondary -> {
                val cfg = repo.getConfig(item.id)
                active = cfg.mode != LedMode.OFF
                holder.binding.txtLedName.text = ctx.getString(item.id.nameRes)
                if (displayOn) {
                    holder.binding.txtLedHint.text = modeLabel(ctx, cfg.mode)
                    holder.itemView.alpha = 1f
                } else {
                    // Display is off: nothing on the physical VFD is visible right now,
                    // so reflect that in the list instead of pretending the LED is live —
                    // the status pulls through everywhere, not just on the display screen.
                    holder.binding.txtLedHint.text =
                        "${modeLabel(ctx, cfg.mode)} \u2022 ${ctx.getString(R.string.led_hidden_by_display_off)}"
                    holder.itemView.alpha = 0.5f
                    active = false
                }
            }
        }
        // mutate() avoids tinting every recycled row that shares the same drawable instance.
        holder.binding.dotStatus.background.mutate().setTint(
            ContextCompat.getColor(ctx, if (active) R.color.status_on else R.color.status_off)
        )
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    private fun modeLabel(ctx: Context, mode: LedMode): String = when (mode) {
        LedMode.OFF -> ctx.getString(R.string.mode_off)
        LedMode.ON -> ctx.getString(R.string.mode_on)
        LedMode.APP_ACTIVE -> ctx.getString(R.string.mode_app_active)
        LedMode.CONDITION -> ctx.getString(R.string.mode_condition)
        LedMode.SCRIPT -> ctx.getString(R.string.mode_script)
        LedMode.TIME -> ctx.getString(R.string.mode_time)
    }

    override fun getItemCount(): Int = items.size
}
