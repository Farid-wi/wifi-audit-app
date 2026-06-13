# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Vision produit

Application Android permettant à un particulier non technique de cartographier la couverture Wi-Fi de son logement.
L'utilisateur **construit son plan directement dans l'app** (canvas interactif) ou photographie un croquis dessiné à la main,
place ses équipements, réalise quelques mesures pièce par pièce, et obtient une heatmap par pièce avec des recommandations contextuelles.

Aucun terme technique (RSSI, BSSID, canal, bande) ne doit apparaître dans l'interface utilisateur **par défaut**.
**Exception « mode expert »** : un affichage avancé, opt-in (lié au mode pro/expert, cf. « Mode rapide »), peut révéler les valeurs techniques (dBm, bande) dans la liste réseau, les tooltips de points et les résultats. Le parcours par défaut du particulier reste 100 % non technique (barres + « signal fort/moyen/faible »).

### Deux options pour créer le plan (étape 1)
- **Option A — Canvas interactif** : l'utilisateur ajoute des pièces rectangulaires, les positionne, les redimensionne et sélectionne leur type (salon, cuisine, chambre, bureau, salle de bain, couloir, salle à manger, autre). Option affichée en premier, même poids visuel.
- **Option B — Photo** : l'utilisateur photographie un croquis. Détection des pièces par ML Kit en fallback.

Les deux options aboutissent à la même structure de données : une liste de `CanvasRoom` avec des limites normalisées.

---

## Stack technique

### Application Android
- **Langage** : Kotlin
- **UI** : Jetpack Compose
- **Architecture** : MVVM + Clean Architecture (UI → ViewModel → UseCase → Repository)
- **DI** : Hilt
- **Navigation** : Navigation Compose (stepper linéaire 5 étapes)
- **Réseau** : Retrofit 2 + Gson
- **Base locale** : Room + SQLite
- **Wi-Fi** : `WifiManager` (android.net.wifi)
- **Analyse image** : Google ML Kit (Text Recognition v2)
- **Heatmap** : Canvas Compose + interpolation IDW

### Backend local (MVP)
- **Langage** : Python 3.11+
- **Framework** : FastAPI
- **ORM** : SQLAlchemy 2.0
- **Base** : SQLite (`wifi_audits.db`)
- **Lancement** : `uvicorn main:app --host 0.0.0.0 --port 8000`

---

## Architecture Android — règle de dépendance

