package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.data.remote.BackendType
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.bar_discount_percent_label
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import org.jetbrains.compose.resources.stringResource

/** Permanent-guest bar discounts are stored on Firestore only, so the form field stays hidden on Sheets. */
@Composable
fun rememberGuestBarDiscountEnabled(viewModel: EventManagerViewModel?): Boolean =
    viewModel?.getActiveBackendType() == BackendType.FIREBASE

/** Clamps to 0..100 and drops non-digits so the stored percentage always matches what POS can apply. */
fun sanitizeBarDiscountInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.trimStart('0')
    if (digits.isEmpty()) return if (raw.isEmpty()) "" else "0"
    val value = digits.toIntOrNull() ?: return "100"
    return value.coerceAtMost(100).toString()
}

@Composable
fun GuestBarDiscountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeBarDiscountInput(it)) },
        label = { Text(stringResource(Res.string.bar_discount_percent_label)) },
        singleLine = true,
        modifier = modifier,
    )
}
