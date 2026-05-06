package com.mobileclaw.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-scoped bus for HTTP request/response log lines.
 * The OkHttp interceptor in OpenAiGateway emits here.
 * ViewModel collects per-task (collection is cancelled when taskJob is cancelled).
 * No replay buffer — new collectors only see future emissions.
 */
object NetworkTracer {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun log(message: String) {
        _events.tryEmit(message)
    }
}
