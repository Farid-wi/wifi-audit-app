package com.wifiaudit.app.domain.model

import java.util.UUID

enum class RoomType(val displayName: String) {
    SALON("Salon"),
    KITCHEN("Cuisine"),
    BEDROOM("Chambre"),
    OFFICE("Bureau"),
    BATHROOM("Salle de bain"),
    HALLWAY("Couloir"),
    DINING("Salle à manger"),
    OTHER("Autre")
}

data class RoomBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val surface: Float get() = (right - left) * (bottom - top)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom

    fun clamped() = copy(
        left   = left.coerceIn(0f, (right - MIN_SIZE).coerceAtLeast(0f)),
        top    = top.coerceIn(0f, (bottom - MIN_SIZE).coerceAtLeast(0f)),
        right  = right.coerceIn(left + MIN_SIZE, 1f),
        bottom = bottom.coerceIn(top + MIN_SIZE, 1f)
    )

    companion object {
        const val MIN_SIZE = 0.05f
    }
}

data class CanvasRoom(
    val id: String       = UUID.randomUUID().toString(),
    val type: RoomType   = RoomType.OTHER,
    val label: String    = type.displayName,
    val bounds: RoomBounds
)
