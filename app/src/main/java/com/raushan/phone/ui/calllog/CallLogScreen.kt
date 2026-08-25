package com.raushan.phone.ui.calllog

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.raushan.phone.ui.incall.rememberContactPhoto
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.data.CallLogGroup
import com.raushan.phone.data.models.CallLogEntry
import com.raushan.phone.data.models.SwipeAction
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun callLogScreen(
    viewModel: CallLogViewModel = viewModel(),
    onEntryClick: (CallLogEntry) -> Unit,
    onCallClick: (String) -> Unit,
    onEditBeforeCall: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onDialpadClick: () -> Unit
) {
    val currentFilter by viewModel.currentFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val groupedCallLogs by viewModel.groupedCallLogs.collectAsState()
    val swipeLeftAction by viewModel.swipeLeftAction.collectAsState()
    val swipeRightAction by viewModel.swipeRightAction.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Searchable Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search recents...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.setSearchQuery("")
                                    isSearching = false
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close search"
                                    )
                                }
                            }
                        )
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Phone",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box {
                            var showOptions by remember { mutableStateOf(false) }
                            IconButton(onClick = { showOptions = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showOptions,
                                onDismissRequest = { showOptions = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSettingsClick()
                                        showOptions = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Sub-Header Recents & Filter
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Recents",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    segmentedControl(
                        selectedFilter = currentFilter,
                        onFilterSelected = { filter ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setFilter(filter)
                        },
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .width(280.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                // Call Log List
                if (groupedCallLogs.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (currentFilter == CallLogFilter.MISSED) "No missed calls" else "No recent calls",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        groupedCallLogs.forEach { (dateHeader, groups) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = dateHeader.uppercase(),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            items(
                                items = groups,
                                key = { it.mainEntry.id }
                            ) { group ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        when (dismissValue) {
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                if (swipeLeftAction != SwipeAction.NONE) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    handleSwipeAction(
                                                        swipeLeftAction,
                                                        group,
                                                        context,
                                                        viewModel,
                                                        onCallClick
                                                    )
                                                    true
                                                } else {
                                                    false
                                                }
                                            }

                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                if (swipeRightAction != SwipeAction.NONE) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    handleSwipeAction(
                                                        swipeRightAction,
                                                        group,
                                                        context,
                                                        viewModel,
                                                        onCallClick
                                                    )
                                                    true
                                                } else {
                                                    false
                                                }
                                            }

                                            else -> false
                                        }
                                    },
                                    positionalThreshold = { distance -> distance * 0.8f }
                                )

                                LaunchedEffect(dismissState.currentValue) {
                                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                        val action =
                                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) swipeLeftAction else swipeRightAction
                                        if (action != SwipeAction.DELETE) {
                                            dismissState.reset()
                                        }
                                    }
                                }

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false, // swipeRightAction != SwipeAction.NONE,
                                    enableDismissFromEndToStart = false, // swipeLeftAction != SwipeAction.NONE,
                                    backgroundContent = {
                                        val direction = dismissState.dismissDirection
                                        val action = when (direction) {
                                            SwipeToDismissBoxValue.StartToEnd -> swipeRightAction
                                            SwipeToDismissBoxValue.EndToStart -> swipeLeftAction
                                            else -> SwipeAction.NONE
                                        }

                                        if (action != SwipeAction.NONE) {
                                            val color = when (action) {
                                                SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer
                                                SwipeAction.CALL -> MaterialTheme.colorScheme.primaryContainer
                                                SwipeAction.MESSAGE -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> Color.Transparent
                                            }

                                            val icon = when (action) {
                                                SwipeAction.DELETE -> Icons.Default.Delete
                                                SwipeAction.CALL -> Icons.Default.Call
                                                SwipeAction.MESSAGE -> Icons.AutoMirrored.Filled.Message
                                                else -> Icons.Default.Delete
                                            }

                                            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                                Alignment.CenterStart
                                            } else {
                                                Alignment.CenterEnd
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(color)
                                                    .padding(horizontal = 24.dp),
                                                contentAlignment = alignment
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = action.getLabel(),
                                                    tint = when (action) {
                                                        SwipeAction.DELETE -> MaterialTheme.colorScheme.onErrorContainer
                                                        SwipeAction.CALL -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        SwipeAction.MESSAGE -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    callLogItem(
                                        group = group,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onEntryClick(group.mainEntry)
                                        },
                                        onCallClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onCallClick(group.mainEntry.number)
                                        },
                                        onEditBeforeCall = onEditBeforeCall,
                                        onDelete = { viewModel.deleteGroup(group) }
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                            }
                        }
                    }
                }
            }

            // Dialpad FAB
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDialpadClick()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = "Dialpad",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun segmentedControl(
    selectedFilter: CallLogFilter,
    onFilterSelected: (CallLogFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallLogFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            val label = when (filter) {
                CallLogFilter.ALL -> "All"
                CallLogFilter.MISSED -> "Missed"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        else Color.Transparent
                    )
                    .clickable { onFilterSelected(filter) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun callLogItem(
    group: CallLogGroup,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onEditBeforeCall: (String) -> Unit,
    onDelete: () -> Unit
) {
    val entry = group.mainEntry
    val isMissed = entry.type == CallLog.Calls.MISSED_TYPE
    val hasName = !entry.cachedName.isNullOrBlank()
    val isCorp = isBusiness(entry.cachedName)

    val nameSuffix = if (group.totalCount > 1) " (${group.totalCount})" else ""
    val displayName = (if (hasName) entry.cachedName else entry.number) + nameSuffix

    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val photoBitmap = rememberContactPhoto(entry.photoUri)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                )
                .padding(vertical = 12.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Avatar Circle
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (isMissed) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                border = if (isMissed) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = "Contact Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isCorp) {
                        Icon(
                            imageVector = Icons.Default.Apartment,
                            contentDescription = "Business",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        val monogram = getMonogram(entry.cachedName, entry.number)
                        Text(
                            text = monogram,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isMissed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Center Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (entry.type) {
                        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived
                        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
                        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
                        else -> Icons.AutoMirrored.Filled.CallReceived
                    }

                    val tint = if (isMissed) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = tint
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    val numberLabel = getNumberTypeLabel(entry.cachedNumberType, entry.cachedNumberLabel)
                    Text(
                        text = numberLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMissed) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right Info & Actions
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = formatTime(entry.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            onCallClick()
                        },
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy number") },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboardManager.setText(AnnotatedString(entry.number))
                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Edit number before call") },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEditBeforeCall(entry.number)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Delete from log") },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    showMenu = false
                }
            )
        }
    }
}

