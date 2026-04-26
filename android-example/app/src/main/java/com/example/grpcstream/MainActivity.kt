package com.example.grpcstream

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.grpcstream.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.subscribeBtn.setOnClickListener {
            val topic = binding.topicInput.text.toString().trim()
            viewModel.subscribe(topic)
            binding.subscribeBtn.isEnabled = false
            binding.unsubscribeBtn.isEnabled = true
        }

        binding.unsubscribeBtn.setOnClickListener {
            viewModel.unsubscribe()
            binding.subscribeBtn.isEnabled = true
            binding.unsubscribeBtn.isEnabled = false
        }

        binding.publishBtn.setOnClickListener {
            val topic = binding.topicInput.text.toString().trim()
            val data = binding.messageInput.text.toString().trim()
            viewModel.publish(topic, data)
            binding.messageInput.setText("")
        }

        observeUi()
    }

    private fun observeUi() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.status.collect { status ->
                        binding.statusText.text = status
                    }
                }
                launch {
                    viewModel.events.collect { events ->
                        if (events.isEmpty()) {
                            binding.eventsText.text = "No events yet."
                        } else {
                            val body = events.joinToString("\n\n") { e ->
                                "#${e.topic}\n${e.data}\n${e.id}"
                            }
                            binding.eventsText.text = body
                        }
                    }
                }
            }
        }
    }
}
