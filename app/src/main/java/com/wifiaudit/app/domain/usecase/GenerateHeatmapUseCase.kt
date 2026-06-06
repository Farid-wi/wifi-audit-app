package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Measurement
import com.wifiaudit.app.domain.model.OverallScore
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.RepeaterPosition
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
     *
     * @param band Bande Wi-Fi à visualiser ("2.4GHz" / "5GHz" / "6GHz"),
     *             ou null pour la bande connectée (RSSI principal).
     */
    operator fun invoke(
        rooms: List<CanvasRoom>,
        measurements: List<Measurement>,
        gridSize: Int = 50,
        band: String? = null
    ): Map<String, HeatmapGrid> =
        rooms.associate { room ->
            room.id to computeRoomGrid(room, measurements.filter { it.roomId == room.id }, gridSize, band)
        }

    private fun computeRoomGrid(
        room: CanvasRoom,
        measurements: List<Measurement>,
        gridSize: Int,
        band: String?,
        power: Double = 2.0
    ): HeatmapGrid {
        val values = FloatArray(gridSize * gridSize) { Float.NaN }
        // Exclure les mesures sans donnée pour la bande demandée.
        // Cela évite d'introduire un -100 fictif dans l'IDW et d'assombrir la carte.
        val activeMeasurements = measurements.filter { it.hasBandData(band) }
        if (activeMeasurements.isEmpty()) return HeatmapGrid(gridSize, values)

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Centre de la cellule en coordonnées normalisées — cohérent avec le rendu
                val px = (col + 0.5f) / gridSize
                val py = (row + 0.5f) / gridSize
                if (!room.bounds.contains(px, py)) continue   // hors pièce → NaN

                var weightSum = 0.0
                var valueSum  = 0.0
                var exactHit  = false

                for (m in activeMeasurements) {
                    val dist = sqrt((px - m.x).pow(2) + (py - m.y).pow(2).toDouble())
                    val rssiValue = m.rssiForDisplay(band).toDouble()
                    if (dist < 1e-6) {
                        valueSum  = rssiValue
                        weightSum = 1.0
                        exactHit  = true
                        break
                    }
                    val w = 1.0 / dist.pow(power)
                    weightSum += w
                    valueSum  += w * rssiValue
                }

                if (exactHit || weightSum > 0)
                    values[row * gridSize + col] = (valueSum / weightSum).toFloat()
            }
        }
        return HeatmapGrid(gridSize, values)
    }

    /**
     * Calcule une grille IDW par pièce pour un BSSID spécifique.
     * Seules les mesures où ce BSSID était visible entrent dans l'interpolation.
     * Toujours appeler depuis Dispatchers.Default.
     */
    fun computeForBssid(
        rooms: List<CanvasRoom>,
        measurements: List<Measurement>,
        bssid: String,
        gridSize: Int = 50
    ): Map<String, HeatmapGrid> =
        rooms.associate { room ->
            room.id to computeRoomGridForBssid(
                room,
                measurements.filter { it.roomId == room.id },
                bssid,
                gridSize
            )
        }

    private fun computeRoomGridForBssid(
        room: CanvasRoom,
        measurements: List<Measurement>,
        bssid: String,
        gridSize: Int,
        power: Double = 2.0
    ): HeatmapGrid {
        val values = FloatArray(gridSize * gridSize) { Float.NaN }
        val active = measurements.filter { it.hasBssidData(bssid) }
        if (active.isEmpty()) return HeatmapGrid(gridSize, values)

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val px = (col + 0.5f) / gridSize
                val py = (row + 0.5f) / gridSize
                if (!room.bounds.contains(px, py)) continue

                var weightSum = 0.0
                var valueSum  = 0.0
                var exactHit  = false

                for (m in active) {
                    val dist = sqrt((px - m.x).pow(2) + (py - m.y).pow(2).toDouble())
                    val rssiValue = m.rssiForBssid(bssid).toDouble()
                    if (dist < 1e-6) {
                        valueSum = rssiValue; weightSum = 1.0; exactHit = true; break
                    }
                    val w = 1.0 / dist.pow(power)
                    weightSum += w; valueSum += w * rssiValue
                }

                if (exactHit || weightSum > 0)
                    values[row * gridSize + col] = (valueSum / weightSum).toFloat()
            }
        }
        return HeatmapGrid(gridSize, values)
    }

    /**
     * Associe chaque appareil placé (box + répéteurs) à son BSSID par bande.
     *
     * Algorithme en deux étapes :
     *
     * 1. "Pic de signal" : pour chaque (bssid, bande), trouver la mesure où ce BSSID
     *    est le plus fort — c'est sa position approximative sur le plan.
     *
     * 2. Assignation optimale globale : pour chaque bande, trouver le mapping
     *    bssid ↔ device qui minimise la somme totale des distances (pic ↔ position device).
     *    Cela résout le cas où les mesures ne couvrent pas tous les appareils :
     *    si les pics de deux BSSIDs sont tous dans la zone du répéteur, l'algorithme
     *    assigne quand même le BSSID "relativement le moins proche" du répéteur à la GW.
     *
     * @return Map deviceId → (band → bssid). Vide si aucune mesure contient des apReadings.
     */
    fun associateDevices(
        measurements: List<Measurement>,
        gatewayPosition: Position,
        repeaterPositions: List<RepeaterPosition>
    ): Map<String, Map<String, String>> {
        val withReadings = measurements.filter { it.apReadings.isNotEmpty() }
        if (withReadings.isEmpty()) return emptyMap()

        val devices: List<Pair<String, Position>> =
            listOf("gateway" to gatewayPosition) +
            repeaterPositions.map { it.id to it.position }

        // Méthode 1 : mesures guidées — prises physiquement à côté du device, donc le BSSID
        // dominant dans chaque mesure guidée EST celui de cet appareil (signal proche = fort).
        // Méthode 1 : mesures guidées — prise physiquement à côté du device.
        // On utilise le RSSI BRUT du scan beacon (apReadings) : le signal beacon reflète la distance
        // physique indépendamment de l'AP auquel le téléphone est connecté.
        // Note : wifiInfo.rssi n'est PAS utilisé ici — il appartient à l'AP connecté qui peut être un
        // AP différent (roaming). Le BSSID avec le plus fort RSSI beacon près du device = BSSID de ce device.
        val hintMeasurements = withReadings.filter { it.deviceHint != null }
        if (hintMeasurements.isNotEmpty()) {
            // deviceId → band → (bssid, bestRssi scan brut)
            val hintResult = mutableMapOf<String, MutableMap<String, Pair<String, Int>>>()
            hintMeasurements.forEach { m ->
                val deviceId = m.deviceHint!!
                val connStr  = m.connectedBssid.ifEmpty { "masqué" }
                m.apReadings.groupBy { it.band }.forEach { (band, readings) ->
                    // Plus fort RSSI brut scan = AP physiquement le plus proche = device hinted
                    val best = readings.maxByOrNull { it.rssi } ?: return@forEach
                    val isPhoneConnectedToThis = best.bssid == m.connectedBssid
                    println("WIFI_AUDIT hint=$deviceId band=$band → best=${best.bssid} rssi=${best.rssi} dBm | connecté=$connStr ${if (isPhoneConnectedToThis) "(téléphone sur cet AP)" else "(téléphone sur autre AP)"}")
                    val existing = hintResult.getOrPut(deviceId) { mutableMapOf() }[band]
                    if (existing == null || best.rssi > existing.second) {
                        hintResult.getOrPut(deviceId) { mutableMapOf() }[band] = best.bssid to best.rssi
                    }
                }
            }
            if (hintResult.isNotEmpty()) {
                return hintResult.mapValues { (_, bm) -> bm.mapValues { (_, v) -> v.first } }
            }
        }

        // Méthode 2 : assignation optimale par position sur le plan (fallback)
        // Étape 1 : pour chaque (bssid, bande), trouver la mesure au RSSI max (= "pic")
        data class PeakInfo(val x: Float, val y: Float, val rssi: Int)
        val peaks = mutableMapOf<Pair<String, String>, PeakInfo>()
        for (m in withReadings) {
            for (ap in m.apReadings) {
                val key = ap.bssid to ap.band
                val cur = peaks[key]
                if (cur == null || ap.rssi > cur.rssi) peaks[key] = PeakInfo(m.x, m.y, ap.rssi)
            }
        }

        // Étape 2 : assignation optimale par bande
        val allBands = peaks.keys.map { it.second }.distinct()
        val result = mutableMapOf<String, MutableMap<String, String>>()

        for (band in allBands) {
            // (bssid, peakX, peakY) triés par RSSI décroissant (les plus proches en tête)
            val bssidPeaks = peaks.entries
                .filter { it.key.second == band }
                .sortedByDescending { it.value.rssi }
                .map { (key, peak) -> Triple(key.first, peak.x, peak.y) }

            optimalAssign(bssidPeaks, devices).forEach { (deviceId, bssid) ->
                result.getOrPut(deviceId) { mutableMapOf() }[band] = bssid
            }
        }

        return result
    }

    /**
     * Assigne chaque appareil à un BSSID distinct (1 par appareil) en minimisant
     * la somme des distances entre le pic du BSSID et la position de l'appareil.
     * Utilise un parcours de toutes les P(|bssidPeaks|, n) sélections ordonnées,
     * où n = min(|bssidPeaks|, |devices|). Correct pour n ≤ 5 (≤ 120 options).
     */
    private fun optimalAssign(
        bssidPeaks: List<Triple<String, Float, Float>>,  // (bssid, peakX, peakY)
        devices: List<Pair<String, Position>>             // (deviceId, position)
    ): Map<String, String> {
        if (bssidPeaks.isEmpty() || devices.isEmpty()) return emptyMap()
        val n = minOf(bssidPeaks.size, devices.size)

        fun dist(b: Triple<String, Float, Float>, d: Pair<String, Position>): Double =
            sqrt((b.second - d.second.x).pow(2) + (b.third - d.second.y).pow(2).toDouble())

        var bestCost = Double.MAX_VALUE
        var bestAssignment = emptyMap<String, String>()

        bssidPeaks.chooseAndPermute(n).forEach { subset ->
            val cost = (0 until n).sumOf { i -> dist(subset[i], devices[i]) }
            if (cost < bestCost) {
                bestCost = cost
                bestAssignment = (0 until n).associate { i -> devices[i].first to subset[i].first }
            }
        }
        return bestAssignment
    }

    /** Génère toutes les sélections ordonnées de [k] éléments (P(n,k) permutations). */
    private fun <T> List<T>.chooseAndPermute(k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (size < k) return emptyList()
        return flatMapIndexed { i, item ->
            (subList(0, i) + subList(i + 1, size))
                .chooseAndPermute(k - 1)
                .map { listOf(item) + it }
        }
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
