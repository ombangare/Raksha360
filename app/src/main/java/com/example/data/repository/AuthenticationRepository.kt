package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.remote.BackendApiClient
import com.example.service.audit.IAuditLoggerService
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val idToken: String? = null,
    val sessionToken: String? = null,
    val expiresAt: Long = 0L,
    val isDemoUser: Boolean = false
)

sealed class AuthState {
    object Initializing : AuthState()
    data class Authenticated(val user: UserProfile) : AuthState()
    data class Unauthenticated(val message: String? = null) : AuthState()
}

/**
 * Authentication Repository using Android Credential Manager for Sign in with Google.
 * Provides session persistence, token verification boundary, and explicit Demo Mode isolation.
 */
class AuthenticationRepository(
    private val context: Context,
    private val backendClient: BackendApiClient,
    private val auditLogger: IAuditLoggerService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "AuthRepository"
        private const val PREFS_NAME = "naari_kavach_auth_prefs"
        private const val KEY_UID = "auth_uid"
        private const val KEY_NAME = "auth_display_name"
        private const val KEY_EMAIL = "auth_email"
        private const val KEY_PHOTO = "auth_photo_url"
        private const val KEY_SESSION_TOKEN = "auth_session_token"
        private const val KEY_EXPIRES_AT = "auth_expires_at"
        private const val KEY_IS_DEMO = "auth_is_demo"

        /**
         * Placeholders for Google OAuth 2.0 Web Client ID.
         * In production, configure via local.properties or environment variable.
         */
        const val DEFAULT_WEB_CLIENT_ID_PLACEHOLDER = "YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com"

        fun getWebClientId(): String {
            val configId = com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
            return if (configId.isNotBlank()) configId else DEFAULT_WEB_CLIENT_ID_PLACEHOLDER
        }

        fun isWebClientIdConfigured(): Boolean {
            val id = getWebClientId()
            return id.isNotBlank() && id != DEFAULT_WEB_CLIENT_ID_PLACEHOLDER && id.contains(".apps.googleusercontent.com")
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser: UserProfile?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    init {
        restoreSession()
    }

    /**
     * Validates active session token with the backend service.
     * If backend returns HTTP 401 or invalid session, wipes local credentials.
     * If backend is unreachable or phone is offline, preserves local authenticated state.
     */
    suspend fun validateSessionWithBackend(): Result<UserProfile> = withContext(ioDispatcher) {
        val current = currentUser
        if (current == null || current.isDemoUser) {
            return@withContext Result.failure(IllegalStateException("No live user session to validate."))
        }
        val token = current.sessionToken
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No session token available."))
        }

        val result = backendClient.verifySession(token)
        if (result.isSuccess) {
            val verified = result.getOrThrow()
            val updated = current.copy(
                displayName = verified.displayName,
                email = verified.email,
                photoUrl = verified.photoUrl ?: current.photoUrl,
                expiresAt = verified.expiresAt
            )
            persistUserSession(updated)
            _authState.value = AuthState.Authenticated(updated)
            Result.success(updated)
        } else {
            val error = result.exceptionOrNull()
            val message = error?.message ?: ""
            if (message.contains("401") || message.contains("invalid", ignoreCase = true) || message.contains("expired", ignoreCase = true)) {
                // Definite authorization rejection from backend: invalidate local session
                prefs.edit().clear().apply()
                _authState.value = AuthState.Unauthenticated("Your session is no longer valid. Please sign in again.")
                auditLogger.logSecurityEvent(
                    action = "SESSION_INVALIDATED_BY_BACKEND",
                    category = "AUTHENTICATION",
                    details = "Backend rejected session for ${current.email}: $message",
                    severity = "WARNING"
                )
                Result.failure(error ?: IllegalStateException("Session invalidated by backend."))
            } else {
                // Network error, 503, or offline: preserve local session (graceful degradation)
                Result.failure(error ?: java.io.IOException("Backend unreachable: preserved offline session."))
            }
        }
    }


    /**
     * Restores locally stored user session upon application startup.
     * Enforces session expiration detection.
     */
    fun restoreSession() {
        val uid = prefs.getString(KEY_UID, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val name = prefs.getString(KEY_NAME, null)
        val photo = prefs.getString(KEY_PHOTO, null)
        val sessionToken = prefs.getString(KEY_SESSION_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val isDemo = prefs.getBoolean(KEY_IS_DEMO, false)

        if (uid != null && email != null && name != null) {
            val now = System.currentTimeMillis()
            if (!isDemo && expiresAt > 0 && now >= expiresAt) {
                // Detected expired session: wipe local credentials and require re-authentication
                prefs.edit().clear().apply()
                _authState.value = AuthState.Unauthenticated("Your session has expired. Please sign in again with Google.")
                auditLogger.logSecurityEvent(
                    action = "SESSION_EXPIRED",
                    category = "AUTHENTICATION",
                    details = "Expired session cleared for account: $email",
                    severity = "WARNING"
                )
                return
            }

            val user = UserProfile(
                uid = uid,
                displayName = name,
                email = email,
                photoUrl = photo,
                sessionToken = sessionToken,
                expiresAt = expiresAt,
                isDemoUser = isDemo
            )
            _authState.value = AuthState.Authenticated(user)
            auditLogger.logSecurityEvent(
                action = "SESSION_RESTORED",
                category = "AUTHENTICATION",
                details = "User session restored for account: $email (Demo: $isDemo)",
                severity = "INFO"
            )
        } else {
            _authState.value = AuthState.Unauthenticated()
        }
    }

    /**
     * Executes Google Sign-in flow via modern Android Credential Manager and verifies
     * the signed ID token against the Raksha360 backend service.
     */
    suspend fun signInWithGoogle(
        activity: Activity,
        serverClientId: String = getWebClientId()
    ): Result<UserProfile> = withContext(ioDispatcher) {
        if (serverClientId == DEFAULT_WEB_CLIENT_ID_PLACEHOLDER || serverClientId.isBlank()) {
            val errorMsg = "Google OAuth Web Client ID not configured. Please add 'google.web.client.id=YOUR_CLIENT_ID' in local.properties or use Demo Mode."
            _authState.value = AuthState.Unauthenticated(errorMsg)
            return@withContext Result.failure(
                AuthenticationException(
                    stage = AuthStage.CredentialAcquisition,
                    category = AuthErrorCategory.CONFIGURATION_ERROR,
                    elapsedMs = 0L,
                    message = errorMsg
                )
            )
        }

        // STAGE 1: CREDENTIAL_ACQUISITION
        val stage1Start = System.currentTimeMillis()
        Log.i(TAG, "[STAGE: CREDENTIAL_ACQUISITION] Starting Google Credential Manager flow")

        val rawIdToken: String
        val profilePictureUriString: String?

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val stage1Elapsed = System.currentTimeMillis() - stage1Start
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val token = googleIdTokenCredential.idToken
                if (token.isNullOrBlank()) {
                    val msg = "No Google ID token was returned by Credential Manager."
                    Log.e(TAG, "[STAGE: CREDENTIAL_ACQUISITION] Empty ID token after ${stage1Elapsed}ms (Category: GOOGLE_CREDENTIAL_ERROR)")
                    val authEx = AuthenticationException(
                        stage = AuthStage.CredentialAcquisition,
                        category = AuthErrorCategory.GOOGLE_CREDENTIAL_ERROR,
                        elapsedMs = stage1Elapsed,
                        message = msg
                    )
                    _authState.value = AuthState.Unauthenticated(msg)
                    return@withContext Result.failure(authEx)
                }
                rawIdToken = token
                profilePictureUriString = googleIdTokenCredential.profilePictureUri?.toString()
                Log.i(TAG, "[STAGE: CREDENTIAL_ACQUISITION] Credential acquired successfully in ${stage1Elapsed}ms")
            } else {
                val msg = "Unexpected credential format returned from Credential Manager"
                Log.w(TAG, "[STAGE: CREDENTIAL_ACQUISITION] Unexpected credential format after ${stage1Elapsed}ms (Category: GOOGLE_CREDENTIAL_ERROR)")
                val authEx = AuthenticationException(
                    stage = AuthStage.CredentialAcquisition,
                    category = AuthErrorCategory.GOOGLE_CREDENTIAL_ERROR,
                    elapsedMs = stage1Elapsed,
                    message = msg
                )
                _authState.value = AuthState.Unauthenticated(msg)
                return@withContext Result.failure(authEx)
            }
        } catch (e: GetCredentialCancellationException) {
            val stage1Elapsed = System.currentTimeMillis() - stage1Start
            Log.i(TAG, "[STAGE: CREDENTIAL_ACQUISITION] User cancelled credential selection after ${stage1Elapsed}ms (Category: GOOGLE_CREDENTIAL_CANCELED)")
            val authEx = AuthenticationException(
                stage = AuthStage.CredentialAcquisition,
                category = AuthErrorCategory.GOOGLE_CREDENTIAL_CANCELED,
                elapsedMs = stage1Elapsed,
                message = "Sign-in cancelled",
                cause = e
            )
            _authState.value = AuthState.Unauthenticated("Sign-in cancelled")
            return@withContext Result.failure(authEx)
        } catch (e: GetCredentialException) {
            val stage1Elapsed = System.currentTimeMillis() - stage1Start
            val isTimeout = e.type.contains("TIMEOUT", ignoreCase = true) ||
                e.message?.contains("timeout", ignoreCase = true) == true ||
                e.message?.contains("15") == true
            val category = if (isTimeout) AuthErrorCategory.GOOGLE_CREDENTIAL_TIMEOUT else AuthErrorCategory.GOOGLE_CREDENTIAL_ERROR
            val userMsg = if (isTimeout) {
                "Google account selection timed out. Please try tapping 'Continue with Google' again."
            } else {
                "Google Sign-In failed: ${e.localizedMessage ?: "Unknown error"}"
            }

            if (isTimeout) {
                Log.w(TAG, "[STAGE: CREDENTIAL_ACQUISITION] Credential acquisition timed out after ${stage1Elapsed}ms (Category: GOOGLE_CREDENTIAL_TIMEOUT)")
            } else {
                Log.e(TAG, "[STAGE: CREDENTIAL_ACQUISITION] Credential acquisition failed after ${stage1Elapsed}ms (Category: GOOGLE_CREDENTIAL_ERROR, type: ${e.type})")
            }

            val authEx = AuthenticationException(
                stage = AuthStage.CredentialAcquisition,
                category = category,
                elapsedMs = stage1Elapsed,
                message = userMsg,
                cause = e
            )
            _authState.value = AuthState.Unauthenticated(userMsg)
            auditLogger.logSecurityEvent(
                action = if (isTimeout) "GOOGLE_CREDENTIAL_TIMEOUT" else "GOOGLE_SIGN_IN_FAILED",
                category = "AUTHENTICATION",
                details = "Credential acquisition failure: ${e.type}",
                severity = "WARNING"
            )
            return@withContext Result.failure(authEx)
        } catch (e: Exception) {
            val stage1Elapsed = System.currentTimeMillis() - stage1Start
            Log.e(TAG, "[STAGE: CREDENTIAL_ACQUISITION] Unexpected error after ${stage1Elapsed}ms", e)
            val authEx = AuthenticationException(
                stage = AuthStage.CredentialAcquisition,
                category = AuthErrorCategory.UNKNOWN_ERROR,
                elapsedMs = stage1Elapsed,
                message = e.localizedMessage ?: "Unexpected sign-in error",
                cause = e
            )
            _authState.value = AuthState.Unauthenticated(authEx.message)
            return@withContext Result.failure(authEx)
        }

        // STAGE 2: BACKEND_AUTHENTICATION
        val stage2Start = System.currentTimeMillis()
        Log.i(TAG, "[STAGE: BACKEND_AUTHENTICATION] Starting token verification with backend")
        val backendResult = backendClient.verifyGoogleIdToken(rawIdToken)
        val stage2Elapsed = System.currentTimeMillis() - stage2Start

        if (backendResult.isFailure) {
            val error = backendResult.exceptionOrNull()
            val authEx = if (error is AuthenticationException) {
                error
            } else {
                val isTimeout = error is java.net.SocketTimeoutException || error?.message?.contains("timeout", ignoreCase = true) == true
                AuthenticationException(
                    stage = AuthStage.BackendAuthentication,
                    category = if (isTimeout) AuthErrorCategory.BACKEND_NETWORK_TIMEOUT else AuthErrorCategory.BACKEND_HTTP_ERROR,
                    elapsedMs = stage2Elapsed,
                    message = if (isTimeout) {
                        "Connection to Raksha360 control room timed out. The server may be waking up; please try again."
                    } else {
                        error?.localizedMessage ?: "Backend verification failed"
                    },
                    cause = error
                )
            }

            Log.w(
                TAG,
                "[STAGE: BACKEND_AUTHENTICATION] Backend verification failed after ${authEx.elapsedMs}ms (Category: ${authEx.category}, HTTP: ${authEx.httpStatusCode ?: "N/A"})"
            )
            _authState.value = AuthState.Unauthenticated(authEx.message)
            auditLogger.logSecurityEvent(
                action = if (authEx.category == AuthErrorCategory.BACKEND_NETWORK_TIMEOUT) "BACKEND_AUTH_TIMEOUT" else "GOOGLE_TOKEN_REJECTED",
                category = "AUTHENTICATION",
                details = "Backend verification failure: category=${authEx.category}, elapsedMs=${authEx.elapsedMs}ms",
                severity = "WARNING"
            )
            return@withContext Result.failure(authEx)
        }

        val authResp = backendResult.getOrThrow()
        Log.i(TAG, "[STAGE: BACKEND_AUTHENTICATION] Backend verification succeeded in ${stage2Elapsed}ms")

        // STAGE 3: SESSION_PERSISTENCE
        val stage3Start = System.currentTimeMillis()
        Log.i(TAG, "[STAGE: SESSION_PERSISTENCE] Storing session credentials")
        try {
            val user = UserProfile(
                uid = authResp.uid,
                displayName = authResp.displayName,
                email = authResp.email,
                photoUrl = authResp.photoUrl ?: profilePictureUriString,
                idToken = rawIdToken,
                sessionToken = authResp.sessionToken,
                expiresAt = authResp.expiresAt,
                isDemoUser = false
            )

            persistUserSession(user)
            _authState.value = AuthState.Authenticated(user)
            val stage3Elapsed = System.currentTimeMillis() - stage3Start
            Log.i(TAG, "[STAGE: SESSION_PERSISTENCE] Session persisted successfully in ${stage3Elapsed}ms (ExpiresAt: ${user.expiresAt})")

            auditLogger.logSecurityEvent(
                action = "GOOGLE_SIGN_IN_SUCCESS",
                category = "AUTHENTICATION",
                details = "User signed in and session persisted successfully (durationMs=${stage1Start + stage2Elapsed + stage3Elapsed})",
                severity = "INFO"
            )

            Result.success(user)
        } catch (e: Exception) {
            val stage3Elapsed = System.currentTimeMillis() - stage3Start
            Log.e(TAG, "[STAGE: SESSION_PERSISTENCE] Failed to persist session after ${stage3Elapsed}ms (Category: SESSION_PERSISTENCE_ERROR)", e)
            val authEx = AuthenticationException(
                stage = AuthStage.SessionPersistence,
                category = AuthErrorCategory.SESSION_PERSISTENCE_ERROR,
                elapsedMs = stage3Elapsed,
                message = "Failed to store session locally: ${e.localizedMessage ?: "Unknown error"}",
                cause = e
            )
            _authState.value = AuthState.Unauthenticated(authEx.message)
            Result.failure(authEx)
        }
    }

    /**
     * Explicit Demo Mode sign-in for evaluation and offline testing without Google Play Services.
     * Clearly isolates demo data and prevents silent preloading of fake identities.
     */
    fun signInDemoMode(demoName: String = "Demo User", demoEmail: String = "demo.safety@naarikavach.local") {
        val demoUser = UserProfile(
            uid = "demo_user_offline_001",
            displayName = demoName,
            email = demoEmail,
            photoUrl = null,
            sessionToken = null,
            expiresAt = 0L,
            isDemoUser = true
        )
        persistUserSession(demoUser)
        _authState.value = AuthState.Authenticated(demoUser)

        auditLogger.logSecurityEvent(
            action = "DEMO_SESSION_STARTED",
            category = "AUTHENTICATION",
            details = "Explicit Demo Mode session started: $demoEmail",
            severity = "INFO"
        )
    }

    /**
     * Signs out the user, revokes backend session, clears Credential Manager state, and wipes local session.
     */
    suspend fun signOut(): Unit = withContext(ioDispatcher) {
        val uid = prefs.getString(KEY_UID, null)
        val isDemo = prefs.getBoolean(KEY_IS_DEMO, false)
        val sessionToken = prefs.getString(KEY_SESSION_TOKEN, null)

        if (!uid.isNullOrEmpty() && !isDemo) {
            if (!sessionToken.isNullOrEmpty()) {
                try {
                    backendClient.revokeSession(sessionToken)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed revoking backend session: ${e.message}")
                }
            }
            try {
                kotlinx.coroutines.withTimeoutOrNull(1500) {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing CredentialManager state: ${e.message}")
            }
        }

        val previousEmail = prefs.getString(KEY_EMAIL, "unknown")
        prefs.edit().clear().apply()
        _authState.value = AuthState.Unauthenticated()

        auditLogger.logSecurityEvent(
            action = "USER_SIGNED_OUT",
            category = "AUTHENTICATION",
            details = "Session cleared for user: $previousEmail",
            severity = "INFO"
        )
    }

    private fun persistUserSession(user: UserProfile) {
        prefs.edit()
            .putString(KEY_UID, user.uid)
            .putString(KEY_NAME, user.displayName)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_PHOTO, user.photoUrl)
            .putString(KEY_SESSION_TOKEN, user.sessionToken)
            .putLong(KEY_EXPIRES_AT, user.expiresAt)
            .putBoolean(KEY_IS_DEMO, user.isDemoUser)
            .apply()
    }
}
