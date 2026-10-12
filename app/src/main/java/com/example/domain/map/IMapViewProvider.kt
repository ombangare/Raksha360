package com.example.domain.map

import android.content.Context
import android.view.View

/**
 * Map View Provider Interface.
 * Decouples the map rendering engine (MapLibre / OSMDroid / Vector Tiles)
 * from the Compose UI layer so the underlying engine can be swapped seamlessly.
 */
interface IMapViewProvider {
    fun createMapView(
        context: Context,
        center: GeoCoordinates,
        zoomLevel: Double = 15.0,
        userLocation: GeoCoordinates?,
        isUserLocationFallback: Boolean = false,
        waypoints: List<SafetyWaypoint> = NagpurGeoConfig.VERIFIED_SAFETY_POINTS,
        onWaypointClicked: ((SafetyWaypoint) -> Unit)? = null
    ): View

    fun updateCenter(view: View, coordinates: GeoCoordinates, zoomLevel: Double? = null)
    fun onDestroy(view: View)
}
