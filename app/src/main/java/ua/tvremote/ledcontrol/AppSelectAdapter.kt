package ua.tvremote.ledcontrol

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ua.tvremote.ledcontrol.databinding.ItemAppSelectBinding

/** A single installed app entry shown in the picker. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

/**
 * Checkable app list used by [AppPickerActivity]. The list itself can be swapped (for the
 * User/System tabs) via [updateList] while [selected] — shared by reference with the caller —
 * keeps the selection consistent across both tabs.
 */
class AppSelectAdapter(
    private var apps: List<AppEntry>,
    private val selected: MutableSet<String>
) : RecyclerView.Adapter<AppSelectAdapter.VH>() {

    inner class VH(val binding: ItemAppSelectBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateList(newApps: List<AppEntry>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAppSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = apps[position]
        holder.binding.txtAppName.text = entry.label
        holder.binding.checkApp.setOnCheckedChangeListener(null)
        holder.binding.checkApp.isChecked = selected.contains(entry.packageName)
        holder.binding.checkApp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(entry.packageName) else selected.remove(entry.packageName)
        }
        holder.binding.root.setOnClickListener { holder.binding.checkApp.toggle() }
    }

    override fun getItemCount(): Int = apps.size

    fun getSelected(): Set<String> = selected
}
