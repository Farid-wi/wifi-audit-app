# Skill : Heatmap Wi-Fi + traitement du plan

## Contexte
Ce skill couvre trois problèmes :
1. **Analyse du plan** : extraire les pièces depuis une photo (ML Kit) ou créer un plan canvas (voir `canvas-plan.md`)
2. **Heatmap par pièce** : interpoler les mesures RSSI, masquée par les limites de chaque pièce
3. **Score global** : agréger les scores pièce pondérés par leur surface

---

## 1. Analyse du plan — ML Kit Text Recognition

### Principe MVP
Pour le MVP, on n'essaie pas de détecter les murs. On détecte uniquement les **labels textuels** (noms des pièces) dans l'image et on crée des zones rectangulaires approximatives autour d'eux.

### Dépendances gradle
```kotlin
implementation("com.google.mlkit:text-recognition:16.0.0")
```

### Extraction des textes et positions
```kotlin
suspend fun detectRooms(bitmap: Bitmap): List<DetectedRoom> =
    suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val rooms = result.textBlocks
                    .filter { block -> block.text.length in 2..20 }
                    .map { block ->
                        val bounds = block.boundingBox ?: return@map null
                        DetectedRoom(
                            label = block.text.trim().lowercase().replaceFirstChar { it.uppercase() },
                            centerX = bounds.centerX().toFloat() / bitmap.width,
                            centerY = bounds.centerY().toFloat() / bitmap.height,
                            approxWidth = (bounds.width().toFloat() / bitmap.width) * 1.5f,
                            approxHeight = (bounds.height().toFloat() / bitmap.height) * 2f
                        )
                    }
                    .filterNotNull()
                cont.resume(rooms)
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

data class DetectedRoom(
    val label: String,
    val centerX: Float,   // normalisé 0..1
    val centerY: Float,
    val approxWidth: Float,
    val approxHeight: Float
)
```

### Fallback manuel
Si ML Kit ne détecte rien ou si l'utilisateur veut corriger :
```kotlin
// Afficher un écran de confirmation avec les pièces détectées
// Permettre d'ajouter / renommer / supprimer une pièce
// Stocker la liste finale des Room dans l'Audit
```

---

## 2. Interpolation IDW — algorithme complet

### Paramètres recommandés
- Puissance (`power`) : 2.0 (standard)
- Résolution grille : 50×50 (bon compromis performance/qualité)
- Minimum de mesures pour interpoler : 2 (sinon afficher la couleur du seul point)

### Implémentation Kotlin
```kotlin
import kotlin.math.pow
import kotlin.math.sqrt

object HeatmapEngine {

    fun compute(
        measurements: List<Measurement>,
        gridSize: Int = 50,
        power: Double = 2.0
    ): HeatmapGrid {
        if (measurements.isEmpty()) return HeatmapGrid(gridSize, FloatArray(gridSize * gridSize) { -90f })

        val values = FloatArray(gridSize * gridSize)

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val px = col.toFloat() / (gridSize - 1)
                val py = row.toFloat() / (gridSize - 1)

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

                values[row * gridSize + col] =
                    if (!exactHit && weightSum > 0) (valueSum / weightSum).toFloat()
                    else valueSum.toFloat()
            }
        }

        return HeatmapGrid(gridSize, values)
    }

    fun roomMedianRssi(room: DetectedRoom, grid: HeatmapGrid): Float {
        val roomValues = mutableListOf<Float>()
        for (row in 0 until grid.size) {
            for (col in 0 until grid.size) {
                val px = col.toFloat() / (grid.size - 1)
                val py = row.toFloat() / (grid.size - 1)
                if (room.contains(px, py)) {
                    roomValues += grid.values[row * grid.size + col]
                }
            }
        }
        return if (roomValues.isEmpty()) -90f else roomValues.sorted()[roomValues.size / 2]
    }
}

data class HeatmapGrid(val size: Int, val values: FloatArray)

fun DetectedRoom.contains(px: Float, py: Float): Boolean {
    val halfW = approxWidth / 2
    val halfH = approxHeight / 2
    return px in (centerX - halfW)..(centerX + halfW) &&
           py in (centerY - halfH)..(centerY + halfH)
}
```

