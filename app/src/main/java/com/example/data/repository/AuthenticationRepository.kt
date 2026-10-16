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
            return@withContext Result.failure(IllegalStateException(errorMsg))
        }

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

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val rawIdToken = googleIdTokenCredential.idToken

                if (rawIdToken.isNullOrBlank()) {
                    val msg = "No Google ID token was returned by Credential Manager."
                    _authState.value = AuthState.Unauthenticated(msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }

                // Verify the Google ID token with the backend
                val backendResult = backendClient.verifyGoogleIdToken(rawIdToken)
                if (backendResult.isFailure) {
                    val error = backendResult.exceptionOrNull()
                    val errorMsg = error?.localizedMessage ?: "Backend verification failed"
                    _authState.value = AuthState.Unauthenticated(errorMsg)
                    auditLogger.logSecurityEvent(
                        action = "GOOGLE_TOKEN_REJECTED",
                        category = "AUTHENTICATION",
                        details = "Backend rejected Google ID token: ${error?.message}",
                        severity = "WARNING"
                    )
                    return@withContext Result.failure(error ?: IllegalStateException(errorMsg))
                }

                val authResp = backendResult.getOrThrow()
                val user = UserProfile(
                    uid = authResp.uid,
                    displayName = authResp.displayName,
                    email = authResp.email,
                    photoUrl = authResp.photoUrl ?: googleIdTokenCredential.profilePictureUri?.toString(),
                    idToken = rawIdToken,
                    sessionToken = authResp.sessionToken,
                    expiresAt = authResp.expiresAt,
                    isDemoUser = false
                )

                // Persist session locally
                persistUserSession(user)
                _authState.value = AuthState.Authenticated(user)

                auditLogger.logSecurityEvent(
                    action = "GOOGLE_SIGN_IN_SUCCESS",
                    category = "AUTHENTICATION",
                    details = "Authenticated and verified via backend: ${user.email}",
                    severity = "INFO"
                )

                Result.success(user)
            } else {
                val msg = "Unexpected credential format returned from Credential Manager"
                Log.w(TAG, msg)
                _authState.value = AuthState.Unauthenticated(msg)
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google Sign-In cancelled by user")
            _authState.value = AuthState.Unauthenticated("Sign-in cancelled")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error: ${e.message}", e)
            val errorMsg = "Google Sign-In failed: ${e.localizedMessage}"
            _authState.value = AuthState.Unauthenticated(errorMsg)
            auditLogger.logSecurityEvent(
                action = "GOOGLE_SIGN_IN_FAILED",
                category = "AUTHENTICATION",
                details = "Credential error: ${e.type}",
                severity = "WARNING"
            )
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected sign-in error", e)
            _authState.value = AuthState.Unauthenticated(e.message)
            Result.failure(e)
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
