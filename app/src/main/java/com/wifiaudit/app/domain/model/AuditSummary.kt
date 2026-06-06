package com.wifiaudit.app.domain.model

enum class OverallScore { GOOD, FAIR, POOR }

fun OverallScore.toUserLabel(): String = when (this) {
    OverallScore.GOOD -> "Couverture bonne"
    OverallScore.FAIR -> "Couverture moyenne"
    OverallScore.POOR -> "À améliorer"
}

enum class Severity { HIGH, MEDIUM, LOW }

data class Recommendation(
    val roomId: String?,          // null = recommandation globale
    val severity: Severity,
    val message: String           // langage naturel uniquement — jamais de termes techniques
)

data class AuditSummary(
    val overallScore: OverallScore,
    val recommendations: List<Recommendation>
)
