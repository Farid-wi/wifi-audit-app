package com.wifiaudit.app.presentation.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape

/**
 * Fond unifié du conteneur de plan (le « héros » de l'app, §2 des recommandations design) :
 * surface légèrement teintée + grille de points subtile, partagée par tous les écrans qui
 * affichent le plan dessiné (dessin, équipements, mesure).
 *
 * @param withBorder ajoute le cadre arrondi + bordure du conteneur (à activer quand le plan n'est
 *                   pas déjà encadré par un parent).
 */
fun Modifier.planBackdrop(
    spacing: Dp = 16.dp,
    withBorder: Boolean = false
): Modifier {
    val base = this
        .background(AppColors.PlanSurface)
        .drawBehind {
            val step = spacing.toPx()
            val radius = 1.dp.toPx()
            if (step <= 0f) return@drawBehind
            var y = step
            while (y < size.height) {
                var x = step
                while (x < size.width) {
                    drawCircle(AppColors.PlanDot, radius = radius, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
    return if (withBorder) base.border(1.dp, AppColors.PlanBorder, AppShape.Large) else base
}