private fun handleSwipeAction(
    action: SwipeAction,
    group: CallLogGroup,
    context: android.content.Context,
    viewModel: CallLogViewModel,
    onCallClick: (String) -> Unit
) {
    when (action) {
        SwipeAction.CALL -> onCallClick(group.mainEntry.number)
        SwipeAction.MESSAGE -> {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${group.mainEntry.number}")
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot send message", Toast.LENGTH_SHORT).show()
            }
        }

        SwipeAction.DELETE -> viewModel.deleteGroup(group)
        SwipeAction.NONE -> {}
    }
}

private fun isBusiness(name: String?): Boolean {
    if (name == null) return false
    val lower = name.lowercase()
    return lower.contains("corp") || lower.contains("hq") || lower.contains("office") ||
        lower.contains("service") || lower.contains("company") || lower.contains("inc.")
}

private fun getMonogram(name: String?, number: String): String {
    if (!name.isNullOrBlank()) {
        val parts = name.split(" ").filter { it.isNotEmpty() }
        if (parts.isNotEmpty()) {
            return if (parts.size >= 2) {
                (parts[0].take(1) + parts[1].take(1)).uppercase()
            } else {
                parts[0].take(2).uppercase()
            }
        }
    }
    val clean = number.filter { it.isDigit() }
    return if (clean.length >= 2) clean.take(2) else number.take(2)
}

private fun getNumberTypeLabel(type: Int?, customLabel: String?): String {
    if (type == null) return "Mobile"
    return when (type) {
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel ?: "Custom"
        else -> "Mobile"
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
