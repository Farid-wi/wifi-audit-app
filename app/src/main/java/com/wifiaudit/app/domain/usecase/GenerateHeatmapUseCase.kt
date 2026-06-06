package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Measurement
import com.wifiaudit.app.domain.model.OverallScore
import com.wifiaudit.app.domain.model.SignalQuality
import com.wifiaudit.app.domain.model.rssiToQuality
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

data class HeatmapGrid(val size: Int, val values: FloatArray) {
    fun valueAt(row: Int, col: Int): Float = values[row * size + col]
    fun hasValues(): Boolean = values.any { !it.isNaN() }
    fun median(): Float {
        val valid = values.filter { !it.isNaN() }.sorted()
        return if (valid.isEmpty()) -90f else valid[valid.size / 2]
    }
}

class GenerateHeatmapUseCase @Inject constructor() {

    /**
     * Calcule une grille IDW par pièce.
     * Chaque pièce a sa propre grille — les mesures d'une pièce n'influencent
     * pas les cellules d'une autre pièce (NaN hors des bounds).
     * Toujours appeler depuis Dispatchers.Default.
     */
    operator fun invoke(
        rooms: List<CanvasRoom>,
        measurements: List<Measurement>,
        gridSize: Int = 50
    ): Map<String, HeatmapGrid> =
        rooms.associate { room ->
            room.id to computeRoomGrid(room, measurements.filter { it.roomId == room.id }, gridSize)
        }

    private fun computeRoomGrid(
        room: CanvasRoom,
        measurements: List<Measurement>,
        gridSize: Int,
        power: Double = 2.0
    ): HeatmapGrid {
        val values = FloatArray(gridSize * gridSize) { Float.NaN }
        if (measurements.isEmpty()) return HeatmapGrid(gridSize, values)

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Centre de la cellule en coordonnées normalisées — cohérent avec le rendu
                val px = (col + 0.5f) / gridSize
                val py = (row + 0.5f) / gridSize
                if (!room.bounds.contains(px, py)) continue   // hors pièce → NaN

                var weightSum = 0.0
                var valueSum  = 0.0
                var exactHit  = false

                for (m in measurements) {
                    val dist = sqrt((px - m.x).pow(2) + (py - m.y).pow(2).toDouble())
                    if (dist < 1e-6) {
                        valueSum  = m.rssi.toDouble()
                        weightSum = 1.0
                        exactHit  = true
                        break
                    }
                    val w = 1.0 / dist.pow(power)
                    weightSum += w
                    valueSum  += w * m.rssi
                }

                if (exactHit || weightSum > 0)
                    values[row * gridSize + col] = (valueSum / weightSum).toFloat()
            }
        }
        return HeatmapGrid(gridSize, values)
    }

    /** Score global = moyenne des scores pièce pondérée par surface. Ignore les pièces vides. */
    fun globalScore(rooms: List<CanvasRoom>, roomGrids: Map<String, HeatmapGrid>): OverallScore {
        val scored = rooms.filter { roomGrids[it.id]?.hasValues() == true }
        if (scored.isEmpty()) return OverallScore.FAIR
        val totalSurface = scored.sumOf { it.bounds.surface.toDouble() }
        val weighted = scored.sumOf { room ->
            val score = when (rssiToQuality(roomGrids[room.id]!!.median().toInt())) {
                SignalQuality.GOOD -> 1.0
                SignalQuality.FAIR -> 0.5
                SignalQuality.POOR -> 0.0
            }
            score * room.bounds.surface / totalSurface
        }
        return when {
            weighted >= 0.67 -> OverallScore.GOOD
            weighted >= 0.34 -> OverallScore.FAIR
            else             -> OverallScore.POOR
        }
    }
}
