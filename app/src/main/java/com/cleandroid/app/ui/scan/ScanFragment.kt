package com.cleandroid.app.ui.scan

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
import com.cleandroid.app.databinding.FragmentScanBinding
import kotlinx.coroutines.launch

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val vm: ScanViewModel by viewModels { ScanViewModel.Factory }

    private lateinit var adapter: ThreatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ThreatAdapter(
            onUninstall = { threat -> threat.packageName?.let { uninstallApp(it) } },
            onShred = { threat -> threat.filePath?.let { vm.shredFile(it) } },
            onInfo = { threat -> threat.packageName?.let { Permissions.openAppDetails(requireActivity(), it) } }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnScan.setOnClickListener { vm.scanApps() }
        binding.btnScanFiles.setOnClickListener { vm.scanDownloads() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ScanUiState) {
        binding.btnScan.isEnabled = !state.scanning
        binding.btnScanFiles.isEnabled = !state.scanning
        binding.progress.isVisible = state.scanning
        binding.txtStatus.isVisible = state.scanning
        binding.txtStatus.text =
            if (state.lastTarget.isEmpty()) getString(R.string.scan_working)
            else state.lastTarget

        val hasResults = state.threats.isNotEmpty()
        binding.cardResult.isVisible = state.done && !state.scanning
        if (state.done) {
            binding.txtResultTitle.setText(
                if (hasResults) R.string.scan_result_threats else R.string.scan_result_clean
            )
            binding.txtResultTitle.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    if (hasResults) R.color.riskHigh else R.color.riskLow
                )
            )
            binding.txtResultDetail.text = getString(R.string.scan_result_detail, state.threats.size)
        }

        adapter.submitList(state.threats)
        binding.txtEmpty.isVisible =
            !state.scanning && !hasResults && !state.done
    }

    private fun uninstallApp(pkg: String) {
        try {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_DELETE,
                    android.net.Uri.parse("package:$pkg")
                )
            )
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
