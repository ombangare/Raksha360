package com.example.data.repository

import android.content.Context
import com.example.data.remote.BackendApiClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PoliceStationRecord(
    val stationId: String,
    val name: String,
    val nameMr: String,
    val phone: String?,
    val email: String?,
    val address: String,
    val jurisdictionLabel: String,
    val latitude: Double?,
    val longitude: Double?,
    val coordinatesVerified: Boolean,
    val coordinateStatus: String
)

data class HelplineContact(
    val id: String,
    val name: String,
    val category: String,
    val phone: String,
    val scope: String
)

class PoliceStationDirectoryRepository(
    private val context: Context,
    private val backendClient: BackendApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _stations = MutableStateFlow<List<PoliceStationRecord>>(emptyList())
    val stations: StateFlow<List<PoliceStationRecord>> = _stations.asStateFlow()

    private val _helplines = MutableStateFlow<List<HelplineContact>>(emptyList())
    val helplines: StateFlow<List<HelplineContact>> = _helplines.asStateFlow()

    init {
        loadBundledDatasets()
    }

    private fun loadBundledDatasets() {
        try {
            // 1. Load Police Stations
            val stationsJson = context.assets.open("data/nagpur_police_stations.json").bufferedReader().use { it.readText() }
            val rootObj = JSONObject(stationsJson)
            val recordsArray = rootObj.optJSONArray("records") ?: rootObj.optJSONArray("items")
            val stationList = mutableListOf<PoliceStationRecord>()
            if (recordsArray != null) {
                for (i in 0 until recordsArray.length()) {
                    val obj = recordsArray.getJSONObject(i)
                    stationList.add(
                        PoliceStationRecord(
                            stationId = obj.optString("station_id", "station_$i"),
                            name = obj.optString("name", "Police Station"),
                            nameMr = obj.optString("name_mr", ""),
                            phone = obj.optString("phone").takeIf { it.isNotBlank() },
                            email = obj.optString("email").takeIf { it.isNotBlank() },
                            address = obj.optString("address", ""),
                            jurisdictionLabel = obj.optString("jurisdiction_label", ""),
                            latitude = if (obj.has("latitude") && !obj.isNull("latitude")) obj.optDouble("latitude") else null,
                            longitude = if (obj.has("longitude") && !obj.isNull("longitude")) obj.optDouble("longitude") else null,
                            coordinatesVerified = obj.optBoolean("coordinates_verified", false),
                            coordinateStatus = obj.optString("coordinate_status", "OFFICIAL_ADDRESS_ONLY")
                        )
                    )
                }
            }
            _stations.value = stationList

            // 2. Load Public Helplines
            val helplinesJson = context.assets.open("data/official_helplines_and_links.json").bufferedReader().use { it.readText() }
            val helplineRoot = JSONObject(helplinesJson)
            val contactsArray = helplineRoot.optJSONArray("contacts")
            val helplineList = mutableListOf<HelplineContact>()
            if (contactsArray != null) {
                for (i in 0 until contactsArray.length()) {
                    val obj = contactsArray.getJSONObject(i)
                    helplineList.add(
                        HelplineContact(
                            id = obj.optString("id", "helpline_$i"),
                            name = obj.optString("name", "Emergency Helpline"),
                            category = obj.optString("category", "emergency"),
                            phone = obj.optString("phone", "112"),
                            scope = obj.optString("scope", "")
                        )
                    )
                }
            }
            _helplines.value = helplineList
        } catch (e: Exception) {
            android.util.Log.e("PoliceDirectoryRepo", "Error loading bundled datasets: ${e.message}", e)
        }
    }

    suspend fun refreshHelplinesFromBackend(): Result<List<HelplineContact>> = withContext(ioDispatcher) {
        val res = backendClient.fetchPublicHelplines()
        res.onSuccess { list ->
            if (list.isNotEmpty()) {
                _helplines.value = list
            }
        }
        res
    }

    fun search(query: String): List<PoliceStationRecord> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return _stations.value
        return _stations.value.filter {
            it.name.lowercase().contains(q) ||
            it.nameMr.lowercase().contains(q) ||
            it.address.lowercase().contains(q) ||
            it.jurisdictionLabel.lowercase().contains(q)
        }
    }

    fun findNearestStation(lat: Double, lon: Double): PoliceStationRecord? {
        val valid = _stations.value.filter { it.latitude != null && it.longitude != null && it.latitude != 0.0 }
        if (valid.isEmpty()) return null
        return valid.minByOrNull { st ->
            haversineKm(lat, lon, st.latitude!!, st.longitude!!)
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
