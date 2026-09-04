package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.remote.FirebaseEmailDomainPolicy
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.firebase_email_domains_add
import com.eventmanager.app.resources.firebase_email_domains_body
import com.eventmanager.app.resources.firebase_email_domains_empty
import com.eventmanager.app.resources.firebase_email_domains_hint
import com.eventmanager.app.resources.firebase_email_domains_label
import com.eventmanager.app.resources.firebase_email_domains_remove
import com.eventmanager.app.resources.firebase_email_domains_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun FirebaseAllowedEmailDomainsSection(
    domains: List<String>,
    onDomainsChange: (List<String>) -> Unit,
    enabled: Boolean = true,
) {
    var draft by remember { mutableStateOf("") }
    GuidedStepCard(
        title = stringResource(Res.string.firebase_email_domains_title),
        body = stringResource(Res.string.firebase_email_domains_body),
    ) {
        if (domains.isEmpty()) {
            Text(
                stringResource(Res.string.firebase_email_domains_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            domains.forEach { domain ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("@$domain", style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = { onDomainsChange(domains.filterNot { it == domain }) },
                        enabled = enabled,
                    ) {
                        Text(stringResource(Res.string.firebase_email_domains_remove))
                    }
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(Res.string.firebase_email_domains_label)) },
            supportingText = { Text(stringResource(Res.string.firebase_email_domains_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
        )
        OutlinedButton(
            onClick = {
                val normalized = FirebaseEmailDomainPolicy.normalizeDomain(draft)
                if (normalized.isBlank() || !normalized.contains('.')) return@OutlinedButton
                if (domains.any { it == normalized }) {
                    draft = ""
                    return@OutlinedButton
                }
                onDomainsChange(domains + normalized)
                draft = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && draft.isNotBlank(),
        ) {
            Text(stringResource(Res.string.firebase_email_domains_add))
        }
    }
}
