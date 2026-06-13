package com.wifiaudit.app.presentation.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    // Fond d'écran global gris très clair — jamais de blanc pur en fond de page.
    val Background   = Color(0xFFF7F8FA)
    // Cartes, modals, bottom sheets : blanc, posés sur le fond gris pour ressortir.
    val Surface      = Color(0xFFFFFFFF)
    // Zones neutres (placeholders, conteneur de plan, chips non sélectionnées).
    val SurfaceAlt   = Color(0xFFEFF1F4)
    val SurfaceWarm  = Color(0xFFFBFBFD)

    val TextPrimary   = Color(0xFF1D1D1F)
    val TextSecondary = Color(0xFF424245)
    val TextMuted     = Color(0xFF6E6E73)
    val TextMeta      = Color(0xFF86868B)

    val Border     = Color(0xFFD2D2D7)
    val BorderSoft = Color(0xFFE8E8ED)

    val Accent       = Color(0xFF0071E3)
    val AccentHover  = Color(0xFF0077ED)
    val AccentActive = Color(0xFF0066CC)
    val OnAccent     = Color(0xFFFFFFFF)

    // Couleurs signal Wi-Fi — sémantique app uniquement
    val SignalGood = Color(0xFF1D9E75)
    val SignalFair = Color(0xFFEF9F27)
    val SignalPoor = Color(0xFFE24B4A)

    val DarkCanvas  = Color(0xFF000000)
    val DarkSurface = Color(0xFF272729)
}
