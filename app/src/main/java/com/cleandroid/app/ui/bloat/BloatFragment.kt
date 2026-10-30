package com.cleandroid.app.ui.bloat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cleandroid.app.R
import com.cleandroid.app.core.Permissions
import com.cleandroid.app.databinding.FragmentBloatBinding
import kotlinx.coroutines.launch

class BloatFragment : Fragment() {

    private var _binding: FragmentBloatBinding? = null
    private val binding get() = _binding!!
    private val vm: BloatViewModel by viewModels { BloatViewModel.Factory }

    private lateinit var adapter: BloatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBloatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = BloatAdapter(
            onUninstall = { uninstall(it.packageName) },
            onInfo = { openInfo(it.packageName) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnRetry.setOnClickListener { vm.scan() }

        binding.chipGroup.setOnCheckedStateChangeListener { group, _ ->
            vm.setFilter(
                when (group.checkedChipIds.firstOrNull()) {
                    R.id.chipAds -> BloatViewModel.FILTER_ADS
                    R.id.chipAnalytics -> BloatViewModel.FILTER_ANALYTICS
                    R.id.chipOem -> BloatViewModel.FILTER_OEM
                    R.id.chipCarrier -> BloatViewModel.FILTER_CARRIER
                    R.id.chipOptional -> BloatViewModel.FILTER_OPTIONAL
                    else -> BloatViewModel.FILTER_ALL
                }
            )
        }

        binding.bannerUsage.btnGrant.setOnClickListener {
            Permissions.openUsageAccessSettings(requireActivity())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.validateRemoved()
    }

    private fun render(state: BloatUiState) {
        binding.progress.isVisible = state.loading
        binding.txtStatus.isVisible = state.loading
        binding.txtStatus.text =
            getString(R.string.bloat_scanning, state.totalScanned)
        binding.btnRetry.isVisible = !state.loading && state.apps.isEmpty()
        binding.chipGroup.isVisible = !state.loading

        val usageGranted = Permissions.hasUsageAccess(requireContext())
        binding.bannerUsage.root.isVisible = !usageGranted && !state.loading
        binding.bannerUsage.txtBannerText.setText(R.string.banner_usage_text)

        adapter.submitList(state.visibleApps)
        binding.txtEmpty.isVisible = !state.loading && state.visibleApps.isEmpty()

        val flagged = state.apps.size
        val shown = state.visibleApps.size
        binding.txtSummary.text = if (state.filter == BloatViewModel.FILTER_ALL) {
            getString(R.string.bloat_summary_all, flagged, state.totalScanned)
        } else {
            getString(R.string.bloat_summary_filter, shown)
        }
    }

    private fun uninstall(pkg: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun openInfo(pkg: String) {
        Permissions.openAppDetails(requireActivity(), pkg)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
