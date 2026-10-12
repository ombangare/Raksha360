package com.example.domain.map

import android.content.Context
import android.graphics.Color
import android.view.View
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Native OpenStreetMap interactive map provider using OSMDroid.
 * Renders real OpenStreetMap vector/raster tile data centered on Nagpur/Sadar.
 * Displays real streets, verified emergency infrastructure, zoom controls,
 * and mandatory OSM attribution.
 */
class OsmNativeMapProvider : IMapViewProvider {

    override fun createMapView(
        context: Context,
        center: GeoCoordinates,
        zoomLevel: Double,
        userLocation: GeoCoordinates?,
        isUserLocationFallback: Boolean,
        waypoints: List<SafetyWaypoint>,
        onWaypointClicked: ((SafetyWaypoint) -> Unit)?
    ): View {
        // Initialize OSMDroid configuration with application package user-agent
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("naari_kavach_osm", Context.MODE_PRIVATE))

        val mapView = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(zoomLevel)
            controller.setCenter(GeoPoint(center.latitude, center.longitude))
        }

        // 1. Mandatory OpenStreetMap Attribution Overlay
        val copyrightOverlay = CopyrightOverlay(context)
        mapView.overlays.add(copyrightOverlay)

        // 2. Draw Sadar Pilot Perimeter Polygon Overlay
        val sadarPolygon = Polygon().apply {
            val points = NagpurGeoConfig.SADAR_PILOT_PERIMETER.map { GeoPoint(it.latitude, it.longitude) }
            this.points = points
            fillPaint.color = Color.argb(40, 27, 85, 155) // Soft translucent blue
            outlinePaint.color = Color.rgb(27, 85, 155) // Solid blue border
            outlinePaint.strokeWidth = 4f
            title = "Sadar Pilot Safety Zone"
        }
        mapView.overlays.add(sadarPolygon)

        // 3. Add Verified Safety Waypoints (Police Stations, Hospitals, Campus)
        waypoints.forEach { wp ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(wp.coordinates.latitude, wp.coordinates.longitude)
                title = wp.name
                snippet = "${wp.category.replace("_", " ")} • ${wp.address}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { m, _ ->
                    m.showInfoWindow()
                    onWaypointClicked?.invoke(wp)
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        // 4. Add User Location Marker (with fallback indicator if GPS unavailable)
        val activeLocation = userLocation ?: NagpurGeoConfig.SADAR_PILOT_CENTER
        val userMarker = Marker(mapView).apply {
            position = GeoPoint(activeLocation.latitude, activeLocation.longitude)
            title = if (isUserLocationFallback) {
                "Sadar, Nagpur (Fallback Location)"
            } else {
                "Current Location (Verified GPS)"
            }
            snippet = if (isUserLocationFallback) {
                "Precise location unavailable. Centered on Sadar Pilot Area."
            } else {
                "Accurate location active"
            }
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        mapView.overlays.add(userMarker)

        return mapView
    }

    override fun updateCenter(view: View, coordinates: GeoCoordinates, zoomLevel: Double?) {
        if (view is MapView) {
            view.controller.animateTo(GeoPoint(coordinates.latitude, coordinates.longitude))
            if (zoomLevel != null) {
                view.controller.setZoom(zoomLevel)
            }
        }
    }

    override fun onDestroy(view: View) {
        if (view is MapView) {
            view.onDetach()
        }
    }
}
