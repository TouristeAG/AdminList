package com.eventmanager.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.stats_graph_chapters
import com.eventmanager.app.ui.utils.getScreenHeightDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

enum class StatsGraphCategory {
    VOLUNTEERS,
    GUEST_LIST,
    POS_ACTIVITY,
    POS_MIX,
}

data class StatsGraphCategoryItem(
    val id: StatsGraphCategory,
    val title: String,
    val icon: ImageVector,
)

@Composable
fun StatsGraphCategoryLayout(
    isDesktop: Boolean,
    categories: List<StatsGraphCategoryItem>,
    expandedCategories: Set<StatsGraphCategory>,
    onToggleCategory: (StatsGraphCategory) -> Unit,
    volunteerContent: @Composable () -> Unit,
    guestListContent: @Composable () -> Unit,
    posActivityContent: @Composable () -> Unit,
    posMixContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isDesktop) {
        StatsGraphDesktopViewer(
            categories = categories,
            volunteerContent = volunteerContent,
            guestListContent = guestListContent,
            posActivityContent = posActivityContent,
            posMixContent = posMixContent,
            modifier = modifier,
        )
    } else {
        StatsGraphMobileAccordion(
            categories = categories,
            expandedCategories = expandedCategories,
            onToggleCategory = onToggleCategory,
            volunteerContent = volunteerContent,
            guestListContent = guestListContent,
            posActivityContent = posActivityContent,
            posMixContent = posMixContent,
            modifier = modifier,
        )
    }
}

@Composable
private fun StatsGraphMobileAccordion(
    categories: List<StatsGraphCategoryItem>,
    expandedCategories: Set<StatsGraphCategory>,
    onToggleCategory: (StatsGraphCategory) -> Unit,
    volunteerContent: @Composable () -> Unit,
    guestListContent: @Composable () -> Unit,
    posActivityContent: @Composable () -> Unit,
    posMixContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        categories.forEach { item ->
            val expanded = item.id in expandedCategories
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .animateContentSize(animationSpec = tween(220)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCategory(item.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (expanded) {
                        Spacer(modifier = Modifier.height(16.dp))
                        when (item.id) {
                            StatsGraphCategory.VOLUNTEERS -> volunteerContent()
                            StatsGraphCategory.GUEST_LIST -> guestListContent()
                            StatsGraphCategory.POS_ACTIVITY -> posActivityContent()
                            StatsGraphCategory.POS_MIX -> posMixContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsGraphDesktopViewer(
    categories: List<StatsGraphCategoryItem>,
    volunteerContent: @Composable () -> Unit,
    guestListContent: @Composable () -> Unit,
    posActivityContent: @Composable () -> Unit,
    posMixContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenHeight = getScreenHeightDp()
    val viewerHeight = (screenHeight * 0.72f).coerceIn(560.dp, 920.dp)
    val scrollState = rememberScrollState()
    val offsets = remember { mutableStateMapOf<StatsGraphCategory, Int>() }
    var activeChapter by remember { mutableStateOf(categories.firstOrNull()?.id ?: StatsGraphCategory.VOLUNTEERS) }
    var composedCategories by remember {
        mutableStateOf(setOfNotNull(categories.firstOrNull()?.id))
    }
    var pendingScrollTo by remember { mutableStateOf<StatsGraphCategory?>(null) }

    fun ensureComposed(id: StatsGraphCategory) {
        if (id !in composedCategories) {
            composedCategories = composedCategories + id
        }
    }

    LaunchedEffect(categories) {
        yield()
        for (item in categories.drop(1)) {
            delay(180)
            ensureComposed(item.id)
        }
    }

    LaunchedEffect(pendingScrollTo, composedCategories) {
        val target = pendingScrollTo ?: return@LaunchedEffect
        ensureComposed(target)
        val offset = snapshotFlow { offsets[target] }.filterNotNull().first()
        scrollState.animateScrollTo(offset)
        pendingScrollTo = null
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { value ->
                val current = categories
                    .mapNotNull { item -> offsets[item.id]?.let { item.id to it } }
                    .filter { it.second <= value + 48 }
                    .maxByOrNull { it.second }
                    ?.first
                if (current != null && current != activeChapter) {
                    activeChapter = current
                }
            }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(viewerHeight),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 20.dp, horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.stats_graph_chapters),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(16.dp))
                categories.forEach { item ->
                    val isSelected = item.id == activeChapter
                    val container = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                    val content = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(container)
                            .clickable {
                                activeChapter = item.id
                                ensureComposed(item.id)
                                pendingScrollTo = item.id
                            }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.0f),
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = content,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                categories.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                offsets[item.id] = coordinates.positionInParent().y.roundToInt()
                            },
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (item.id in composedCategories) {
                            when (item.id) {
                                StatsGraphCategory.VOLUNTEERS -> volunteerContent()
                                StatsGraphCategory.GUEST_LIST -> guestListContent()
                                StatsGraphCategory.POS_ACTIVITY -> posActivityContent()
                                StatsGraphCategory.POS_MIX -> posMixContent()
                            }
                        } else {
                            GraphLoadingPlaceholder(isPhone = false, cardCount = 2)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
