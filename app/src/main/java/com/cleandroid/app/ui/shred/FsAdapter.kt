package com.cleandroid.app.ui.shred

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cleandroid.app.R
import com.cleandroid.app.core.Fmt
import com.cleandroid.app.databinding.ItemFsBinding
import java.io.File

class FsAdapter(
    private val onOpen: (File) -> Unit,
    private val onShred: (File) -> Unit
) : ListAdapter<File, FsAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemFsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemFsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File) {
            val ctx = binding.root.context
            val isDir = file.isDirectory
            binding.imgIcon.setImageResource(if (isDir) R.drawable.ic_folder else R.drawable.ic_file)
            binding.imgIcon.setColorFilter(
                ContextCompat.getColor(
                    ctx, if (isDir) R.color.accent else R.color.textSecondary
                )
            )
            binding.txtName.text = file.name
            binding.txtSub.text = if (isDir) {
                ctx.getString(R.string.fs_folder)
            } else {
                "${Fmt.bytes(file.length())} · ${Fmt.date(file.lastModified())}"
            }
            binding.btnShred.setText(
                if (isDir) R.string.fs_shred_folder else R.string.fs_shred_file
            )
            binding.btnShred.setOnClickListener { onShred(file) }
            binding.root.setOnClickListener {
                if (isDir) onOpen(file)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File) =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: File, newItem: File) =
                oldItem.path == newItem.path && oldItem.lastModified() == newItem.lastModified()
        }
    }
}
