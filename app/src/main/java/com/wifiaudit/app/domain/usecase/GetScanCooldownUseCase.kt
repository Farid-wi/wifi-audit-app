package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.repository.WifiRepository
import javax.inject.Inject

/**
 * Retourne le délai (ms) avant qu'un scan frais soit de nouveau possible (0 si dispo maintenant).
 * Permet à l'UI de désactiver le bouton de mesure avec un compte à rebours quand le throttle
 * Android (4 scans / 2 min) est atteint, plutôt que de renvoyer une mesure périmée.
 */
class GetScanCooldownUseCase @Inject constructor(
    private val wifiRepository: WifiRepository
) {
    suspend operator fun invoke(): Long = wifiRepository.scanCooldownRemainingMs()
}
