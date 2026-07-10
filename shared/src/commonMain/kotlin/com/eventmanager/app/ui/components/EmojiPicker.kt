package com.eventmanager.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

private val curatedEmojis = listOf(
    "🍺", "🍷", "🥃", "🍸", "🍹", "☕", "🧃", "🥤",
    "🍕", "🍔", "🌮", "🍟", "🥗", "🍰", "🍫", "🍿",
    "🎫", "🎟️", "🎟", "🎪", "🎭", "🎵", "🎧", "💿",
    "👕", "🧢", "🎽", "👜", "🧥", "🧣", "🧤", "👟",
    "💰", "💳", "🛒", "🎁", "⭐", "🔥", "✨", "❤️",
    "🍾", "🥂", "🍻", "🧊", "🍋", "🍊", "🫗", "🍽️"
)

@Composable
fun EmojiPickerDialog(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.emoji_picker_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.heightIn(max = 320.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(curatedEmojis) { emoji ->
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable {
                                onEmojiSelected(emoji)
                                onDismiss()
                            }
                            .padding(4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
fun EmojiPickerField(
    emoji: String,
    onEmojiChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = emoji.ifBlank { "🛒" },
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
        OutlinedButton(onClick = { showPicker = true }) {
            Text(stringResource(Res.string.emoji_picker_choose))
        }
    }
    if (showPicker) {
        EmojiPickerDialog(
            onDismiss = { showPicker = false },
            onEmojiSelected = onEmojiChange
        )
    }
}
