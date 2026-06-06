# Skill : Canvas Plan Builder

## Contexte
Permet à l'utilisateur de construire son plan de logement directement dans l'app en ajoutant des pièces rectangulaires qu'il positionne et redimensionne. Alternative à la photo — les deux options sont présentées avec le même poids visuel dans l'étape 1.

---

## Modèles domaine

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

    fun clampedTo(canvasW: Float, canvasH: Float) = copy(
        left  = left.coerceIn(0f, right - MIN_ROOM_SIZE),
        top   = top.coerceIn(0f, bottom - MIN_ROOM_SIZE),
        right = right.coerceIn(left + MIN_ROOM_SIZE, 1f),
        bottom= bottom.coerceIn(top + MIN_ROOM_SIZE, 1f)
    )
    companion object { const val MIN_ROOM_SIZE = 0.05f }
}

data class CanvasRoom(
    val id: String = UUID.randomUUID().toString(),
    val type: RoomType,
    val label: String = type.displayName,
    val bounds: RoomBounds
)
```

---

## ViewModel — PlanCanvasViewModel

```kotlin
@HiltViewModel
class PlanCanvasViewModel @Inject constructor() : ViewModel() {

    private val _rooms = MutableStateFlow<List<CanvasRoom>>(emptyList())
    val rooms: StateFlow<List<CanvasRoom>> = _rooms.asStateFlow()

    private val _selectedRoomId = MutableStateFlow<String?>(null)
    val selectedRoomId: StateFlow<String?> = _selectedRoomId.asStateFlow()

    private val _editingLabelRoomId = MutableStateFlow<String?>(null)
    val editingLabelRoomId: StateFlow<String?> = _editingLabelRoomId.asStateFlow()

    fun addRoom(type: RoomType) {
        val newRoom = CanvasRoom(
            type   = type,
            bounds = nextAvailableBounds()
        )
        _rooms.update { it + newRoom }
        _selectedRoomId.value = newRoom.id
    }

    fun selectRoom(id: String?) {
        _selectedRoomId.value = id
        _editingLabelRoomId.value = null
    }

    fun moveRoom(id: String, dx: Float, dy: Float) {
        _rooms.update { rooms ->
            rooms.map { r ->
                if (r.id != id) return@map r
                val b = r.bounds
                val w = b.right - b.left
                val h = b.bottom - b.top
                val newLeft = (b.left + dx).coerceIn(0f, 1f - w)
                val newTop  = (b.top  + dy).coerceIn(0f, 1f - h)
                r.copy(bounds = b.copy(
                    left = newLeft, top = newTop,
                    right = newLeft + w, bottom = newTop + h
                ))
            }
        }
    }

    fun resizeRoom(id: String, handle: ResizeHandle, dx: Float, dy: Float) {
        _rooms.update { rooms ->
            rooms.map { r ->
                if (r.id != id) return@map r
                val b = r.bounds
                val newBounds = when (handle) {
                    ResizeHandle.TOP_LEFT     -> b.copy(left = b.left + dx, top = b.top + dy)
                    ResizeHandle.TOP_RIGHT    -> b.copy(right = b.right + dx, top = b.top + dy)
                    ResizeHandle.BOTTOM_LEFT  -> b.copy(left = b.left + dx, bottom = b.bottom + dy)
                    ResizeHandle.BOTTOM_RIGHT -> b.copy(right = b.right + dx, bottom = b.bottom + dy)
                }
                r.copy(bounds = newBounds.clampedTo(1f, 1f))
            }
        }
    }

    fun renameRoom(id: String, newLabel: String) {
        _rooms.update { it.map { r -> if (r.id == id) r.copy(label = newLabel.trim().ifEmpty { r.type.displayName }) else r } }
        _editingLabelRoomId.value = null
    }

    fun startEditingLabel(id: String) { _editingLabelRoomId.value = id }

    fun deleteRoom(id: String) {
        _rooms.update { it.filter { r -> r.id != id } }
        if (_selectedRoomId.value == id) _selectedRoomId.value = null
    }

