package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.isDesktop
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Entry validation control: slide-to-confirm on touch devices, explicit button on desktop.
 */
@Composable
fun EntryConfirmControl(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isConfirmed: Boolean = false,
) {
    if (LocalPlatformContext.current.isDesktop) {
        DesktopEntryConfirmButton(
            onConfirm = onConfirm,
            modifier = modifier,
            enabled = enabled,
            isConfirmed = isConfirmed,
        )
    } else {
        SlideToConfirmButton(
            text = stringResource(Res.string.slide_to_confirm),
            onConfirm = onConfirm,
            enabled = enabled,
            isConfirmed = isConfirmed,
            modifier = modifier,
        )
    }
}

@Composable
private fun DesktopEntryConfirmButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isConfirmed: Boolean = false,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onConfirm,
            enabled = enabled && !isConfirmed,
            modifier = Modifier
                .widthIn(min = 280.dp, max = 420.dp)
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 6.dp,
                disabledElevation = 0.dp,
            ),
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isConfirmed) Icons.Default.Check else Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        if (isConfirmed) Res.string.desktop_entry_confirmed
                        else Res.string.desktop_confirm_entry_button,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