---

## 2b. IDW masqué par pièce (règle principale)

La heatmap **ne déborde pas** d'une pièce à l'autre. Chaque pièce a sa propre grille IDW calculée uniquement à partir de ses mesures. Les cellules hors des limites de la pièce reçoivent `Float.NaN`.

```kotlin
fun computeAllRoomGrids(
    rooms: List<CanvasRoom>,
    measurements: List<Measurement>,
    gridSize: Int = 50
): Map<String, HeatmapGrid> =
    rooms.associate { room ->
        val roomMeasurements = measurements.filter { it.roomId == room.id }
        room.id to computeRoomGrid(room, roomMeasurements, gridSize)
    }

private fun computeRoomGrid(
    room: CanvasRoom,
    measurements: List<Measurement>,
    gridSize: Int
): HeatmapGrid {
    val values = FloatArray(gridSize * gridSize) { Float.NaN }
    if (measurements.isEmpty()) return HeatmapGrid(gridSize, values)

    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val px = col.toFloat() / (gridSize - 1)
            val py = row.toFloat() / (gridSize - 1)
            if (!room.bounds.contains(px, py)) continue

            var wSum = 0.0; var vSum = 0.0
            for (m in measurements) {
                val d = sqrt((px - m.x).pow(2) + (py - m.y).pow(2).toDouble())
                if (d < 1e-6) { vSum = m.rssi.toDouble(); wSum = 1.0; break }
                val w = 1.0 / d.pow(2.0)
                wSum += w; vSum += w * m.rssi
            }
            if (wSum > 0) values[row * gridSize + col] = (vSum / wSum).toFloat()
        }
    }
    return HeatmapGrid(gridSize, values)
}
```

## 2c. Score global pondéré par surface

```kotlin
fun computeGlobalScore(
    rooms: List<CanvasRoom>,
    roomGrids: Map<String, HeatmapGrid>
): OverallScore {
    val scored = rooms.filter { roomGrids[it.id]?.hasValues() == true }
    if (scored.isEmpty()) return OverallScore.FAIR
    val totalSurface = scored.sumOf { it.bounds.surface.toDouble() }
    val weighted = scored.sumOf { room ->
        val median = roomGrids[room.id]!!.median()
        val score = when (rssiToQuality(median.toInt())) {
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

fun HeatmapGrid.hasValues() = values.any { !it.isNaN() }
fun HeatmapGrid.median(): Float {
    val valid = values.filter { !it.isNaN() }.sorted()
    return if (valid.isEmpty()) -90f else valid[valid.size / 2]
}
```

---

## 3. Rendu Compose Canvas

### Heatmap multi-pièces (une grille par pièce, masquée)

