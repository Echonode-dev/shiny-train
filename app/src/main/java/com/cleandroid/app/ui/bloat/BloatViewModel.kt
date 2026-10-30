package com.cleandroid.app.ui.bloat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cleandroid.app.CleanDroidApp
import com.cleandroid.app.R
import com.cleandroid.app.data.bloat.BloatApp
import com.cleandroid.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BloatUiState(
    val loading: Boolean = false,
    val apps: List<BloatApp> = emptyList(),
    val filter: String = FILTER_ALL,
    val totalScanned: Int = 0,
    val error: String? = null
) {
    val visibleApps: List<BloatApp>
        get() = if (filter == FILTER_ALL) apps else apps.filter { it.entry.category == filter }
}

class BloatViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(BloatUiState())
    val state: StateFlow<BloatUiState> = _state.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        val found = mutableListOf<BloatApp>()
        container.launchOp(container.app.getString(R.string.op_bloat)) {
            try {
                val result = container.bloatRepo.scan { found.add(it) }
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            loading = false,
                            apps = found.toList(),
                            totalScanned = result.totalScanned
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(loading = false, error = e.message ?: "error") }
                }
            }
        }
    }

    fun setFilter(filter: String) {
        _state.update { it.copy(filter = filter) }
    }

    /** After returning from the uninstall dialog, drop apps that no longer exist. */
    fun validateRemoved() {
        val current = _state.value.apps
        if (current.isEmpty()) return
        val pm = container.app.packageManager
        val stillThere = current.filter { app ->
            try {
                pm.getPackageInfo(app.packageName, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (stillThere.size != current.size) {
            _state.update { it.copy(apps = stillThere) }
        }
    }

    companion object {
        const val FILTER_ALL = "all"
        const val FILTER_ADS = "ads"
        const val FILTER_ANALYTICS = "analytics"
        const val FILTER_OEM = "oem"
        const val FILTER_CARRIER = "carrier"
        const val FILTER_OPTIONAL = "optional"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as CleanDroidApp
                BloatViewModel(app.container)
            }
        }
    }
}
