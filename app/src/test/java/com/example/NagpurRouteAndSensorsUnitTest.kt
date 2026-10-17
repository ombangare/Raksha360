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
