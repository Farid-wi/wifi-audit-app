package com.wifiaudit.app.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Tiers intentionnels — ne jamais utiliser une valeur unique pour tout
object AppShape {
    val Small  = RoundedCornerShape(8.dp)   // champs, contrôles compacts
    val Medium = RoundedCornerShape(12.dp)  // boutons, chips
    val Large  = RoundedCornerShape(18.dp)  // cards, panels
    val Pill   = RoundedCornerShape(980.dp) // CTA capsule — signature Apple
    val Circle = CircleShape
}
