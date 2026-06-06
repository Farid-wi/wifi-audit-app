package com.wifiaudit.app.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

class RunPingUseCase @Inject constructor() {

    /**
     * Ping [host] et retourne le RTT en ms, ou null si injoignable.
     * Utilise InetAddress.isReachable — pas de root nécessaire.
     */
    suspend operator fun invoke(host: String, timeoutMs: Int = 1000): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(host).isReachable(timeoutMs)
                if (reachable) (System.currentTimeMillis() - start).toInt() else null
            }.getOrNull()
        }
}
