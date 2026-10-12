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
        private const val KEY_IS_DEMO = "auth_is_demo"

        /**
         * Placeholders for Google OAuth 2.0 Web Client ID.
         * In production, configure via Secrets Gradle Plugin or BuildConfig.
         */
        const val DEFAULT_WEB_CLIENT_ID_PLACEHOLDER = "YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        restoreSession()
    }

    /**
     * Restores locally stored user session upon application startup.
     */
    fun restoreSession() {
        val uid = prefs.getString(KEY_UID, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val name = prefs.getString(KEY_NAME, null)
        val photo = prefs.getString(KEY_PHOTO, null)
        val isDemo = prefs.getBoolean(KEY_IS_DEMO, false)

        if (uid != null && email != null && name != null) {
            val user = UserProfile(
                uid = uid,
                displayName = name,
                email = email,
                photoUrl = photo,
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
     * Executes Google Sign-in flow via modern Android Credential Manager.
     */
    suspend fun signInWithGoogle(
        activity: Activity,
        serverClientId: String = DEFAULT_WEB_CLIENT_ID_PLACEHOLDER
    ): Result<UserProfile> = withContext(ioDispatcher) {
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

                val user = UserProfile(
                    uid = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.givenName ?: "User",
                    email = googleIdTokenCredential.id, // In GoogleIdTokenCredential, id is the user's email
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    idToken = googleIdTokenCredential.idToken,
                    isDemoUser = false
                )

                // Persist session locally
                persistUserSession(user)
                _authState.value = AuthState.Authenticated(user)

                auditLogger.logSecurityEvent(
                    action = "GOOGLE_SIGN_IN_SUCCESS",
                    category = "AUTHENTICATION",
                    details = "Authenticated via Credential Manager: ${user.email}",
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
            val errorMsg = if (serverClientId == DEFAULT_WEB_CLIENT_ID_PLACEHOLDER) {
                "Google OAuth Web Client ID not configured. Please supply a valid Client ID or use Demo Mode."
            } else {
                "Google Sign-In failed: ${e.localizedMessage}"
            }
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
     * Signs out the user, clears Credential Manager state, and wipes local session.
     */
    suspend fun signOut(): Unit = withContext(ioDispatcher) {
        val uid = prefs.getString(KEY_UID, null)
        val isDemo = prefs.getBoolean(KEY_IS_DEMO, false)
        if (!uid.isNullOrEmpty() && !isDemo) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(1000) {
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
            .putBoolean(KEY_IS_DEMO, user.isDemoUser)
            .apply()
    }
}
