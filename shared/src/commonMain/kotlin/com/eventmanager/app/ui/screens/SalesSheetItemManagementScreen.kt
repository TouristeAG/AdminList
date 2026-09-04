package com.eventmanager.app.ui.screens

import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.data.models.PosSubcategory
import com.eventmanager.app.data.models.PosSubcategoryCatalog
import com.eventmanager.app.data.models.PosVenueScope
import com.eventmanager.app.data.models.SalesCategory
import com.eventmanager.app.data.models.SalesSheetItem
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.VolunteerRank
import com.eventmanager.app.ui.components.EmojiPickerField
import com.eventmanager.app.ui.components.SearchBarWithFilter
import com.eventmanager.app.ui.components.phoneFractionDialogProperties
import com.eventmanager.app.ui.components.DialogFractionSizer
import com.eventmanager.app.ui.components.FractionalDialogProfile
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesSheetItemManagementScreen(
    items: List<SalesSheetItem>,
    venues: List<VenueEntity> = emptyList(),
    onAddItem: (SalesSheetItem) -> Unit,
    onUpdateItem: (SalesSheetItem) -> Unit,
    onDeleteItem: (SalesSheetItem) -> Unit,
    onUpdateItemStatus: (Long, Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: EventManagerViewModel? = null,
) {
    val platformContext = LocalPlatformContext.current
    val subcategoriesEnabled = viewModel?.isPosSubcategoriesEnabled() == true
    val subcategoryCatalog by (viewModel?.posSubcategories ?: remember { MutableStateFlow(emptyList()) })
        .collectAsState()
    var searchText by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<SalesSheetItem?>(null) }
    val padding = getResponsivePadding()

    val filtered = remember(items, searchText) {
        if (searchText.isBlank()) items
        else items.filter { it.name.contains(searchText, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                }
                Text(
                    text = stringResource(Res.string.sales_items_title),
                    style = getResponsiveTypography(),
                    fontWeight = FontWeight.Bold
                )
            }
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.add_sales_item))
            }
        }

        Spacer(Modifier.height(12.dp))
        SearchBarWithFilter(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            placeholder = stringResource(Res.string.search_sales_items_placeholder),
            filterOptions = emptyList(),
            selectedFilter = null,
            onFilterChange = { }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.sales_items_count, filtered.count { it.isActive }, filtered.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { item ->
                SalesSheetItemRow(
                    item = item,
                    onEdit = { editItem = item },
                    onDelete = { onDeleteItem(item) },
                    onToggleActive = { onUpdateItemStatus(item.id, !item.isActive) }
                )
            }
        }
    }

    if (showAddDialog) {
        SalesSheetItemEditorDialog(
            title = stringResource(Res.string.add_sales_item),
            initial = null,
            venues = venues,
            subcategoriesEnabled = subcategoriesEnabled,
            subcategoryCatalog = subcategoryCatalog,
            onAddSubcategory = { category, name -> viewModel?.addPosSubcategory(category, name) },
            onRemoveSubcategory = { category, name -> viewModel?.removePosSubcategory(category, name) },
            onDismiss = { showAddDialog = false },
            onSave = { item ->
                onAddItem(item)
                showAddDialog = false
            }
        )
    }
    editItem?.let { item ->
        SalesSheetItemEditorDialog(
            title = stringResource(Res.string.edit_sales_item),
            initial = item,
            venues = venues,
            subcategoriesEnabled = subcategoriesEnabled,
            subcategoryCatalog = subcategoryCatalog,
            onAddSubcategory = { category, name -> viewModel?.addPosSubcategory(category, name) },
            onRemoveSubcategory = { category, name -> viewModel?.removePosSubcategory(category, name) },
            onDismiss = { editItem = null },
            onSave = { updated ->
                onUpdateItem(updated.copy(id = item.id, sheetsId = item.sheetsId))
                editItem = null
            }
        )
    }
}

