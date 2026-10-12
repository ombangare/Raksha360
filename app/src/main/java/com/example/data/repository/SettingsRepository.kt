package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("naari_kavach_settings", Context.MODE_PRIVATE)

    private val _isGuardModeEnabled = MutableStateFlow(prefs.getBoolean("guard_mode_enabled", false))
    val isGuardModeEnabled: StateFlow<Boolean> = _isGuardModeEnabled.asStateFlow()

    private val _isDemoMode = MutableStateFlow(prefs.getBoolean("demo_mode_enabled", true))
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(prefs.getString("selected_language", "en") ?: "en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _autoRecordIncidentAudio = MutableStateFlow(prefs.getBoolean("auto_record_incident", true))
    val autoRecordIncidentAudio: StateFlow<Boolean> = _autoRecordIncidentAudio.asStateFlow()

    // Separate, explicit consent for emailing the last known location after missed device heartbeats.
    private val _offlineLocationEmailConsent = MutableStateFlow(prefs.getBoolean("offline_location_email_consent", false))
    val offlineLocationEmailConsent: StateFlow<Boolean> = _offlineLocationEmailConsent.asStateFlow()

    private val _policeEndpointConfigured = MutableStateFlow(prefs.getBoolean("police_endpoint_configured", false))
    val policeEndpointConfigured: StateFlow<Boolean> = _policeEndpointConfigured.asStateFlow()

    private val _policeEndpointUrl = MutableStateFlow(prefs.getString("police_endpoint_url", "http://10.0.2.2:4000/api/v1") ?: "")
    val policeEndpointUrl: StateFlow<String> = _policeEndpointUrl.asStateFlow()

    private val _policeAuthToken = MutableStateFlow(prefs.getString("police_auth_token", "") ?: "")
    val policeAuthToken: StateFlow<String> = _policeAuthToken.asStateFlow()

    // Safety Journey state
    private val _isJourneyActive = MutableStateFlow(prefs.getBoolean("journey_active", false))
    val isJourneyActive: StateFlow<Boolean> = _isJourneyActive.asStateFlow()

    private val _journeyDestination = MutableStateFlow(prefs.getString("journey_destination", "") ?: "")
    val journeyDestination: StateFlow<String> = _journeyDestination.asStateFlow()

    private val _journeyEtaMinutes = MutableStateFlow(prefs.getInt("journey_eta_minutes", 30))
    val journeyEtaMinutes: StateFlow<Int> = _journeyEtaMinutes.asStateFlow()

    fun setGuardModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("guard_mode_enabled", enabled).apply()
        _isGuardModeEnabled.value = enabled
    }

    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean("demo_mode_enabled", enabled).apply()
        _isDemoMode.value = enabled
    }

    fun setLanguage(langCode: String) {
        prefs.edit().putString("selected_language", langCode).apply()
        _selectedLanguage.value = langCode
    }

    fun setAutoRecordIncidentAudio(enabled: Boolean) {
        prefs.edit().putBoolean("auto_record_incident", enabled).apply()
        _autoRecordIncidentAudio.value = enabled
    }

    fun setOfflineLocationEmailConsent(enabled: Boolean) {
        prefs.edit().putBoolean("offline_location_email_consent", enabled).apply()
        _offlineLocationEmailConsent.value = enabled
    }

    fun setPoliceEndpointConfig(configured: Boolean, url: String, token: String) {
        prefs.edit()
            .putBoolean("police_endpoint_configured", configured)
            .putString("police_endpoint_url", url)
            .putString("police_auth_token", token)
            .apply()
        _policeEndpointConfigured.value = configured
        _policeEndpointUrl.value = url
        _policeAuthToken.value = token
    }

    fun setJourneyState(active: Boolean, destination: String = "", etaMinutes: Int = 30) {
        prefs.edit()
            .putBoolean("journey_active", active)
            .putString("journey_destination", destination)
            .putInt("journey_eta_minutes", etaMinutes)
            .apply()
        _isJourneyActive.value = active
        _journeyDestination.value = destination
        _journeyEtaMinutes.value = etaMinutes
    }
}
