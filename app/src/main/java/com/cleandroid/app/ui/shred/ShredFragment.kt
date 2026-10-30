package com.cleandroid.app.ui.shred

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import com.cleandroid.app.core.Permissions
import com.cleandroid.app.databinding.FragmentShredBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

class ShredFragment : Fragment() {

    private var _binding: FragmentShredBinding? = null
    private val binding get() = _binding!!
    private val vm: ShredViewModel by viewModels { ShredViewModel.Factory }

    private lateinit var adapter: FsAdapter

    private val legacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.refreshAccess()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShredBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FsAdapter(onOpen = { vm.navigate(it) }, onShred = { confirmAndShred(it) })
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnUp.setOnClickListener { vm.up() }

        binding.chipDownload.setOnClickListener { openPublic(Environment.DIRECTORY_DOWNLOADS) }
        binding.chipDcim.setOnClickListener { openPublic(Environment.DIRECTORY_DCIM) }
        binding.chipDocuments.setOnClickListener { openPublic(Environment.DIRECTORY_DOCUMENTS) }
        binding.chipPictures.setOnClickListener { openPublic(Environment.DIRECTORY_PICTURES) }

        binding.btnWipe.setOnClickListener { confirmWipe() }
        binding.btnCancel.setOnClickListener {
            (requireActivity().application as com.cleandroid.app.CleanDroidApp)
                .container.cancelOp()
        }

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

    private fun openPublic(type: String) {
        val dir = Environment.getExternalStoragePublicDirectory(type)
        vm.navigate(dir)
    }

    private fun confirmAndShred(file: File) {
        val isDir = file.isDirectory
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shred_confirm_title)
            .setMessage(
                getString(
                    if (isDir) R.string.shred_confirm_folder else R.string.shred_confirm_file,
                    file.name
                )
            )
            .setPositiveButton(R.string.shred_confirm_yes) { _, _ ->
                if (isDir) vm.shredDirectory(file) else vm.shred(file)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmWipe() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.wipe_confirm_title)
            .setMessage(R.string.wipe_confirm_msg)
            .setPositiveButton(R.string.wipe_confirm_yes) { _, _ -> vm.wipeFreeSpace() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun render(state: ShredUiState) {
        val hasAccess = Permissions.hasFullStorageAccess(requireContext())
        binding.bannerAccess.root.isVisible = !hasAccess

        binding.txtPath.text = state.currentDir.path
        binding.btnUp.isEnabled = state.currentDir.path !=
            Environment.getExternalStorageDirectory().path

        adapter.submitList(state.entries)
        binding.txtEmpty.isVisible = !state.busy && state.entries.isEmpty() && hasAccess

        binding.btnCancel.isVisible = state.busy
        binding.progress.isVisible = state.busy
        binding.btnWipe.isEnabled = !state.busy && hasAccess
        binding.recycler.isVisible = hasAccess

        state.message?.let {
            android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_LONG)
                .show()
            vm.consumeMessage()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
