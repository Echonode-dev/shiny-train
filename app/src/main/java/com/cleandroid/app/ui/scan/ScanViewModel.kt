package com.cleandroid.app.ui.scan

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cleandroid.app.CleanDroidApp
import com.cleandroid.app.R
import com.cleandroid.app.data.av.Threat
import com.cleandroid.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ScanUiState(
    val scanning: Boolean = false,
    val threats: List<Threat> = emptyList(),
    val done: Boolean = false,
    val lastTarget: String = ""
)

class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** Full app scan: APK signature hashes + permission-combo heuristics. */
    fun scanApps() {
        if (_state.value.scanning) return
        _state.update {
            it.copy(scanning = true, done = false, threats = emptyList(), lastTarget = "")
        }
        container.launchOp(container.app.getString(R.string.op_scan_apps)) {
            try {
                val threats = container.avEngine.scanApps()
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(scanning = false, done = true, threats = threats) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(scanning = false, done = true) }
                }
            }
        }
    }

    /** Quick offline hash check of files in the public Downloads folder. */
    fun scanDownloads() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, done = false, lastTarget = "") }
        container.launchOp(container.app.getString(R.string.op_scan_files)) {
            try {
                val downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val found = container.avEngine.scanFiles(File(downloads.absolutePath))
                withContext(Dispatchers.Main) {
                    _state.update {
                        val merged = (it.threats + found).distinctBy { t -> t.hashCode() }
                        it.copy(scanning = false, done = true, threats = merged)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(scanning = false, done = true) }
                }
            }
        }
    }

    /** Shred a detected malicious file directly from the results list. */
    fun shredFile(path: String) {
        if (_state.value.scanning) return
        container.launchOp(container.app.getString(R.string.op_shred_file)) {
            val result = container.shredEngine.shredFile(File(path))
            withContext(Dispatchers.Main) {
                if (result.success) {
                    _state.update { s ->
                        s.copy(threats = s.threats.filterNot { it.filePath == path })
                    }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as CleanDroidApp
                ScanViewModel(app.container)
            }
        }
    }
}
