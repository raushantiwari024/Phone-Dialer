package com.raushan.phone.ui.contacts

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.data.models.CallLogEntry
import com.raushan.phone.data.models.Contact
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: Long?,
    phoneNumber: String?,
    onBackClick: () -> Unit,
    onCallClick: (String) -> Unit,
    viewModel: ContactDetailViewModel = viewModel()
) {
    val contact by viewModel.contact.collectAsState()
    val callHistory by viewModel.callHistory.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(contactId, phoneNumber) {
        viewModel.loadContact(contactId, phoneNumber)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    contact?.let { c ->
                        if (c.id != -1L) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Edit contact intent
                                val intent = Intent(Intent.ACTION_EDIT).apply {
                                    data = ContentUris_withAppendedId(c.id)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot edit contact", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Contact")
                            }
                        }
                    }
                    IconButton(onClick = { /* More actions */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        contact?.let { c ->
            val isUnsaved = c.id == -1L
            val isCorp = isBusiness(c.name)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Identity Section (Avatar & Info)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(112.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isCorp) {
                                    Icon(
                                        imageVector = Icons.Default.Apartment,
                                        contentDescription = "Business",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                } else {
                                    val monogram = getMonogram(if (isUnsaved) null else c.name, c.number)
                                    Text(
                                        text = monogram,
                                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isUnsaved) "Unsaved Number" else c.name,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = c.number,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ContactAction(
                            icon = Icons.Default.Call,
                            label = "Call",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCallClick(c.number)
                            }
                        )
                        Spacer(modifier = Modifier.width(32.dp))
                        ContactAction(
                            icon = Icons.AutoMirrored.Filled.Message,
                            label = "Message",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${c.number}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot send message", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(32.dp))
                        ContactAction(
                            icon = Icons.Default.VideoCall,
                            label = "Video",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Video call not supported by operator", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Unsaved Contact Action Card
                if (isUnsaved) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "This number is not in your contacts list.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                                putExtra(ContactsContract.Intents.Insert.PHONE, c.number)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot add contact", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Create New", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                                putExtra(ContactsContract.Intents.Insert.PHONE, c.number)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot edit contact", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Add to Existing", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Info Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Contact info",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = c.number,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Mobile",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:${c.number}")
                                            }
                                            context.startActivity(intent)
                                        },
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Message,
                                            contentDescription = "Message",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent History Section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent History",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "VIEW ALL",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (callHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recent history for this number",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(callHistory) { log ->
                        CallHistoryItem(log = log)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryItem(log: CallLogEntry) {
    val isMissed = log.type == CallLog.Calls.MISSED_TYPE
    val typeLabel = when (log.type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming call"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing call"
        CallLog.Calls.MISSED_TYPE -> "Missed call"
        else -> "Incoming call"
    }

    val icon = when (log.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
        else -> Icons.AutoMirrored.Filled.CallReceived
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon container
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (isMissed) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(2.dp))

            val timeText = formatHistoryDate(log.date)
            val durationText = formatDuration(log.duration)
            val subtitle = if (durationText.isNotEmpty()) {
                "$timeText • $durationText"
            } else {
                timeText
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMissed) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ContentUris_withAppendedId(id: Long): Uri {
    return ContentUris_withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
}

private fun ContentUris_withAppendedId(contentUri: Uri, id: Long): Uri {
    return Uri.withAppendedPath(contentUri, id.toString())
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

private fun formatHistoryDate(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    
    return when {
        isSameDay(cal, today) -> "Today, $timeStr"
        isSameDay(cal, yesterday) -> "Yesterday, $timeStr"
        else -> {
            val sdf = SimpleDateFormat("EEEE, MMMM d, h:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun formatDuration(duration: Long): String {
    if (duration <= 0) return ""
    val mins = duration / 60
    val secs = duration % 60
    return when {
        mins > 0 && secs > 0 -> "$mins min $secs sec"
        mins > 0 -> "$mins min"
        else -> "$secs sec"
    }
}

@Composable
fun ContactAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
