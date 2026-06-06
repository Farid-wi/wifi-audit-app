package com.wifiaudit.app.domain.model

enum class EquipmentType {
    GATEWAY,    // Box Internet
    REPEATER,   // Répéteur Wi-Fi
    MESH_NODE   // Borne mesh
}

data class WifiEquipment(
    val id: String,
    val type: EquipmentType,
    val position: Position
)

// Utilisé dans le payload JSON et dans Audit.repeaterPositions
data class RepeaterPosition(
    val id: String,
    val position: Position
)
