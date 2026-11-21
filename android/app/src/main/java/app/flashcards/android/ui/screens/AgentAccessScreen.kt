package app.flashcards.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flashcards.android.AppContainer
import app.flashcards.android.BuildConfig
import app.flashcards.android.api.ApiKey
import app.flashcards.android.api.CreatedKey
import app.flashcards.android.data.KeyStore
import app.flashcards.android.logic.KEY_NAME_MAX
import app.flashcards.android.logic.claudeCommand
import app.flashcards.android.logic.dayLabel
import app.flashcards.android.logic.mcpUrl
import app.flashcards.android.logic.usedLabel
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainrotIcons
import app.flashcards.android.ui.components.Composer
import app.flashcards.android.ui.components.ConfirmSheet
import app.flashcards.android.ui.components.ListError
import app.flashcards.android.ui.components.ListLoading
import app.flashcards.android.ui.components.SheetButton
import app.flashcards.android.ui.components.SwipeAction
import app.flashcards.android.ui.components.SwipeRow
import app.flashcards.android.ui.components.TopBar
import app.flashcards.android.ui.components.groupedRow
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.Inter
import kotlinx.coroutines.delay

// ClipDescription.EXTRA_IS_SENSITIVE is API 33. Older versions ignore the same key.
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

private fun copy(context: Context, label: String, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

private val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.5.sp)

@Composable
fun AgentAccessScreen(container: AppContainer) {
    val store = container.keyStore
    val state by store.state.collectAsState()
    val c = Brainrot.colors
    var name by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var toRevoke by remember { mutableStateOf<ApiKey?>(null) }
    val input = remember { FocusRequester() }
    val list = state.keys.orEmpty()
    val trimmed = name.trim()
    val endpoint = remember { mcpUrl(BuildConfig.API_BASE_URL) }
    val command = remember(endpoint) { claudeCommand(endpoint) }

    LaunchedEffect(store) { store.refresh() }
    BackHandler(enabled = openId != null) { openId = null }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("Agent access")
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "connect-label") { SectionLabel("Connect") }
            item(key = "connect") { ConnectCard(endpoint, command) }
            item(key = "keys-label") { SectionLabel("Keys") }
            when {
                state.keys != null -> {
                    if (list.isEmpty()) {
                        item(key = "empty") {
                            BText(
                                "No keys yet. Name one below to connect an agent.",
                                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                color = c.muted,
                                size = 14.sp,
                                align = TextAlign.Center,
                            )
                        }
                    }
                    itemsIndexed(list, key = { _, key -> key.id }) { index, key ->
                        Box(Modifier.groupedRow(index == 0, index == list.lastIndex)) {
                            KeyRow(
                                key = key,
                                open = openId == key.id,
                                onOpenChange = { isOpen ->
                                    openId = if (isOpen) key.id else openId.takeIf { it != key.id }
                                },
                                onTap = { openId = null },
                                onRevoke = { toRevoke = key },
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        item(key = "note") { SectionNote("Swipe a key left to revoke it.") }
                    }
                }
                state.error != null -> item(key = "error") {
                    ListError(errorLine(state.error), store::refresh, horizontalPadding = 0.dp)
                }
                else -> item(key = "loading") { ListLoading(horizontalPadding = 0.dp) }
            }
        }
        Composer(
            value = name,
            onChange = { name = it },
            onSubmit = {
                store.create(trimmed)
                name = ""
            },
            // Needs the loaded list, so a new key can't be lost behind the first load
            canSend = trimmed.isNotEmpty() && state.keys != null,
            placeholder = "Name a new key…",
            label = "New API key name",
            sendLabel = "Create key",
            maxLength = KEY_NAME_MAX,
            focusRequester = input,
        )
    }

    toRevoke?.let { key ->
        val dismiss = { toRevoke = null }
        ConfirmSheet(
            title = "Revoke ${key.name}?",
            message = "Agents using this key stop working immediately. This can't be undone.",
            onDismiss = dismiss,
        ) {
            SheetButton("Cancel", onClick = dismiss)
            SheetButton("Revoke", danger = true) {
                store.revoke(key.id)
                toRevoke = null
            }
        }
    }
}

/** Shown above every screen: this is the only time the full key exists on the client. */
@Composable
fun KeyRevealDialog(key: CreatedKey, onDone: () -> Unit) {
    val c = Brainrot.colors
    val context = LocalContext.current
    var copied by remember(key.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val shape = RoundedCornerShape(12.dp)
    ConfirmSheet(
        title = "Key created",
        message = "Copy ${key.name} now. You won't be able to see it again.",
        onDismiss = onDone,
        dismissible = false,
        body = {
            SelectionContainer(
                Modifier
                    .padding(bottom = 20.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.bg)
                    .border(1.dp, c.line, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicText(key.key, style = CodeStyle.copy(color = c.ink))
            }
        },
    ) {
        SheetButton(if (copied) "Copied" else "Copy key", accent = true) {
            copy(context, "brainrot API key", key.key, sensitive = true)
            copied = true
        }
        SheetButton("Done", primary = true, onClick = onDone)
    }
}

@Composable
private fun ConnectCard(endpoint: String, command: String) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BText("MCP endpoint", Modifier.padding(top = 4.dp), color = c.muted, size = 13.sp, weight = FontWeight.Medium)
        CodeBox(endpoint, "MCP endpoint")
        BText("Claude Code", Modifier.padding(top = 4.dp), color = c.muted, size = 13.sp, weight = FontWeight.Medium)
        CodeBox(command, "Claude Code command")
    }
}

@Composable
private fun CodeBox(text: String, label: String) {
    val c = Brainrot.colors
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(999.dp)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bg)
            .border(1.dp, c.line, shape)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(text, Modifier.weight(1f), style = CodeStyle.copy(color = c.ink))
        Box(
            Modifier
                .height(30.dp)
                .clip(pill)
                .background(c.card)
                .border(1.dp, c.line, pill)
                .plainClick {
                    copy(context, label, text, sensitive = false)
                    copied = true
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BText(if (copied) "Copied" else "Copy", color = c.good, size = 13.sp, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun KeyRow(key: ApiKey, open: Boolean, onOpenChange: (Boolean) -> Unit, onTap: () -> Unit, onRevoke: () -> Unit) {
    val c = Brainrot.colors
    val actions = if (KeyStore.isUnsaved(key)) {
        emptyList()
    } else {
        listOf(SwipeAction("Revoke ${key.name}", BrainrotIcons.Trash, danger = true, onClick = onRevoke))
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
                key.name,
                style = TextStyle(color = c.ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText("${key.prefix}…", style = TextStyle(color = c.muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp))
        }
        Column(Modifier.padding(top = 2.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BText("Created ${dayLabel(key.createdAt)}", color = c.muted, size = 12.sp)
            BText(usedLabel(key.lastUsedAt), color = c.muted, size = 12.sp)
        }
    }
}
