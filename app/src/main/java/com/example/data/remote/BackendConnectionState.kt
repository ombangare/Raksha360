package com.example.data.remote

sealed class BackendConnectionState {
    object Connecting : BackendConnectionState()

    data class Connected(
        val endpoint: String,
        val isUserAuthenticated: Boolean,
        val userEmail: String? = null,
        val isDemoMode: Boolean = false,
        val database: String? = null,
        val latencyMs: Long? = null
    ) : BackendConnectionState()

    data class Offline(
        val reason: String = "Device is offline. Local safety features, voice triggers, siren, and offline queue remain active."
    ) : BackendConnectionState()

    data class AuthenticationRequired(
        val reason: String = "Sign in with Google to enable live control room alerts. Offline safety remains active."
    ) : BackendConnectionState()

    data class ServiceUnavailable(
        val error: String,
        val lastAttemptAt: Long = System.currentTimeMillis()
    ) : BackendConnectionState()
}
