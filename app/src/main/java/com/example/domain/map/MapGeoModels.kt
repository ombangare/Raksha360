package com.example.domain.map

/**
 * Real geographic coordinates and seed data for Nagpur, Maharashtra.
 * Focused on Sadar as the primary pilot zone.
 */
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
)

data class SafetyWaypoint(
    val id: String,
    val name: String,
    val category: String, // "POLICE_STATION", "HOSPITAL", "TRANSIT_HUB", "CAMPUS"
    val coordinates: GeoCoordinates,
    val contactNumber: String? = null,
    val address: String
)

object NagpurGeoConfig {
    // Official Nagpur, Maharashtra Coordinates
    val NAGPUR_CENTER = GeoCoordinates(21.1458, 79.0882)

    // Sadar Pilot Area Center (Fallback location when GPS is unavailable)
    val SADAR_PILOT_CENTER = GeoCoordinates(21.1612, 79.0825)

    // Sadar Pilot Perimeter Boundary (Polygon coordinates defining the pilot zone)
    val SADAR_PILOT_PERIMETER = listOf(
        GeoCoordinates(21.1710, 79.0820), // North: Poonam Chambers / Residency Rd
        GeoCoordinates(21.1645, 79.0910), // East: Railway Colony / Central Ave
        GeoCoordinates(21.1520, 79.0860), // South: RBI Square / Kingsway
        GeoCoordinates(21.1590, 79.0730), // West: Raj Bhavan / Katol Rd perimeter
        GeoCoordinates(21.1710, 79.0820)  // Closing polygon loop
    )

    // Real Verified Nagpur Safety Infrastructure Points
    val VERIFIED_SAFETY_POINTS = listOf(
        SafetyWaypoint(
            id = "nagpur_ps_sadar",
            name = "Sadar Police Station",
            category = "POLICE_STATION",
            coordinates = GeoCoordinates(21.1628, 79.0815),
            contactNumber = "0712-2560333",
            address = "Mount Road, Sadar, Nagpur, Maharashtra 440001"
        ),
        SafetyWaypoint(
            id = "nagpur_ps_sitabuldi",
            name = "Sitabuldi Police Station",
            category = "POLICE_STATION",
            coordinates = GeoCoordinates(21.1465, 79.0832),
            contactNumber = "0712-2561222",
            address = "Wardha Rd, Sitabuldi, Nagpur, Maharashtra 440012"
        ),
        SafetyWaypoint(
            id = "nagpur_hosp_mayo",
            name = "Indira Gandhi Govt Medical College (Mayo Hospital)",
            category = "HOSPITAL",
            coordinates = GeoCoordinates(21.1540, 79.0920),
            contactNumber = "0712-2725423",
            address = "Central Ave, Mominpura, Nagpur, Maharashtra 440018"
        ),
        SafetyWaypoint(
            id = "nagpur_campus_vnit",
            name = "VNIT Campus Main Gate",
            category = "CAMPUS",
            coordinates = GeoCoordinates(21.1265, 79.0515),
            contactNumber = "0712-2228323",
            address = "South Ambazari Road, Nagpur, Maharashtra 440010"
        )
    )
}
