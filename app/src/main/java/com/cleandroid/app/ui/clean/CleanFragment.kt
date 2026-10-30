package com.cleandroid.app.ui.clean

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cleandroid.app.R
import com.cleandroid.app.core.Fmt
import com.cleandroid.app.core.Permissions
import com.cleandroid.app.databinding.FragmentCleanBinding
import kotlinx.coroutines.launch

class CleanFragment : Fragment() {

    private var _binding: FragmentCleanBinding? = null
    private val binding get() = _binding!!
    private val vm: CleanViewModel by viewModels { CleanViewModel.Factory }

    private lateinit var adapter: JunkAdapter

    private val legacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.refreshAccess()
            if (Permissions.hasFullStorageAccess(requireContext())) vm.scan()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCleanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = JunkAdapter(onToggle = { group, checked -> vm.toggle(group.id, checked) })
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnScan.setOnClickListener { vm.scan() }
        binding.btnClean.setOnClickListener { vm.clean() }

        binding.bannerAccess.btnGrant.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Permissions.requestFullStorageAccess(requireActivity())
            } else {
                legacyPermissionLauncher.launch(Permissions.legacyStoragePermissions())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAccess()
    }

    private fun render(state: CleanUiState) {
        val busy = state.scanning || state.cleaning
        val hasAccess = Permissions.hasFullStorageAccess(requireContext())
        binding.bannerAccess.root.isVisible = !hasAccess

        binding.btnScan.isVisible = true
        binding.btnScan.isEnabled = !busy && hasAccess
        binding.btnScan.text = getString(
            if (state.groups.isEmpty()) R.string.clean_scan else R.string.clean_rescan
        )
        binding.progress.isVisible = busy
        binding.txtStatus.isVisible = busy
        binding.txtStatus.text = if (state.cleaning) getString(R.string.clean_working)
        else getString(R.string.clean_scanning)

        adapter.submitList(state.groups)
        adapter.setCheckedState(state.selected)

        binding.txtEmpty.isVisible = !busy && !state.done && state.groups.isEmpty() && hasAccess

        binding.btnClean.isVisible = state.groups.isNotEmpty() && !state.scanning
        binding.btnClean.isEnabled = state.selected.isNotEmpty() && !state.cleaning
        binding.btnClean.text = getString(
            R.string.clean_clean_button, Fmt.bytes(state.selectedBytes)
        )

        binding.cardDone.isVisible = state.done && state.freedBytes > 0
        binding.txtDone.text = getString(R.string.clean_done, Fmt.bytes(state.freedBytes))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
