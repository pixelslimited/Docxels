package com.docscan.app.export

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What is being exported right now, so Home can show it on the Continue card. [fraction] is null when it cannot be measured. */
class ExportStatus {
    data class State(val key: String, val label: String, val fraction: Float?)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun begin(key: String, label: String) { _state.value = State(key, label, null) }
    fun progress(fraction: Float) { _state.value = _state.value?.copy(fraction = fraction.coerceIn(0f, 1f)) }
    fun end() { _state.value = null }
}
