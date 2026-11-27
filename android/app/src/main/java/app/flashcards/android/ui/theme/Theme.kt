package app.flashcards.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.flashcards.android.R
import com.clerk.api.ui.ClerkColors
import com.clerk.api.ui.ClerkTheme

@Immutable
data class BrainrotColors(
    val bg: Color,
    val ink: Color,
    val muted: Color,
    val card: Color,
    val line: Color,
    val again: Color,
    val hard: Color,
    val good: Color,
    val easy: Color,
    val onGrade: Color,
    val shadowNear: Color,
    val shadowFar: Color,
)

val LightColors = BrainrotColors(
    bg = Color(0xFFF4F1EA), ink = Color(0xFF1D1B18), muted = Color(0xFF8A847A), card = Color(0xFFFFFDF8),
    line = Color(0xFFE4DED2), again = Color(0xFFB4553F), hard = Color(0xFFA2623F), good = Color(0xFF437C77),
    easy = Color(0xFF2F6B78), onGrade = Color.White,
    shadowNear = Color(0f, 0f, 0f, 0.06f), shadowFar = Color(60, 40, 10).copy(alpha = 0.10f),
)

val DarkColors = BrainrotColors(
    bg = Color(0xFF15140F), ink = Color(0xFFEFEBE2), muted = Color(0xFF8F897D), card = Color(0xFF211F1A),
    line = Color(0xFF34312A), again = Color(0xFFD9806A), hard = Color(0xFFE0AA88), good = Color(0xFF8FC0BA),
    easy = Color(0xFF62A0AD), onGrade = Color(0xFF15140F),
    shadowNear = Color(0f, 0f, 0f, 0.40f), shadowFar = Color(0f, 0f, 0f, 0.45f),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold))

private val LocalColors = staticCompositionLocalOf { LightColors }

object Brainrot {
    val colors: BrainrotColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
}

@Composable
fun BrainrotTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalColors provides if (dark) DarkColors else LightColors, content = content)
}

/** The CSS two-layer --shadow: 0 1px 2px, 0 12px 32px. */
fun Modifier.cardShadow(colors: BrainrotColors, radius: Dp): Modifier = drawBehind {
    val r = radius.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply { color = Color.Transparent.toArgb() }
        paint.setShadowLayer(16.dp.toPx(), 0f, 12.dp.toPx(), colors.shadowFar.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        paint.setShadowLayer(1.dp.toPx(), 0f, 1.dp.toPx(), colors.shadowNear.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
}

fun BrainrotColors.toClerkTheme() = ClerkTheme(
    colors = ClerkColors(
        primary = good, background = bg, input = card, danger = again, success = good, warning = hard,
        foreground = ink, mutedForeground = muted, primaryForeground = onGrade, inputForeground = ink,
        neutral = line, border = line, ring = good, muted = bg,
        secondaryButtonBackground = card, secondaryButtonForeground = ink, shadow = shadowFar,
    ),
)