```
presentation → domain ← data
```
- `domain` ne dépend de rien (pas d'Android, pas de Retrofit, pas de Room)
- `data` implémente les interfaces du domaine
- `presentation` observe les `StateFlow` des ViewModels

### Ce qu'il ne faut jamais faire (Android)
- Appeler Room ou Retrofit directement depuis un Composable ou ViewModel
- Exposer des types Android (`Context`, `WifiInfo`) dans le domaine
- Afficher des valeurs dBm, BSSID ou canaux dans l'UI
- Lancer des coroutines en dehors de `viewModelScope` dans les ViewModels

---

## Conventions de nommage (Android)

| Élément | Convention | Exemple |
|---|---|---|
| Fichiers Compose | `NomScreen.kt` | `MeasureScreen.kt` |
| ViewModels | `NomViewModel.kt` | `MeasureViewModel.kt` |
| UseCases | `VerbNomUseCase.kt` | `ScanWifiUseCase.kt` |
| Entities Room | `NomEntity.kt` | `AuditEntity.kt` |
| DTOs réseau | `NomDto.kt` / `NomPayload.kt` | `AuditPayload.kt` |
| StateFlow UI | `uiState: StateFlow<NomUiState>` | — |
| Events VM | `fun verbNom(...)` | `fun takeMeasurement(...)` |

---

## Règles de développement

### Qualité du signal — seuils internes (ne jamais afficher à l'utilisateur)
| RSSI (dBm) | Label interne | Couleur heatmap |
|---|---|---|
| ≥ -60 | GOOD | Vert `#1D9E75` |
| -60 à -75 | FAIR | Orange `#EF9F27` |
| < -75 | POOR | Rouge `#E24B4A` |

### Langage utilisateur
- Parcours par défaut — ne jamais utiliser : RSSI, dBm, BSSID, canal, fréquence, MHz, GHz, protocole, latence, ping, passerelle, gateway, SSID
- Utiliser à la place : "signal fort/faible", "connexion lente", "zone mal couverte", "box/routeur", "répéteur/borne", "nom du réseau Wi-Fi"
- **Mode expert (opt-in)** : seul ce mode peut afficher dBm et bande (jamais BSSID/canal en clair). Toujours accompagner la valeur d'un label compréhensible ("signal fort · -58 dBm").

### Interpolation heatmap (IDW) — par pièce
- Algorithme : Inverse Distance Weighting (puissance 2)
- Résolution de la grille : 50×50 (20×20 en temps réel pendant l'audit)
- **Heatmap calculée séparément par pièce** : les mesures d'une pièce n'influencent pas les cellules des pièces voisines
- Les cellules hors des limites de la pièce reçoivent `Float.NaN` (rendu transparent/gris)
- **Pièce sans mesures = gris neutre** (`#E5E5EA`) — jamais colorée par interpolation externe
- **Score global = moyenne des scores pièce pondérée par surface** (`bounds.width × bounds.height`)
- Calcul toujours sur `Dispatchers.Default`, jamais sur le thread UI

### Permissions Android requises
```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.CAMERA"/>
```

### Comportement offline
- Chaque audit est sauvegardé localement (Room) avant tout envoi réseau
- Si le backend local est inaccessible, l'audit reste en statut `PENDING` et est envoyé au prochain lancement
- L'app fonctionne entièrement sans backend (heatmap générée en local)

### Architecture réseau (évolutivité cloud)
- `AuditRepository` est une interface dans le domaine
- `AuditRepositoryImpl` contient Retrofit + Room
- L'URL de base est stockée dans `SharedPreferences` (`pref_server_url`, défaut : `http://192.168.1.1:8000`)
- Migration cloud = changer l'URL uniquement, zéro modification du domaine

---

## Règles de code Python (backend)
- Pydantic v2 : utiliser `model_dump()` et non `dict()`
- SQLAlchemy 2.0 style déclaratif avec `DeclarativeBase`
- Toujours utiliser `Depends(get_db)` pour les sessions — jamais de session globale
- Les routes retournent toujours des types sérialisables (dict ou modèles Pydantic)
- Pas de logique métier dans `main.py` — extraire dans des fonctions séparées si complexe

---

## Règles UX (à ne jamais enfreindre)
1. Jamais plus de 2 actions par écran (une principale, une secondaire max)
2. Jamais de champ texte libre sauf pour saisir le nom d'une pièce manuellement
3. Toujours indiquer la progression (step indicator + compteur de mesures)
4. Toujours avoir un fallback manuel si la détection automatique échoue
5. Les termes techniques restent dans les logs et le JSON — jamais dans l'UI
6. Le bouton d'action principal est toujours en bas avec padding 24dp
7. Minimum 48dp de zone tactile pour tous les éléments interactifs

### Navigation — stepper linéaire 5 étapes
```
[1] Plan  →  [2] Équipements  →  [3] Réseau  →  [4] Mesures  →  [5] Résultats
```
L'étape [1] "Plan" propose les deux options (Canvas / Photo) avec le même poids visuel.

---

## Modèles de domaine clés

### RoomType + CanvasRoom
```kotlin
enum class RoomType(val displayName: String) {
    SALON("Salon"), KITCHEN("Cuisine"), BEDROOM("Chambre"),
    OFFICE("Bureau"), BATHROOM("Salle de bain"), HALLWAY("Couloir"),
    DINING("Salle à manger"), OTHER("Autre")
}

data class RoomBounds(
    val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    val surface: Float get() = (right - left) * (bottom - top)
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

data class CanvasRoom(
    val id: String,           // UUID
    val type: RoomType,
    val label: String,        // personnalisable, défaut = type.displayName
    val bounds: RoomBounds    // normalisé 0..1 sur le canvas
)
```

### Audit
```kotlin
data class Audit(
    val id: String,           // UUID
    val createdAt: Long,
    val ssid: String,
    val planImagePath: String,        // "" si canvas pur (pas de photo)
    val rooms: List<CanvasRoom>,      // toujours présent (canvas ou issu ML Kit)
    val gatewayPosition: Position,
    val repeaterPositions: List<RepeaterPosition>,
    val measurements: List<Measurement>,
    val summary: AuditSummary?
)
```

### Measurement
```kotlin
data class Measurement(
    val id: String,
    val auditId: String,
    val roomId: String?,      // ID de la CanvasRoom contenant ce point (null si hors pièce)
    val x: Float,             // position normalisée 0..1 sur le plan
    val y: Float,
    val rssi: Int,            // dBm
    val bssid: String,
    val channel: Int,
    val band: String,         // "2.4GHz" | "5GHz" | "6GHz"
    val pingGatewayMs: Int,
    val pingInternetMs: Int,
    val neighbors: List<NeighborNetwork>
)
```

### AuditEntity (Room) — statuts
`status` : `"DRAFT"` | `"PENDING"` | `"SYNCED"`

---

## Payload JSON envoyé au backend

```json
{
  "audit_id": "uuid-v4",
  "created_at": "ISO8601",
  "ssid": "string",
  "device_info": { "model": "string", "android_version": "string", "app_version": "string" },
  "plan_image_base64": "string",
  "gateway_position": { "x": 0.35, "y": 0.42 },
  "repeater_positions": [{ "id": "string", "x": 0.72, "y": 0.21 }],
  "rooms": [{
    "id": "uuid", "type": "BEDROOM", "label": "Chambre",
    "bounds": { "left": 0.1, "top": 0.1, "right": 0.45, "bottom": 0.55 }
  }],
  "measurements": [{
    "x": 0.45, "y": 0.30, "room_id": "uuid",
    "rssi": -62, "bssid": "AA:BB:CC:DD:EE:FF",
    "channel": 6, "band": "2.4GHz",
    "ping_gateway_ms": 4, "ping_internet_ms": 18,
    "neighboring_networks": [{ "ssid": "string", "bssid": "string", "rssi": -81, "channel": 11, "band": "2.4GHz" }]
  }]
}
```

---

## Commandes utiles

### Backend local
```bash
cd backend
pip install fastapi uvicorn sqlalchemy pillow python-multipart
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
# Interface Swagger : http://localhost:8000/docs
```

### Android
```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
```

---

## Compétences spécialisées (skills)

Des guides d'implémentation détaillés (patterns de code, exemples complets) sont disponibles dans `.claude/skills/` :
- `android-kotlin.md` — ViewModel, UseCase, Room, Retrofit, IDW par pièce, tap-to-place
- `backend-python.md` — FastAPI routes, SQLAlchemy models, Pydantic schemas, migration cloud
- `heatmap-plan.md` — IDW masqué par pièce, score pondéré surface, rendu Canvas
- `ux-interface.md` — règles UX, vocabulaire, dual-option plan, composants Compose
- `apple-design.md` — tokens Apple traduits en Compose : `AppColors`, `AppType`, `AppShape`, `AppMotion`, `AppSpacing`
- `canvas-plan.md` — canvas interactif : ajout/déplacement/redimensionnement de pièces, RoomType, rendu

### Commandes slash disponibles
- `/new-screen [nom]` — génère un écran Compose complet avec ViewModel et états UI (utilise apple-design)
- `/new-usecase [nom]` — génère un UseCase dans `domain/usecase/`
- `/new-endpoint [méthode] [chemin]` — génère une route FastAPI avec schéma Pydantic
- `/heatmap-debug` — affiche les données de la grille IDW sous forme lisible
- `/check-ux` — vérifie qu'un écran Compose respecte les règles UX et les tokens Apple
- `/apple-design [composant]` — génère ou refactorise un composant avec les tokens Apple

---

## Priorités MVP

1. **Canvas plan builder** — ajout/déplacement/redimensionnement de pièces typées
2. Scan Wi-Fi fonctionnel (WifiManager) + rattachement mesure → pièce
3. Heatmap **par pièce masquée** (pas de débordement inter-pièces) — valeur principale du produit
4. Score global pondéré par surface des pièces
5. Recommandations contextuelles (type de pièce)
6. Export JSON + envoi au backend local

Ce qui peut attendre la V2 : mesh multi-AP avancé, historique des audits dans l'app, analyse ML du plan.
