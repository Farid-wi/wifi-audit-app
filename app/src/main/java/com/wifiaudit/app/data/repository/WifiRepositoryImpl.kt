package com.wifiaudit.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.wifiaudit.app.data.log.WifiDiagLog
import com.wifiaudit.app.domain.model.ApReading
import com.wifiaudit.app.domain.model.NeighborNetwork
import com.wifiaudit.app.domain.model.ScanMode
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

// Clé SharedPreferences du mode de scan choisi par l'utilisateur.
private const val PREF_SCAN_MODE = "pref_scan_mode"

// Détection empirique du throttling : 3 scans espacés de ~5 s.
private const val THROTTLE_PROBE_COUNT = 3
private const val THROTTLE_PROBE_SPACING_MS = 5_000L

class WifiRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) : WifiRepository {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // ─── Compteurs de diagnostic de la session de scan (tag WifiDiagScan) ────────
    private var sessionScansOk = 0       // scans renvoyant des résultats frais
    private var sessionScansFailed = 0   // startScan refusé ou aucun résultat
    private var sessionScansStale = 0    // résultats périmés (cache renvoyé par le throttle)

    // true si la DERNIÈRE tentative de scan a dû retomber sur un cache périmé. Sert à détecter
    // qu'un mode rapide ne tient pas (throttle toujours actif) → bascule automatique en standard.
    private var lastScanWasStale = false

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
                // Permission de localisation + localisation système + Wi-Fi : prérequis de tout scan.
                ensureScanPrerequisites()

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
                    // Mode rapide qui ne tient pas (throttle toujours actif) → bascule en standard.
                    maybeFallbackFromFastMode()

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
                    // Mode rapide qui ne tient pas (throttle toujours actif) → bascule en standard.
                    maybeFallbackFromFastMode()
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
                    Triple(ssid, r.level, ssid == connectedSsid)
                }
                // Un même SSID peut apparaître sur plusieurs BSSID/bandes → garder le plus fort.
                .groupBy { it.first }
                .map { (ssid, list) ->
                    WifiRepository.VisibleNetwork(
                        ssid        = ssid,
                        isConnected = list.any { it.third },
                        level       = list.maxOf { it.second }
                    )
                }
                .sortedWith(compareByDescending<WifiRepository.VisibleNetwork> { it.isConnected }
                    .thenByDescending { it.level })
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

            // Diagnostic : âge de chaque ScanResult + marquage STALE (> 10 s = cache du throttle).
            logScanFreshness(results, confirmedFresh, attempt)

            // Frais si : le broadcast a confirmé de nouveaux résultats (EXTRA_RESULTS_UPDATED=true),
            // OU le timestamp du scan a avancé depuis le dernier consommé.
            // (Si throttlé, Android renvoie le cache → pas de confirmation + même timestamp → réessai.)
            val isFresh = results.isNotEmpty() && (confirmedFresh || maxTs > lastScanTimestampUs)
            if (isFresh) {
                if (maxTs > 0L) lastScanTimestampUs = maxTs
                lastScanWasStale = false
                sessionScansOk++
                Log.d(TAG, "Scan FRAIS (tentative $attempt, confirmé=$confirmedFresh) — ${results.size} APs (ts=$maxTs)")
                WifiDiagLog.d { "Scan FRESH (attempt $attempt, confirmed=$confirmedFresh) — ${results.size} BSSID" }
                return results
            }

            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (elapsed >= maxTotalWaitMs) {
                // Abandon : on rend le dernier cache disponible. C'est un scan PÉRIMÉ → le mode rapide
                // (s'il est actif) en déduira que le throttling est toujours en place.
                lastScanWasStale = results.isNotEmpty()
                if (results.isEmpty()) sessionScansFailed++ else sessionScansStale++
                Log.w(TAG, "Scan toujours périmé après ${elapsed}ms / $attempt tentatives " +
                        "(throttle Android 4/2min) — utilisation du dernier disponible (ts=$maxTs)")
                WifiDiagLog.w("Scan STALE after ${elapsed}ms / $attempt attempts (Android throttle) — " +
                        "falling back to last cache (${results.size} BSSID)")
                if (maxTs > 0L) lastScanTimestampUs = maxTs
                return results
            }

            // Throttlé : la fenêtre se libère quand le plus ancien des 4 scans dépasse 2 min.
            // On patiente puis on retente — startScan finira par être accepté.
            Log.w(TAG, "Scan périmé (throttle, tentative $attempt) — nouvelle tentative dans 3s…")
            WifiDiagLog.w("Scan STALE (throttle, attempt $attempt) — retrying in 3s…")
            delay(3000)
        }
    }

    /**
     * Diagnostic logging (DEBUG only) of scan freshness: for each [ScanResult], the age in seconds
     * (elapsedRealtime − result.timestamp/1000) and an explicit STALE marker when the age exceeds
     * [WifiDiagLog.STALE_AGE_MS] — a stale result means the throttle returned cache, not a real scan.
     */
    private fun logScanFreshness(results: List<ScanResult>, confirmedFresh: Boolean, attempt: Int) {
        if (!com.wifiaudit.app.BuildConfig.DEBUG) return
        val nowMs = SystemClock.elapsedRealtime()
        var staleCount = 0
        val lines = results.joinToString("\n") { r ->
            val ageMs = nowMs - r.timestamp / 1000   // timestamp is µs since boot → /1000 = ms
            val stale = ageMs > WifiDiagLog.STALE_AGE_MS
            if (stale) staleCount++
            "    ${r.BSSID}  age=${ageMs / 1000}s${if (stale) "  ⚠ STALE" else ""}"
        }
        WifiDiagLog.d(buildString {
            appendLine("SCAN freshness (attempt $attempt, confirmedFresh=$confirmedFresh, mode=${getScanMode()}) " +
                    "— ${results.size} BSSID, stale=$staleCount")
            if (results.isNotEmpty()) append(lines)
        })
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
                        @Suppress("DEPRECATION")
                        val results = wifiManager.scanResults
                        WifiDiagLog.d { "SCAN_RESULTS_AVAILABLE received — ${results.size} BSSID, EXTRA_RESULTS_UPDATED=$updated" }
                        if (cont.isActive) {
                            cont.resume(if (updated) results else null)
                        }
                    }
                }
                registerScanReceiver(receiver)
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

                @Suppress("DEPRECATION")
                val started = wifiManager.startScan()
                if (started) recordAcceptedScan() else sessionScansFailed++
                Log.d(TAG, "startScan déclenché — accepté=$started, attente complétion réelle…")
                WifiDiagLog.d { "startScan() — accepted=$started, mode=${getScanMode()}, t=${SystemClock.elapsedRealtime()}ms" }
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
        // En mode rapide, l'utilisateur a désactivé la limitation Android : pas de plafond throttle.
        // La cadence ~1 scan/5 s est alors gérée par la cadence délibérée côté ViewModel.
        if (getScanMode() == ScanMode.FAST) return 0L
        val now = SystemClock.elapsedRealtime()
        while (acceptedScanTimes.isNotEmpty() && now - acceptedScanTimes.first() >= SCAN_THROTTLE_WINDOW_MS) {
            acceptedScanTimes.removeFirst()
        }
        // Tant qu'on n'a pas atteint le quota, un scan est dispo immédiatement.
        if (acceptedScanTimes.size < SCAN_THROTTLE_MAX) return 0L
        // Sinon : le prochain créneau se libère quand le plus ancien scan sort de la fenêtre.
        return (acceptedScanTimes.first() + SCAN_THROTTLE_WINDOW_MS - now).coerceAtLeast(0L)
    }

    // ─── Mode de scan ────────────────────────────────────────────────────────

    override fun getScanMode(): ScanMode {
        val name = prefs.getString(PREF_SCAN_MODE, ScanMode.STANDARD.name)
        return runCatching { ScanMode.valueOf(name!!) }.getOrDefault(ScanMode.STANDARD)
    }

    override fun setScanMode(mode: ScanMode) {
        prefs.edit().putString(PREF_SCAN_MODE, mode.name).apply()
        // Nouveau choix de mode = nouvelle session de diagnostic → remise à zéro des compteurs.
        sessionScansOk = 0
        sessionScansFailed = 0
        sessionScansStale = 0
        lastScanWasStale = false
        WifiDiagLog.d("Scan mode set to $mode — diagnostic counters reset")
    }

    @SuppressLint("MissingPermission")
    override suspend fun detectThrottlingDisabled(): Boolean = withContext(Dispatchers.IO) {
        // Les prérequis (permission + localisation + Wi-Fi) doivent être réunis pour scanner.
        ensureScanPrerequisites()

        var freshCount = 0
        repeat(THROTTLE_PROBE_COUNT) { i ->
            val before = lastScanTimestampUs
            val (results, confirmedFresh) = triggerScanAndAwait(6000)
            val maxTs = results.maxOfOrNull { it.timestamp } ?: 0L
            val fresh = results.isNotEmpty() && (confirmedFresh || maxTs > before)
            if (fresh) {
                freshCount++
                if (maxTs > 0L) lastScanTimestampUs = maxTs
            }
            WifiDiagLog.d("Throttle probe ${i + 1}/$THROTTLE_PROBE_COUNT — fresh=$fresh " +
                    "confirmed=$confirmedFresh ts=$maxTs (${results.size} BSSID)")
            if (i < THROTTLE_PROBE_COUNT - 1) delay(THROTTLE_PROBE_SPACING_MS)
        }
        val disabled = freshCount == THROTTLE_PROBE_COUNT
        WifiDiagLog.d("Throttle detection — freshCount=$freshCount/$THROTTLE_PROBE_COUNT → throttlingDisabled=$disabled")
        disabled
    }

    override fun logScanSessionSummary() {
        val total = sessionScansOk + sessionScansFailed + sessionScansStale
        WifiDiagLog.d(buildString {
            appendLine("=== SCAN SESSION SUMMARY (mode=${getScanMode()}) ===")
            appendLine("  total attempts : $total")
            appendLine("  fresh (ok)     : $sessionScansOk")
            appendLine("  failed         : $sessionScansFailed")
            append    ("  stale (cache)  : $sessionScansStale")
        })
    }

    /**
     * In fast mode, if the latest scan had to fall back to a stale cache, the throttle is actually
     * still active → silently switch the persisted mode back to STANDARD. The ViewModel detects the
     * mode change after the measurement and informs the user with a toast.
     */
    private fun maybeFallbackFromFastMode() {
        if (getScanMode() == ScanMode.FAST && lastScanWasStale) {
            WifiDiagLog.w("Fast mode produced a STALE scan — throttle still active. Auto-switching to STANDARD.")
            setScanMode(ScanMode.STANDARD)
        }
    }

    // ─── Prérequis de scan : permission + localisation système + Wi-Fi ───────────

    /**
     * @throws SecurityException if ACCESS_FINE_LOCATION is missing.
     * @throws IllegalStateException if system location services or Wi-Fi are turned off.
     */
    private fun ensureScanPrerequisites() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Autorisez l'accès à la localisation pour analyser le Wi-Fi.")
        }
        if (!isLocationEnabled()) {
            throw IllegalStateException("Activez la localisation de votre téléphone pour analyser le Wi-Fi.")
        }
        if (!wifiManager.isWifiEnabled) {
            throw IllegalStateException("Activez le Wi-Fi de votre téléphone.")
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
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
