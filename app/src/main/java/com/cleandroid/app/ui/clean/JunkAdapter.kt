package com.cleandroid.app.ui.clean

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cleandroid.app.R
import com.cleandroid.app.core.Fmt
import com.cleandroid.app.data.clean.JunkGroup
import com.cleandroid.app.databinding.ItemJunkBinding

class JunkAdapter(
    private val onToggle: (JunkGroup, Boolean) -> Unit
) : ListAdapter<JunkGroup, JunkAdapter.Holder>(DIFF) {

    private val checked = mutableSetOf<String>()

    fun setCheckedState(ids: Set<String>) {
        checked.clear()
        checked.addAll(ids)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemJunkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemJunkBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: JunkGroup) {
            val ctx = binding.root.context
            binding.txtTitle.text = group.title
            binding.txtDesc.text = group.desc
            val sizeText = if (group.sizeBytes > 0) Fmt.bytes(group.sizeBytes) else "—"
            binding.txtMeta.text = ctx.getString(
                R.string.junk_meta, group.files.size, sizeText
            )
            binding.chk.isChecked = checked.contains(group.id)
            binding.chk.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checked.add(group.id) else checked.remove(group.id)
                onToggle(group, isChecked)
            }
            binding.root.setOnClickListener {
                binding.chk.isChecked = !binding.chk.isChecked
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<JunkGroup>() {
            override fun areItemsTheSame(oldItem: JunkGroup, newItem: JunkGroup) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: JunkGroup, newItem: JunkGroup) =
                oldItem == newItem
        }
    }
}
