package com.wifiaudit.app.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Tiers intentionnels — ne jamais utiliser une valeur unique pour tout
object AppShape {
    val Small  = RoundedCornerShape(8.dp)   // chips, badges, contrôles compacts
    val Medium = RoundedCornerShape(12.dp)  // boutons, inputs, pièces du plan
    val Large  = RoundedCornerShape(16.dp)  // cards, conteneur du plan, panels
    val Pill   = RoundedCornerShape(980.dp) // CTA capsule, FAB — signature Apple
    val Circle = CircleShape
}
