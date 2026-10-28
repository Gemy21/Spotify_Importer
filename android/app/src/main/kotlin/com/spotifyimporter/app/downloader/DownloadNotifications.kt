package com.spotifyimporter.app.downloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bridge between [DownloadQueueManager] and [DownloadService].
 * The service observes this state and mirrors it into the foreground notification.
 */
object DownloadNotifications {

    data class State(
        val title: String = "Spotify Importer",
        val text: String = "Preparing download…",
        val progress: Int = 0,
        val indeterminate: Boolean = true
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(title: String, text: String, progress: Int, indeterminate: Boolean = false) {
        _state.value = State(
            title = title,
            text = text,
            progress = progress.coerceIn(0, 100),
            indeterminate = indeterminate
        )
    }
}