    // Place la nouvelle pièce en décalage des existantes
    private fun nextAvailableBounds(): RoomBounds {
        val offset = (_rooms.value.size * 0.05f).coerceAtMost(0.3f)
        return RoomBounds(
            left   = 0.10f + offset,
            top    = 0.10f + offset,
            right  = 0.40f + offset,
            bottom = 0.40f + offset
        )
    }
}

enum class ResizeHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
```

---

## Composable principal — PlanCanvasScreen

```kotlin
@Composable
fun PlanCanvasScreen(
    onPlanReady: (List<CanvasRoom>) -> Unit,
    viewModel: PlanCanvasViewModel = hiltViewModel()
) {
    val rooms           by viewModel.rooms.collectAsStateWithLifecycle()
    val selectedId      by viewModel.selectedRoomId.collectAsStateWithLifecycle()
    val editingLabelId  by viewModel.editingLabelRoomId.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = AppColors.Background,
        contentWindowInsets = WindowInsets.Zero,
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)
            ) {
                RoomTypeChips(onTypeSelected = viewModel::addRoom)
                Button(
                    onClick = { if (rooms.isNotEmpty()) onPlanReady(rooms) },
                    enabled = rooms.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                ) {
                    Text("Continuer", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StepProgressBar(currentStep = 1, totalSteps = 5)
            Spacer(Modifier.height(AppSpacing.MD))
            Text(
                "Dessinez votre logement",
                style = AppType.CardTitle,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(horizontal = AppSpacing.XXL)
            )
            Spacer(Modifier.height(AppSpacing.XS))
            Text(
                "Appuyez sur un type de pièce pour l'ajouter",
                style = AppType.ControlLabel,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(horizontal = AppSpacing.XXL)
            )
            Spacer(Modifier.height(AppSpacing.LG))

            RoomCanvas(
                rooms            = rooms,
                selectedRoomId   = selectedId,
                editingLabelId   = editingLabelId,
                onRoomSelected   = viewModel::selectRoom,
                onRoomMoved      = viewModel::moveRoom,
                onRoomResized    = viewModel::resizeRoom,
                onRoomRenamed    = viewModel::renameRoom,
                onStartEditLabel = viewModel::startEditingLabel,
                onRoomDeleted    = viewModel::deleteRoom,
                modifier         = Modifier
                    .weight(1f)
                    .padding(horizontal = AppSpacing.LG)
                    .background(AppColors.Surface, AppShape.Large)
                    .clip(AppShape.Large)
                    .border(1.dp, AppColors.BorderSoft, AppShape.Large)
            )
        }
    }
}
```

---

## Canvas des pièces — RoomCanvas

```kotlin
@Composable
fun RoomCanvas(
    rooms: List<CanvasRoom>,
    selectedRoomId: String?,
    editingLabelId: String?,
    onRoomSelected: (String?) -> Unit,
    onRoomMoved: (String, Float, Float) -> Unit,
    onRoomResized: (String, ResizeHandle, Float, Float) -> Unit,
    onRoomRenamed: (String, String) -> Unit,
    onStartEditLabel: (String) -> Unit,
    onRoomDeleted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Désélectionner si tap sur fond vide
                    val tappedRoom = rooms.lastOrNull { room ->
                        room.bounds.contains(
                            offset.x / canvasSize.width,
                            offset.y / canvasSize.height
                        )
                    }
                    onRoomSelected(tappedRoom?.id)
                }
            }
    ) {
        // Grille de fond (helper visuel)
        CanvasGrid(modifier = Modifier.fillMaxSize())

        // Pièces
        rooms.forEach { room ->
            RoomBlock(
                room           = room,
                isSelected     = room.id == selectedRoomId,
                isEditingLabel = room.id == editingLabelId,
                canvasSize     = canvasSize,
                onSelect       = { onRoomSelected(room.id) },
                onMove         = { dx, dy -> onRoomMoved(room.id, dx, dy) },
                onResize       = { handle, dx, dy -> onRoomResized(room.id, handle, dx, dy) },
                onRename       = { newLabel -> onRoomRenamed(room.id, newLabel) },
                onStartEdit    = { onStartEditLabel(room.id) },
                onDelete       = { onRoomDeleted(room.id) }
            )
        }

        // Placeholder si canvas vide
        if (rooms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Choisissez un type de pièce ci-dessous",
                    style = AppType.BodyPrimary,
                    color = AppColors.TextMuted
                )
            }
        }
    }
}
```

---

## Bloc pièce — RoomBlock

```kotlin
@Composable
private fun RoomBlock(
    room: CanvasRoom,
    isSelected: Boolean,
    isEditingLabel: Boolean,
    canvasSize: IntSize,
    onSelect: () -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    onResize: (ResizeHandle, Float, Float) -> Unit,
    onRename: (String) -> Unit,
    onStartEdit: () -> Unit,
    onDelete: () -> Unit
) {
    if (canvasSize == IntSize.Zero) return
    val density = LocalDensity.current

    val leftPx   = (room.bounds.left   * canvasSize.width).toInt()
    val topPx    = (room.bounds.top    * canvasSize.height).toInt()
    val widthPx  = ((room.bounds.right  - room.bounds.left) * canvasSize.width).toInt()
    val heightPx = ((room.bounds.bottom - room.bounds.top)  * canvasSize.height).toInt()

    val roomColor = roomTypeColor(room.type)

    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx, topPx) }
            .size(
                width  = with(density) { widthPx.toDp() },
                height = with(density) { heightPx.toDp() }
            )
            .background(
                color = roomColor.copy(alpha = if (isSelected) 0.22f else 0.14f),
                shape = AppShape.Small
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) roomColor else roomColor.copy(alpha = 0.5f),
                shape = AppShape.Small
            )
            .pointerInput(isSelected) {
                if (!isSelected) {
                    detectTapGestures { onSelect() }
                } else {
                    // Glisser pour déplacer
                    detectDragGestures { _, dragAmount ->
                        onMove(
                            dragAmount.x / canvasSize.width,
                            dragAmount.y / canvasSize.height
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Label ou champ de saisie
        if (isEditingLabel) {
            var text by remember { mutableStateOf(room.label) }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = AppType.ControlLabel.copy(color = AppColors.TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onRename(text) }),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(Color.White.copy(alpha = 0.85f), AppShape.Small)
                    .padding(horizontal = AppSpacing.SM, vertical = AppSpacing.XS)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text  = room.label,
                    style = AppType.ControlLabel,
                    color = roomColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onStartEdit() }) }
                )
            }
        }

        // Bouton supprimer (coin haut-droite, visible si sélectionné)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(20.dp)
                    .background(AppColors.SignalPoor, AppShape.Circle)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Supprimer",
                     tint = Color.White, modifier = Modifier.size(12.dp))
            }

            // Poignées de redimensionnement aux 4 coins
            ResizeHandle.entries.forEach { handle ->
                ResizeHandleDot(
                    handle     = handle,
                    roomWidthPx  = widthPx,
                    roomHeightPx = heightPx,
                    onDrag = { dx, dy -> onResize(handle, dx / canvasSize.width, dy / canvasSize.height) }
                )
            }
        }
    }
}

