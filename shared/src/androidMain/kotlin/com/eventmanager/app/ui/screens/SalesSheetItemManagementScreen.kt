package com.eventmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.R
import com.eventmanager.app.data.models.SalesSheetItem
import com.eventmanager.app.data.models.VolunteerRank
import com.eventmanager.app.ui.components.SearchBarWithFilter
import com.eventmanager.app.ui.utils.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesSheetItemManagementScreen(
    items: List<SalesSheetItem>,
    onAddItem: (SalesSheetItem) -> Unit,
    onUpdateItem: (SalesSheetItem) -> Unit,
    onDeleteItem: (SalesSheetItem) -> Unit,
    onUpdateItemStatus: (Long, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
                    Icon(Icons.Default.Close, contentDescription = context.getString(R.string.close))
                }
                Text(
                    text = context.getString(R.string.sales_items_title),
                    style = getResponsiveTypography(),
                    fontWeight = FontWeight.Bold
                )
            }
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.add_sales_item))
            }
        }

        Spacer(Modifier.height(12.dp))
        SearchBarWithFilter(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            placeholder = context.getString(R.string.search_sales_items_placeholder),
            filterOptions = emptyList(),
            selectedFilter = null,
            onFilterChange = { }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = context.getString(R.string.sales_items_count, filtered.count { it.isActive }, filtered.size),
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
            title = context.getString(R.string.add_sales_item),
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { item ->
                onAddItem(item)
                showAddDialog = false
            }
        )
    }
    editItem?.let { item ->
        SalesSheetItemEditorDialog(
            title = context.getString(R.string.edit_sales_item),
            initial = item,
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
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold)
                Text(
                    text = context.getString(
                        R.string.sales_item_summary_line,
                        item.price,
                        if (item.hasDiscount) context.getString(R.string.yes) else context.getString(R.string.no),
                        item.requiredRank?.name ?: context.getString(R.string.sales_rank_none_required)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = item.isActive, onCheckedChange = { onToggleActive() })
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = context.getString(R.string.edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = context.getString(R.string.delete))
            }
        }
    }
}

@Composable
private fun SalesSheetItemEditorDialog(
    title: String,
    initial: SalesSheetItem?,
    onDismiss: () -> Unit,
    onSave: (SalesSheetItem) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var priceText by remember { mutableStateOf(initial?.price?.toString().orEmpty()) }
    var hasDiscount by remember { mutableStateOf(initial?.hasDiscount ?: false) }
    var rank by remember { mutableStateOf(initial?.requiredRank) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(context.getString(R.string.name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(context.getString(R.string.sales_price_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.sales_discount_label), modifier = Modifier.weight(1f))
                    Switch(checked = hasDiscount, onCheckedChange = { hasDiscount = it })
                }
                var rankExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = rankExpanded, onExpandedChange = { rankExpanded = it }) {
                    OutlinedTextField(
                        value = rank?.name ?: context.getString(R.string.sales_rank_none_required),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.getString(R.string.sales_required_rank_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rankExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = rankExpanded, onDismissRequest = { rankExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.sales_rank_none_required)) },
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
                    TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull() ?: return@Button
                            onSave(
                                SalesSheetItem(
                                    name = name.trim(),
                                    price = price,
                                    hasDiscount = hasDiscount,
                                    requiredRank = rank
                                )
                            )
                        },
                        enabled = name.isNotBlank() && priceText.toDoubleOrNull() != null
                    ) {
                        Text(context.getString(if (initial == null) R.string.add else R.string.sales_update_item))
                    }
                }
            }
        }
    }
}
