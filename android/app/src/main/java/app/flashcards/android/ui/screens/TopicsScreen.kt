package app.flashcards.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flashcards.android.AppContainer
import app.flashcards.android.api.Topic
import app.flashcards.android.data.TopicStore
import app.flashcards.android.logic.TOPIC_NAME_MAX
import app.flashcards.android.logic.canSendName
import app.flashcards.android.logic.filterTopics
import app.flashcards.android.logic.isDuplicateName
import app.flashcards.android.logic.plural
import app.flashcards.android.logic.showsTopicSearch
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
import app.flashcards.android.ui.theme.Brainrot

@Composable
fun TopicsScreen(container: AppContainer, drawerOpen: Boolean, onOpenTopic: (String) -> Unit) {
    val store = container.topicStore
    val state by store.state.collectAsState()
    val c = Brainrot.colors

    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val list = state.topics.orEmpty()
    val editing = list.firstOrNull { it.id == editingId }
    val toDelete = list.firstOrNull { it.id == deleteId }
    val shown = filterTopics(list, query)
    val trimmed = name.trim()
    val duplicate = trimmed.isNotEmpty() && isDuplicateName(list, trimmed, editingId)

    val input = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    fun resetComposer() {
        editingId = null
        name = ""
    }

    LaunchedEffect(store) { store.refresh() }
    LaunchedEffect(editingId, state.topics) {
        if (editingId != null && state.topics != null && editing == null) resetComposer()
    }
    LaunchedEffect(editingId) {
        if (editingId != null) {
            input.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) focus.clearFocus()
    }

    // The drawer's own back handler sits lower, so step aside while it's open
    BackHandler(enabled = !drawerOpen && (openId != null || editingId != null)) {
        if (openId != null) openId = null else resetComposer()
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (drawerOpen) Modifier.clearAndSetSemantics {} else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar("Topics")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when {
                state.topics != null -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showsTopicSearch(list)) {
                        item(key = "search") {
                            SearchField(query, { query = it }, "Search topics", Modifier.padding(top = 8.dp, bottom = 12.dp))
                        }
                    }
                    if (shown.isEmpty() && showsTopicSearch(list)) {
                        item(key = "no-match") {
                            BText(
                                "No topics match \"${query.trim()}\"",
                                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                color = c.muted,
                                size = 14.sp,
                                align = TextAlign.Center,
                            )
                        }
                    }
                    itemsIndexed(shown, key = { _, topic -> topic.id }) { index, topic ->
                        Box(Modifier.groupedRow(index == 0, index == shown.lastIndex)) {
                            TopicRow(
                                topic = topic,
                                open = openId == topic.id,
                                editing = editingId == topic.id,
                                onOpenChange = { isOpen ->
                                    openId = if (isOpen) topic.id else openId.takeIf { it != topic.id }
                                },
                                onTap = {
                                    when {
                                        openId != null -> openId = null
                                        !TopicStore.isUnsaved(topic) -> onOpenTopic(topic.id)
                                    }
                                },
                                onRename = {
                                    editingId = topic.id
                                    name = topic.name
                                },
                                onDelete = { deleteId = topic.id },
                            )
                        }
                    }
                }
                state.error != null -> ListError(errorLine(state.error), store::refresh)
                else -> ListLoading()
            }
        }
        Composer(
            value = name,
            onChange = { name = it },
            onSubmit = {
                val target = editing
                if (target != null) store.rename(target.id, trimmed) else store.create(trimmed)
                resetComposer()
            },
            canSend = state.topics != null && canSendName(list, name, editing),
            placeholder = "Add a topic…",
            label = editing?.let { "New name for ${it.name}" } ?: "New topic name",
            sendLabel = if (editing != null) "Save new name" else "Add topic",
            maxLength = TOPIC_NAME_MAX,
            focusRequester = input,
            error = if (duplicate) "\"$trimmed\" already exists" else null,
            context = editing?.let { topic -> { ComposerContext("Renaming ", topic.name, "Cancel rename", ::resetComposer) } },
        )
    }

    if (toDelete != null) {
        val dismiss = { deleteId = null }
        if (toDelete.cardCount > 0) {
            ConfirmSheet(
                title = "Can't delete ${toDelete.name}",
                message = "It still has ${plural(toDelete.cardCount, "card")}. Move or delete them first.",
                onDismiss = dismiss,
            ) {
                SheetButton("OK", onClick = dismiss)
            }
        } else {
            ConfirmSheet(title = "Delete ${toDelete.name}?", message = "This can't be undone.", onDismiss = dismiss) {
                SheetButton("Cancel", onClick = dismiss)
                SheetButton("Delete", danger = true) {
                    store.delete(toDelete.id)
                    if (editingId == toDelete.id) resetComposer()
                    deleteId = null
                }
            }
        }
    }
}

@Composable
private fun TopicRow(
    topic: Topic,
    open: Boolean,
    editing: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val actions = if (TopicStore.isUnsaved(topic)) {
        emptyList()
    } else {
        buildList {
            add(SwipeAction("Rename ${topic.name}", BrainrotIcons.Pencil, onClick = onRename))
            // The backend won't delete the default topic, so it only gets Rename
            if (!topic.isDefault) add(SwipeAction("Delete ${topic.name}", BrainrotIcons.Trash, danger = true, onClick = onDelete))
        }
    }
    SwipeRow(actions, open, onOpenChange, onTap, editing) {
        BText(topic.name, Modifier.weight(1f))
        BText(plural(topic.cardCount, "card"), color = Brainrot.colors.muted, size = 14.sp)
    }
}
