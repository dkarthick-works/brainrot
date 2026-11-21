package app.flashcards.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.flashcards.android.ui.theme.Brainrot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class SwipeAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

private val ActionWidth = 64.dp
private val SwipeEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)

/** List row that slides left to reveal icon actions. The parent owns which row is open. */
@Composable
fun SwipeRow(
    actions: List<SwipeAction>,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    editing: Boolean = false,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    verticalPadding: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Brainrot.colors
    val density = LocalDensity.current
    val widthPx = with(density) { (ActionWidth * actions.size).toPx() }
    val overshootPx = with(density) { 24.dp.toPx() }
    val edgePx = with(density) { 3.dp.toPx() }
    val offset = remember { Animatable(if (open) -widthPx else 0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestOpenChange by rememberUpdatedState(onOpenChange)
    val reduced = reducedMotion()

    suspend fun settle(target: Float) {
        if (reduced) offset.snapTo(target) else offset.animateTo(target, tween(250, easing = SwipeEasing))
    }

    LaunchedEffect(open, widthPx) {
        if (!dragging) settle(if (open) -widthPx else 0f)
    }

    Box {
        // Screen readers get these as custom actions on the row instead
        Row(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.End,
        ) {
            actions.forEach { action ->
                Box(
                    Modifier
                        .width(ActionWidth)
                        .fillMaxHeight()
                        .background(if (action.danger) c.again else c.good)
                        .plainClick {
                            onOpenChange(false)
                            action.onClick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BrainrotIcon(action.icon, c.onGrade, size = 22.dp)
                }
            }
        }
        Row(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(c.card)
                .drawWithContent {
                    drawContent()
                    if (editing) drawRect(c.good, size = Size(edgePx, size.height))
                }
                .semantics {
                    if (actions.isNotEmpty()) {
                        customActions = actions.map { action ->
                            CustomAccessibilityAction(action.label) {
                                onOpenChange(false)
                                action.onClick()
                                true
                            }
                        }
                    }
                }
                .pointerInput(widthPx) {
                    if (widthPx == 0f) return@pointerInput
                    var x = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            x = offset.value
                        },
                        onDragEnd = {
                            dragging = false
                            val shouldOpen = x < -widthPx / 2
                            latestOpenChange(shouldOpen)
                            scope.launch { settle(if (shouldOpen) -widthPx else 0f) }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { settle(if (x < -widthPx / 2) -widthPx else 0f) }
                        },
                    ) { change, delta ->
                        change.consume()
                        x = (x + delta).coerceIn(-widthPx - overshootPx, 0f)
                        scope.launch { offset.snapTo(x) }
                    }
                }
                .plainClick(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}
