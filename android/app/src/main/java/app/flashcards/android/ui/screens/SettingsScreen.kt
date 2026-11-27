package app.flashcards.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.flashcards.android.AppContainer
import app.flashcards.android.BuildConfig
import app.flashcards.android.logic.initials
import app.flashcards.android.logic.plural
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainrotIcon
import app.flashcards.android.ui.components.BrainrotIcons
import app.flashcards.android.ui.components.ThemeSegmented
import app.flashcards.android.ui.components.TopBar
import app.flashcards.android.ui.components.groupedRow
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.Inter
import app.flashcards.android.ui.theme.SpaceGrotesk
import com.clerk.api.Clerk
import kotlinx.coroutines.launch

@Composable
internal fun SectionLabel(text: String) {
    BasicText(
        text.uppercase(),
        Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
        style = TextStyle(
            color = Brainrot.colors.muted,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.08.em,
        ),
    )
}

@Composable
internal fun SectionNote(text: String) {
    BText(
        text,
        Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        color = Brainrot.colors.muted,
        size = 13.sp,
    )
}

@Composable
fun SettingsScreen(container: AppContainer, drawerOpen: Boolean, onManageAccount: () -> Unit, onAgentAccess: () -> Unit) {
    val c = Brainrot.colors
    val user by Clerk.userFlow.collectAsState()
    val keys by container.keyStore.state.collectAsState()
    val mode by container.themeStore.mode.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { container.keyStore.refresh() }

    val name = listOfNotNull(user?.firstName, user?.lastName).joinToString(" ").trim().ifEmpty { user?.username.orEmpty() }
    val email = user?.primaryEmailAddress?.emailAddress.orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .then(if (drawerOpen) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        TopBar("Settings")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel("Account")
            Row(
                Modifier
                    .groupedRow(first = true, last = false)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(c.good, CircleShape)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    BText(initials(name.ifEmpty { email }), color = c.onGrade, weight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BText(name.ifEmpty { email }, weight = FontWeight.Bold)
                    if (name.isNotEmpty()) {
                        BasicText(
                            email,
                            style = TextStyle(color = c.muted, fontFamily = Inter, fontSize = 14.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            SettingsRow(first = false, last = false, onClick = onManageAccount) {
                BText("Manage account")
                BrainrotIcon(BrainrotIcons.ChevronRight, c.muted, size = 16.dp)
            }
            SettingsRow(first = false, last = true, onClick = { scope.launch { Clerk.auth.signOut() } }) {
                BText("Sign out", color = c.again, weight = FontWeight.Medium)
            }

            SectionLabel("Agent access")
            SettingsRow(first = true, last = true, onClick = onAgentAccess) {
                BText("API keys")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    keys.keys?.let { BText(plural(it.size, "key"), color = c.muted, size = 14.sp) }
                    BrainrotIcon(BrainrotIcons.ChevronRight, c.muted, size = 16.dp)
                }
            }
            SectionNote("Lets AI agents like Claude Code read and add cards for you over MCP.")

            SectionLabel("Appearance")
            Box(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                ThemeSegmented(mode, container.themeStore::set)
            }

            SectionLabel("About")
            Row(
                Modifier
                    .groupedRow(first = true, last = true)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BText("brainrot.", size = 20.sp, weight = FontWeight.Bold, family = SpaceGrotesk)
                BText("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = c.muted, size = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsRow(
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier
            .groupedRow(first, last)
            .heightIn(min = 56.dp)
            .plainClick(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
