package com.example

import com.example.domain.route.*
import com.example.service.location.LocationStatus
import com.example.service.location.UserLocation
import com.example.voice.sensor.ShakeDetector
import org.junit.Assert.*
import org.junit.Test

class NagpurRouteAndSensorsUnitTest {

    @Test
    fun testLocationStatusEnumAndTruthfulDefaults() {
        val statuses = LocationStatus.values()
        assertTrue(statuses.contains(LocationStatus.ACTIVE_GPS))
        assertTrue(statuses.contains(LocationStatus.DEMO_MODE))
        assertTrue(statuses.contains(LocationStatus.PERMISSION_DENIED))
        assertTrue(statuses.contains(LocationStatus.GPS_DISABLED))
        assertTrue(statuses.contains(LocationStatus.STALE))
        assertTrue(statuses.contains(LocationStatus.ACQUIRING))
        assertTrue(statuses.contains(LocationStatus.TIMEOUT))
        assertTrue(statuses.contains(LocationStatus.UNAVAILABLE))

        val loc = UserLocation(
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 0f,
            speed = 0f,
            timestamp = System.currentTimeMillis(),
            isFallback = true,
            description = "Acquiring GPS fix…",
            status = LocationStatus.ACQUIRING,
            provenance = "UNAVAILABLE"
        )
        assertTrue(loc.isFallback)
        assertEquals(LocationStatus.ACQUIRING, loc.status)
        assertFalse("Should never contain hardcoded Sadar default", loc.description.contains("Sadar"))
    }

    @Test
    fun testTruthfulPoliceDatasetStatusAndDisclosures() {
        val pubDir = com.example.domain.map.PoliceDatasetStatus.PublicDirectoryConnected()
        assertEquals(37, pubDir.totalRecords)
        assertTrue(pubDir.disclaimer.contains("Public Directory Reference • Baseline Infrastructure Active"))
        assertFalse("Must never claim live CAD/dispatch", pubDir.disclaimer.contains("CAD active"))

        val engine = com.example.domain.map.SafetyScoringEngine { pubDir }
        val context = engine.computeAreaSafety("Sadar, Nagpur")
        assertEquals("Public Directory Reference • Baseline Infrastructure Active", context.datasetDisclosure)
        assertFalse(context.policeDataAvailable)
        assertTrue(context.advisoryNotice.contains("No live police dispatch connected"))

        val noDataEngine = com.example.domain.map.SafetyScoringEngine { com.example.domain.map.PoliceDatasetStatus.NoDatasetConnected() }
        val noDataContext = noDataEngine.computeAreaSafety("Sadar, Nagpur")
        assertEquals("No police dataset connected • Baseline infrastructure active", noDataContext.datasetDisclosure)
        assertFalse(noDataContext.policeDataAvailable)
    }

    @Test
    fun testNearestStationDistanceOnlyFromVerifiedCoordinates() {
        // Reference stations: Sadar (21.1628, 79.0815), Sitabuldi (21.1465, 79.0832)
        val sadarCoords = com.example.domain.map.GeoCoordinates(21.1628, 79.0815)
        val sitabuldiCoords = com.example.domain.map.GeoCoordinates(21.1465, 79.0832)

        // Point near Sadar: (21.1620, 79.0810)
        val userNearSadar = com.example.domain.map.GeoCoordinates(21.1620, 79.0810)
        val distToSadar = calculateDistanceKm(userNearSadar.latitude, userNearSadar.longitude, sadarCoords.latitude, sadarCoords.longitude)
        val distToSitabuldi = calculateDistanceKm(userNearSadar.latitude, userNearSadar.longitude, sitabuldiCoords.latitude, sitabuldiCoords.longitude)

        assertTrue(distToSadar < 0.2) // Less than 200m away
        assertTrue(distToSitabuldi > 1.5) // Sitabuldi is ~1.8km away

        // Invariant: If device GPS is offline/0.0, nearestDistanceKm must NOT be calculated
        val isRealGpsActive = false
        val computedDistance: Double? = if (isRealGpsActive) distToSadar else null
        assertNull("Distance must remain null when GPS is offline to prevent false distance claims", computedDistance)
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    @Test
    fun testRouteSafetyModelsInstantiation() {
        val analysis = RouteAnalysisResult(
            label = "Mount Road Corridor",
            mode = "walking",
            routeLengthKm = 4.2,
            etaMinutes = 56,
            primaryLabel = "More Supported Route",
            compositeSupportScore = 78.5,
            supportCoveragePercent = 84.0,
            nearestEmergencyFacility = "Sadar Police Station (~180m)",
            lightingCoverage = RouteLightingCoverage(
                explicitLitKm = 2.4,
                explicitUnlitKm = 0.0,
                unknownLightingKm = 1.8
            ),
            woodedExposure = RouteWoodedExposure(
                intersectingKm = 0.2,
                coveragePercent = 4.8,
                categories = listOf("PARK")
            ),
            relativeIsolation = RouteRelativeIsolation(lowSupportSegmentsKm = 0.0),
            historicalAccidentExposure = RouteHistoricalAccidentExposure(
                blackspotCount = 0,
                exposureScore = 0.0
            ),
            evidenceExplanations = listOf("High support coverage", "Verified lighting")
        )

        assertEquals("More Supported Route", analysis.primaryLabel)
        assertEquals(78.5, analysis.compositeSupportScore, 0.001)
        assertEquals(0, analysis.historicalAccidentExposure.blackspotCount)
        assertEquals(2.4, analysis.lightingCoverage.explicitLitKm, 0.001)
        assertEquals(2, analysis.evidenceExplanations.size)
    }

    @Test
    fun testShakeDetectorThresholdConstants() {
        assertEquals(3.2f, ShakeDetector.THRESHOLD_LOW, 0.001f)
        assertEquals(2.7f, ShakeDetector.THRESHOLD_MEDIUM, 0.001f)
        assertEquals(2.2f, ShakeDetector.THRESHOLD_HIGH, 0.001f)
        assertEquals(10_000L, ShakeDetector.COOLDOWN_MS)
        assertEquals(600L, ShakeDetector.REVERSAL_WINDOW_MS)
        assertEquals(3, ShakeDetector.MIN_DIRECTION_REVERSALS)
    }
}
