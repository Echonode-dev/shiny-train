package com.cleandroid.app.di

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cleandroid.app.data.av.AvEngine
import com.cleandroid.app.data.bloat.BloatRepository
import com.cleandroid.app.data.clean.CleanEngine
import com.cleandroid.app.data.shred.ShredEngine
import com.cleandroid.app.core.ProcessUtils
import com.cleandroid.app.service.OpsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State of the single long-running operation slot (scan / clean / shred / ram boost). */
data class OpState(
    val running: Boolean = false,
    val label: String = "",
    /** 0..1 when determinate, negative when indeterminate. */
    val progress: Float = -1f
) {
    companion object {
        val IDLE = OpState()
    }
}

/**
 * Manual dependency container. One instance lives as long as the process.
 *
 * All long operations run through [launchOp] so that:
 *  - the foreground service can show progress,
 *  - the user can cancel,
 *  - the previous operation is always cancelled before a new one starts
 *    (no orphaned child work, no leaked threads).
 */
class AppContainer(val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("CleanDroid"))

    private val _opState = MutableStateFlow(OpState.IDLE)
    val opState: StateFlow<OpState> = _opState.asStateFlow()

    @Volatile
    private var currentJob: Job? = null

    val bloatRepo = BloatRepository(app, this)
    val avEngine = AvEngine(app, this)
    val cleanEngine = CleanEngine(app, this)
    val shredEngine = ShredEngine(app, this)
    val processUtils = ProcessUtils(app)

    /** Cancel any running operation and start a new one. */
    fun launchOp(label: String, block: suspend () -> Unit): Job {
        cancelOp()
        val job = appScope.launch {
            beginOp(label)
            try {
                block()
            } catch (_: CancellationException) {
                // Cancelled by the user or replaced by a newer op; state reset in finally.
            } finally {
                _opState.value = OpState.IDLE
                currentJob = null
            }
        }
        currentJob = job
        return job
    }

    fun cancelOp() {
        currentJob?.cancel()
        currentJob = null
    }

    fun updateOp(progress: Float? = null, label: String? = null) {
        val s = _opState.value
        if (!s.running) return
        _opState.value = s.copy(
            progress = progress ?: s.progress,
            label = label ?: s.label
        )
    }

    private fun beginOp(label: String) {
        _opState.value = OpState(running = true, label = label, progress = -1f)
        // The foreground service observes opState and stops itself when the op ends.
        ContextCompat.startForegroundService(app, Intent(app, OpsService::class.java))
    }
}