@Composable
private fun SalesSheetItemRow(
    item: SalesSheetItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.emoji.isNotBlank()) {
                        Text(item.emoji, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    if (item.isDeposit) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                stringResource(Res.string.sales_deposit_badge),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                val categoryLabels = SalesCategory.parseList(item.categories).map { category ->
                    when (category) {
                        SalesCategory.MERCH -> stringResource(Res.string.sales_category_merch)
                        SalesCategory.ENTRY -> stringResource(Res.string.sales_category_entry)
                        SalesCategory.BAR -> stringResource(Res.string.sales_category_bar)
                        SalesCategory.OTHER -> stringResource(Res.string.sales_category_other)
                    }
                }
                val summaryLine = stringResource(Res.string.sales_item_summary_line, item.price,
                    if (item.hasDiscount) stringResource(Res.string.yes) else stringResource(Res.string.no),
                    item.requiredRank?.name ?: stringResource(Res.string.sales_rank_none_required)
                )
                Text(
                    text = buildString {
                        append(summaryLine)
                        if (categoryLabels.isNotEmpty()) append(" • ${categoryLabels.joinToString(", ")}")
                        if (item.subcategory.isNotBlank()) append(" › ${item.subcategory}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = item.isActive, onCheckedChange = { onToggleActive() })
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
            }
        }
    }
}

/**
 * Sub-category picker for the selected general categories, with inline create/delete.
 * Deleting is org-wide, so it goes through a confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesSubcategoryEditor(
    categories: Set<SalesCategory>,
    catalog: List<PosSubcategory>,
    selected: String,
    categoryLabel: @Composable (SalesCategory) -> String,
    onSelect: (String) -> Unit,
    onAdd: (SalesCategory, String) -> Unit,
    onRemove: (SalesCategory, String) -> Unit,
) {
    var addingFor by remember { mutableStateOf<SalesCategory?>(null) }
    var pendingRemoval by remember { mutableStateOf<PosSubcategory?>(null) }
    val orderedCategories = remember(categories) { SalesCategory.entries.filter { categories.contains(it) } }

    Text(stringResource(Res.string.sales_subcategory_label), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(Res.string.sales_subcategory_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    orderedCategories.forEach { category ->
        val entries = PosSubcategoryCatalog.forCategory(catalog, category)
        if (orderedCategories.size > 1) {
            Text(
                categoryLabel(category),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selected.isBlank(),
                onClick = { onSelect("") },
                label = { Text(stringResource(Res.string.sales_subcategory_none)) },
            )
            entries.forEach { entry ->
                FilterChip(
                    selected = selected.equals(entry.name, ignoreCase = true),
                    onClick = { onSelect(entry.name) },
                    label = { Text(entry.name) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.delete),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { pendingRemoval = entry },
                        )
                    },
                )
            }
            AssistChip(
                onClick = { addingFor = category },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(stringResource(Res.string.sales_subcategory_add)) },
            )
        }
    }

    addingFor?.let { category ->
        var draft by remember(category) { mutableStateOf("") }
        val normalized = PosSubcategoryCatalog.normalizeName(draft)
        val duplicate = PosSubcategoryCatalog.contains(catalog, category, normalized)
        AlertDialog(
            onDismissRequest = { addingFor = null },
            title = { Text(stringResource(Res.string.sales_subcategory_add)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(PosSubcategoryCatalog.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(Res.string.sales_subcategory_name_label)) },
                    singleLine = true,
                    isError = duplicate,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAdd(category, normalized)
                        onSelect(normalized)
                        addingFor = null
                    },
                    enabled = normalized.isNotEmpty() && !duplicate,
                ) { Text(stringResource(Res.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { addingFor = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    pendingRemoval?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(Res.string.sales_subcategory_remove_title)) },
            text = { Text(stringResource(Res.string.sales_subcategory_remove_message, entry.name)) },
            confirmButton = {
                Button(onClick = {
                    onRemove(entry.category, entry.name)
                    pendingRemoval = null
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SalesSheetItemEditorDialog(
    title: String,
    initial: SalesSheetItem?,
    venues: List<VenueEntity>,
    subcategoriesEnabled: Boolean = false,
    subcategoryCatalog: List<PosSubcategory> = emptyList(),
    onAddSubcategory: (SalesCategory, String) -> Unit = { _, _ -> },
    onRemoveSubcategory: (SalesCategory, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onSave: (SalesSheetItem) -> Unit
) {
    val platformContext = LocalPlatformContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var priceText by remember { mutableStateOf(initial?.price?.toString().orEmpty()) }
    var hasDiscount by remember { mutableStateOf(initial?.hasDiscount ?: false) }
    var isDeposit by remember { mutableStateOf(initial?.isDeposit ?: false) }
    var rank by remember { mutableStateOf(initial?.requiredRank) }
    var emoji by remember { mutableStateOf(initial?.emoji.orEmpty()) }
    var subcategory by remember { mutableStateOf(initial?.subcategory.orEmpty()) }
    var selectedCategories by remember {
        mutableStateOf(SalesCategory.parseList(initial?.categories.orEmpty()))
    }
    var selectedVenues by remember {
        val parsed = PosVenueScope.parseVenueList(initial?.availableVenues.orEmpty())
        mutableStateOf(
            if (parsed.isEmpty() && initial == null) setOf(PosVenueScope.GLOBAL) else parsed
        )
    }
    val activeVenues = remember(venues) { venues.filter { it.isActive } }

    @Composable
    fun categoryLabel(category: SalesCategory): String = when (category) {
        SalesCategory.MERCH -> stringResource(Res.string.sales_category_merch)
        SalesCategory.ENTRY -> stringResource(Res.string.sales_category_entry)
        SalesCategory.BAR -> stringResource(Res.string.sales_category_bar)
        SalesCategory.OTHER -> stringResource(Res.string.sales_category_other)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = phoneFractionDialogProperties(),
    ) {
        DialogFractionSizer(profile = FractionalDialogProfile.Compact) { maxDialogWidth, maxDialogHeight ->
            Card(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(Res.string.sales_price_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                EmojiPickerField(
                    emoji = emoji,
                    onEmojiChange = { emoji = it }
                )
                Text(stringResource(Res.string.sales_categories_label), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SalesCategory.entries.forEach { category ->
                        val selected = selectedCategories.contains(category)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedCategories = if (selected) {
                                    selectedCategories - category
                                } else {
                                    selectedCategories + category
                                }
                            },
                            label = { Text(categoryLabel(category)) }
                        )
                    }
                }
                if (subcategoriesEnabled && selectedCategories.isNotEmpty()) {
                    SalesSubcategoryEditor(
                        categories = selectedCategories,
                        catalog = subcategoryCatalog,
                        selected = subcategory,
                        categoryLabel = { categoryLabel(it) },
                        onSelect = { subcategory = it },
                        onAdd = onAddSubcategory,
                        onRemove = { category, name ->
                            if (subcategory.equals(name, ignoreCase = true)) subcategory = ""
                            onRemoveSubcategory(category, name)
                        },
                    )
                }
                Text(stringResource(Res.string.sales_item_venues_label), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.sales_item_venues_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val globalSelected = selectedVenues.contains(PosVenueScope.GLOBAL)
                    FilterChip(
                        selected = globalSelected,
                        onClick = {
                            selectedVenues = if (globalSelected) {
                                selectedVenues - PosVenueScope.GLOBAL
                            } else {
                                selectedVenues + PosVenueScope.GLOBAL
                            }
                        },
                        label = { Text(stringResource(Res.string.pos_venue_global)) },
                    )
                    activeVenues.forEach { venue ->
                        val selected = selectedVenues.contains(venue.name)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedVenues = if (selected) {
                                    selectedVenues - venue.name
                                } else {
                                    selectedVenues + venue.name
                                }
                            },
                            label = { Text(venue.name) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.sales_discount_label), modifier = Modifier.weight(1f))
                    Switch(checked = hasDiscount, onCheckedChange = { hasDiscount = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.sales_deposit_label))
                        Text(
                            stringResource(Res.string.sales_deposit_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isDeposit, onCheckedChange = { isDeposit = it })
                }
                var rankExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = rankExpanded, onExpandedChange = { rankExpanded = it }) {
                    OutlinedTextField(
                        value = rank?.name ?: stringResource(Res.string.sales_rank_none_required),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.sales_required_rank_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rankExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = rankExpanded, onDismissRequest = { rankExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.sales_rank_none_required)) },
                            onClick = { rank = null; rankExpanded = false }
                        )
                        VolunteerRank.entries.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.name) },
                                onClick = { rank = r; rankExpanded = false }
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return@Button
                            val venuesToSave = selectedVenues.ifEmpty { setOf(PosVenueScope.GLOBAL) }
                            onSave(
                                SalesSheetItem(
                                    id = initial?.id ?: 0,
                                    sheetsId = initial?.sheetsId,
                                    name = name.trim(),
                                    price = price,
                                    hasDiscount = hasDiscount,
                                    isDeposit = isDeposit,
                                    requiredRank = rank,
                                    categories = SalesCategory.formatList(selectedCategories),
                                    subcategory = if (subcategoriesEnabled) {
                                        PosSubcategoryCatalog.normalizeName(subcategory)
                                    } else {
                                        initial?.subcategory.orEmpty()
                                    },
                                    emoji = emoji.trim(),
                                    availableVenues = PosVenueScope.formatVenueList(venuesToSave),
                                    isActive = initial?.isActive ?: true,
                                    lastModified = System.currentTimeMillis(),
                                )
                            )
                        },
                        // Negative prices are reserved for deposit returns, which the POS derives
                        // itself — letting one be typed here would hand out credit unchecked.
                        enabled = name.isNotBlank() && (priceText.toDoubleOrNull() ?: -1.0) >= 0.0
                    ) {
                        Text(stringResource(if (initial == null) Res.string.add else Res.string.sales_update_item))
                    }
                }
            }
        }
        }
    }
}
