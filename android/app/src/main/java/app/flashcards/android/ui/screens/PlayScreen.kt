package app.flashcards.android.ui.screens

import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.flashcards.android.AppContainer
import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.apiCall
import app.flashcards.android.data.PlayController
import app.flashcards.android.data.PlayView
import app.flashcards.android.data.toView
import app.flashcards.android.logic.Grades
import app.flashcards.android.logic.nextInterval
import app.flashcards.android.logic.untilLabel
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainError
import app.flashcards.android.ui.components.FullLoader
import app.flashcards.android.ui.components.TealButton
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.Inter
import app.flashcards.android.ui.theme.SpaceGrotesk
import app.flashcards.android.ui.theme.cardShadow
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

class PlayViewModel(container: AppContainer) : ViewModel() {
    val controller = PlayController(
        fetchDue = { apiCall { container.api.due() } },
        fetchTopics = { apiCall { container.api.topics() } },
        writes = container.reviewWrites,
        scope = viewModelScope,
    )
}

private val FlipEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)

@Composable
fun PlayScreen(container: AppContainer, onAddCards: () -> Unit, onConnectAgent: () -> Unit, onFreestyle: () -> Unit) {
    val vm = viewModel { PlayViewModel(container) }
    val controller = vm.controller
    val activity = LocalActivity.current
    // Each visit fetches fresh cards, but a rotation or theme change keeps the session
    DisposableEffect(controller) {
        controller.enter()
        onDispose { if (activity?.isChangingConfigurations != true) controller.leave() }
    }
    val model by controller.model.collectAsState()
    val topicNames = model.topics.orEmpty().associate { it.id to it.name }

    when (val view = model.toView()) {
        PlayView.Loading -> Stage { FullLoader() }
        is PlayView.PageError -> Stage { BrainError(errorLine(view.error), controller::retry) }
        is PlayView.TopicsError -> Stage { BrainError(errorLine(view.error), controller::retryTopics) }
        PlayView.UnsavedReviews -> Stage {
            BText("Resolve the unsaved scheduled reviews to finish.", Modifier.padding(horizontal = 32.dp), align = TextAlign.Center)
        }
        PlayView.BrandNew -> Stage {
            DoneColumn("Let's put those brain cells to work.") {
                DoneLine("Add your first cards, or connect an agent to make them for you.")
                Spacer(Modifier.height(16.dp))
                DoneButton("Add cards", primary = true, onAddCards)
                DoneButton("Connect an agent", primary = false, onConnectAgent)
            }
        }
        is PlayView.Done -> Stage {
            DoneColumn("Brain Upgraded") {
                DoneLine(view.nextDueAt?.let { "Next card due ${untilLabel(it)}." } ?: "Nothing else is scheduled.")
                if (view.reviewed > 0) {
                    BText(
                        "${view.reviewed} reviewed · ${view.goodOrEasy} Good or Easy",
                        Modifier.padding(top = 8.dp),
                        size = 15.sp,
                        weight = FontWeight.Medium,
                        align = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                DoneButton("Freestyle", primary = true, onFreestyle)
            }
        }
        is PlayView.Study -> Study(view, topicNames, controller::grade)
    }
}

private fun errorLine(error: ApiError?) = if (error?.status == 0) {
    "Couldn't reach brainrot. Check your connection."
} else {
    "Something broke on our end. Try again in a moment."
}

@Composable
private fun Stage(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = 68.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DoneColumn(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            title,
            Modifier.padding(bottom = 4.dp),
            style = TextStyle(
                color = Brainrot.colors.ink,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 36.8.sp,
                letterSpacing = (-0.02).em,
                textAlign = TextAlign.Center,
            ),
        )
        content()
    }
}

@Composable
private fun DoneLine(text: String) = BText(text, color = Brainrot.colors.muted, size = 15.sp, align = TextAlign.Center)

@Composable
private fun DoneButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (primary) c.good else c.card)
            .border(1.dp, if (primary) c.good else c.line, shape)
            .plainClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BText(label, color = if (primary) c.onGrade else c.ink, size = 15.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun Study(view: PlayView.Study, topicNames: Map<String, String>, onGraded: (Card, Int) -> Unit) {
    val top = view.queue.first()
    var flipped by remember(top.id) { mutableStateOf(false) }
    var grade by remember(top.id) { mutableStateOf<Int?>(null) }
    var exiting by remember(top.id) { mutableStateOf(false) }
    val drag = remember(top.id) { Animatable(0f) }
    val rotation = remember(top.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latestGraded by rememberUpdatedState(onGraded)
    val density = LocalDensity.current
    val dismissPx = with(density) { 100.dp.toPx() }

    LaunchedEffect(top.id, flipped) {
        rotation.animateTo(if (flipped) 180f else 0f, tween(450, easing = FlipEasing))
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(top = 68.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .widthIn(max = 640.dp)
                .heightIn(max = 800.dp)
                .fillMaxWidth(),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }

            fun advance(direction: Float) {
                val picked = grade
                if (!flipped || picked == null || exiting) return
                exiting = true
                scope.launch {
                    drag.animateTo(direction * widthPx * 1.1f, tween(260, easing = EaseOut))
                    latestGraded(top, picked)
                    flipped = false
                    grade = null
                    exiting = false
                    drag.snapTo(0f)
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .semantics {
                        stateDescription = if (flipped) "Answer shown" else "Question shown"
                        onClick(if (flipped) "Show question" else "Reveal answer") {
                            if (!exiting) flipped = !flipped
                            true
                        }
                        if (flipped && grade != null) {
                            customActions = listOf(CustomAccessibilityAction("Next card") { advance(1f); true })
                        }
                    }
                    .pointerInput(top.id) {
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
                                        if (flipped && grade != null && abs(dx) > dismissPx) {
                                            advance(sign(dx))
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
                view.queue.take(3).reversed().forEachIndexed { index, card ->
                    val depth = minOf(view.queue.size, 3) - 1 - index
                    key(card.id) {
                        val animatedDepth by animateFloatAsState(depth.toFloat(), tween(260, easing = EaseOut), label = "depth")
                        val isTop = depth == 0
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (isTop) {
                                        val x = if (flipped && grade != null) drag.value else drag.value / 8
                                        translationX = x
                                        rotationZ = (x / this.density) / 18f
                                    }
                                    translationY = animatedDepth * 14.dp.toPx()
                                    val scale = 1f - animatedDepth * 0.05f
                                    scaleX = scale
                                    scaleY = scale
                                },
                        ) {
                            val hint = when {
                                !isTop -> ""
                                grade == null -> "Pick a difficulty"
                                else -> "Marked ${Grades.first { it.quality == grade }.label}. Swipe for next"
                            }
                            FlipCard(
                                card = card,
                                topic = topicNames[card.topicId].orEmpty(),
                                backHint = hint,
                                rotation = { if (isTop) rotation.value else 0f },
                            )
                        }
                    }
                }
            }
        }

        BText(view.counter, Modifier.padding(top = 32.dp), color = Brainrot.colors.muted, size = 13.sp)
        GradeBar(top, visible = flipped, selected = grade) { grade = it }
    }
}

@Composable
private fun FlipCard(card: Card, topic: String, backHint: String, rotation: () -> Float) {
    // A peeking card becomes the top card without leaving composition, so always read the latest lambda
    val latestRotation by rememberUpdatedState(rotation)
    val showBack by remember { derivedStateOf { latestRotation() > 90f } }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = latestRotation()
                cameraDistance = 12f * density
            },
    ) {
        if (showBack) {
            CardFace(card.answer, topic, backHint, Modifier.graphicsLayer { rotationY = 180f })
        } else {
            CardFace(card.question, topic, "Tap to reveal")
        }
    }
}

