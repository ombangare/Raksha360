package com.example.domain.map

data class SafetyBeacon(
    val id: String,
    val name: String,
    val category: BeaconCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val signals: List<String>,
    val openHours: String = "24x7"
)

enum class BeaconCategory {
    POLICE_STATION,
    VERIFIED_SHELTER,
    WELL_LIT_CORRIDOR,
    CCTV_SURVEILLANCE_HUB,
    METRO_SECURITY_DESK
}

object NagpurSafetyData {

    // Sadar, Nagpur beacon list
    val sadarBeacons = listOf(
        SafetyBeacon(
            id = "sadar_ps_1",
            name = "Sadar Police Station",
            category = BeaconCategory.POLICE_STATION,
            latitude = 21.1624,
            longitude = 79.0831,
            address = "Katol Road, near Sadar Bazar, Nagpur",
            signals = listOf("24x7 Active Duty Officer", "Nirbhaya Squad Beat HQ", "Direct 112 Dispatch Point"),
            openHours = "24x7 Always Open"
        ),
        SafetyBeacon(
            id = "mount_road_chowki",
            name = "Mount Road Police Beat Chowki",
            category = BeaconCategory.POLICE_STATION,
            latitude = 21.1578,
            longitude = 79.0812,
            address = "Mount Road Extension, Sadar, Nagpur",
            signals = listOf("Active Beat Constables", "Direct Wireless Link", "High Street Visibility"),
            openHours = "8:00 AM - 12:00 AM"
        ),
        SafetyBeacon(
            id = "kasturchand_park_metro",
            name = "Kasturchand Park Metro Station Security",
            category = BeaconCategory.METRO_SECURITY_DESK,
            latitude = 21.1534,
            longitude = 79.0845,
            address = "Kasturchand Park Ground, Sadar/Civil Lines",
            signals = listOf("MahaMetro Security Staff", "CCTV Covered Concourse", "Emergency Call Point"),
            openHours = "6:00 AM - 10:30 PM"
        ),
        SafetyBeacon(
            id = "poonam_chambers_hub",
            name = "Poonam Chambers Commercial Hub",
            category = BeaconCategory.VERIFIED_SHELTER,
            latitude = 21.1645,
            longitude = 79.0862,
            address = "Chhindwara Road, Byramji Town / Sadar border",
            signals = listOf("24x7 Security Guards", "Well-illuminated forecourt", "Open ATM kiosks"),
            openHours = "24x7"
        ),
        SafetyBeacon(
            id = "residency_road_corridor",
            name = "Residency Road Commercial Corridor",
            category = BeaconCategory.WELL_LIT_CORRIDOR,
            latitude = 21.1592,
            longitude = 79.0820,
            address = "Residency Road, Sadar",
            signals = listOf("High-density LED Street Lighting", "Active Footfall until 11:30 PM", "Frequent PCR Van Patrols"),
            openHours = "Continuous Street Light Coverage"
        ),
        SafetyBeacon(
            id = "rbi_square_junction",
            name = "RBI Square Smart City CCTV Point",
            category = BeaconCategory.CCTV_SURVEILLANCE_HUB,
            latitude = 21.1511,
            longitude = 79.0789,
            address = "RBI Square, Nagpur",
            signals = listOf("360° Smart City Police CCTV Surveillance", "Traffic Booth", "Emergency Panic Box"),
            openHours = "24x7 Live Feed"
        )
    )
}
