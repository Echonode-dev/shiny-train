package com.cleandroid.app.ui.shred

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cleandroid.app.CleanDroidApp
import com.cleandroid.app.R
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

data class ShredUiState(
    val currentDir: File = defaultDir(),
    val entries: List<File> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val noAccess: Boolean = false
) {
    companion object {
        fun defaultDir(): File =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }
}

class ShredViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ShredUiState())
    val state: StateFlow<ShredUiState> = _state.asStateFlow()

    init {
        refreshAccess()
        navigate(ShredUiState.defaultDir())
    }

    fun refreshAccess() {
        val has = com.cleandroid.app.core.Permissions.hasFullStorageAccess(container.app)
        _state.update { it.copy(noAccess = !has) }
        if (has) navigate(_state.value.currentDir)
    }

    fun navigate(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = listEntries(dir)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(currentDir = dir, entries = entries) }
            }
        }
    }

    fun up() {
        val parent = _state.value.currentDir.parentFile ?: return
        // Never navigate above the primary storage root.
        val root = Environment.getExternalStorageDirectory()
        if (parent.path.startsWith(root.path)) navigate(parent)
    }

    fun shred(file: File) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        container.launchOp(container.app.getString(R.string.op_shred_file)) {
            try {
                val result = container.shredEngine.shredFile(file)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            busy = false,
                            message = if (result.success) {
                                container.app.getString(R.string.shred_done_one)
                            } else {
                                container.app.getString(R.string.shred_failed)
                            }
                        )
                    }
                    navigate(_state.value.currentDir)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun shredDirectory(dir: File) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        container.launchOp(container.app.getString(R.string.op_shred_dir)) {
            try {
                val results = container.shredEngine.shredDirectory(dir)
                val ok = results.count { it.success }
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            busy = false,
                            message = container.app.getString(R.string.shred_done_many, ok)
                        )
                    }
                    navigate(_state.value.currentDir)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    /** Overwrite all free space on the internal volume (1 pass), then delete fillers. */
    fun wipeFreeSpace() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        container.launchOp(container.app.getString(R.string.op_wipe_free)) {
            try {
                val filled = container.shredEngine.wipeFreeSpace()
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            busy = false,
                            message = container.app.getString(
                                R.string.wipe_done, com.cleandroid.app.core.Fmt.bytes(filled)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun listEntries(dir: File): List<File> = try {
        val children = dir.listFiles() ?: emptyList()
        children.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as CleanDroidApp
                ShredViewModel(app.container)
            }
        }
    }
}
