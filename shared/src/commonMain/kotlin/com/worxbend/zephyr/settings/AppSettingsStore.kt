package com.worxbend.zephyr.settings

import com.worxbend.zephyr.logging.ZephyrLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppSettingsStore(
    private val repository: AppSettingsRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val updates = Channel<(AppSettings) -> AppSettings>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state

    init {
        scope.launch {
            _state.value = try {
                repository.load()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                ZephyrLogger.warn("Unable to load application settings.", exception)
                AppSettings()
            }

            for (transform in updates) {
                val next = transform(_state.value)
                if (next == _state.value) continue
                _state.value = next
                try {
                    repository.save(next)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    ZephyrLogger.warn("Unable to save application settings.", exception)
                }
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        if (updates.trySend(transform).isFailure) {
            ZephyrLogger.warn("Application settings update was ignored because the store is closed.")
        }
    }

    fun close() {
        updates.close()
        scope.cancel()
    }
}
