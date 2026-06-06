# Skill : Android Kotlin — Wi-Fi Audit

## Contexte
Application Android Kotlin avec Jetpack Compose, Hilt, Room, Retrofit.
Architecture MVVM + Clean Architecture stricte.

---

## Architecture

### Règle de dépendance
```
presentation → domain ← data
```
- `domain` ne dépend de rien (pas d'Android, pas de Retrofit, pas de Room)
- `data` implémente les interfaces du domaine
- `presentation` observe les StateFlow des ViewModels

### ViewModel pattern
```kotlin
@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val scanWifiUseCase: ScanWifiUseCase,
    private val runPingUseCase: RunPingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    fun takeMeasurement(x: Float, y: Float) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = scanWifiUseCase(x, y)
            _uiState.update { it.copy(isLoading = false, lastMeasurement = result) }
        }
    }
}
```

### UseCase pattern
```kotlin
class ScanWifiUseCase @Inject constructor(
    private val wifiRepository: WifiRepository
) {
    suspend operator fun invoke(x: Float, y: Float): Result<Measurement> =
        wifiRepository.scan(x, y)
}
```

---

## Wi-Fi — WifiManager

### Scan des réseaux
```kotlin
@SuppressLint("MissingPermission")
fun scanNetworks(context: Context): List<ScanResult> {
    val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    wifiManager.startScan()
    return wifiManager.scanResults
}
```

### Lecture du réseau connecté (RSSI courant)
```kotlin
fun getCurrentConnectionInfo(context: Context): WifiInfo? {
    val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wifiManager.connectionInfo
}
```

### Mapping RSSI → qualité utilisateur
```kotlin
fun rssiToQuality(rssi: Int): SignalQuality = when {
    rssi >= -60 -> SignalQuality.GOOD
    rssi >= -75 -> SignalQuality.FAIR
    else        -> SignalQuality.POOR
}

enum class SignalQuality { GOOD, FAIR, POOR }
```

**Ne jamais exposer les valeurs dBm dans l'UI.** Toujours passer par `rssiToQuality()`.

### Ping gateway
```kotlin
suspend fun pingHost(host: String, timeoutMs: Int = 1000): Int? =
    withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val reachable = InetAddress.getByName(host).isReachable(timeoutMs)
        if (reachable) (System.currentTimeMillis() - start).toInt() else null
    }
```

### Permissions — demande runtime
```kotlin
val permissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_WIFI_STATE,
    Manifest.permission.CAMERA
)
// Demander via ActivityResultContracts.RequestMultiplePermissions
```

---

## Heatmap — Interpolation IDW

```kotlin
data class GridPoint(val x: Float, val y: Float, val value: Float)

fun interpolateIDW(
    measurements: List<Measurement>,
    gridSize: Int = 50,
    power: Double = 2.0
): Array<FloatArray> {
    val grid = Array(gridSize) { FloatArray(gridSize) }
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val px = col.toFloat() / gridSize
            val py = row.toFloat() / gridSize
            var weightSum = 0.0
            var valueSum = 0.0
            measurements.forEach { m ->
                val dist = sqrt((px - m.x).pow(2) + (py - m.y).pow(2)).toDouble()
                if (dist < 0.001) {
                    valueSum = m.rssi.toDouble()
                    weightSum = 1.0
                    return@forEach
                }
                val w = 1.0 / dist.pow(power)
                weightSum += w
                valueSum += w * m.rssi
            }
            grid[row][col] = (valueSum / weightSum).toFloat()
        }
    }
    return grid
}
```

### Rendu Compose Canvas
```kotlin
@Composable
fun HeatmapOverlay(grid: Array<FloatArray>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellW = size.width / grid[0].size
        val cellH = size.height / grid.size
        grid.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, rssi ->
                val color = rssiToColor(rssi)
                drawRect(
                    color = color.copy(alpha = 0.6f),
                    topLeft = Offset(col * cellW, row * cellH),
                    size = Size(cellW, cellH)
                )
            }
        }
    }
}

fun rssiToColor(rssi: Float): Color = when {
    rssi >= -60f -> Color(0xFF1D9E75)
    rssi >= -75f -> Color(0xFFEF9F27)
    else         -> Color(0xFFE24B4A)
}
```

---

## Room — base locale

### Entity
```kotlin
@Entity(tableName = "audits")
data class AuditEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val ssid: String,
    val planImagePath: String,
    val gatewayX: Float,
    val gatewayY: Float,
    val status: String   // "DRAFT" | "PENDING" | "SYNCED"
)

@Entity(tableName = "measurements", foreignKeys = [
    ForeignKey(entity = AuditEntity::class, parentColumns = ["id"],
               childColumns = ["auditId"], onDelete = ForeignKey.CASCADE)
])
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val auditId: String,
    val x: Float, val y: Float,
    val rssi: Int,
    val bssid: String,
    val channel: Int,
    val band: String,
    val pingGatewayMs: Int,
    val pingInternetMs: Int,
    val neighborsJson: String   // JSON sérialisé
)
```

### DAO
```kotlin
@Dao
interface AuditDao {
    @Query("SELECT * FROM audits ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(audit: AuditEntity)

    @Query("SELECT * FROM audits WHERE status = 'PENDING'")
    suspend fun getPending(): List<AuditEntity>
}
```

---

## Retrofit — envoi au backend

```kotlin
interface AuditApiService {
    @POST("audits")
    suspend fun submitAudit(@Body payload: AuditPayload): Response<SubmitResponse>

    @GET("audits")
    suspend fun listAudits(): Response<List<AuditSummaryDto>>
}

// Construction dans le module Hilt
@Provides @Singleton
fun provideAuditApi(prefs: SharedPreferences): AuditApiService {
    val baseUrl = prefs.getString("pref_server_url", "http://192.168.1.1:8000")!!
    return Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuditApiService::class.java)
}
```

---

## Plan tap-to-place (placement équipements)

```kotlin
@Composable
fun InteractivePlanView(
    planBitmap: ImageBitmap,
    equipments: List<Equipment>,
    onTap: (x: Float, y: Float) -> Unit
) {
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    Image(
        bitmap = planBitmap,
        contentDescription = "Plan du logement",
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { imageSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val nx = offset.x / imageSize.width
                    val ny = offset.y / imageSize.height
                    onTap(nx, ny)   // positions normalisées 0..1
                }
            }
    )
    // Superposer les icônes d'équipements
    equipments.forEach { eq ->
        EquipmentIcon(equipment = eq, imageSize = imageSize)
    }
}
```

---

## Recommandations — règles métier

```kotlin
fun generateRecommendations(
    measurements: List<Measurement>,
    rooms: List<Room>
): List<Recommendation> {
    val recs = mutableListOf<Recommendation>()

    rooms.forEach { room ->
        val roomMeasurements = measurements.filter { it.roomId == room.id }
        if (roomMeasurements.isEmpty()) return@forEach
        val medianRssi = roomMeasurements.map { it.rssi }.median()

        when (rssiToQuality(medianRssi)) {
            SignalQuality.POOR ->
                recs += Recommendation(
                    roomId = room.id,
                    severity = Severity.HIGH,
                    message = "La couverture Wi-Fi est insuffisante en ${room.name}."
                )
            SignalQuality.FAIR ->
                recs += Recommendation(
                    roomId = room.id,
                    severity = Severity.MEDIUM,
                    message = "Le signal Wi-Fi est moyen en ${room.name}."
                )
            else -> {}
        }
    }
    return recs
}
```

---

---

## Modèles domaine — Plan et pièces

### RoomType + CanvasRoom
```kotlin
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
    val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    val surface: Float get() = (right - left) * (bottom - top)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

data class CanvasRoom(
    val id: String = UUID.randomUUID().toString(),
    val type: RoomType,
    val label: String = type.displayName,
    val bounds: RoomBounds
)
```

### Rattachement mesure → pièce
```kotlin
fun assignRoomToMeasurement(x: Float, y: Float, rooms: List<CanvasRoom>): String? =
    rooms.firstOrNull { it.bounds.contains(x, y) }?.id
```

Appeler cette fonction dans `ScanWifiUseCase` au moment de construire la `Measurement`.

---

## IDW masqué par pièce

```kotlin
/**
 * Calcule une grille IDW pour UNE pièce uniquement.
 * Les cellules hors bounds reçoivent Float.NaN (rendu transparent).
 * Seules les mesures de la pièce participent à l'interpolation.
 */
fun computeRoomGrid(
    room: CanvasRoom,
    measurements: List<Measurement>,
    gridSize: Int = 50,
    power: Double = 2.0
): HeatmapGrid {
    val roomMeasurements = measurements.filter { it.roomId == room.id }
    val values = FloatArray(gridSize * gridSize) { Float.NaN }

    if (roomMeasurements.isEmpty()) return HeatmapGrid(gridSize, values)

    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val px = col.toFloat() / (gridSize - 1)
            val py = row.toFloat() / (gridSize - 1)
            if (!room.bounds.contains(px, py)) continue   // hors pièce → NaN

            var weightSum = 0.0
            var valueSum  = 0.0
            for (m in roomMeasurements) {
                val dist = sqrt((px - m.x).pow(2) + (py - m.y).pow(2).toDouble())
                if (dist < 1e-6) { valueSum = m.rssi.toDouble(); weightSum = 1.0; break }
                val w = 1.0 / dist.pow(power)
                weightSum += w
                valueSum  += w * m.rssi
            }
            if (weightSum > 0) values[row * gridSize + col] = (valueSum / weightSum).toFloat()
        }
    }
    return HeatmapGrid(gridSize, values)
}

/**
 * Score global = moyenne des scores pièce pondérée par surface.
 * Ignore les pièces sans mesures.
 */
fun computeGlobalScore(
    roomScores: Map<String, SignalQuality>,
    rooms: List<CanvasRoom>
): OverallScore {
    val scored = rooms.filter { roomScores.containsKey(it.id) }
    if (scored.isEmpty()) return OverallScore.FAIR
    val total = scored.sumOf { it.bounds.surface.toDouble() }
    val weighted = scored.sumOf { room ->
        val s = when (roomScores[room.id]!!) {
            SignalQuality.GOOD -> 1.0
            SignalQuality.FAIR -> 0.5
            SignalQuality.POOR -> 0.0
        }
        s * room.bounds.surface / total
    }
    return when {
        weighted >= 0.67 -> OverallScore.GOOD
        weighted >= 0.34 -> OverallScore.FAIR
        else             -> OverallScore.POOR
    }
}
```

---

## Recommandations contextuelles (type de pièce)

```kotlin
fun generateContextualRecommendation(
    room: CanvasRoom,
    quality: SignalQuality
): String? = when {
    quality == SignalQuality.POOR && room.type == RoomType.OFFICE ->
        "Le signal est insuffisant dans votre bureau — votre connexion sera lente au travail."
    quality == SignalQuality.POOR && room.type == RoomType.BEDROOM ->
        "Le signal est faible dans votre chambre, ce qui peut affecter vos appareils la nuit."
    quality == SignalQuality.POOR && room.type == RoomType.HALLWAY ->
        "Un couloir mal couvert crée une zone morte entre vos pièces."
    quality == SignalQuality.POOR ->
        "Le signal est insuffisant en ${room.label}. Un répéteur pourrait aider."
    quality == SignalQuality.FAIR && room.type == RoomType.OFFICE ->
        "Le signal est moyen dans votre bureau. Rapprocher le répéteur améliorerait la connexion."
    quality == SignalQuality.FAIR ->
        "Le signal est moyen en ${room.label}."
    else -> null
}
```

---

## Conventions de nommage

| Élément | Convention | Exemple |
|---|---|---|
| Fichiers Compose | `NomScreen.kt` | `MeasureScreen.kt` |
| ViewModels | `NomViewModel.kt` | `MeasureViewModel.kt` |
| UseCases | `VerbNomUseCase.kt` | `ScanWifiUseCase.kt` |
| Entities Room | `NomEntity.kt` | `AuditEntity.kt` |
| DTOs réseau | `NomDto.kt` / `NomPayload.kt` | `AuditPayload.kt` |
| StateFlow UI | `uiState: StateFlow<NomUiState>` | — |
| Events VM | `fun verbNom(...)` | `fun takeMeasurement(...)` |

## Ce qu'il ne faut jamais faire
- Appeler Room ou Retrofit directement depuis un Composable ou ViewModel
- Exposer des types Android (`Context`, `WifiInfo`) dans le domaine
- Afficher des valeurs dBm, BSSID ou canaux dans l'UI
- Lancer des coroutines en dehors de `viewModelScope` dans les ViewModels
