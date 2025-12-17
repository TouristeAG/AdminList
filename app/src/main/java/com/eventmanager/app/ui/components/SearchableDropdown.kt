package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableDropdown(
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    itemText: (T) -> String,
    label: String,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchText: (T) -> String = itemText
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isEmpty()) {
            items
        } else {
            items.filter { item ->
                searchText(item).contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (expanded) searchQuery else (selectedItem?.let { itemText(it) } ?: ""),
            onValueChange = { newValue ->
                if (expanded) {
                    searchQuery = newValue
                }
            },
            readOnly = !expanded,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = {
                Row {
                    if (expanded && searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                searchQuery = ""
                            }
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            enabled = enabled
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { 
                expanded = false
                searchQuery = ""
            },
            modifier = Modifier.heightIn(max = 200.dp)
        ) {
            if (filteredItems.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No volunteers found") },
                    onClick = { }
                )
            } else {
                // Use regular Column with verticalScroll instead of LazyColumn to avoid intrinsic measurement issues
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filteredItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(itemText(item)) },
                            onClick = {
                                onItemSelected(item)
                                expanded = false
                                searchQuery = ""
                            }
                        )
                    }
                }
            }
        }
    }
}
