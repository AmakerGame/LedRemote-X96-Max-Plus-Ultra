package ua.tvremote.ledcontrol

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ua.tvremote.ledcontrol.databinding.ItemAppSelectBinding

class AppSelectAdapter(
    private val apps: List<ActivityInfo>,
    private val pm: PackageManager,
    initiallySelected: Set<String>
) : RecyclerView.Adapter<AppSelectAdapter.VH>() {

    private val selected = initiallySelected.toMutableSet()

    inner class VH(val binding: ItemAppSelectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAppSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val info = apps[position]
        holder.binding.txtAppName.text = info.loadLabel(pm)
        holder.binding.checkApp.setOnCheckedChangeListener(null)
        holder.binding.checkApp.isChecked = selected.contains(info.packageName)
        holder.binding.checkApp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(info.packageName) else selected.remove(info.packageName)
        }
        holder.binding.root.setOnClickListener { holder.binding.checkApp.toggle() }
    }

    override fun getItemCount(): Int = apps.size

    fun getSelected(): Set<String> = selected
}
