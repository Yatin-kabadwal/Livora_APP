package com.livora.corbett.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Runs [action] every [intervalMs] while started. Screens start/stop it from lifecycle resume/pause. */
class Poller(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val action: suspend () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                action()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
