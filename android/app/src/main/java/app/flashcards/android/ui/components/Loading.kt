package app.flashcards.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.SpaceGrotesk
import kotlinx.coroutines.delay

val LoadingMessages = listOf(
    "Warming up the neurons...",
    "Brain cells assembling...",
    "Neuron activation in progress...",
    "The brain is braining...",
    "Loading intelligence DLC...",
    "Acquiring brain wrinkles...",
    "Upgrading from NPC mode...",
    "Big brain energy incoming...",
    "One brain cell at a time...",
    "Downloading common sense...",
    "Cooking up some knowledge...",
    "Let him cook...",
    "Chat, are we cooked?",
    "Hold up, the neurons are cooking...",
    "It's giving intelligence...",
    "Thinking thoughts... allegedly.",
    "Brainrot.exe is running...",
    "Summoning the last brain cell...",
    "The neurons are having a meeting...",
    "Currently fighting the forgetting allegations...",
    "99% loading, 1% brain cells...",
    "Garbage collecting bad thoughts...",
    "Compiling brain cells...",
    "Cache miss. Fetching knowledge...",
    "SELECT * FROM brain...",
    "Rebuilding neural indexes...",
    "Waiting for the brain API...",
    "Resolving memory dependencies...",
    "Initializing cognitive services...",
    "A thought is forming...",
    "Making room for knowledge...",
    "Connecting the dots...",
    "Give it a thought...",
    "Something's clicking...",
    "Getting smarter...",
    "Almost a genius...",
    "Putting thoughts together...",
)

@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** App start and Play: nothing for 300ms, then a pulsing card outline and a random message. */
@Composable
fun FullLoader() {
    val message = remember { LoadingMessages.random() }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }
    if (!visible) return

    val colors = Brainrot.colors
    val still = reducedMotion()
    val pulse = if (still) {
        null
    } else {
        rememberInfiniteTransition(label = "loader").animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = InfiniteRepeatableSpec(tween(800, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse",
        )
    }
    Column(
        Modifier.widthIn(max = 320.dp).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Canvas(Modifier.size(150.dp, 214.dp).graphicsLayer { alpha = pulse?.value ?: 1f }) {
            val w = 150.dp.toPx()
            val h = 200.dp.toPx()
            val radius = CornerRadius(20.dp.toPx())
            val stroke = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            val backW = w * 0.92f
            val backH = h * 0.92f
            drawRoundRect(colors.line, Offset((w - backW) / 2, (h - backH) / 2 + 14.dp.toPx()), Size(backW, backH), radius, stroke)
            drawRoundRect(colors.bg, Offset.Zero, Size(w, h), radius)
            drawRoundRect(colors.line, Offset.Zero, Size(w, h), radius, stroke)
        }
        BText(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, size = 24.sp, weight = FontWeight.Bold, family = SpaceGrotesk, align = TextAlign.Center)
    }
}
