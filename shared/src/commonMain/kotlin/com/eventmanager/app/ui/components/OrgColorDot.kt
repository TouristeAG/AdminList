package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel

@Composable
fun OrgColorDot(
    orgId: String,
    viewModel: EventManagerViewModel,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    if (orgId.isBlank()) return
    val color = Color(viewModel.orgColorArgb(orgId))
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = orgId },
    )
}

@Composable
fun OrgColorDots(
    orgIds: List<String>,
    viewModel: EventManagerViewModel,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    val distinct = orgIds.filter { it.isNotBlank() }.distinct()
    if (distinct.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        distinct.forEach { orgId ->
            OrgColorDot(orgId = orgId, viewModel = viewModel, size = size)
        }
    }
}

@Composable
fun OrgColorDotLabel(
    orgId: String,
    viewModel: EventManagerViewModel,
    modifier: Modifier = Modifier,
) {
    if (orgId.isBlank()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OrgColorDot(orgId = orgId, viewModel = viewModel)
        Text(
            text = orgId,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
