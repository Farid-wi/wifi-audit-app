package com.wifiaudit.app.data.repository

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.wifiaudit.app.domain.model.ApReading
import com.wifiaudit.app.domain.model.NeighborNetwork
import com.wifiaudit.app.domain.repository.WifiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

private const val TAG = "WIFI_AUDIT"

// Modèle du throttle de scan Android (foreground) : 4 scans max par fenêtre glissante de 2 min.
private const val SCAN_THROTTLE_WINDOW_MS = 120_000L
private const val SCAN_THROTTLE_MAX = 4

class WifiRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WifiRepository {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // Timestamp (µs depuis le boot) du scan le plus récent déjà consommé.
    // Sert à détecter qu'un nouveau scan est réellement frais (et pas le cache renvoyé par
    // Android quand startScan est throttlé : 4 scans / 2 min sur Android 9+).
    private var lastScanTimestampUs: Long = 0L

    // Horodatages (elapsedRealtime ms) des scans réellement acceptés, pour modéliser le throttle
    // Android (4 scans / fenêtre glissante de 2 min) et calculer le délai avant le prochain scan.
    private val acceptedScanTimes = ArrayDeque<Long>()

    private val connectivityManager: ConnectivityManager by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @SuppressLint("MissingPermission")
    override suspend fun scan(targetSsid: String?): Result<WifiRepository.ScanData> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!wifiManager.isWifiEnabled) {
                    throw IllegalStateException("Activez le Wi-Fi de votre téléphone")
                }

                val wifiInfo = getConnectedWifiInfo()

                if (wifiInfo != null && wifiInfo.rssi > -127) {
                    // ── Mode connecté ─────────────────────────────────────────
                    val rssi      = wifiInfo.rssi
                    val bssid     = wifiInfo.bssid?.takeIf { it != "02:00:00:00:00:00" } ?: ""
                    val frequency = wifiInfo.frequency
                    val band      = frequencyToBand(frequency)
                    val channel   = frequencyToChannel(frequency)
                    val gatewayIp = intToIp(wifiManager.dhcpInfo.gateway)

                    Log.d(TAG, "Mode connecté — rssi=$rssi bssid=$bssid freq=$frequency ch=$channel gw=$gatewayIp")

                    // ── Scan FRAIS synchronisé sur la position actuelle ───────
                    // On attend la complétion réelle du scan (broadcast), pas un délai fixe : un scan
                    // Wi-Fi complet prend 2-4s, donc lire scanResults trop tôt renvoie le scan PRÉCÉDENT
                    // → décalage d'un cycle entre la position physique et les données (bug du matching).
                    val scanResults = awaitFreshScanResults()

                    val neighbors = scanResults
                        .filter { it.BSSID != bssid }
                        .mapNotNull { r ->
                            val ssid = r.SSID?.trim()
                                ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                            NeighborNetwork(ssid, r.BSSID, r.level,
                                frequencyToChannel(r.frequency), frequencyToBand(r.frequency))
                        }

                    // Sur Android 13+ sans ACCESS_FINE_LOCATION (mais avec NEARBY_WIFI_DEVICES),
                    // wifiInfo.ssid retourne "<unknown ssid>" — on utilise alors targetSsid (SSID
                    // sélectionné par l'utilisateur à l'étape 3) pour retrouver les APs dans scanResults.
                    val connectedSsid = wifiInfo.ssid?.trim('"')
                        ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                        ?: targetSsid

                    // apReadings : RSSI beacon BRUT du scan frais — un par BSSID du même SSID.
                    // Le beacon est mesuré passivement pour CHAQUE AP → comparable entre AP (box vs répéteur)
                    // → c'est le seul indicateur fiable de proximité physique pour le matching device↔BSSID.
                    // NE PAS écraser par wifiInfo.rssi : ça gonflerait l'AP connecté (potentiellement distant
                    // par roaming) et fausserait le matching.
                    val beaconReadings = if (!connectedSsid.isNullOrEmpty()) {
                        apReadingsForSsid(scanResults, connectedSsid)
                    } else emptyList()

                    // Garantir la présence de l'AP connecté : certains appareils l'omettent de scanResults.
                    // On l'ajoute depuis wifiInfo (seule estimation disponible) pour que la liste soit complète.
                    val apReadings = if (bssid.isNotEmpty() &&
                        beaconReadings.none { it.bssid.equals(bssid, ignoreCase = true) }) {
                        Log.w(TAG, "AP connecté $bssid absent du scan — ajout depuis wifiInfo (rssi=$rssi)")
                        (beaconReadings + ApReading(bssid, rssi, band)).sortedByDescending { it.rssi }
                    } else beaconReadings

                    // rssiPerBand : max beacon par bande — dérivé de apReadings (cohérent avec la liste).
                    val perBand = apReadings
                        .groupBy { it.band }
                        .mapValues { (_, list) -> list.maxOf { it.rssi } }
                        .ifEmpty { mapOf(band to rssi) }

                    Log.d(TAG, buildString {
                        appendLine("connectedSsid=$connectedSsid  connectedBssid=$bssid (lien=$rssi dBm $band)")
                        appendLine("rssiPerBand=$perBand")
                        if (apReadings.isNotEmpty()) {
                            appendLine("APs du réseau (${apReadings.size}) — RSSI beacon brut :")
                            apReadings.forEach { ap ->
                                val marker = if (ap.bssid.equals(bssid, ignoreCase = true)) " ← connecté" else ""
                                appendLine("  ${ap.bssid}  ${ap.band.padEnd(6)}  ${ap.rssi} dBm$marker")
                            }
                        }
                    })

                    WifiRepository.ScanData(rssi, bssid, channel, band, gatewayIp, neighbors, perBand, apReadings, connectedBssid = bssid)

                } else {
                    // ── Mode passif (non connecté) — lecture des scanResults ──
                    // Scan frais synchronisé sur la complétion réelle (voir awaitFreshScanResults).
                    val results = awaitFreshScanResults()
                    if (results.isEmpty()) {
                        throw IllegalStateException(
                            "Aucun réseau Wi-Fi détecté. Rapprochez-vous de votre box."
                        )
                    }

                    // Sélectionne le meilleur signal pour le SSID cible, sinon le réseau le plus fort
                    val target = targetSsid?.let { ssid ->
                        results
                            .filter { it.SSID?.trim('"') == ssid.trim('"') }
                            .maxByOrNull { it.level }
                    } ?: results.maxByOrNull { it.level }!!

                    val bssid   = target.BSSID ?: ""
                    val band    = frequencyToBand(target.frequency)
                    val channel = frequencyToChannel(target.frequency)

                    Log.d(TAG, "Mode passif — ${target.SSID} rssi=${target.level} freq=${target.frequency} ch=$channel")

                    val neighbors = results
                        .filter { it.BSSID != bssid }
                        .mapNotNull { r ->
                            val ssid = r.SSID?.trim()
                                ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                            NeighborNetwork(ssid, r.BSSID, r.level,
                                frequencyToChannel(r.frequency), frequencyToBand(r.frequency))
                        }

                    val targetSsidClean = targetSsid ?: target.SSID?.trim('"')
                    val apReadings = if (!targetSsidClean.isNullOrEmpty()) {
                        apReadingsForSsid(results, targetSsidClean)
                    } else emptyList()
                    val perBand = apReadings
                        .groupBy { it.band }
                        .mapValues { (_, list) -> list.maxOf { it.rssi } }
                        .ifEmpty { mapOf(band to target.level) }

                    Log.d(TAG, buildString {
                        appendLine("Mode passif rssiPerBand=$perBand")
                        if (apReadings.isNotEmpty()) {
                            appendLine("APs du réseau (${apReadings.size}) :")
                            apReadings.forEach { ap ->
                                appendLine("  ${ap.bssid}  ${ap.band.padEnd(6)}  ${ap.rssi} dBm")
                            }
                        }
                    })

                    WifiRepository.ScanData(target.level, bssid, channel, band, null, neighbors, perBand, apReadings)
                }
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun getVisibleNetworks(): List<WifiRepository.VisibleNetwork> =
        withContext(Dispatchers.IO) {
            val connectedBssid = getConnectedWifiInfo()?.bssid
            val connectedSsid  = getConnectedWifiInfo()?.ssid?.trim('"')
                ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }

            Log.d(TAG, "getVisibleNetworks — connectedSsid=$connectedSsid")

            @Suppress("DEPRECATION")
            wifiManager.scanResults
                .mapNotNull { r ->
                    val ssid = r.SSID?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                        ?: return@mapNotNull null
                    WifiRepository.VisibleNetwork(ssid, ssid == connectedSsid)
                }
                .distinctBy { it.ssid }
                .sortedByDescending { it.isConnected }
        }

    // ─── API moderne pour WifiInfo (Android 10+) ─────────────────────────────

    @SuppressLint("MissingPermission")
    private fun getConnectedWifiInfo(): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager.allNetworks.forEach { network ->
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return@forEach
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val info = caps.transportInfo as? WifiInfo ?: return@forEach
                    Log.d(TAG, "Wi-Fi trouvé via allNetworks — rssi=${info.rssi} ssid=${info.ssid}")
                    return info
                }
            }
            Log.w(TAG, "allNetworks vide (${connectivityManager.allNetworks.size}) — fallback connectionInfo")
            // Fallback : certains ROMs/versions exposent encore connectionInfo correctement
            @Suppress("DEPRECATION")
            val legacy = wifiManager.connectionInfo
            if (legacy != null && legacy.networkId != -1 && legacy.rssi > -127) {
                Log.d(TAG, "Wi-Fi via fallback connectionInfo — rssi=${legacy.rssi} networkId=${legacy.networkId}")
                return legacy
            }
            return null
        }
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Déclenche un scan et SUSPEND jusqu'à ce que les résultats de CE scan soient réellement
     * disponibles, via le broadcast [WifiManager.SCAN_RESULTS_AVAILABLE_ACTION].
     *
     * Pourquoi pas un simple delay() : un scan Wi-Fi complet (tous canaux 2.4/5/6 GHz) prend 2 à 4s.
     * Lire scanResults après un délai fixe trop court renvoie le scan PRÉCÉDENT — donc des données
     * correspondant à la position antérieure de l'utilisateur (décalage d'un cycle), ce qui fausse
     * le RSSI affiché ET le matching device↔BSSID.
     *
     * Si le scan est throttlé (Android 9+ : 4 scans / 2 min, startScan renvoie false) ou si le
     * broadcast n'arrive pas dans [timeoutMs], on retombe sur le dernier cache disponible.
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFreshScanResults(
        perAttemptTimeoutMs: Long = 6000,
        maxTotalWaitMs: Long = 12000
    ): List<ScanResult> {
        val startedAt = SystemClock.elapsedRealtime()
        var attempt = 0
        var lastResults: List<ScanResult> = emptyList()

        while (true) {
            attempt++
            val (results, confirmedFresh) = triggerScanAndAwait(perAttemptTimeoutMs)
            lastResults = results
            val maxTs = results.maxOfOrNull { it.timestamp } ?: 0L

            // Frais si : le broadcast a confirmé de nouveaux résultats (EXTRA_RESULTS_UPDATED=true),
            // OU le timestamp du scan a avancé depuis le dernier consommé.
            // (Si throttlé, Android renvoie le cache → pas de confirmation + même timestamp → réessai.)
            val isFresh = results.isNotEmpty() && (confirmedFresh || maxTs > lastScanTimestampUs)
            if (isFresh) {
                if (maxTs > 0L) lastScanTimestampUs = maxTs
                Log.d(TAG, "Scan FRAIS (tentative $attempt, confirmé=$confirmedFresh) — ${results.size} APs (ts=$maxTs)")
                return results
            }

            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (elapsed >= maxTotalWaitMs) {
                Log.w(TAG, "Scan toujours périmé après ${elapsed}ms / $attempt tentatives " +
                        "(throttle Android 4/2min) — utilisation du dernier disponible (ts=$maxTs)")
                if (maxTs > 0L) lastScanTimestampUs = maxTs
                return results
            }

            // Throttlé : la fenêtre se libère quand le plus ancien des 4 scans dépasse 2 min.
            // On patiente puis on retente — startScan finira par être accepté.
            Log.w(TAG, "Scan périmé (throttle, tentative $attempt) — nouvelle tentative dans 3s…")
            delay(3000)
        }
    }

    /**
     * Une tentative : déclenche un scan et attend sa complétion réelle (broadcast) ou le timeout.
     * @return (résultats, confirmedFresh) — confirmedFresh=true si le broadcast a signalé de
     *         nouveaux résultats (EXTRA_RESULTS_UPDATED=true). Sinon les résultats sont un cache.
     */
    @SuppressLint("MissingPermission")
    private suspend fun triggerScanAndAwait(timeoutMs: Long): Pair<List<ScanResult>, Boolean> {
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<List<ScanResult>?> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)
                        runCatching { context.unregisterReceiver(this) }
                        if (cont.isActive) {
                            @Suppress("DEPRECATION")
                            cont.resume(if (updated) wifiManager.scanResults else null)
                        }
                    }
                }
                registerScanReceiver(receiver)
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

                @Suppress("DEPRECATION")
                val started = wifiManager.startScan()
                if (started) recordAcceptedScan()
                Log.d(TAG, "startScan déclenché — accepté=$started, attente complétion réelle…")
                if (!started) {
                    // Throttlé : aucun broadcast de scan frais ne viendra → on rend la main
                    runCatching { context.unregisterReceiver(receiver) }
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (fresh != null) return fresh to true   // broadcast confirmé frais
        @Suppress("DEPRECATION")
        return wifiManager.scanResults to false    // cache (timeout / throttle / updated=false)
    }

    /** Mémorise un scan accepté et purge ceux sortis de la fenêtre de throttle. */
    private fun recordAcceptedScan() {
        val now = SystemClock.elapsedRealtime()
        acceptedScanTimes.addLast(now)
        while (acceptedScanTimes.isNotEmpty() && now - acceptedScanTimes.first() >= SCAN_THROTTLE_WINDOW_MS) {
            acceptedScanTimes.removeFirst()
        }
    }

    override suspend fun scanCooldownRemainingMs(): Long {
        val now = SystemClock.elapsedRealtime()
        while (acceptedScanTimes.isNotEmpty() && now - acceptedScanTimes.first() >= SCAN_THROTTLE_WINDOW_MS) {
            acceptedScanTimes.removeFirst()
        }
        // Tant qu'on n'a pas atteint le quota, un scan est dispo immédiatement.
        if (acceptedScanTimes.size < SCAN_THROTTLE_MAX) return 0L
        // Sinon : le prochain créneau se libère quand le plus ancien scan sort de la fenêtre.
        return (acceptedScanTimes.first() + SCAN_THROTTLE_WINDOW_MS - now).coerceAtLeast(0L)
    }

    /** Enregistre le receiver de scan (RECEIVER_NOT_EXPORTED requis sur Android 13+). */
    private fun registerScanReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    /**
     * Retourne un [ApReading] par AP visible du [ssid] dans [scanResults], trié par RSSI décroissant.
     * Cela inclut tous les BSSIDs du réseau (box + répéteurs + éventuels autres nœuds mesh).
     */
    private fun apReadingsForSsid(
        scanResults: List<android.net.wifi.ScanResult>,
        ssid: String
    ): List<ApReading> {
        val clean = ssid.trim('"').trim()
        return scanResults
            .filter { r -> r.SSID?.trim('"')?.trim() == clean }
            .map { r -> ApReading(r.BSSID, r.level, frequencyToBand(r.frequency)) }
            .sortedByDescending { it.rssi }
    }

    private fun frequencyToBand(freq: Int): String = when {
        freq < 3000  -> "2.4GHz"
        freq < 5925  -> "5GHz"   // 6GHz commence à 5925 MHz (ch.1 = 5955 MHz)
        else         -> "6GHz"
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        freq in 5955..7115 -> (freq - 5955) / 5 + 1
        else               -> 0
    }

    private fun intToIp(ip: Int): String? {
        if (ip == 0) return null
        return buildString {
            append(ip and 0xff).append('.')
            append(ip shr 8 and 0xff).append('.')
            append(ip shr 16 and 0xff).append('.')
            append(ip shr 24 and 0xff)
        }
    }
}
