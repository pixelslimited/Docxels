package com.docscan.app.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether Docxels' online services (account, writing assistant, Pro) can be reached. The scanner, editor and PDF tools never depend on it. */
enum class ServiceState { OK, DOWN, BACK }

/**
 * Set by [Cloud] from real requests: a request that fails while the phone is online, or is answered with a server error,
 * makes it DOWN; the first good answer after that makes it BACK (the banner then offers a refresh button, which sets OK).
 */
object ServiceStatus {
    private val _state = MutableStateFlow(ServiceState.OK)
    val state: StateFlow<ServiceState> = _state

    fun failed() { _state.value = ServiceState.DOWN }
    fun succeeded() { if (_state.value == ServiceState.DOWN) _state.value = ServiceState.BACK }
    fun acknowledged() { _state.value = ServiceState.OK }
}
