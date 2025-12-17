package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.utils.VolunteerActivityManager
import com.eventmanager.app.ui.utils.*

@Composable
fun CleanupInactiveVolunteersDialog(
    volunteers: List<Volunteer>,
    onConfirm: (Int) -> Unit, // Int represents years of inactivity
    onDismiss: () -> Unit
) {
    var selectedYears by remember { mutableStateOf(4) } // Default to 4 years
    var showPreview by remember { mutableStateOf(false) }
    
    // Calculate volunteers that would be deleted based on selected years
    val volunteersToDelete = remember(selectedYears) {
        volunteers.filter { volunteer ->
            val daysSinceLastShift = VolunteerActivityManager.getDaysSinceLastShift(volunteer)
            daysSinceLastShift != null && daysSinceLastShift >= (selectedYears * 365L)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Cleanup Inactive Volunteers")
            }
        },
        text = {
            Column {
                Text(
                    text = "Choose how long volunteers must be inactive to be deleted:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Years selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Inactive for:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Slider(
                        value = selectedYears.toFloat(),
                        onValueChange = { selectedYears = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8, // 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 years
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = "$selectedYears year${if (selectedYears > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preview button
                Button(
                    onClick = { showPreview = !showPreview },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showPreview) "Hide Preview" else "Preview Volunteers to Delete (${volunteersToDelete.size})"
                    )
                }
                
                // Preview list
                if (showPreview && volunteersToDelete.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Volunteers to be deleted:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(volunteersToDelete) { volunteer ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = volunteer.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = VolunteerActivityManager.getActivityStatusText(volunteer),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (showPreview && volunteersToDelete.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "No volunteers found that have been inactive for $selectedYears year${if (selectedYears > 1) "s" else ""} or more.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Warning message
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "⚠️ Warning:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "This action will permanently delete ${volunteersToDelete.size} volunteer${if (volunteersToDelete.size != 1) "s" else ""} and all their associated job records. This cannot be undone!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedYears) },
                enabled = volunteersToDelete.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete ${volunteersToDelete.size} Volunteer${if (volunteersToDelete.size != 1) "s" else ""}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