@Composable
private fun CardFace(text: String, topic: String, hint: String, modifier: Modifier = Modifier) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxSize()
            .cardShadow(c, 24.dp)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(
            topic.uppercase(),
            style = TextStyle(color = c.muted, fontFamily = Inter, fontSize = 12.sp, letterSpacing = 0.08.em),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val minHeight = maxHeight
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight),
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    text,
                    style = TextStyle(
                        color = c.ink,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        lineHeight = 32.4.sp,
                    ),
                )
            }
        }
        BText(hint, color = c.muted, size = 13.sp)
    }
}

@Composable
private fun GradeBar(card: Card, visible: Boolean, selected: Int?, onSelect: (Int) -> Unit) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(18.dp)
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "grades-alpha")
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .graphicsLayer {
                this.alpha = alpha
                translationY = (1f - alpha) * 12.dp.toPx()
            }
            .cardShadow(c, 18.dp)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape),
    ) {
        Grades.forEachIndexed { index, g ->
            val color = when (g.quality) { 1 -> c.again; 3 -> c.hard; 5 -> c.easy; else -> c.good }
            val active = selected == g.quality
            val neighborActive = active || Grades.getOrNull(index - 1)?.quality == selected
            if (index > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(if (neighborActive) Color.Transparent else c.line))
            }
            Column(
                Modifier
                    .weight(1f)
                    .background(if (active) color else Color.Transparent)
                    .then(if (visible) Modifier.plainClick { onSelect(g.quality) } else Modifier)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BText(g.label, color = if (active) c.onGrade else color, size = 15.sp, weight = FontWeight.SemiBold)
                BText(
                    "${nextInterval(card.repetitions, card.interval, card.easeFactor, g.quality)}d",
                    color = if (active) c.onGrade.copy(alpha = 0.75f) else c.muted,
                    size = 11.sp,
                    weight = FontWeight.Medium,
                )
            }
        }
    }
}
