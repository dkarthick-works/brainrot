package app.flashcards.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flashcards.android.ui.theme.Brainrot

/** Unlike the generic 8-second save banner, this stays until the failed reviews are resolved. */
@Composable
fun ReviewWritesNotice(count: Int, showBackHome: Boolean, onBackHome: () -> Unit, onRetry: () -> Unit) {
    val c = Brainrot.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(c.card)
                .border(1.dp, c.line, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c.again))
            Row(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BText(
                    if (count == 1) "A scheduled review couldn't be saved." else "$count scheduled reviews couldn't be saved.",
                    Modifier.weight(1f),
                    size = 13.sp,
                )
                if (showBackHome) NoticeAction("Back to home", onBackHome)
                NoticeAction("Try again", onRetry)
            }
        }
    }
}

@Composable
private fun NoticeAction(label: String, onClick: () -> Unit) {
    BText(
        label,
        Modifier.plainClick(onClick = onClick).padding(8.dp),
        color = Brainrot.colors.good,
        size = 13.sp,
        weight = FontWeight.SemiBold,
    )
}
