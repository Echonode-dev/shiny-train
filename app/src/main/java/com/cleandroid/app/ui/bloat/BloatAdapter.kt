package com.cleandroid.app.ui.bloat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cleandroid.app.R
import com.cleandroid.app.core.Fmt
import com.cleandroid.app.data.bloat.BloatApp
import com.cleandroid.app.databinding.ItemBloatBinding

class BloatAdapter(
    private val onUninstall: (BloatApp) -> Unit,
    private val onInfo: (BloatApp) -> Unit
) : ListAdapter<BloatApp, BloatAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemBloatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemBloatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: BloatApp) {
            val ctx = binding.root.context
            binding.imgIcon.setImageDrawable(app.icon)
            binding.txtLabel.text = app.label
            binding.txtPackage.text = app.packageName
            binding.txtCategory.text = when (app.entry.category) {
                "ads" -> ctx.getString(R.string.cat_ads)
                "analytics" -> ctx.getString(R.string.cat_analytics)
                "carrier" -> ctx.getString(R.string.cat_carrier)
                "optional" -> ctx.getString(R.string.cat_optional)
                else -> ctx.getString(R.string.cat_oem)
            }
            binding.txtRisk.text = when (app.entry.risk) {
                "high" -> ctx.getString(R.string.risk_recommended)
                "medium" -> ctx.getString(R.string.risk_check_first)
                else -> ctx.getString(R.string.risk_optional)
            }
            val riskColor = when (app.entry.risk) {
                "high" -> ContextCompat.getColor(ctx, R.color.riskHigh)
                "medium" -> ContextCompat.getColor(ctx, R.color.riskMedium)
                else -> ContextCompat.getColor(ctx, R.color.riskLow)
            }
            binding.txtRisk.setTextColor(riskColor)

            binding.txtSize.isVisible = app.sizeBytes > 0
            if (app.sizeBytes > 0) {
                binding.txtSize.text = Fmt.bytes(app.sizeBytes)
            }
            binding.txtNote.isVisible = app.entry.note.isNotEmpty()
            binding.txtNote.text = app.entry.note

            binding.btnUninstall.isVisible = app.canUninstall
            binding.btnUninstall.setOnClickListener { onUninstall(app) }
            binding.btnInfo.setOnClickListener { onInfo(app) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<BloatApp>() {
            override fun areItemsTheSame(oldItem: BloatApp, newItem: BloatApp) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: BloatApp, newItem: BloatApp) =
                oldItem == newItem
        }
    }
}
