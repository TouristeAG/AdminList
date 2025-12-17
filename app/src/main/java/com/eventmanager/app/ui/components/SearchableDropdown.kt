package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
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
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    
    val filteredItems = remember(items, searchQuery.text) {
        if (searchQuery.text.isEmpty()) {
            items
        } else {
            items.filter { item ->
                searchText(item).contains(searchQuery.text, ignoreCase = true)
            }
        }
    }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (expanded) searchQuery else TextFieldValue(selectedItem?.let { itemText(it) } ?: ""),
            onValueChange = { 
                if (expanded) {
                    searchQuery = it
                }
            },
            readOnly = !expanded,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = {
                Row {
                    if (expanded && searchQuery.text.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                searchQuery = TextFieldValue("")
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
                searchQuery = TextFieldValue("")
            }
        ) {
            if (filteredItems.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No volunteers found") },
                    onClick = { }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                ) {
                    items(filteredItems) { item ->
                        DropdownMenuItem(
                            text = { Text(itemText(item)) },
                            onClick = {
                                onItemSelected(item)
                                expanded = false
                                searchQuery = TextFieldValue("")
                            }
                        )
                    }
                }
            }
        }
    }
}
