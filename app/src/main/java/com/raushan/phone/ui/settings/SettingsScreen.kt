package com.raushan.phone.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raushan.phone.data.models.SwipeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sharedPrefs = remember { context.getSharedPreferences("dialer_settings", Context.MODE_PRIVATE) }

    var swipeLeft by remember {
        mutableStateOf(
            SwipeAction.valueOf(
                sharedPrefs.getString("swipe_left", SwipeAction.DELETE.name) ?: SwipeAction.DELETE.name
            )
        )
    }

    var swipeRight by remember {
        mutableStateOf(
            SwipeAction.valueOf(
                sharedPrefs.getString("swipe_right", SwipeAction.CALL.name) ?: SwipeAction.CALL.name
            )
        )
    }

    var showLeftDialog by remember { mutableStateOf(false) }
    var showRightDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Description
            item {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Manage your phone preferences",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // GESTURES SECTION
            item {
                SettingsSectionHeader("Gestures")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.SwipeLeft,
                            title = "Swipe Left Action",
                            value = swipeLeft.getLabel(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showLeftDialog = true
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                        SettingsRow(
                            icon = Icons.Default.SwipeRight,
                            title = "Swipe Right Action",
                            value = swipeRight.getLabel(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showRightDialog = true
                            }
                        )
                    }
                }
            }

            // GENERAL SECTION
            item {
                SettingsSectionHeader("General")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.PhoneAndroid,
                            title = "Default Phone App",
                            value = "Yes",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Palette,
                            title = "Theme",
                            value = "Dark",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Dark mode enabled by system theme", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // PRIVACY SECTION
            item {
                SettingsSectionHeader("Privacy")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Block,
                            title = "Blocked Numbers",
                            value = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Blocked contacts managed by system provider", Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Security,
                            title = "App Permissions",
                            value = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            // ABOUT SECTION
            item {
                SettingsSectionHeader("About")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Info,
                            title = "Version",
                            value = "2.4.0",
                            onClick = {}
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Help,
                            title = "Help & Feedback",
                            value = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Feedback sent successfully!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Swipe Left configuration dialog
    if (showLeftDialog) {
        SwipeActionPickerDialog(
            title = "Swipe Left Action",
            currentSelection = swipeLeft,
            onDismiss = { showLeftDialog = false },
            onSelect = { action ->
                swipeLeft = action
                sharedPrefs.edit().putString("swipe_left", action.name).apply()
                showLeftDialog = false
            }
        )
    }

    // Swipe Right configuration dialog
    if (showRightDialog) {
        SwipeActionPickerDialog(
            title = "Swipe Right Action",
            currentSelection = swipeRight,
            onDismiss = { showRightDialog = false },
            onSelect = { action ->
                swipeRight = action
                sharedPrefs.edit().putString("swipe_right", action.name).apply()
                showRightDialog = false
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SwipeActionPickerDialog(
    title: String,
    currentSelection: SwipeAction,
    onDismiss: () -> Unit,
    onSelect: (SwipeAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SwipeAction.values().forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = action == currentSelection,
                            onClick = { onSelect(action) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = action.getLabel(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
