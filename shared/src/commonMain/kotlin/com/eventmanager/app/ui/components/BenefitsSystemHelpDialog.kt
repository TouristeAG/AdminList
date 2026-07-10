package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

private enum class BenefitsHelpTab { Modes, Nova, Extras }

@Composable
fun BenefitsSystemHelpDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(BenefitsHelpTab.Modes) }
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = phoneFractionDialogProperties(),
    ) {
        DialogFractionSizer(profile = FractionalDialogProfile.Compact) { maxDialogWidth, maxDialogHeight ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
            ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.benefits_help_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(Res.string.benefits_help_dialog_subtitle),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(Res.string.benefits_help_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                BenefitsHelpTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        BenefitsHelpTab.Modes -> BenefitsHelpModesTab()
                        BenefitsHelpTab.Nova -> BenefitsHelpNovaTab()
                        BenefitsHelpTab.Extras -> BenefitsHelpExtrasTab()
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.got_it))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun BenefitsHelpTabRow(
    selectedTab: BenefitsHelpTab,
    onTabSelected: (BenefitsHelpTab) -> Unit
) {
    val tabs = listOf(
        BenefitsHelpTab.Modes to stringResource(Res.string.benefits_help_tab_modes),
        BenefitsHelpTab.Nova to stringResource(Res.string.benefits_help_tab_nova),
        BenefitsHelpTab.Extras to stringResource(Res.string.benefits_help_tab_extras)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (tab, label) ->
            BenefitsHelpTabChip(
                label = label,
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BenefitsHelpTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun BenefitsHelpModesTab() {
    HelpBulletBlock(
        title = stringResource(Res.string.stellar_benefits),
        body = stringResource(Res.string.benefits_help_app_stellar)
    )
    HelpBulletBlock(
        title = stringResource(Res.string.manual_rewards),
        body = stringResource(Res.string.benefits_help_app_manual)
    )
}

@Composable
private fun BenefitsHelpNovaTab() {
    HelpSectionBlock(
        title = stringResource(Res.string.benefits_help_section_profité_title),
        body = stringResource(Res.string.benefits_help_section_profité_body)
    )
    Text(
        text = stringResource(Res.string.benefits_help_section_nova_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    NovaHelpCard(
        title = stringResource(Res.string.benefits_help_nova_profité_title),
        rationale = stringResource(Res.string.benefits_help_nova_profité_rationale),
        perks = stringResource(Res.string.benefits_help_nova_profité_perks)
    )
    NovaHelpCard(
        title = stringResource(Res.string.benefits_help_nova_non_profité_title),
        rationale = stringResource(Res.string.benefits_help_nova_non_profité_rationale),
        perks = stringResource(Res.string.benefits_help_nova_non_profité_perks)
    )
    NovaHelpCard(
        title = stringResource(Res.string.benefits_help_nova_photo_title),
        rationale = stringResource(Res.string.benefits_help_nova_photo_rationale),
        perks = stringResource(Res.string.benefits_help_nova_photo_perks)
    )
    NovaHelpCard(
        title = stringResource(Res.string.benefits_help_nova_meeting_title),
        rationale = stringResource(Res.string.benefits_help_nova_meeting_rationale),
        perks = stringResource(Res.string.benefits_help_nova_meeting_perks)
    )
    NovaHelpCard(
        title = stringResource(Res.string.benefits_help_nova_gd_event_title),
        rationale = stringResource(Res.string.benefits_help_nova_gd_event_rationale),
        perks = stringResource(Res.string.benefits_help_nova_gd_event_perks)
    )
    NovaHelpCard(
        title = stringResource(Res.string.benefits_help_nova_gd_asso_title),
        rationale = stringResource(Res.string.benefits_help_nova_gd_asso_rationale),
        perks = stringResource(Res.string.benefits_help_nova_gd_asso_perks)
    )
}

@Composable
private fun BenefitsHelpExtrasTab() {
    HelpSectionBlock(
        title = stringResource(Res.string.benefits_help_section_galaxie_title),
        body = stringResource(Res.string.benefits_help_section_galaxie_body)
    )
    HelpSectionBlock(
        title = stringResource(Res.string.benefits_help_section_orion_title),
        body = stringResource(Res.string.benefits_help_section_orion_body)
    )
    HelpSectionBlock(
        title = stringResource(Res.string.benefits_help_section_veteran_title),
        body = stringResource(Res.string.benefits_help_section_veteran_body)
    )
    HelpSectionBlock(
        title = stringResource(Res.string.benefits_help_section_timing_title),
        body = stringResource(Res.string.benefits_help_section_timing_body)
    )
    HelpSectionBlock(
        title = stringResource(Res.string.benefits_help_footer_title),
        body = stringResource(Res.string.benefits_help_footer_bullets)
    )
}

@Composable
private fun HelpSectionBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        MultilineBulletList(body)
    }
}

@Composable
private fun HelpBulletBlock(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            MultilineBulletList(body)
        }
    }
}

@Composable
private fun NovaHelpCard(title: String, rationale: String, perks: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MultilineBulletList(perks)
        }
    }
}

@Composable
private fun MultilineBulletList(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
    }
}
