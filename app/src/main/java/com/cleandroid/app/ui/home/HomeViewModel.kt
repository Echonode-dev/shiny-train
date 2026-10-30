package com.cleandroid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cleandroid.app.CleanDroidApp
import com.cleandroid.app.R
import com.cleandroid.app.core.RamInfo
import com.cleandroid.app.core.StorageUtils
import com.cleandroid.app.core.VolumeInfo
import com.cleandroid.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val storage: VolumeInfo = VolumeInfo(0, 0),
    val ram: RamInfo = RamInfo(0, 0, false),
    val bloatCount: Int = 0,
    val junkBytes: Long = -1L, // -1 = not scanned yet
    val threatCount: Int = 0,
    val opRunning: Boolean = false,
    val ramFreed: Int? = null
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun refresh() {
        val junk = container.cleanEngine.lastScanGroups
        val junkBytes = if (junk.isEmpty()) -1L else junk.sumOf { it.sizeBytes }
        _state.update {
            it.copy(
                storage = StorageUtils.primaryVolume(),
                ram = container.processUtils.ramInfo(),
                bloatCount = container.bloatRepo.lastResult?.flaggedCount ?: 0,
                junkBytes = junkBytes,
                threatCount = container.avEngine.lastThreats.size,
                opRunning = container.opState.value.running
            )
        }
    }

    /** Ask the system to stop background processes of user apps. */
    fun killBackgroundApps() {
        if (container.opState.value.running) return
        container.launchOp(container.app.getString(R.string.op_ram)) {
            val asked = container.processUtils.killBackgroundProcesses()
            withContext(Dispatchers.Main) {
                _state.update { it.copy(ramFreed = asked) }
                refresh()
            }
        }
    }

    fun consumeRamFreed() {
        _state.update { it.copy(ramFreed = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as CleanDroidApp
                HomeViewModel(app.container)
            }
        }
    }
}
