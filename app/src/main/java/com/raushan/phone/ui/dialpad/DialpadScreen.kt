package com.raushan.phone.ui.dialpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.data.models.Contact

@Composable
fun DialpadScreen(
    viewModel: DialpadViewModel = viewModel(),
    onCallClick: (String) -> Unit
) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    
    val keys = listOf(
        DialKeyInfo("1", ""),
        DialKeyInfo("2", "ABC"),
        DialKeyInfo("3", "DEF"),
        DialKeyInfo("4", "GHI"),
        DialKeyInfo("5", "JKL"),
        DialKeyInfo("6", "MNO"),
        DialKeyInfo("7", "PQRS"),
        DialKeyInfo("8", "TUV"),
        DialKeyInfo("9", "WXYZ"),
        DialKeyInfo("*", ""),
        DialKeyInfo("0", "+"),
        DialKeyInfo("#", "")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Search Results Area (Flexible)
        Box(modifier = Modifier.weight(1f)) {
            if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(searchResults) { contact ->
                        SimpleContactItem(contact = contact, onClick = { onCallClick(contact.number) })
                    }
                }
            }
        }
        
        // Dialed Number
        Text(
            text = phoneNumber,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 40.sp,
                fontWeight = FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 24.dp),
            maxLines = 1
        )

        // Keypad
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.width(300.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(keys) { keyInfo ->
                DialKey(
                    info = keyInfo,
                    onClick = { viewModel.onDigitClick(keyInfo.number) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(56.dp)) 

            FloatingActionButton(
                onClick = { onCallClick(phoneNumber) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(32.dp))
            }

            IconButton(
                onClick = { viewModel.onDeleteClick() },
                modifier = Modifier.padding(start = 24.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class DialKeyInfo(val number: String, val letters: String)

@Composable
fun DialKey(
    info: DialKeyInfo,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = info.number,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 32.sp
            )
            if (info.letters.isNotEmpty()) {
                Text(
                    text = info.letters,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun SimpleContactItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = contact.number,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
