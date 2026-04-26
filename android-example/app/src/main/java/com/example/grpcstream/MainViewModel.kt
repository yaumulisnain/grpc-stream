package com.example.grpcstream

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pubsub.Pubsub

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var config = GrpcServerSettings.load(application)
    private var repository = PubSubRepository(config.host, config.port)
    private var streamJob: Job? = null

    private val _status = MutableStateFlow("Disconnected")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _events = MutableStateFlow<List<Pubsub.Event>>(emptyList())
    val events: StateFlow<List<Pubsub.Event>> = _events.asStateFlow()

    fun refreshConnectionSettings() {
        val updated = GrpcServerSettings.load(getApplication())
        if (updated == config) return

        streamJob?.cancel()
        repository.close()
        config = updated
        repository = PubSubRepository(config.host, config.port)
        _status.value = "Server updated: ${config.host}:${config.port}"
    }

    fun subscribe(topic: String) {
        if (topic.isBlank()) return

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _status.value = "Subscribed: $topic"
            try {
                repository.subscribe(topic).collect { event ->
                    _events.value = listOf(event) + _events.value
                }
            } catch (_: Exception) {
                _status.value = "Disconnected"
            }
        }
    }

    fun unsubscribe() {
        streamJob?.cancel()
        streamJob = null
        _status.value = "Disconnected"
    }

    fun publish(topic: String, data: String) {
        if (topic.isBlank() || data.isBlank()) return

        viewModelScope.launch {
            try {
                val response = repository.publish(topic, data)
                _status.value = "Published: ${response.id}"
            } catch (e: Exception) {
                _status.value = "Publish failed: ${e.message ?: "error"}"
            }
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        repository.close()
        super.onCleared()
    }
}
