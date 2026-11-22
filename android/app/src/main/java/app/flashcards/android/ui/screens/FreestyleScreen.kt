package app.flashcards.android.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.flashcards.android.AppContainer
import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.apiCall
import app.flashcards.android.data.FreestyleController
import app.flashcards.android.logic.FreestyleLoad
import app.flashcards.android.logic.FreestyleScreenState
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainError
import app.flashcards.android.ui.components.FullLoader
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.components.reducedMotion
import app.flashcards.android.ui.theme.Brainrot
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

class FreestyleViewModel(container: AppContainer) : ViewModel() {
    val controller = FreestyleController(
        fetchPage = { cursor -> apiCall { container.api.cards(after = cursor) } },
        fetchTopics = { apiCall { container.api.topics() } },
        scope = viewModelScope,
    ).also { it.start() }
}

private val FlipEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)

/** The ViewModel lives with this route, so leaving discards the pass and the drawer keeps it. */
@Composable
fun FreestyleScreen(container: AppContainer, suspended: Boolean, onExit: () -> Unit, onAddCards: () -> Unit) {
    val vm = viewModel { FreestyleViewModel(container) }
    val controller = vm.controller
    val state by controller.state.collectAsState()
    val topicNames by controller.topicNames.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .then(if (suspended) Modifier.clearAndSetSemantics {} else Modifier)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
    ) {
        FreestyleHeader(onExit)
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state.screen) {
                FreestyleScreenState.Playing -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FreestyleStack(
                        queue = state.queue,
                        topicNames = topicNames,
                        suspended = suspended,
                        onAdvance = controller::advance,
                        modifier = Modifier.weight(1f),
                    )
                    BText(
                        "Card ${state.advancedCount + 1}",
                        Modifier.padding(top = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = Brainrot.colors.muted,
                        size = 13.sp,
                    )
                    if (state.load == FreestyleLoad.Error) ReadNotice(state.error, controller::retry)
                }
                FreestyleScreenState.Error -> BrainError(errorLine(state.error), controller::retry)
                FreestyleScreenState.Loading -> FullLoader()
                FreestyleScreenState.Empty -> DoneColumn("No cards yet") {
                    Spacer(Modifier.height(8.dp))
                    DoneButton("Add cards", primary = true, onAddCards)
                    DoneButton("Back to home", primary = false, onExit)
                }
                FreestyleScreenState.Complete -> DoneColumn("Freestyle complete") {
                    Spacer(Modifier.height(8.dp))
                    DoneButton("Play again", primary = true, controller::restart)
                    DoneButton("Back to home", primary = false, onExit)
                }
            }
        }
    }
}

@Composable
private fun FreestyleHeader(onExit: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val title: @Composable (Modifier) -> Unit = { modifier ->
            BText(
                "Freestyle",
                modifier.semantics { heading() },
                size = 18.sp,
                weight = FontWeight.Bold,
                align = TextAlign.Center,
            )
        }
        if (maxWidth <= 420.dp) {
            // Two rows, so the title and Exit both clear the menu pill
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                title(Modifier.fillMaxWidth().padding(horizontal = 60.dp))
                ExitButton(Modifier.align(Alignment.End), onExit)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(130.dp))
                title(Modifier.weight(1f))
                ExitButton(Modifier, onExit)
            }
        }
    }
}

@Composable
private fun ExitButton(modifier: Modifier, onClick: () -> Unit) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .width(130.dp)
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .plainClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BText("Exit Freestyle", size = 13.sp)
    }
}

@Composable
private fun ReadNotice(error: ApiError?, onRetry: () -> Unit) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(top = 12.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BText(errorLine(error), Modifier.weight(1f), color = c.muted, size = 13.sp)
        BText(
            "Try again",
            Modifier.plainClick(onClick = onRetry).padding(8.dp),
            color = c.good,
            size = 13.sp,
            weight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FreestyleStack(
    queue: List<Card>,
    topicNames: Map<String, String>,
    suspended: Boolean,
    onAdvance: (String) -> Unit,
    modifier: Modifier,
) {
    val top = queue.first()
    var flipped by rememberSaveable(top.id) { mutableStateOf(false) }
    var exiting by remember(top.id) { mutableStateOf(false) }
    val drag = remember(top.id) { Animatable(0f) }
    val rotation = remember(top.id) { Animatable(if (flipped) 180f else 0f) }
    val scope = rememberCoroutineScope()
    val latestAdvance by rememberUpdatedState(onAdvance)
    val density = LocalDensity.current
    val dismissPx = with(density) { 100.dp.toPx() }
    val reduced = reducedMotion()

    LaunchedEffect(top.id, flipped) {
        val target = if (flipped) 180f else 0f
        if (reduced) rotation.snapTo(target) else rotation.animateTo(target, tween(450, easing = FlipEasing))
    }
    LaunchedEffect(top.id, suspended) {
        if (suspended && !exiting) drag.animateTo(0f, tween(260, easing = EaseOut))
    }

    BoxWithConstraints(
        modifier
            .widthIn(max = 640.dp)
            .heightIn(max = 800.dp)
            .fillMaxWidth(),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }

        fun dismiss(direction: Float) {
            if (exiting || suspended) return
            exiting = true
            if (reduced) {
                latestAdvance(top.id)
                return
            }
            scope.launch {
                drag.animateTo(direction * widthPx * 1.1f, tween(260, easing = EaseOut))
                latestAdvance(top.id)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    stateDescription = if (flipped) "Answer shown" else "Question shown"
                    onClick(if (flipped) "Show question" else "Reveal answer") {
                        if (!exiting && !suspended) flipped = !flipped
                        true
                    }
                    customActions = listOf(CustomAccessibilityAction("Next card") { dismiss(1f); true })
                }
                .pointerInput(top.id, suspended) {
                    if (suspended) return@pointerInput
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (exiting) return@awaitEachGesture
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!change.pressed) {
                                if (dragging) {
                                    change.consume()
                                    if (abs(dx) > dismissPx) {
                                        dismiss(sign(dx))
                                    } else {
                                        scope.launch { drag.animateTo(0f, tween(260, easing = EaseOut)) }
                                    }
                                } else if (abs(dx) < slop && abs(dy) < slop) {
                                    flipped = !flipped
                                }
                                break
                            }
                            if (!dragging) {
                                if (abs(dy) > slop && abs(dy) > abs(dx)) break // leave it to the card's scroll
                                if (abs(dx) > slop) dragging = true
                            }
                            if (dragging) {
                                change.consume()
                                scope.launch { drag.snapTo(dx) }
                            }
                        }
                    }
                },
        ) {
            val visible = queue.take(3)
            visible.reversed().forEachIndexed { index, card ->
                val depth = visible.size - 1 - index
                key(card.id) {
                    val animatedDepth by animateFloatAsState(depth.toFloat(), tween(260, easing = EaseOut), label = "depth")
                    val isTop = depth == 0
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (isTop) Modifier else Modifier.clearAndSetSemantics {})
                            .graphicsLayer {
                                if (isTop) {
                                    translationX = drag.value
                                    rotationZ = (drag.value / this.density) / 18f
                                }
                                translationY = animatedDepth * 14.dp.toPx()
                                val scale = 1f - animatedDepth * 0.05f
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        FlipCard(
                            card = card,
                            topic = topicNames[card.topicId].orEmpty(),
                            backHint = "Tap for question · Swipe to continue",
                            rotation = { if (isTop) rotation.value else 0f },
                            frontHint = "Tap to reveal · Swipe to continue",
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}
