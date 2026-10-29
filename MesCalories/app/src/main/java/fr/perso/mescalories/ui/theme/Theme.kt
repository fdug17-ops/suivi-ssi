package fr.perso.mescalories.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F0B0),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    secondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFF38656A),
    background = Color(0xFFF7FBF4),
    surface = Color(0xFFF7FBF4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DD495),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF15521A),
    onPrimaryContainer = Color(0xFFB8F0B0),
    secondary = Color(0xFFB9CCB4),
    secondaryContainer = Color(0xFF3B4B38),
    tertiary = Color(0xFFA0CFD4),
    background = Color(0xFF101410),
    surface = Color(0xFF101410),
)

/** Couleurs fixes des macronutriments (identiques partout dans l'appli). */
object MacroColors {
    val carbs = Color(0xFFF2A93B)
    val protein = Color(0xFFE5534B)
    val fat = Color(0xFF4A90D9)
    val water = Color(0xFF3BA7E0)
}

@Composable
fun MesCaloriesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
