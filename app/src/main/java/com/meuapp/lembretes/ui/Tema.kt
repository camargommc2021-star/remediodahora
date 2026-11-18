package com.meuapp.lembretes.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.lembretes.R

// Bitter (serifa de bloco, lembra rótulo de farmácia) para horários e títulos.
// Fira Sans (humanista, muito legível) para o resto.
val Bitter = FontFamily(
    Font(R.font.bitter_medium, FontWeight.Medium),
    Font(R.font.bitter_bold, FontWeight.Bold)
)
val Fira = FontFamily(
    Font(R.font.fira_regular, FontWeight.Normal),
    Font(R.font.fira_medium, FontWeight.Medium),
    Font(R.font.fira_semibold, FontWeight.SemiBold)
)

/** Cores além do esquema do Material. */
@Immutable
data class Extras(
    val folha: Color,       // dose tomada
    val sobreFolha: Color,
    val folhaSuave: Color,
    val ambar: Color,       // próxima / atrasada
    val ambarSuave: Color,
    val apagado: Color,     // texto secundário
    val linha: Color,       // divisórias e cápsulas vazias
    val papel: Color        // superfícies agrupadas
)

private val ExtrasClaro = Extras(
    folha = Color(0xFF2F7D5B),
    sobreFolha = Color.White,
    folhaSuave = Color(0xFFDCEDE3),
    ambar = Color(0xFFB8740C),
    ambarSuave = Color(0xFFF7E7C6),
    apagado = Color(0xFF64776F),
    linha = Color(0xFFDCE3E0),
    papel = Color.White
)

private val ExtrasEscuro = Extras(
    folha = Color(0xFF6CC398),
    sobreFolha = Color(0xFF0F1C1A),
    folhaSuave = Color(0xFF1F3D33),
    ambar = Color(0xFFF0B451),
    ambarSuave = Color(0xFF3B2F17),
    apagado = Color(0xFF93A6A1),
    linha = Color(0xFF26403B),
    papel = Color(0xFF172925)
)

val LocalExtras = staticCompositionLocalOf { ExtrasClaro }

private val Claro = lightColorScheme(
    primary = Color(0xFF183A37),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEDE3),
    onPrimaryContainer = Color(0xFF183A37),
    secondary = Color(0xFF2F7D5B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEDE3),
    onSecondaryContainer = Color(0xFF183A37),
    background = Color(0xFFEEF2EF),
    onBackground = Color(0xFF183A37),
    surface = Color(0xFFEEF2EF),
    onSurface = Color(0xFF183A37),
    surfaceVariant = Color(0xFFDCE3E0),
    onSurfaceVariant = Color(0xFF64776F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F8F6),
    surfaceContainer = Color(0xFFEEF2EF),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFFB4C3BE),
    outlineVariant = Color(0xFFDCE3E0),
    error = Color(0xFFB3261E)
)

private val Escuro = darkColorScheme(
    primary = Color(0xFFCFE7DC),
    onPrimary = Color(0xFF0F1C1A),
    primaryContainer = Color(0xFF1F3D33),
    onPrimaryContainer = Color(0xFFE4ECE9),
    secondary = Color(0xFF6CC398),
    onSecondary = Color(0xFF0F1C1A),
    secondaryContainer = Color(0xFF1F3D33),
    onSecondaryContainer = Color(0xFFE4ECE9),
    background = Color(0xFF0F1C1A),
    onBackground = Color(0xFFE4ECE9),
    surface = Color(0xFF0F1C1A),
    onSurface = Color(0xFFE4ECE9),
    surfaceVariant = Color(0xFF26403B),
    onSurfaceVariant = Color(0xFF93A6A1),
    surfaceContainerLowest = Color(0xFF172925),
    surfaceContainerLow = Color(0xFF14241F),
    surfaceContainer = Color(0xFF0F1C1A),
    surfaceContainerHigh = Color(0xFF1B302B),
    surfaceContainerHighest = Color(0xFF1B302B),
    outline = Color(0xFF45605A),
    outlineVariant = Color(0xFF26403B),
    error = Color(0xFFF2B8B5)
)

private val Tipos = Typography(
    displaySmall = TextStyle(
        fontFamily = Bitter, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(fontFamily = Bitter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Bitter, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Fira, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Fira, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Fira, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Fira, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Fira, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Fira, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Fira, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Fira, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)

/** Horário grande, com algarismos de largura fixa para alinhar em coluna. */
val EstiloHora = TextStyle(
    fontFamily = Bitter, fontWeight = FontWeight.Medium,
    fontSize = 22.sp, lineHeight = 26.sp, fontFeatureSettings = "tnum"
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun Tema(conteudo: @Composable () -> Unit) {
    val escuro = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtras provides if (escuro) ExtrasEscuro else ExtrasClaro) {
        MaterialTheme(
            colorScheme = if (escuro) Escuro else Claro,
            typography = Tipos,
            shapes = Formas,
            content = conteudo
        )
    }
}
