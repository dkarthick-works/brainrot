package app.flashcards.android.ui.screens

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.flashcards.android.api.Card
import app.flashcards.android.api.Topic
import app.flashcards.android.logic.ANSWER_MAX
import app.flashcards.android.logic.QUESTION_MAX
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainrotIcon
import app.flashcards.android.ui.components.BrainrotIcons
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.components.reducedMotion
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.Inter
import app.flashcards.android.ui.theme.cardShadow

private val EditFlipEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)

/** Edit a card as a flashcard: question and topic on the front, answer on the back. */
@Composable
fun EditCardDialog(
    card: Card,
    topics: List<Topic>,
    onCancel: () -> Unit,
    onSave: (question: String, answer: String, topicId: String) -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0.35f)
            @Suppress("DEPRECATION") // still needed below API 30 for the keyboard to resize this dialog
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        var question by remember(card.id) { mutableStateOf(card.question) }
        var answer by remember(card.id) { mutableStateOf(card.answer) }
        var topicId by remember(card.id) { mutableStateOf(card.topicId) }
        var flipped by remember(card.id) { mutableStateOf(false) }
        val rotation = remember(card.id) { Animatable(0f) }
        val showBack by remember(card.id) { derivedStateOf { rotation.value > 90f } }
        val reduced = reducedMotion()
        val focus = LocalFocusManager.current
        val valid = question.isNotBlank() && answer.isNotBlank()

        fun flip() {
            focus.clearFocus()
            flipped = !flipped
        }
        val save = { if (valid) onSave(question.trim(), answer.trim(), topicId) }

        LaunchedEffect(flipped) {
            val target = if (flipped) 180f else 0f
            if (reduced) rotation.snapTo(target) else rotation.animateTo(target, tween(450, easing = EditFlipEasing))
        }

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .widthIn(max = 640.dp)
                    .heightIn(max = 800.dp)
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = rotation.value
                        cameraDistance = 12f * density
                    },
            ) {
                if (showBack) {
                    EditFace(::flip, onCancel, save, valid, Modifier.graphicsLayer { rotationY = 180f }) {
                        BasicText(
                            "ANSWER",
                            style = TextStyle(color = Brainrot.colors.muted, fontFamily = Inter, fontSize = 12.sp, letterSpacing = 0.08.em),
                        )
                        FaceField(answer, { answer = it.take(ANSWER_MAX) }, "Answer")
                        FlipHint("Tap card to edit question", ::flip)
                    }
                } else {
                    EditFace(::flip, onCancel, save, valid) {
                        FaceField(question, { question = it.take(QUESTION_MAX) }, "Question")
                        TopicPicker(topics, topicId) { topicId = it }
                        FlipHint("Tap card to edit answer", ::flip)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditFace(
    onFlip: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    valid: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxSize()
            .cardShadow(c, 24.dp)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            // Fields and buttons take their own taps, so only empty space flips the card
            .pointerInput(Unit) { detectTapGestures { onFlip() } },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .plainClick(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                BText("Cancel", size = 15.sp, weight = FontWeight.SemiBold)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .alpha(if (valid) 1f else 0.4f)
                    .background(c.good)
                    .semantics { if (!valid) disabled() }
                    .then(if (valid) Modifier.plainClick(onClick = onSave) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                BText("Save", color = c.onGrade, size = 15.sp, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ColumnScope.FaceField(value: String, onChange: (String) -> Unit, label: String) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            // Runs after the field handles the touch, so a tap here edits and never flips the card
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .clip(shape)
            .background(if (focused) c.bg else Color.Transparent)
            .drawBehind {
                val line = 1.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                drawRoundRect(
                    color = if (focused) c.good else c.line,
                    topLeft = Offset(line / 2, line / 2),
                    size = Size(size.width - line, size.height - line),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(line, pathEffect = if (focused) null else dash),
                )
            }
            .semantics { contentDescription = label },
        textStyle = TextStyle(
            color = c.ink,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 29.7.sp,
        ),
        cursorBrush = SolidColor(c.good),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(8.dp)) {
                if (value.isEmpty()) {
                    BasicText(
                        label,
                        style = TextStyle(color = c.muted, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun ColumnScope.FlipHint(text: String, onFlip: () -> Unit) {
    BText(
        text,
        Modifier
            .align(Alignment.CenterHorizontally)
            .plainClick(onClick = onFlip)
            .padding(4.dp),
        color = Brainrot.colors.muted,
        size = 13.sp,
    )
}

@Composable
private fun ColumnScope.TopicPicker(topics: List<Topic>, selectedId: String, onSelect: (String) -> Unit) {
    val c = Brainrot.colors
    val pillShape = RoundedCornerShape(999.dp)
    val menuShape = RoundedCornerShape(14.dp)
    var open by remember { mutableStateOf(false) }
    val name = topics.firstOrNull { it.id == selectedId }?.name.orEmpty()
    val gapPx = with(LocalDensity.current) { 40.dp.roundToPx() }

    Box(Modifier.align(Alignment.CenterHorizontally)) {
        Row(
            Modifier
                .clip(pillShape)
                .background(c.bg)
                .border(1.dp, if (open) c.good else c.line, pillShape)
                .semantics { contentDescription = "Topic: $name" }
                .plainClick { open = true }
                .padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                name.uppercase(),
                Modifier.widthIn(max = 200.dp),
                style = TextStyle(color = c.muted, fontFamily = Inter, fontSize = 12.sp, letterSpacing = 0.08.em),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BrainrotIcon(BrainrotIcons.ChevronDown, c.muted, size = 14.dp)
        }
        if (open) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, -gapPx),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .widthIn(min = 180.dp, max = 280.dp)
                        .heightIn(max = 280.dp)
                        .clip(menuShape)
                        .background(c.card)
                        .border(1.dp, c.line, menuShape)
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                ) {
                    topics.forEach { topic ->
                        val active = topic.id == selectedId
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) c.good else Color.Transparent)
                                .semantics { selected = active }
                                .plainClick {
                                    onSelect(topic.id)
                                    open = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            BasicText(
                                topic.name,
                                style = TextStyle(color = if (active) c.onGrade else c.ink, fontFamily = Inter, fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
