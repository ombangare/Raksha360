package com.example.domain.threat

data class ThreatAnalysisResult(
    val category: String,
    val severityLevel: String, // "LOW", "MODERATE", "HIGH", "CRITICAL"
    val legalProvisions: List<String>,
    val recommendedActions: List<String>,
    val helplineDirectDial: String,
    val preservationTips: String
)

object ThreatAnalyzer {

    fun analyzeTextIncident(description: String): ThreatAnalysisResult {
        val lower = description.lowercase()

        return when {
            lower.contains("follow") || lower.contains("stalk") || lower.contains("picha") || lower.contains("bike") || lower.contains("car") -> {
                ThreatAnalysisResult(
                    category = "Physical Stalking / Trailing",
                    severityLevel = "HIGH",
                    legalProvisions = listOf(
                        "Bharatiya Nyaya Sanhita (BNS) Section 78 (Stalking)",
                        "IPC Section 354D (Previous Penal Code)",
                        "Non-bailable on second offense, up to 5 years imprisonment"
                    ),
                    recommendedActions = listOf(
                        "Do not proceed to an isolated destination or your residence.",
                        "Head directly to Sadar Police Station or nearest petrol pump/crowded commercial hub.",
                        "Enable Voice SOS Guard Mode immediately.",
                        "Notify your trusted contacts with Live Location tracking."
                    ),
                    helplineDirectDial = "112",
                    preservationTips = "Note vehicle license plate, approximate physical build, clothing, and timestamp in Evidence Vault."
                )
            }
            lower.contains("photo") || lower.contains("morph") || lower.contains("instagram") || lower.contains("whatsapp") || lower.contains("blackmail") || lower.contains("call") -> {
                ThreatAnalysisResult(
                    category = "Cyber Harassment & Digital Intimidation",
                    severityLevel = "MODERATE",
                    legalProvisions = listOf(
                        "Information Technology Act Section 67 / 66E (Privacy Violation)",
                        "BNS Section 79 (Insulting modesty of a woman)",
                        "National Cybercrime Portal (cybercrime.gov.in)"
                    ),
                    recommendedActions = listOf(
                        "Take immediate screenshots capturing sender ID, handle, and exact timestamps.",
                        "Do not delete chat logs or voice messages.",
                        "Report to National Cyber Helpline 1930.",
                        "File complaint with Nagpur Cyber Cell (Civil Lines)."
                    ),
                    helplineDirectDial = "1930",
                    preservationTips = "Store uncompressed screenshots in Raksha360 Evidence Vault to seal cryptographic SHA-256 hash."
                )
            }
            lower.contains("bus") || lower.contains("auto") || lower.contains("metro") || lower.contains("crowd") || lower.contains("touch") -> {
                ThreatAnalysisResult(
                    category = "Public Transit / Street Harassment",
                    severityLevel = "HIGH",
                    legalProvisions = listOf(
                        "BNS Section 74 (Assault to outrage modesty)",
                        "BNS Section 79 (Word, gesture or act intended to insult modesty)",
                        "Women Helpline 1091 & Emergency 112"
                    ),
                    recommendedActions = listOf(
                        "Move towards the driver/conductor or station security kiosk.",
                        "Raise an alert firmly to draw public attention.",
                        "Press the Emergency SOS button in Raksha360.",
                        "Report to Metro security staff at Kasturchand Park / Sitabuldi Interchange."
                    ),
                    helplineDirectDial = "1091",
                    preservationTips = "Record audio or discreetly note auto/bus route number and exact junction."
                )
            }
            else -> {
                ThreatAnalysisResult(
                    category = "General Safety & Suspicious Activity",
                    severityLevel = "MODERATE",
                    legalProvisions = listOf(
                        "BNS Section 351 (Criminal Intimidation)",
                        "Right to Private Defense (BNS Sections 34-44)",
                        "Immediate Police Assistance via 112"
                    ),
                    recommendedActions = listOf(
                        "Keep Voice SOS Guard Mode active.",
                        "Share live trip with trusted contacts via Safety Journey.",
                        "Stay on illuminated arterial roads like Mount Road or Residency Road."
                    ),
                    helplineDirectDial = "112",
                    preservationTips = "Document any suspicious interaction in the Incident Log."
                )
            }
        }
    }
}
