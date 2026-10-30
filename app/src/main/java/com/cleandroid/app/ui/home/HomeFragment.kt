package com.cleandroid.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cleandroid.app.R
import com.cleandroid.app.core.Fmt
import com.cleandroid.app.databinding.FragmentHomeBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by viewModels { HomeViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRam.setOnClickListener {
            vm.killBackgroundApps()
            Toast.makeText(
                requireContext(), R.string.home_ram_working, Toast.LENGTH_SHORT
            ).show()
        }
        binding.btnStorageSettings.setOnClickListener {
            try {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                    )
                )
            } catch (_: Exception) {
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.state.collect { render(it) }
                }
                launch {
                    while (true) {
                        vm.refresh()
                        delay(3000)
                    }
                }
            }
        }
    }

    private fun render(state: HomeUiState) {
        val usedText = Fmt.bytes(state.storage.usedBytes)
        val totalText = Fmt.bytes(state.storage.totalBytes)
        binding.storageRing.setProgressAnimated(state.storage.usedFraction)
        binding.txtStorageUsed.text = usedText
        binding.txtStorageTotal.text = getString(R.string.home_storage_total, totalText)
        binding.txtStorageFree.text = getString(
            R.string.home_storage_free, Fmt.bytes(state.storage.freeBytes)
        )

        binding.ramBar.progress = (state.ram.usedFraction * 100).toInt()
        binding.txtRam.text = getString(
            R.string.home_ram_value,
            Fmt.bytes(state.ram.totalBytes - state.ram.availableBytes),
            Fmt.bytes(state.ram.totalBytes)
        )
        binding.txtRamLow.isVisible = state.ram.low

        binding.txtStatBloat.text = state.bloatCount.toString()
        binding.txtStatJunk.text =
            if (state.junkBytes < 0) getString(R.string.home_not_scanned)
            else Fmt.bytes(state.junkBytes)
        binding.txtStatThreats.text = state.threatCount.toString()

        state.ramFreed?.let {
            Toast.makeText(
                requireContext(),
                getString(R.string.home_ram_done, it),
                Toast.LENGTH_SHORT
            ).show()
            vm.consumeRamFreed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
