package com.example.domain.route

// Android client models for Nagpur route spatial context and support scoring.
// Matches backend schemas from backend/app/route_scoring.py and /api/v1/routes/ endpoints.

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class RouteLightingCoverage(
    val explicitLitKm: Double = 0.0,
    val explicitUnlitKm: Double = 0.0,
    val unknownLightingKm: Double = 0.0,
    val note: String = "Missing tags represent unknown lighting on OpenStreetMap, not proof lights are absent."
)

data class RouteWoodedExposure(
    val intersectingKm: Double = 0.0,
    val coveragePercent: Double = 0.0,
    val categories: List<String> = emptyList(),
    val note: String = "Woodland and parks are environmental context; they do not indicate crime or danger."
)

data class RouteRelativeIsolation(
    val lowSupportSegmentsKm: Double = 0.0,
    val coincidingSignals: String = ""
)

data class RouteBlackspotItem(
    val id: Int = 0,
    val name: String = "",
    val distanceMeters: Double = 0.0,
    val highway: String = ""
)

data class RouteHistoricalAccidentExposure(
    val blackspotCount: Int = 0,
    val exposureScore: Double = 0.0,
    val nearBlackspots: List<RouteBlackspotItem> = emptyList(),
    val disclaimer: String = "National Highway vehicle crash statistics (2021-2023) from Maharashtra Highway Police. Not crime or personal assault data."
)

data class RouteDataFreshness(
    val extractedAtUtc: String = "",
    val source: String = "",
    val coverageNote: String = ""
)

data class RouteAnalysisResult(
    val label: String = "Candidate Route",
    val mode: String = "walking",
    val routeLengthKm: Double = 0.0,
    val etaMinutes: Int = 0,
    val primaryLabel: String = "Route Context",
    val compositeSupportScore: Double = 50.0,
    val supportCoveragePercent: Double = 0.0,
    val nearestEmergencyFacility: String? = null,
    val lightingCoverage: RouteLightingCoverage = RouteLightingCoverage(),
    val woodedExposure: RouteWoodedExposure = RouteWoodedExposure(),
    val relativeIsolation: RouteRelativeIsolation = RouteRelativeIsolation(),
    val historicalAccidentExposure: RouteHistoricalAccidentExposure = RouteHistoricalAccidentExposure(),
    val evidenceExplanations: List<String> = emptyList(),
    val dataFreshness: RouteDataFreshness = RouteDataFreshness()
)

data class RouteCandidateInput(
    val id: String,
    val coordinates: List<Pair<Double, Double>>,
    val label: String = "Candidate Route",
    val mode: String = "walking"
)

data class RouteRankItem(
    val routeId: String,
    val label: String,
    val compositeSupportScore: Double,
    val primaryLabel: String,
    val routeLengthKm: Double,
    val etaMinutes: Int,
    val nearBlackspots: Int,
    val woodedKm: Double,
    val supportCoveragePercent: Double
)

data class RouteComparisonSummary(
    val candidateCount: Int,
    val mostSupportedRouteId: String?,
    val recommendationNote: String,
    val candidateRankings: List<RouteRankItem>
)

data class RouteLayersSummary(
    val status: String,
    val blackspotCount: Int,
    val facilitiesCount: Int,
    val greenPolygonCount: Int,
    val roadFeaturesCount: Int,
    val bboxNote: String
)
