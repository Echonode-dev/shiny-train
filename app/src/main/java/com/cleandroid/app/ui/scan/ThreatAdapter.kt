package com.cleandroid.app.ui.scan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cleandroid.app.R
import com.cleandroid.app.data.av.Threat
import com.cleandroid.app.databinding.ItemThreatBinding

class ThreatAdapter(
    private val onUninstall: (Threat) -> Unit,
    private val onShred: (Threat) -> Unit,
    private val onInfo: (Threat) -> Unit
) : ListAdapter<Threat, ThreatAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemThreatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemThreatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(threat: Threat) {
            val ctx = binding.root.context
            binding.txtTitle.text = threat.appName ?: threat.title
            binding.txtTarget.text = threat.packageName ?: threat.filePath ?: ""
            binding.txtReasons.text = threat.reasons.joinToString("\n") { "• $it" }
            binding.txtSeverity.setText(
                if (threat.severity >= 2) R.string.sev_high else R.string.sev_medium
            )
            binding.txtSeverity.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (threat.severity >= 2) R.color.riskHigh else R.color.riskMedium
                )
            )
            binding.btnUninstall.isVisible = threat.packageName != null
            binding.btnShred.isVisible = threat.filePath != null
            binding.btnUninstall.setOnClickListener { onUninstall(threat) }
            binding.btnShred.setOnClickListener { onShred(threat) }
            binding.btnInfo.setOnClickListener { onInfo(threat) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Threat>() {
            override fun areItemsTheSame(oldItem: Threat, newItem: Threat): Boolean =
                oldItem.packageName == newItem.packageName && oldItem.filePath == newItem.filePath

            override fun areContentsTheSame(oldItem: Threat, newItem: Threat) = oldItem == newItem
        }
    }
}
