package com.example.data.repository

/**
 * Stage in the end-to-end authentication flow.
 */
sealed class AuthStage(val stageName: String) {
    object CredentialAcquisition : AuthStage("CREDENTIAL_ACQUISITION")
    object BackendAuthentication : AuthStage("BACKEND_AUTHENTICATION")
    object SessionPersistence : AuthStage("SESSION_PERSISTENCE")

    override fun toString(): String = stageName
}

/**
 * Granular error categories for authentication diagnostics.
 * Explicitly distinguishes Google Credential Manager timeouts from backend network timeouts.
 */
enum class AuthErrorCategory {
    GOOGLE_CREDENTIAL_TIMEOUT,
    GOOGLE_CREDENTIAL_CANCELED,
    GOOGLE_CREDENTIAL_ERROR,
    BACKEND_NETWORK_TIMEOUT,
    BACKEND_HTTP_ERROR,
    BACKEND_NETWORK_ERROR,
    BACKEND_OFFLINE,
    SESSION_PERSISTENCE_ERROR,
    CONFIGURATION_ERROR,
    UNKNOWN_ERROR
}

/**
 * Structured exception thrown during authentication.
 * Records the stage, error category, elapsed time, and optional HTTP status code.
 * Ensures user-friendly error messages rather than raw strings like "timeout".
 */
class AuthenticationException(
    val stage: AuthStage,
    val category: AuthErrorCategory,
    val elapsedMs: Long,
    val httpStatusCode: Int? = null,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun toString(): String {
        return "AuthenticationException(stage=$stage, category=$category, elapsedMs=${elapsedMs}ms, httpCode=$httpStatusCode, message='$message')"
    }
}
