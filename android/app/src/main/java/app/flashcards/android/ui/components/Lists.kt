package app.flashcards.android.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.flashcards.android.data.FailedSave
import app.flashcards.android.data.SaveNotice
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.Inter
import app.flashcards.android.ui.theme.SpaceGrotesk
import app.flashcards.android.ui.theme.cardShadow
import kotlinx.coroutines.delay

private val ListWidth = 480.dp
private val ListRadius = 18.dp
private val SkeletonWidths = listOf(0.52f, 0.38f, 0.64f, 0.45f, 0.58f)

/** One row of the rounded grouped list, drawn so rows in a lazy list look like a single card. */
@Composable
fun Modifier.groupedRow(first: Boolean, last: Boolean): Modifier {
    val c = Brainrot.colors
    val top = if (first) ListRadius else 0.dp
    val bottom = if (last) ListRadius else 0.dp
    return widthIn(max = ListWidth)
        .fillMaxWidth()
        .clip(RoundedCornerShape(top, top, bottom, bottom))
        .background(c.card)
        .drawWithContent {
            drawContent()
            val line = 1.dp.toPx()
            val radius = ListRadius.toPx()
            // Middle rows extend the outline past their edges, leaving only the side borders
            val y0 = if (first) line / 2 else -radius * 2
            val y1 = if (last) size.height - line / 2 else size.height + radius * 2
            drawRoundRect(c.line, Offset(line / 2, y0), Size(size.width - line, y1 - y0), CornerRadius(radius), style = Stroke(line))
            if (!first) drawLine(c.line, Offset(0f, line / 2), Offset(size.width, line / 2), line)
        }
}

/** Nothing for 300ms, then a muted message and five pulsing placeholder rows. */
@Composable
fun ListLoading(horizontalPadding: Dp = 16.dp) {
    val message = remember { LoadingMessages.random() }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }
    if (!visible) return

    val c = Brainrot.colors
    val still = reducedMotion()
    val pulse = if (still) {
        null
    } else {
        rememberInfiniteTransition(label = "rows").animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = InfiniteRepeatableSpec(tween(800, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse",
        )
    }
    Column(Modifier.widthIn(max = ListWidth).fillMaxWidth().padding(horizontal = horizontalPadding)) {
        BText(
            message,
            Modifier
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = c.muted,
            size = 14.sp,
            weight = FontWeight.Medium,
        )
        Column(Modifier.clearAndSetSemantics {}) {
            SkeletonWidths.forEachIndexed { index, width ->
                Row(
                    Modifier
                        .groupedRow(index == 0, index == SkeletonWidths.lastIndex)
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val bar = Modifier
                        .height(12.dp)
                        .graphicsLayer { alpha = pulse?.value ?: 1f }
                        .background(c.line, RoundedCornerShape(6.dp))
                    Box(bar.then(Modifier.fillMaxWidth(width)))
                    Box(bar.then(Modifier.width(48.dp)))
                }
            }
        }
    }
}

/** A list couldn't load. Sits in the list's place, so the top bar and composer stay usable. */
@Composable
fun ListError(detail: String, onRetry: () -> Unit, horizontalPadding: Dp = 16.dp) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(ListRadius)
    Box(Modifier.widthIn(max = ListWidth).fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.card)
                .border(1.dp, c.line, shape)
                .padding(horizontal = 16.dp, vertical = 48.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BText("Brain.exe stopped working", Modifier.semantics { heading() }, size = 22.sp, weight = FontWeight.Bold, family = SpaceGrotesk, align = TextAlign.Center)
            BText(detail, Modifier.padding(bottom = 10.dp), color = c.muted, size = 15.sp, align = TextAlign.Center)
            TealButton("Try again", onRetry)
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .widthIn(max = ListWidth)
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(c.card)
            .border(1.dp, if (focused) c.good else c.line, shape)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrainrotIcon(BrainrotIcons.Search, c.muted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            textStyle = TextStyle(color = c.ink, fontFamily = Inter, fontSize = 16.sp),
            singleLine = true,
            cursorBrush = SolidColor(c.good),
            interactionSource = interaction,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BText(placeholder, color = c.muted)
                    inner()
                }
            },
        )
    }
}

/** Centered confirmation dialog. System back and tapping outside dismiss it. */
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    body: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(22.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0.35f) }
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .cardShadow(c, 22.dp)
                .clip(shape)
                .background(c.card)
                .border(1.dp, c.line, shape)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BText(
                title,
                Modifier.padding(bottom = 6.dp).semantics { heading() },
                size = 18.sp,
                weight = FontWeight.SemiBold,
                align = TextAlign.Center,
            )
            BText(message, Modifier.padding(bottom = 20.dp), color = c.muted, size = 15.sp, align = TextAlign.Center)
            body?.invoke()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
fun RowScope.SheetButton(
    label: String,
    danger: Boolean = false,
    primary: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(12.dp)
    val fill = if (danger) c.again else if (primary) c.good else c.bg
    val text = if (danger || primary) c.onGrade else if (accent) c.good else c.ink
    Box(
        Modifier
            .weight(1f)
            .height(44.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (danger || primary) fill else c.line, shape)
            .plainClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BText(label, color = text, size = 15.sp, weight = FontWeight.SemiBold)
    }
}

/** "Couldn't save" banner above the bottom bar. Clears itself after 8s. */
@Composable
fun SaveErrorBanner(notice: SaveNotice, modifier: Modifier = Modifier) {
    val current by notice.current.collectAsState()
    val save: FailedSave = current ?: return
    LaunchedEffect(save) {
        delay(8_000)
        notice.dismiss(save)
    }
    val c = Brainrot.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .cardShadow(c, 14.dp)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .drawWithContent {
                drawContent()
                drawRect(c.again, size = Size(4.dp.toPx(), size.height))
            }
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BText("Couldn't save. Your change was undone.", Modifier.weight(1f), size = 14.sp)
        BText(
            "Try again",
            Modifier
                .plainClick {
                    notice.clear()
                    save.retry()
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            color = c.good,
            size = 14.sp,
            weight = FontWeight.SemiBold,
        )
        Box(
            Modifier
                .semantics { contentDescription = "Dismiss" }
                .plainClick { notice.dismiss(save) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            BrainrotIcon(BrainrotIcons.Close, c.muted, size = 16.dp)
        }
    }
}
