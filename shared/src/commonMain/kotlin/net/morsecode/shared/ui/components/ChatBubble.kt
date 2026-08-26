package net.morsecode.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.morsecode.shared.chat.ChatMessage

@Composable
fun ChatBubble(message: ChatMessage) {
    val isSent = message.direction == "sent"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isSent) androidx.compose.foundation.layout.Arrangement.End
        else androidx.compose.foundation.layout.Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (isSent) Alignment.End else Alignment.Start) {
            Surface(
                color = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isSent) 16.dp else 4.dp,
                    bottomEnd = if (isSent) 4.dp else 16.dp,
                ),
            ) {
                Text(
                    message.text,
                    color = if (isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Text(
                (timeLabel(message.sentAt) + if (isSent && message.delivered) " · delivered" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

private fun timeLabel(epochMs: Long): String = try {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    "%02d:%02d".format(t.hour, t.minute)
} catch (e: Exception) {
    ""
}
