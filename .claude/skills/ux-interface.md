# Skill : UX & Interface — Wi-Fi Audit

## Principe fondamental
L'utilisateur cible n'a aucune connaissance technique du Wi-Fi.
Chaque écran doit être compréhensible par quelqu'un qui n'a jamais vu un router.

---

## Vocabulaire interdit / autorisé

| Interdit (technique) | Remplacer par |
|---|---|
| RSSI | signal, qualité du signal |
| dBm | (ne jamais afficher) |
| BSSID | (ne jamais afficher) |
| Canal / fréquence | (ne jamais afficher) |
| 2.4 GHz / 5 GHz / 6 GHz | (ne jamais afficher) |
| Latence | rapidité de connexion |
| Ping | réactivité |
| Passerelle / gateway | box, routeur |
| Point d'accès | répéteur, borne |
| SSID | nom du réseau Wi-Fi |
| Interpolation / IDW | (interne uniquement) |

---

## Palette couleurs Wi-Fi

> Les couleurs de surface, texte, accent et motion sont définies dans `apple-design.md`.
> Ce fichier garde uniquement les couleurs sémantiques signal (spécifiques à l'app).

```kotlin
// Couleurs signal — sémantique Wi-Fi audit (ne pas modifier)
object WifiColors {
    val GOOD = Color(0xFF1D9E75)   // vert  — "signal fort"
    val FAIR = Color(0xFFEF9F27)   // orange — "signal moyen"
    val POOR = Color(0xFFE24B4A)   // rouge  — "signal faible"
}

// Couleurs UI — tokens Apple (voir apple-design.md pour la référence complète)
// Utiliser AppColors.* dans les composants plutôt que ces valeurs inline
val Background   = Color(0xFFF5F5F7)   // Pale Apple Gray
val TextPrimary  = Color(0xFF1D1D1F)   // Near-Black Ink (pas #000000)
val TextMuted    = Color(0xFF6E6E73)   // helper copy
val Accent       = Color(0xFF0071E3)   // Apple Action Blue
```

---

## Navigation — stepper linéaire 5 étapes

```
[1] Plan  →  [2] Équipements  →  [3] Réseau  →  [4] Mesures  →  [5] Résultats
```

- Barre de progression en haut (step indicator)
- Pas de retour arrière sauf si explicitement demandé (évite les erreurs)
- Chaque étape a un titre court + une phrase d'instruction maximale
- Bouton d'action principal toujours en bas, pleine largeur

### Composant step indicator
```kotlin
@Composable
fun StepIndicator(current: Int, total: Int = 5, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < current) WifiColors.GOOD else Color(0xFFE0DDD6))
            )
        }
    }
}
```

---

## Écran 1 — Plan du logement (deux options égales)

```
Titre : "Créez votre plan"
```

Deux cartes côte à côte avec le même poids visuel (même taille, même élévation).
**Aucune n'est mise en avant** — l'utilisateur choisit librement.

```kotlin
@Composable
fun PlanOptionPicker(
    onCanvasSelected: () -> Unit,
    onPhotoSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding()
            .padding(AppSpacing.XXL),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.LG)
    ) {
        StepProgressBar(currentStep = 1, totalSteps = 5)
        Spacer(Modifier.height(AppSpacing.XL))
        Text("Créez votre plan", style = AppType.SectionTitle, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpacing.Section))

        // Deux options équivalentes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.LG)
        ) {
            PlanOptionCard(
                title = "Dessiner",
                description = "Créez votre plan directement dans l'app",
                icon = Icons.Outlined.GridView,
                onClick = onCanvasSelected,
                modifier = Modifier.weight(1f)
            )
            PlanOptionCard(
                title = "Photographier",
                description = "Prenez en photo un croquis dessiné à la main",
                icon = Icons.Outlined.PhotoCamera,
                onClick = onPhotoSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlanOptionCard(
    title: String, description: String,
    icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AppColors.Surface, AppShape.Large)
            .border(1.dp, AppColors.BorderSoft, AppShape.Large)
            .clickable(onClick = onClick)
            .padding(AppSpacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(AppColors.Accent.copy(alpha = 0.10f), AppShape.Medium),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.Accent,
                 modifier = Modifier.size(26.dp))
        }
        Text(title, style = AppType.BodyEmphasis, color = AppColors.TextPrimary)
        Text(description, style = AppType.ControlLabel, color = AppColors.TextMuted,
             textAlign = TextAlign.Center)
    }
}
```

### Option A — Canvas interactif
Voir `canvas-plan.md` pour l'implémentation complète (ajout/déplacement/redimensionnement).

Points clés UX :
- Liste horizontale de types de pièce en bas (chips cliquables)
- Appui long sur une pièce = sélection + poignées de redimensionnement
- Double-tap sur le label = renommer inline (seul champ texte libre autorisé)
- Bouton "Ajouter une pièce" en bas de l'écran

### Option B — Photo
- Ouvrir directement l'appareil photo (pas de galerie par défaut)
- Après la photo : afficher la prévisualisation + les pièces détectées en overlay
- Si ML Kit détecte ≥1 pièce → bouton "Ça semble bon, continuer"
- Si ML Kit ne détecte rien → passer directement au canvas avec un message explicatif

---

## Écran 3 — Positionner les équipements

```
Titre : "Où se trouve votre box ?"
Instruction : "Appuyez sur le plan à l'endroit où elle se trouve."
```

- Séquence en 2 temps : 1. Box → 2. "Avez-vous un répéteur ?" (oui/non)
- Si oui → même tap-to-place pour le répéteur
- Icônes : box = routeur, répéteur = antenne

```kotlin
enum class EquipmentType {
    GATEWAY,    // Box Internet
    REPEATER,   // Répéteur Wi-Fi
    MESH_NODE   // Nœud mesh
}

val equipmentIcon = mapOf(
    EquipmentType.GATEWAY   to Icons.Outlined.Router,
    EquipmentType.REPEATER  to Icons.Outlined.SettingsInputAntenna,
    EquipmentType.MESH_NODE to Icons.Outlined.Hub
)

val equipmentLabel = mapOf(
    EquipmentType.GATEWAY   to "Box",
    EquipmentType.REPEATER  to "Répéteur",
    EquipmentType.MESH_NODE to "Borne mesh"
)
```

---

## Écran 4 — Sélection du réseau

```
Titre : "Quel est votre réseau Wi-Fi ?"
Instruction : "Choisissez votre réseau dans la liste."
```

- Liste des SSID visibles (dédupliqués)
- Le réseau actuellement connecté mis en avant (badge "Connecté")
- Masquer les réseaux sans SSID (réseaux cachés)

```kotlin
@Composable
fun NetworkListItem(ssid: String, isConnected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Wifi, contentDescription = null, tint = WifiColors.GOOD)
        Spacer(Modifier.width(12.dp))
        Text(ssid, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (isConnected) {
            Badge(containerColor = WifiColors.GOOD) { Text("Connecté", color = Color.White) }
        }
    }
}
```

---

## Écran 5 — Mesures

```
Titre : "Déplacez-vous dans chaque pièce"
Instruction : "Appuyez sur votre position puis sur 'Mesurer'."
```

- Plan zoomable (pinch-to-zoom)
- Tap sur le plan = point de mesure temporaire (cercle pulsant)
- Bouton FAB "Mesurer ici" en bas
- Pendant la mesure : indicateur de progression (2-3 secondes)
- Point de mesure devient vert/orange/rouge après la mesure
- Compteur : "3 mesures — Minimum recommandé : 5"

```kotlin
@Composable
fun MeasurePointIndicator(quality: SignalQuality, isLoading: Boolean) {
    val color = when (quality) {
        SignalQuality.GOOD -> WifiColors.GOOD
        SignalQuality.FAIR -> WifiColors.FAIR
        SignalQuality.POOR -> WifiColors.POOR
    }
    if (isLoading) {
        // Cercle pulsant
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(1f, 1.4f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse))
        Box(
            modifier = Modifier
                .size((20 * scale).dp)
                .background(Color(0xFF378ADD).copy(alpha = 0.5f), CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}
```

---

## Écran 5 — Résultats

```
Titre : "Votre couverture Wi-Fi"
```

### Layout résultats (mis à jour)
1. **Heatmap par pièce** — chaque pièce colorée indépendamment, pièces sans mesures en gris
2. **Score global** — calculé en pondérant par surface, pas une simple moyenne
3. **Badges par pièce** — colorés + type de pièce en label
4. **Recommandations contextuelles** — tenant compte du type de pièce (bureau, chambre…)
5. Bouton "Envoyer au serveur" + bouton "Nouvel audit"

### Règles d'affichage heatmap
- Pièce avec mesures → heatmap IDW masquée (ne déborde pas sur les voisines)
- Pièce sans mesures → fond gris `#E5E5EA` avec label centré
- Jamais de cellule colorée en dehors des limites d'une pièce déclarée

### Composant badge de pièce
```kotlin
@Composable
fun RoomBadge(roomName: String, quality: SignalQuality) {
    val (bg, text, icon) = when (quality) {
        SignalQuality.GOOD -> Triple(Color(0xFFE1F5EE), Color(0xFF085041), "✓")
        SignalQuality.FAIR -> Triple(Color(0xFFFAEEDA), Color(0xFF633806), "~")
        SignalQuality.POOR -> Triple(Color(0xFFFCEBEB), Color(0xFF791F1F), "✗")
    }
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(icon, color = text, fontWeight = FontWeight.Bold)
        Text(roomName, color = text, style = MaterialTheme.typography.bodyMedium)
    }
}
```

### Recommandations contextuelles — ton et formulation
```kotlin
// Toujours en langage naturel, jamais technique
// Templates enrichis avec le type de pièce
val templates = mapOf(
    // Par type de pièce (POOR)
    "poor_office"         to "Le signal est insuffisant dans votre bureau — votre connexion sera lente.",
    "poor_bedroom"        to "Le signal est faible dans votre chambre, ce qui peut affecter vos appareils la nuit.",
    "poor_hallway"        to "Un couloir mal couvert crée une zone morte entre vos pièces.",
    "poor_room"           to "Le signal est trop faible en {room}. Un répéteur entre {room} et votre box pourrait aider.",
    // Par type de pièce (FAIR)
    "fair_office"         to "Le signal est moyen dans votre bureau. Rapprocher le répéteur améliorerait la connexion.",
    "fair_room"           to "Le signal est moyen en {room}.",
    // Équipements
    "repeater_misplaced"  to "Votre répéteur semble mal positionné. Essayez de le rapprocher de votre box.",
    "gateway_location"    to "Votre box est placée dans un coin. La déplacer vers le centre améliorerait la couverture.",
    "good_overall"        to "La couverture Wi-Fi de votre logement est globalement satisfaisante."
)
```

---

## Règles UX à ne jamais enfreindre

1. **Jamais plus de 2 actions par écran** (une principale, une secondaire max)
2. **Jamais de champ texte libre** sauf pour saisir le nom d'une pièce manuellement
3. **Toujours indiquer la progression** (step indicator + compteur de mesures)
4. **Toujours avoir un fallback manuel** si la détection automatique échoue
5. **Les termes techniques restent dans les logs et le JSON** — jamais dans l'UI
6. **Le bouton d'action principal est toujours en bas** avec padding 24dp
7. **Minimum 48dp de zone tactile** pour tous les éléments interactifs
