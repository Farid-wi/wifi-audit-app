package com.wifiaudit.app.presentation.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Baseline corps : 17sp (Apple utilise 17, pas 16)
// Tracking serré sur les tailles display (-0.015em ≈ -0.28sp à 56sp)
object AppType {
    val HeroDisplay = TextStyle(
        fontSize      = 56.sp,
        fontWeight    = FontWeight.SemiBold,
        lineHeight    = 60.sp,
        letterSpacing = (-0.28).sp
    )
    val SectionTitle = TextStyle(
        fontSize      = 40.sp,
        fontWeight    = FontWeight.SemiBold,
        lineHeight    = 44.sp,
        letterSpacing = 0.sp
    )
    val CardTitle = TextStyle(
        fontSize      = 28.sp,
        fontWeight    = FontWeight.SemiBold,
        lineHeight    = 32.sp,
        letterSpacing = 0.196.sp
    )
    val BodyPrimary = TextStyle(
        fontSize      = 17.sp,
        fontWeight    = FontWeight.Normal,
        lineHeight    = 25.sp,
        letterSpacing = (-0.374).sp
    )
    val BodyEmphasis = TextStyle(
        fontSize      = 17.sp,
        fontWeight    = FontWeight.SemiBold,
        lineHeight    = 21.sp,
        letterSpacing = (-0.374).sp
    )
    val ControlLabel = TextStyle(
        fontSize      = 14.sp,
        fontWeight    = FontWeight.Normal,
        lineHeight    = 18.sp,
        letterSpacing = (-0.224).sp
    )
    val Micro = TextStyle(
        fontSize      = 12.sp,
        fontWeight    = FontWeight.Normal,
        lineHeight    = 16.sp,
        letterSpacing = (-0.12).sp
    )
}
