package com.eventmanager.app.ui.components
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

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
import androidx.compose.ui.window.PopupProperties

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
        onExpandedChange = { expanded = it },
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
        
        // focusable=false keeps IME on the TextField (backspace works). dismissOnClickOutside=false
        // avoids IME/key events being treated as an outside tap, which was closing the menu on each key.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            },
            modifier = Modifier
                .exposedDropdownSize(true)
                .heightIn(max = 200.dp),
            properties = PopupProperties(
                focusable = false,
                dismissOnClickOutside = false
            )
        ) {
            if (filteredItems.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.dropdown_no_volunteers_found)) },
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
