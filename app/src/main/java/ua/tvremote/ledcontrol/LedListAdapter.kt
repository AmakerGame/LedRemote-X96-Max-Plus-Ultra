package ua.tvremote.ledcontrol

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ua.tvremote.ledcontrol.databinding.ItemLedBinding

class LedListAdapter(
    private val items: List<LedListItem>,
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
        when (item) {
            is LedListItem.Display -> {
                holder.binding.txtLedName.text = ctx.getString(R.string.led_display)
                holder.binding.txtLedHint.text = ctx.getString(R.string.led_display_hint)
            }
            is LedListItem.Secondary -> {
                holder.binding.txtLedName.text = ctx.getString(item.id.nameRes)
                holder.binding.txtLedHint.text = item.id.attr
            }
        }
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
