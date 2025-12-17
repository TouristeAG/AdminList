package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmanager.app.ui.utils.isTablet
import androidx.compose.ui.platform.LocalContext
import com.eventmanager.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(searchText)) }
    val isPhone = !isTablet()
    
    LaunchedEffect(searchText) {
        if (textFieldValue.text != searchText) {
textFieldValue = TextFieldValue(searchText)
        }
    }
    
    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            onSearchTextChange(newValue.text)
        },
        placeholder = { 
            Text(
                text = placeholder,
                style = if (isPhone) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            ) 
        },
        textStyle = if (isPhone) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (isPhone) 18.dp else 24.dp)
            )
        },
        trailingIcon = {
            if (textFieldValue.text.isNotEmpty()) {
                IconButton(
                    onClick = {
                        textFieldValue = TextFieldValue("")
                        onSearchTextChange("")
                    }
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (isPhone) 18.dp else 24.dp)
                    )
                }
            }
        },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarWithFilter(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    placeholder: String = "Search...",
    filterOptions: List<String> = emptyList(),
    selectedFilter: String? = null,
    onFilterChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFilterDropdown by remember { mutableStateOf(false) }
    val isPhone = !isTablet()
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isPhone) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                placeholder = placeholder,
                modifier = Modifier.weight(1f)
            )
            
            if (filterOptions.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = showFilterDropdown,
                    onExpandedChange = { showFilterDropdown = !showFilterDropdown }
                ) {
                    val context = LocalContext.current
                    val allLabel = context.getString(R.string.filter_all)
                    OutlinedTextField(
                        value = selectedFilter ?: allLabel,
                        onValueChange = { },
                        readOnly = true,
                        textStyle = if (isPhone) 
                            MaterialTheme.typography.bodySmall 
                        else 
                            MaterialTheme.typography.bodyMedium,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = showFilterDropdown
                            )
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .width(if (isPhone) 120.dp else 160.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showFilterDropdown,
                        onDismissRequest = { showFilterDropdown = false },
                        modifier = Modifier
                            .width(if (isPhone) 120.dp else 160.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    allLabel,
                                    style = if (isPhone) 
                                        MaterialTheme.typography.bodySmall
                                    else 
                                        MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            onClick = {
                                onFilterChange(null)
                                showFilterDropdown = false
                            },
                            modifier = Modifier
                                .height(if (isPhone) 48.dp else 56.dp)
                                .background(
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        )
                        filterOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        option,
                                        style = if (isPhone) 
                                            MaterialTheme.typography.bodySmall
                                        else 
                                            MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                onClick = {
                                    onFilterChange(option)
                                    showFilterDropdown = false
                                },
                                modifier = Modifier
                                    .height(if (isPhone) 48.dp else 56.dp)
                                    .background(
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
