package com.example.grpcstream

import android.content.Context

object GrpcServerSettings {
    private const val PREFS_NAME = "grpc_settings"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"

    data class Config(val host: String, val port: Int)

    fun load(context: Context): Config {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val host = prefs.getString(KEY_HOST, BuildConfig.GRPC_HOST)?.trim().orEmpty()
        val port = prefs.getInt(KEY_PORT, BuildConfig.GRPC_PORT)
        val safeHost = if (host.isBlank()) BuildConfig.GRPC_HOST else host
        val safePort = if (port in 1..65535) port else BuildConfig.GRPC_PORT
        return Config(host = safeHost, port = safePort)
    }

    fun save(context: Context, host: String, port: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_HOST, host.trim())
            .putInt(KEY_PORT, port)
            .apply()
    }
}
