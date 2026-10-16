package com.example.domain.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
                icon = createWaypointDrawable(context, wp.category)
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
            icon = createUserLocationDrawable(context, isUserLocationFallback)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        mapView.overlays.add(userMarker)

        return mapView
    }

    private fun createUserLocationDrawable(context: Context, isFallback: Boolean): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (32 * density).toInt().coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isFallback) Color.argb(60, 245, 158, 11) else Color.argb(60, 37, 99, 235)
            style = Paint.Style.FILL
        }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isFallback) Color.rgb(217, 119, 6) else Color.rgb(29, 78, 216)
            style = Paint.Style.FILL
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        canvas.drawCircle(center, center, center - 2 * density, haloPaint)
        canvas.drawCircle(center, center, 9 * density, circlePaint)
        canvas.drawCircle(center, center, 4.5f * density, innerPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createWaypointDrawable(context: Context, category: String): Drawable {
        val density = context.resources.displayMetrics.density
        val width = (28 * density).toInt().coerceAtLeast(28)
        val height = (38 * density).toInt().coerceAtLeast(38)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pinColor = when (category) {
            "POLICE_STATION" -> Color.rgb(15, 23, 42) // Deep navy #0F172A
            "HOSPITAL" -> Color.rgb(220, 38, 38)     // Red #DC2626
            "CAMPUS" -> Color.rgb(16, 185, 129)       // Emerald #10B981
            else -> Color.rgb(37, 99, 235)           // Blue #2563EB
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor
            style = Paint.Style.FILL
        }

        val path = Path()
        val r = width / 2f
        path.addCircle(r, r, r - 2, Path.Direction.CW)
        path.moveTo(2f, r)
        path.lineTo(r, height.toFloat() - 2)
        path.lineTo(width.toFloat() - 2, r)
        path.close()
        canvas.drawPath(path, paint)

        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(r, r, r * 0.55f, whitePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor
            textSize = 11 * density
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val letter = when (category) {
            "POLICE_STATION" -> "P"
            "HOSPITAL" -> "+"
            "CAMPUS" -> "C"
            else -> "•"
        }
        val yPos = r - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(letter, r, yPos, textPaint)

        return BitmapDrawable(context.resources, bitmap)
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
