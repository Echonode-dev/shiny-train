package com.cleandroid.app.ui.clean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cleandroid.app.CleanDroidApp
import com.cleandroid.app.R
import com.cleandroid.app.data.clean.JunkGroup
import com.cleandroid.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanUiState(
    val scanning: Boolean = false,
    val cleaning: Boolean = false,
    val groups: List<JunkGroup> = emptyList(),
    val selected: Set<String> = emptySet(),
    val freedBytes: Long = 0,
    val done: Boolean = false,
    val noAccess: Boolean = false
) {
    val selectedBytes: Long
        get() = groups.filter { selected.contains(it.id) }.sumOf { it.sizeBytes }
}

class CleanViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CleanUiState())
    val state: StateFlow<CleanUiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning || _state.value.cleaning) return
        _state.update {
            it.copy(scanning = true, done = false, groups = emptyList(), selected = emptySet())
        }
        container.launchOp(container.app.getString(R.string.op_clean_scan)) {
            try {
                container.cleanEngine.scan { group ->
                    withContext(Dispatchers.Main) {
                        _state.update { s ->
                            s.copy(
                                groups = s.groups + group,
                                selected = if (group.defaultChecked) s.selected + group.id else s.selected
                            )
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(scanning = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(scanning = false) }
                }
            }
        }
    }

    fun toggle(groupId: String, checked: Boolean) {
        _state.update {
            it.copy(
                selected = if (checked) it.selected + groupId else it.selected - groupId
            )
        }
    }

    fun clean() {
        val current = _state.value
        if (current.scanning || current.cleaning || current.selected.isEmpty()) return
        val targets = current.groups.filter { current.selected.contains(it.id) }
        _state.update { it.copy(cleaning = true, done = false) }
        container.launchOp(container.app.getString(R.string.op_clean_run)) {
            try {
                val freed = container.cleanEngine.clean(targets)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            cleaning = false,
                            done = true,
                            freedBytes = freed,
                            groups = emptyList(),
                            selected = emptySet()
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(cleaning = false, done = true) }
                }
            }
        }
    }

    fun refreshAccess() {
        val has = com.cleandroid.app.core.Permissions.hasFullStorageAccess(container.app)
        _state.update { it.copy(noAccess = !has) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as CleanDroidApp
                CleanViewModel(app.container)
            }
        }
    }
}
