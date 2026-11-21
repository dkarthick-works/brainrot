package app.flashcards.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.Inter
import app.flashcards.android.ui.theme.cardShadow

/** Chat-style bar pinned to the bottom of list screens, for adding and renaming. */
@Composable
fun Composer(
    value: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    canSend: Boolean,
    placeholder: String,
    label: String,
    sendLabel: String,
    maxLength: Int,
    focusRequester: FocusRequester,
    error: String? = null,
    context: (@Composable () -> Unit)? = null,
) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(999.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    // Text set from outside (starting a rename) puts the cursor at the end
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    val submit = { if (canSend) onSubmit() }

    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 16.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth(),
    ) {
        if (error != null) {
            BText(
                error,
                Modifier
                    .padding(start = 16.dp, bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                color = c.again,
                size = 13.sp,
            )
        } else {
            context?.invoke()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .cardShadow(c, 28.dp)
                .clip(shape)
                .background(c.card)
                .border(1.dp, if (focused) c.good else c.line, shape)
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = shown,
                onValueChange = {
                    field = if (it.text.length > maxLength) it.copy(text = it.text.take(maxLength), composition = null) else it
                    onChange(field.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label },
                textStyle = TextStyle(color = c.ink, fontFamily = Inter, fontSize = 16.sp),
                singleLine = true,
                cursorBrush = SolidColor(c.good),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) BText(placeholder, color = c.muted)
                        inner()
                    }
                },
            )
            Box(
                Modifier
                    .size(36.dp)
                    .alpha(if (canSend) 1f else 0.25f)
                    .clip(CircleShape)
                    .background(c.good)
                    .semantics {
                        contentDescription = sendLabel
                        if (!canSend) disabled()
                    }
                    .then(if (canSend) Modifier.plainClick(onClick = onSubmit) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                BrainrotIcon(BrainrotIcons.Send, c.onGrade, size = 18.dp)
            }
        }
    }
}
