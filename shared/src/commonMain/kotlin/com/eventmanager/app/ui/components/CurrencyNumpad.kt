package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun CurrencyNumpad(
    amountText: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = amountText.ifBlank { "0" },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫")
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    NumpadKey(
                        label = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.numpad_clear))
        }
    }
}

@Composable
private fun NumpadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (label == "⌫") {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(Res.string.numpad_backspace))
        } else {
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun appendNumpadDigit(current: String, digit: String): String {
    if (digit == ".") {
        if (current.contains('.')) return current
        return if (current.isEmpty()) "0." else "$current."
    }
    if (current == "0" && digit != ".") return digit
    val parts = if (current.contains('.')) current.split('.') else listOf(current)
    if (parts.size == 2 && parts[1].length >= 2) return current
    return current + digit
}

fun backspaceNumpad(current: String): String =
    if (current.length <= 1) "" else current.dropLast(1)