@Composable
private fun ResizeHandleDot(
    handle: ResizeHandle,
    roomWidthPx: Int,
    roomHeightPx: Int,
    onDrag: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val alignX = when (handle) {
        ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> 0f
        else -> 1f
    }
    val alignY = when (handle) {
        ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> 0f
        else -> 1f
    }
    Box(
        modifier = Modifier
            .align(BiasAlignment(alignX * 2 - 1, alignY * 2 - 1))  // top-left=-1/-1, bottom-right=1/1
            .offset(
                x = if (alignX == 0f) (-6).dp else 6.dp,
                y = if (alignY == 0f) (-6).dp else 6.dp
            )
            .size(12.dp)
            .background(AppColors.Accent, AppShape.Circle)
            .border(1.5.dp, Color.White, AppShape.Circle)
            .pointerInput(Unit) {
                detectDragGestures { _, delta -> onDrag(delta.x, delta.y) }
            }
    )
}

private fun roomTypeColor(type: RoomType): Color = when (type) {
    RoomType.SALON   -> Color(0xFF5AC8FA)   // bleu clair
    RoomType.KITCHEN -> Color(0xFFFF9500)   // orange
    RoomType.BEDROOM -> Color(0xFF5E5CE6)   // violet
    RoomType.OFFICE  -> Color(0xFF30B0C7)   // cyan
    RoomType.BATHROOM-> Color(0xFF34C759)   // vert
    RoomType.HALLWAY -> Color(0xFFAEAEB2)   // gris
    RoomType.DINING  -> Color(0xFFFF6B6B)   // rouge rosé
    RoomType.OTHER   -> Color(0xFF8E8E93)   // gris moyen
}
```

---

## Chips types de pièce (barre inférieure)

```kotlin
@Composable
fun RoomTypeChips(onTypeSelected: (RoomType) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = AppSpacing.XXL),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)
    ) {
        items(RoomType.entries) { type ->
            FilterChip(
                selected  = false,
                onClick   = { onTypeSelected(type) },
                label     = { Text(type.displayName, style = AppType.ControlLabel) },
                colors    = FilterChipDefaults.filterChipColors(
                    containerColor         = AppColors.Surface,
                    selectedContainerColor = AppColors.Accent.copy(alpha = 0.12f)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled      = true,
                    selected     = false,
                    borderColor  = AppColors.BorderSoft,
                    borderWidth  = 1.dp
                ),
                shape = AppShape.Pill
            )
        }
    }
}
```

---

## Grille de fond (helper visuel)

```kotlin
@Composable
private fun CanvasGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val step = size.width / 10
        val gridColor = Color(0xFFD1D1D6)
        // Lignes verticales
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
            x += step
        }
        // Lignes horizontales
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
            y += step
        }
    }
}
```

---

## Règles UX spécifiques au canvas

1. **Sélection** : un tap sur une pièce = sélection (bordure + poignées). Tap sur fond = désélection.
2. **Déplacement** : drag sur une pièce sélectionnée. Clamper les bounds à [0, 1].
3. **Redimensionnement** : drag sur les 4 coins. Taille minimum : `0.05f` (5% du canvas).
4. **Renommage** : double-tap sur le label → champ inline. Seul champ texte libre autorisé (règle UX n°2).
5. **Suppression** : bouton ✕ en haut-à-droite de la pièce sélectionnée.
6. **Ajout** : tap sur un chip de type → nouvelle pièce centrée avec décalage progressif.
7. **Couleurs des pièces** : palette neutre (pastel) — distinctes des couleurs signal Wi-Fi.

---

## Conversion ML Kit → CanvasRoom (Option B → même structure)

Quand l'utilisateur choisit l'option Photo, les `DetectedRoom` (ML Kit) sont converties en `CanvasRoom` pour utiliser la même structure de données :

```kotlin
fun DetectedRoom.toCanvasRoom(): CanvasRoom {
    val halfW = approxWidth / 2
    val halfH = approxHeight / 2
    return CanvasRoom(
        type   = inferRoomType(label),
        label  = label,
        bounds = RoomBounds(
            left   = (centerX - halfW).coerceIn(0f, 1f),
            top    = (centerY - halfH).coerceIn(0f, 1f),
            right  = (centerX + halfW).coerceIn(0f, 1f),
            bottom = (centerY + halfH).coerceIn(0f, 1f)
        )
    )
}

private fun inferRoomType(label: String): RoomType {
    val l = label.lowercase()
    return when {
        l.contains("salon") || l.contains("séjour") || l.contains("living") -> RoomType.SALON
        l.contains("cuisine") || l.contains("kitchen")                       -> RoomType.KITCHEN
        l.contains("chambre") || l.contains("bedroom")                       -> RoomType.BEDROOM
        l.contains("bureau") || l.contains("office")                         -> RoomType.OFFICE
        l.contains("bain") || l.contains("wc") || l.contains("toilette")     -> RoomType.BATHROOM
        l.contains("couloir") || l.contains("hall") || l.contains("entrée")  -> RoomType.HALLWAY
        l.contains("manger") || l.contains("dining")                         -> RoomType.DINING
        else                                                                  -> RoomType.OTHER
    }
}
```