```kotlin
@Composable
fun PlanWithHeatmap(
    planImagePath: String?,
    rooms: List<CanvasRoom>,
    roomGrids: Map<String, HeatmapGrid>,    // roomId → grille (NaN hors pièce)
    measurementDots: List<MeasurementDotInfo>,
    gatewayPosition: Position?,
    repeaterPositions: List<RepeaterPosition>,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(planImagePath) {
        planImagePath?.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.background(AppColors.Surface).onSizeChanged { boxSize = it }) {
        // Plan photo (optionnel si canvas pur)
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Plan",
                  contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }

        // Pièces sans mesures → fond gris neutre
        if (boxSize != IntSize.Zero) {
            rooms.forEach { room ->
                val hasData = roomGrids[room.id]?.hasValues() == true
                if (!hasData) {
                    RoomEmptyOverlay(room = room, boxSize = boxSize)
                }
            }
        }

        // Heatmap par pièce
        Canvas(modifier = Modifier.fillMaxSize()) {
            rooms.forEach { room ->
                val grid = roomGrids[room.id] ?: return@forEach
                val cellW = size.width  / grid.size
                val cellH = size.height / grid.size
                val minV = grid.values.filter { !it.isNaN() }.minOrNull() ?: return@forEach
                val maxV = grid.values.filter { !it.isNaN() }.maxOrNull() ?: return@forEach

                for (row in 0 until grid.size) {
                    for (col in 0 until grid.size) {
                        val rssi = grid.values[row * grid.size + col]
                        if (rssi.isNaN()) continue
                        val color = rssiToColorRelative(rssi, minV, maxV).copy(alpha = 0.52f)
                        drawRect(color = color,
                                 topLeft = Offset(col * cellW, row * cellH),
                                 size    = Size(cellW, cellH))
                    }
                }
            }
        }

        // Points de mesure + équipements (voir ResultsScreen pour le code complet)
    }
}

@Composable
private fun RoomEmptyOverlay(room: CanvasRoom, boxSize: IntSize) {
    val l = (room.bounds.left  * boxSize.width).toInt()
    val t = (room.bounds.top   * boxSize.height).toInt()
    val w = ((room.bounds.right  - room.bounds.left) * boxSize.width).toInt()
    val h = ((room.bounds.bottom - room.bounds.top)  * boxSize.height).toInt()
    Box(
        modifier = Modifier
            .offset { IntOffset(l, t) }
            .size(width = w.dp, height = h.dp)   // approx — utiliser LocalDensity en pratique
            .background(Color(0xFFE5E5EA).copy(alpha = 0.6f), AppShape.Small)
            .border(1.dp, Color(0xFFAEAEB2), AppShape.Small),
        contentAlignment = Alignment.Center
    ) {
        Text(room.label, style = AppType.Micro, color = Color(0xFF6E6E73))
    }
}

private fun rssiToColorRelative(rssi: Float, min: Float, max: Float): Color {
    if (max == min) return Color(0xFFEF9F27)
    val n = (rssi - min) / (max - min)
    return when {
        n >= 0.60f -> Color(0xFF1D9E75)
        n >= 0.30f -> Color(0xFFEF9F27)
        else       -> Color(0xFFE24B4A)
    }
}
```

### Heatmap filtrée par équipement (BSSID)
```kotlin
fun computeHeatmapForBssid(
    measurements: List<Measurement>,
    bssid: String,
    gridSize: Int = 50
): HeatmapGrid {
    val filtered = measurements.filter { it.bssid == bssid }
    return HeatmapEngine.compute(filtered, gridSize)
}
```

---

## 4. Légende heatmap — composant Compose

```kotlin
@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(color = Color(0xFF1D9E75), label = "Signal fort")
        LegendItem(color = Color(0xFFEF9F27), label = "Signal moyen")
        LegendItem(color = Color(0xFFE24B4A), label = "Signal faible")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color, shape = CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
```

---

## 5. Performance — optimisations

### Calculer la grille hors du thread UI
```kotlin
// Dans le ViewModel
fun computeHeatmap(measurements: List<Measurement>) {
    viewModelScope.launch(Dispatchers.Default) {
        val grid = HeatmapEngine.compute(measurements)
        _uiState.update { it.copy(heatmapGrid = grid) }
    }
}
```

### Mettre en cache la grille
La grille ne recalcule que si la liste de mesures change. Utiliser `derivedStateOf` en Compose ou un simple cache dans le ViewModel.

### Résolution adaptative
- Pendant l'audit (mesures en cours) : grille 20×20 pour affichage temps réel
- Résultats finaux : grille 50×50

---

## 6. Checklist plan analyse

- [ ] ML Kit détecte au moins 2 pièces → afficher la vue de confirmation
- [ ] ML Kit ne détecte rien → passer directement en saisie manuelle
- [ ] Les coordonnées des pièces sont bien normalisées (0..1) avant stockage
- [ ] L'image du plan est redimensionnée à 1024px max avant envoi au backend (économie base64)
- [ ] Le plan est sauvegardé localement (Room `PlanImageCache`) avant envoi réseau
