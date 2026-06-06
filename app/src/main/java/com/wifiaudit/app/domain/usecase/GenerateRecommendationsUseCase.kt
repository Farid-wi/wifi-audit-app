package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Measurement
import com.wifiaudit.app.domain.model.Recommendation
import com.wifiaudit.app.domain.model.RoomType
import com.wifiaudit.app.domain.model.Severity
import com.wifiaudit.app.domain.model.SignalQuality
import com.wifiaudit.app.domain.model.rssiToQuality
import javax.inject.Inject

class GenerateRecommendationsUseCase @Inject constructor() {

    operator fun invoke(
        measurements: List<Measurement>,
        rooms: List<CanvasRoom>
    ): List<Recommendation> {
        if (measurements.isEmpty()) return emptyList()

        val recs = mutableListOf<Recommendation>()

        // 1. Analyse par pièce — recommandations contextuelles selon le type
        rooms.forEach { room ->
            val roomMeasurements = measurements.filter { it.roomId == room.id }
            if (roomMeasurements.isEmpty()) return@forEach
            val medianRssi = roomMeasurements.map { it.rssi }.median()

            contextualMessage(room, rssiToQuality(medianRssi))?.let { (severity, message) ->
                recs += Recommendation(roomId = room.id, severity = severity, message = message)
            }
        }

        // 2. Position de la box dans un coin
        val gatewayMeasurement = measurements.maxByOrNull { it.rssi }
        if (gatewayMeasurement != null) {
            val inCorner = (gatewayMeasurement.x < 0.15f || gatewayMeasurement.x > 0.85f) &&
                           (gatewayMeasurement.y < 0.15f || gatewayMeasurement.y > 0.85f)
            if (inCorner && recs.none { it.severity == Severity.HIGH }) {
                recs += Recommendation(
                    roomId   = null,
                    severity = Severity.MEDIUM,
                    message  = "Votre box semble placée dans un coin. " +
                               "La rapprocher du centre améliorerait la couverture globale."
                )
            }
        }

        // 3. Message positif si aucune anomalie
        if (recs.isEmpty()) {
            recs += Recommendation(
                roomId   = null,
                severity = Severity.LOW,
                message  = "La couverture Wi-Fi de votre logement est globalement satisfaisante."
            )
        }

        return recs.sortedByDescending { it.severity.ordinal }.take(4)
    }

    private fun contextualMessage(
        room: CanvasRoom,
        quality: SignalQuality
    ): Pair<Severity, String>? = when {
        quality == SignalQuality.POOR && room.type == RoomType.OFFICE ->
            Severity.HIGH to "Le signal est insuffisant dans votre bureau — " +
                "votre connexion sera lente pour travailler."
        quality == SignalQuality.POOR && room.type == RoomType.BEDROOM ->
            Severity.HIGH to "Le signal est faible dans votre chambre, " +
                "ce qui peut affecter vos appareils connectés la nuit."
        quality == SignalQuality.POOR && room.type == RoomType.HALLWAY ->
            Severity.HIGH to "Un couloir mal couvert crée une zone morte " +
                "entre vos pièces."
        quality == SignalQuality.POOR ->
            Severity.HIGH to "Le signal est insuffisant en ${room.label}. " +
                "Un répéteur entre votre box et ${room.label} pourrait aider."
        quality == SignalQuality.FAIR && room.type == RoomType.OFFICE ->
            Severity.MEDIUM to "Le signal est moyen dans votre bureau. " +
                "Rapprocher un répéteur améliorerait la connexion."
        quality == SignalQuality.FAIR ->
            Severity.MEDIUM to "Le signal est moyen en ${room.label}. " +
                "Réduisez les obstacles entre votre box et cette pièce."
        else -> null
    }

    private fun List<Int>.median(): Int {
        if (isEmpty()) return -90
        return sorted()[size / 2]
    }
}
