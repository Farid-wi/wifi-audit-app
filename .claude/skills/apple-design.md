# Skill : Apple Design System — Wi-Fi Audit (Jetpack Compose)

Tokens et règles de design traduits depuis le système Apple (apple.com) pour l'app Android.
Source : `design-systems/apple` du dépôt [nexu-io/open-design](https://github.com/nexu-io/open-design).

---

## Couleurs — `AppColors`

```kotlin
object AppColors {
    // ─── Surfaces ───────────────────────────────────────────────────
    val Background  = Color(0xFFFFFFFF)   // canvas blanc (pages retail)
    val Surface     = Color(0xFFF5F5F7)   // Pale Apple Gray (cards, feature bands)
    val SurfaceWarm = Color(0xFFFBFBFD)   // tier intermédiaire entre blanc et gris

    // ─── Texte (4 niveaux — ne jamais utiliser #000000 pour le corps) ─
    val TextPrimary   = Color(0xFF1D1D1F) // Near-Black Ink
    val TextSecondary = Color(0xFF424245) // Utility Dark Gray
    val TextMuted     = Color(0xFF6E6E73) // helper copy, métadonnées
    val TextMeta      = Color(0xFF86868B) // captions, micro labels

    // ─── Bordures ───────────────────────────────────────────────────
    val Border     = Color(0xFFD2D2D7)    // dividers, contours de cards
    val BorderSoft = Color(0xFFE8E8ED)    // séparateurs internes denses

    // ─── Accent (UNE seule couleur chromatique — règle Apple stricte) ─
    val Accent       = Color(0xFF0071E3)  // Apple Action Blue
    val AccentHover  = Color(0xFF0077ED)  // légèrement plus lumineux (pas plus sombre)
    val AccentActive = Color(0xFF0066CC)  // Body Link Blue = press tone
    val OnAccent     = Color(0xFFFFFFFF)

    // ─── Signal Wi-Fi (sémantique app, inchangé) ────────────────────
    val SignalGood = Color(0xFF1D9E75)
    val SignalFair = Color(0xFFEF9F27)
    val SignalPoor = Color(0xFFE24B4A)

    // ─── Dark chapter (écrans immersifs / heatmap plein écran) ──────
    val DarkCanvas  = Color(0xFF000000)
    val DarkSurface = Color(0xFF272729)
}
```

**Règles couleur :**
- Jamais plus d'un accent chromatique visible par écran
- Le bleu accent (`Accent`) est réservé aux actions et liens — jamais décoratif
- `DarkCanvas` uniquement pour les chapitres visuels immersifs (ex. plein écran heatmap)

---

## Typographie — `AppType`

Apple utilise 17sp comme baseline corps (pas 16sp). Le tracking se resserre sur les tailles display.

```kotlin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppType {
    val HeroDisplay = TextStyle(
        fontSize = 56.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 60.sp,          // ≈ 1.07
        letterSpacing = (-0.28).sp
    )
    val SectionTitle = TextStyle(
        fontSize = 40.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 44.sp,          // ≈ 1.10
        letterSpacing = 0.sp
    )
    val CardTitle = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp,          // ≈ 1.14
        letterSpacing = 0.196.sp
    )
    val BodyPrimary = TextStyle(
        fontSize = 17.sp,            // baseline Apple — pas 16
        fontWeight = FontWeight.Normal,
        lineHeight = 25.sp,          // ≈ 1.47 (aéré)
        letterSpacing = (-0.374).sp
    )
    val BodyEmphasis = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 21.sp,
        letterSpacing = (-0.374).sp
    )
    val ControlLabel = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,          // ≈ 1.29
        letterSpacing = (-0.224).sp
    )
    val Micro = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,          // ≈ 1.33
        letterSpacing = (-0.12).sp
    )
}
```

---

## Formes — `AppShape`

Apple utilise des tiers de radius intentionnels — jamais une valeur unique pour tout.

```kotlin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp

object AppShape {
    val Small  = RoundedCornerShape(8.dp)   // champs, contrôles compacts
    val Medium = RoundedCornerShape(12.dp)  // boutons, chips, configurateur
    val Large  = RoundedCornerShape(18.dp)  // cards, panels, modales
    val Pill   = RoundedCornerShape(980.dp) // CTA capsule (signature Apple)
    val Circle = CircleShape               // sélecteurs, points de mesure
}
```

---

## Élévation

Apple préfère le contraste tonal aux ombres lourdes. Trois niveaux seulement :

```kotlin
// Flat  → aucune ombre (la majorité des surfaces)
// Ring  → bordure 1dp Color(0xFFD2D2D7) — containment dense
// Raised → ombre douce pour les cards mises en avant
val CardShadow = listOf(
    Shadow(
        color = Color(0x14000000),  // rgba(0,0,0,0.08)
        offset = Offset(0f, 12f),
        blurRadius = 32f
    )
)
// Jamais de shadow > 0.22 alpha — effet Material, pas Apple
```

---

## Motion

Apple décélère dur et s'arrête net — jamais de rebond.

```kotlin
import androidx.compose.animation.core.*

object AppMotion {
    // cubic-bezier(0.28, 0, 0.22, 1) → FastOutSlowIn est la meilleure
    // approximation disponible nativement en Compose
    val Easing = FastOutSlowInEasing

    const val Fast = 150    // ms — hover, focus, press micro-feedback
    const val Base = 220    // ms — transitions de composants
    const val Slow = 400    // ms — transitions d'écran, reveals

    fun <T> spec(durationMs: Int = Base) = tween<T>(
        durationMs = durationMs,
        easing = Easing
    )
}
```

**Règles motion :**
- Animer exclusivement via `alpha` et `graphicsLayer { … }` (translate/scale/rotate)
- Jamais animer `size`, `padding`, ou `offset` — déclenche des reflows
- Les éléments entrants : `alpha 0→1` + `translationY 12dp→0dp` sur `AppMotion.Base`

---

## Espacement — `AppSpacing`

Grille 8dp. Sections généreuses (Apple respire beaucoup).

```kotlin
object AppSpacing {
    val XS  = 4.dp
    val SM  = 8.dp
    val MD  = 12.dp
    val LG  = 16.dp
    val XL  = 20.dp
    val XXL = 24.dp
    val Section = 40.dp   // padding vertical entre sections (mobile)
}
```

---

## Composants types

### Bouton primaire (capsule)
```kotlin
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = AppShape.Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent,
            contentColor = AppColors.OnAccent
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
    ) {
        Text(text, style = AppType.BodyEmphasis)
    }
}
```

### Card standard
```kotlin
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = AppShape.Large,
        color = AppColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.XXL), content = content)
    }
}
```

### Badge de pièce (résultats)
```kotlin
@Composable
fun RoomBadge(roomName: String, quality: SignalQuality) {
    val (bg, textColor, symbol) = when (quality) {
        SignalQuality.GOOD -> Triple(Color(0xFFE1F5EE), Color(0xFF085041), "✓")
        SignalQuality.FAIR -> Triple(Color(0xFFFAEEDA), Color(0xFF633806), "~")
        SignalQuality.POOR -> Triple(Color(0xFFFCEBEB), Color(0xFF791F1F), "✗")
    }
    Row(
        modifier = Modifier
            .background(bg, AppShape.Medium)
            .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(symbol, color = textColor, fontWeight = FontWeight.Bold, style = AppType.BodyEmphasis)
        Text(roomName, color = textColor, style = AppType.BodyPrimary)
    }
}
```

---

## Anti-patterns à éviter

| Interdit | Raison |
|---|---|
| Ombres > `rgba(0,0,0,0.22)` | Effet Material, pas Apple |
| Plusieurs couleurs d'accent | Rompt la hiérarchie — 1 seul bleu |
| `borderRadius` unique pour tout | Apple utilise des tiers intentionnels |
| Gradients dans le chrome UI | Décoratif sans raison produit |
| `#000000` pour le texte corps | Utiliser `#1d1d1f` (Near-Black Ink) |
| Transitions `linear` ou `ease-in-out` | Utiliser `FastOutSlowInEasing` |
| Animer `padding` / `size` / `offset` | Déclenche des reflows — `graphicsLayer` seulement |
