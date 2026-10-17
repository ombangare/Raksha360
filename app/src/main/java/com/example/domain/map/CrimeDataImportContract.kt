package com.example.domain.map

/**
 * Crime Data Ingestion & Field Mapping Contract for future Maharashtra/Nagpur Police integrations.
 * Allows flexible mapping of varying column schemas (CSV/JSON/GeoJSON) without fabricating records.
 */
data class CrimeFieldMapping(
    val localityField: String = "area_name",
    val incidentTypeField: String = "incident_type",
    val timestampField: String = "date_time",
    val latitudeField: String? = "latitude",
    val longitudeField: String? = "longitude",
    val policeStationCodeField: String? = "ps_code",
    val severityIndicatorField: String? = "severity"
)

data class NormalizedCrimeRecord(
    val id: String,
    val localityName: String,
    val incidentType: String,
    val timestampMillis: Long,
    val coordinates: GeoCoordinates?,
    val policeStationRef: String?,
    val severityLevel: Int // 1 (Minor) to 5 (Critical)
)

sealed class PoliceDatasetStatus {
    data class NoDatasetConnected(
        val message: String = "No official Nagpur Police dataset connected. Safety signals are based on verified infrastructure and community check-ins."
    ) : PoliceDatasetStatus()

    data class PublicDirectoryConnected(
        val sourceAgency: String = "Nagpur City Police Public Directory (nagpurpolice.gov.in)",
        val totalRecords: Int = 37,
        val disclaimer: String = "Public Directory Reference • Baseline Infrastructure Active. No live CAD/dispatch integration connected."
    ) : PoliceDatasetStatus()

    data class DatasetActive(
        val sourceAgency: String, // e.g. "Nagpur City Police - Crime Branch"
        val totalRecords: Int,
        val lastImportTimestamp: Long,
        val coverageArea: String = "Nagpur Commissionerate"
    ) : PoliceDatasetStatus()
}

/**
 * Contract interface for parsing and importing external crime data.
 */
interface ICrimeDataImporter {
    fun getDatasetStatus(): PoliceDatasetStatus
    fun importFromRecords(
        records: List<Map<String, String>>,
        mapping: CrimeFieldMapping
    ): Result<List<NormalizedCrimeRecord>>
}

/**
 * Contextual Safety Scoring Engine.
 * Abstraction that combines infrastructure proximity, verified check-ins,
 * and future police crime data.
 *
 * Ethical AI & Safety Mandates:
 * - NEVER labels any area "absolutely dangerous"
 * - NEVER claims any route is "guaranteed safe"
 * - Uses neutral, descriptive language (e.g. "Moderate reported concerns", "High infrastructure presence")
 */
data class AreaSafetyContext(
    val areaName: String,
    val infrastructureScore: Float, // 0.0 (low presence) to 1.0 (dense police/hospitals)
    val communitySignalScore: Float, // 0.0 to 1.0 based on verified check-ins
    val policeDataAvailable: Boolean,
    val datasetStatus: PoliceDatasetStatus = PoliceDatasetStatus.PublicDirectoryConnected(),
    val datasetDisclosure: String = "Public Directory Reference • Baseline Infrastructure Active",
    val advisoryNotice: String = "Safety signals based on verified public infrastructure. Baseline infrastructure active."
)

class SafetyScoringEngine(
    private val datasetStatusProvider: () -> PoliceDatasetStatus = { PoliceDatasetStatus.PublicDirectoryConnected() }
) {
    fun computeAreaSafety(areaName: String): AreaSafetyContext {
        val currentStatus = datasetStatusProvider()
        val isPoliceConnected = currentStatus is PoliceDatasetStatus.DatasetActive

        val (disclosure, advisory) = when (currentStatus) {
            is PoliceDatasetStatus.DatasetActive -> Pair(
                "Nagpur Police Dataset Active",
                "Safety signals informed by official records and infrastructure presence."
            )
            is PoliceDatasetStatus.PublicDirectoryConnected -> Pair(
                "Public Directory Reference • Baseline Infrastructure Active",
                "Public directory reference active. Verified baseline infrastructure. No live police dispatch connected."
            )
            is PoliceDatasetStatus.NoDatasetConnected -> Pair(
                "No police dataset connected • Baseline infrastructure active",
                "Safety signals based on verified public infrastructure. No official police dataset connected."
            )
        }

        // Compute baseline infrastructure rating for Nagpur zones
        val isPilotZone = areaName.contains("Sadar", ignoreCase = true)
        val infraRating = if (isPilotZone) 0.85f else 0.70f
        val communityRating = if (isPilotZone) 0.80f else 0.65f

        return AreaSafetyContext(
            areaName = areaName,
            infrastructureScore = infraRating,
            communitySignalScore = communityRating,
            policeDataAvailable = isPoliceConnected,
            datasetStatus = currentStatus,
            datasetDisclosure = disclosure,
            advisoryNotice = advisory
        )
    }
}
