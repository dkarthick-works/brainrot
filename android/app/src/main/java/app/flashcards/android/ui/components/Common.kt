package app.flashcards.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flashcards.android.data.ThemeMode
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.Inter
import app.flashcards.android.ui.theme.SpaceGrotesk

@Composable
fun BText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Brainrot.colors.ink,
    size: TextUnit = 16.sp,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Inter,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(text, modifier, TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = family, textAlign = align))
}

fun Modifier.plainClick(role: Role = Role.Button, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, role = role, onClick = onClick)

@Composable
fun TopBar(title: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brainrot.colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .height(68.dp),
        contentAlignment = Alignment.Center,
    ) {
        BText(title, size = 17.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
fun TealButton(label: String, onClick: () -> Unit) {
    val c = Brainrot.colors
    Box(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.good)
            .plainClick(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        BText(label, color = c.onGrade, size = 15.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
fun BrainError(detail: String, onRetry: () -> Unit) {
    val c = Brainrot.colors
    Column(
        Modifier
            .padding(horizontal = 32.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BText("Brain.exe stopped working", Modifier.semantics { heading() }, size = 28.sp, weight = FontWeight.Bold, family = SpaceGrotesk, align = TextAlign.Center)
        BText(detail, color = c.muted, align = TextAlign.Center)
        TealButton("Try again", onRetry)
    }
}

@Composable
fun ThemeSegmented(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().height(44.dp).clip(shape).background(c.card).border(1.dp, c.line, shape)) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            val active = mode == selected
            if (index > 0) {
                val touchesActive = active || ThemeMode.entries[index - 1] == selected
                Box(Modifier.width(1.dp).fillMaxHeight().background(if (touchesActive) Color.Transparent else c.line))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (active) c.good else Color.Transparent)
                    .semantics { this.selected = active }
                    .plainClick(Role.RadioButton) { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                BText(
                    mode.label,
                    color = if (active) c.onGrade else c.ink,
                    size = 15.sp,
                    weight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}
