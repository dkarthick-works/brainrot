package app.flashcards.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.flashcards.android.AppContainer
import app.flashcards.android.api.Card
import app.flashcards.android.data.CardStore
import app.flashcards.android.logic.ANSWER_MAX
import app.flashcards.android.logic.DUE_NOW
import app.flashcards.android.logic.QUESTION_MAX
import app.flashcards.android.logic.dueLabel
import app.flashcards.android.logic.questionKey
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainrotIcons
import app.flashcards.android.ui.components.Composer
import app.flashcards.android.ui.components.ComposerContext
import app.flashcards.android.ui.components.ConfirmSheet
import app.flashcards.android.ui.components.ListError
import app.flashcards.android.ui.components.ListLoading
import app.flashcards.android.ui.components.SearchField
import app.flashcards.android.ui.components.SheetButton
import app.flashcards.android.ui.components.SwipeAction
import app.flashcards.android.ui.components.SwipeRow
import app.flashcards.android.ui.components.TopBar
import app.flashcards.android.ui.components.groupedRow
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.Inter
import kotlinx.coroutines.delay

@Composable
fun CardsScreen(container: AppContainer, initialTopicId: String?, drawerOpen: Boolean) {
    val store = container.cardStore
    val state by store.state.collectAsState()
    val topicState by container.topicStore.state.collectAsState()
    val c = Brainrot.colors

    var topicId by rememberSaveable(initialTopicId) { mutableStateOf(initialTopicId) }
    var query by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") } // debounced text sent to the server
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Card?>(null) }
    var editing by remember { mutableStateOf<Card?>(null) }
    // Two-step add: first the question, then the answer
    var text by rememberSaveable { mutableStateOf("") }
    var draftQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    var serverDuplicate by rememberSaveable { mutableStateOf<String?>(null) }

    val topics = topicState.topics.orEmpty()
    val names = topics.associate { it.id to it.name }
    val defaultTopic = topics.firstOrNull { it.isDefault }
    val target = topicId ?: defaultTopic?.id
    val targetName = if (topicId != null) names[topicId].orEmpty() else defaultTopic?.name ?: "Default"
    val shown = state.cards.orEmpty().takeIf { state.topicId == topicId }.orEmpty()
    val loaded = state.topicId == topicId && state.cards != null

    val trimmed = text.trim()
    val duplicate = draftQuestion == null && trimmed.isNotEmpty() && (
        "${target.orEmpty()}|${questionKey(trimmed)}" == serverDuplicate ||
            shown.any { it.topicId == target && questionKey(it.question) == questionKey(trimmed) }
        )
    val canSend = trimmed.isNotEmpty() && !duplicate

    val input = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val chips = rememberLazyListState()

    fun backToQuestion() {
        text = draftQuestion.orEmpty()
        draftQuestion = null
        input.requestFocus()
    }

    fun send() {
        val question = draftQuestion
        if (question == null) {
            draftQuestion = trimmed
            text = ""
        } else {
            store.create(topicId, target.orEmpty(), question, trimmed)
            draftQuestion = null
            text = ""
        }
    }

    LaunchedEffect(Unit) { container.topicStore.refresh() }
    LaunchedEffect(query) {
        delay(250)
        q = query.trim()
    }
    LaunchedEffect(topicId, q) { store.show(topicId, q) }
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) focus.clearFocus()
    }
    // A topic deleted elsewhere falls back to All
    LaunchedEffect(topicId, topicState.topics) {
        if (topicId != null && topicState.topics != null && topicId !in names) topicId = null
    }
    LaunchedEffect(topicId, topics.size) {
        val index = topics.indexOfFirst { it.id == topicId }
        if (index >= 0) chips.scrollToItem(index + 1)
    }
    // Waits for an empty bar, so a refused question never replaces what's being typed
    val rejected = state.rejected.firstOrNull()
    LaunchedEffect(rejected, text.isEmpty(), draftQuestion) {
        if (rejected != null && text.isEmpty() && draftQuestion == null) {
            text = rejected.question
            serverDuplicate = "${rejected.topicId}|${questionKey(rejected.question)}"
            store.consumeRejected(rejected)
        }
    }

    BackHandler(enabled = !drawerOpen && (openId != null || draftQuestion != null)) {
        if (openId != null) openId = null else backToQuestion()
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (drawerOpen) Modifier.clearAndSetSemantics {} else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar("Cards")
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "chips") {
                LazyRow(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                    state = chips,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") { TopicChip("All", topicId == null) { topicId = null } }
                    items(topics, key = { it.id }) { topic ->
                        TopicChip(topic.name, topicId == topic.id) { topicId = topic.id }
                    }
                }
            }
            item(key = "search") {
                SearchField(query, { query = it }, "Search cards", Modifier.padding(bottom = 12.dp))
            }
            when {
                state.error != null && state.topicId == topicId -> item(key = "error") {
                    ListError(errorLine(state.error), store::reload, horizontalPadding = 0.dp)
                }
                !loaded -> item(key = "loading") { ListLoading(horizontalPadding = 0.dp) }
                shown.isEmpty() -> item(key = "empty") {
                    BText(
                        if (state.q.isNotEmpty()) "No cards match \"${state.q}\"" else "No cards here yet. Add one below.",
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        color = c.muted,
                        size = 14.sp,
                        align = TextAlign.Center,
                    )
                }
                else -> {
                    val hasMore = state.nextCursor != null
                    itemsIndexed(shown, key = { _, card -> card.id }) { index, card ->
                        Box(Modifier.groupedRow(index == 0, index == shown.lastIndex && !hasMore)) {
                            CardRow(
                                card = card,
                                topicName = if (topicId == null) names[card.topicId].orEmpty() else null,
                                open = openId == card.id,
                                onOpenChange = { isOpen ->
                                    openId = if (isOpen) card.id else openId.takeIf { it != card.id }
                                },
                                onTap = {
                                    when {
                                        openId != null -> openId = null
                                        !CardStore.isUnsaved(card) -> editing = card
                                    }
                                },
                                onEdit = { editing = card },
                                onDelete = { toDelete = card },
                            )
                        }
                    }
                    if (hasMore) {
                        item(key = "more") {
                            Box(
                                Modifier
                                    .groupedRow(first = false, last = true)
                                    .heightIn(min = 56.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.moreFailed) {
                                    BText(
                                        "Try again",
                                        Modifier.plainClick(onClick = store::retryMore).padding(12.dp),
                                        color = c.good,
                                        size = 13.sp,
                                        weight = FontWeight.SemiBold,
                                    )
                                } else {
                                    LaunchedEffect(state.nextCursor) { store.loadMore() }
                                    BText("Loading more…", color = c.muted, size = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Composer(
            value = text,
            onChange = { text = it },
            onSubmit = ::send,
            canSend = canSend,
            placeholder = when {
                draftQuestion != null -> "Type the answer…"
                targetName.isEmpty() -> "New card…"
                else -> "New card in $targetName…"
            },
            label = if (draftQuestion == null) "New card question" else "New card answer",
            sendLabel = if (draftQuestion == null) "Next: answer" else "Add card",
            maxLength = if (draftQuestion == null) QUESTION_MAX else ANSWER_MAX,
            focusRequester = input,
            imeAction = if (draftQuestion == null) ImeAction.Next else ImeAction.Send,
            error = if (duplicate) "Already in $targetName" else null,
            context = draftQuestion?.let { question ->
                { ComposerContext("Answer for ", question, "Back to question", ::backToQuestion) }
            },
        )
    }

    toDelete?.let { card ->
        val dismiss = { toDelete = null }
        ConfirmSheet(
            title = "Delete this card?",
            message = "\"${card.question}\" and its review history will be gone. This can't be undone.",
            onDismiss = dismiss,
        ) {
            SheetButton("Cancel", onClick = dismiss)
            SheetButton("Delete", danger = true) {
                store.delete(card)
                toDelete = null
            }
        }
    }

    editing?.let { card ->
        EditCardDialog(
            card = card,
            topics = topics,
            onCancel = { editing = null },
            onSave = { question, answer, newTopicId ->
                store.update(card, question, answer, newTopicId)
                editing = null
            },
        )
    }
}

@Composable
private fun TopicChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (active) c.good else c.card)
            .border(1.dp, if (active) c.good else c.line, shape)
            .semantics { selected = active }
            .plainClick(Role.Tab, onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BText(label, color = if (active) c.onGrade else c.ink, size = 14.sp, weight = FontWeight.Medium)
    }
}

@Composable
private fun CardRow(
    card: Card,
    topicName: String?,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Brainrot.colors
    val due = dueLabel(card.dueAt)
    val actions = if (CardStore.isUnsaved(card)) {
        emptyList()
    } else {
        listOf(
            SwipeAction("Edit card", BrainrotIcons.Pencil, onClick = onEdit),
            SwipeAction("Delete card", BrainrotIcons.Trash, danger = true, onClick = onDelete),
        )
    }
    SwipeRow(
        actions = actions,
        open = open,
        onOpenChange = onOpenChange,
        onTap = onTap,
        verticalAlignment = Alignment.Top,
        verticalPadding = 12.dp,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                card.question,
                style = TextStyle(color = c.ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                card.answer,
                style = TextStyle(color = c.muted, fontFamily = Inter, fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!topicName.isNullOrEmpty()) {
                BasicText(
                    topicName.uppercase(),
                    Modifier.padding(top = 2.dp),
                    style = TextStyle(color = c.muted, fontFamily = Inter, fontSize = 11.sp, letterSpacing = 0.06.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val now = due == DUE_NOW
        BText(
            due,
            Modifier.padding(top = 2.dp),
            color = if (now) c.good else c.muted,
            size = 12.sp,
            weight = if (now) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
