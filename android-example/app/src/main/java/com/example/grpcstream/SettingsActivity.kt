package com.example.grpcstream

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.grpcstream.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val config = GrpcServerSettings.load(this)
        binding.hostInput.setText(config.host)
        binding.portInput.setText(config.port.toString())

        binding.saveBtn.setOnClickListener {
            val host = binding.hostInput.text.toString().trim()
            val port = binding.portInput.text.toString().trim().toIntOrNull()

            if (host.isBlank()) {
                binding.hostInput.error = getString(R.string.host_required)
                return@setOnClickListener
            }
            if (port == null || port !in 1..65535) {
                binding.portInput.error = getString(R.string.invalid_port)
                return@setOnClickListener
            }

            GrpcServerSettings.save(this, host, port)
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.cancelBtn.setOnClickListener { finish() }
    }
}
