package com.raushan.phone.ui.dialpad

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(
    viewModel: DialpadViewModel = viewModel(),
    onCallClick: (String) -> Unit
) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Check clipboard for phone number
    val clipboardText = remember(phoneNumber.text) {
        clipboardManager.getText()?.text?.filter { it.isDigit() || it == '+' } ?: ""
    }

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
                    items(searchResults) { result ->
                        SimpleContactItem(
                            result = result,
                            query = phoneNumber.text,
                            onClick = { onCallClick(result.number) }
                        )
                    }
                }
            }
        }

        // Dialed Number Display and Suggestion Chip
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (clipboardText.isNotEmpty() && phoneNumber.text.isEmpty()) {
                SuggestionChip(
                    onClick = { viewModel.onPaste(clipboardText) },
                    label = { Text("Paste: $clipboardText") },
                    modifier = Modifier.padding(bottom = 8.dp),
                    shape = CircleShape,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = null
                )
            }

            BasicTextField(
                value = phoneNumber,
                onValueChange = { viewModel.onPhoneNumberChange(it) },
                readOnly = true,
                textStyle = TextStyle(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = PhoneVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        innerTextField()
                    }
                }
            )
        }

        // Quick Actions Shortcut Row
        if (phoneNumber.text.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber.text)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open Contacts", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Contact", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(16.dp))

                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:${phoneNumber.text}")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot send SMS", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Message,
                        contentDescription = "Send Message",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send SMS", fontSize = 12.sp)
                }
            }
        }

        // Keypad
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.width(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(keys) { keyInfo ->
                DialKey(
                    info = keyInfo,
                    onClick = { viewModel.onDigitClick(keyInfo.number) },
                    onLongClick = {
                        if (keyInfo.number == "0") {
                            viewModel.onDigitClick("+")
                        } else {
                            val speedDial = viewModel.getSpeedDialNumber(context, keyInfo.number)
                            if (!speedDial.isNullOrEmpty()) {
                                onCallClick(speedDial)
                                Toast.makeText(context, "Calling speed dial: $speedDial", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons (Call & Backspace)
        Box(
            modifier = Modifier
                .width(300.dp)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = {
                    if (phoneNumber.text.isNotEmpty()) {
                        onCallClick(phoneNumber.text)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(32.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        if (phoneNumber.text.isNotEmpty())
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        else
                            Color.Transparent
                    )
                    .combinedClickable(
                        enabled = phoneNumber.text.isNotEmpty(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onDeleteClick()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onClearAll()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    modifier = Modifier.size(24.dp),
                    tint = if (phoneNumber.text.isNotEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

data class DialKeyInfo(val number: String, val letters: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialKey(
    info: DialKeyInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
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
    result: DialpadSearchResult,
    query: String,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

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
            color = if (result.isCallLog) {
                if (result.callType == CallLog.Calls.MISSED_TYPE) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                }
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (result.isCallLog) {
                    val icon = when (result.callType) {
                        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived
                        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
                        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
                        else -> Icons.Default.Person
                    }
                    val tint = if (result.callType == CallLog.Calls.MISSED_TYPE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tint
                    )
                } else {
                    Text(
                        text = result.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getHighlightedText(result.name, query, isNumber = false, highlightColor = primaryColor),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = getHighlightedText(result.number, query, isNumber = true, highlightColor = primaryColor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun getHighlightedText(text: String, query: String, isNumber: Boolean, highlightColor: Color): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(text)

    val highlightStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        color = highlightColor
    )

    return buildAnnotatedString {
        if (isNumber) {
            val cleanText = text.replace(Regex("[^0-9+]"), "")
            val cleanQuery = query.replace(Regex("[^0-9+]"), "")
            val index = cleanText.indexOf(cleanQuery, ignoreCase = true)
            if (index != -1 && cleanQuery.isNotEmpty()) {
                var rawMatchStart = -1
                var rawMatchEnd = -1
                var cleanIdx = 0
                for (i in text.indices) {
                    val char = text[i]
                    if (char.isDigit() || char == '+') {
                        if (cleanIdx == index) {
                            rawMatchStart = i
                        }
                        if (cleanIdx == index + cleanQuery.length - 1) {
                            rawMatchEnd = i + 1
                            break
                        }
                        cleanIdx++
                    }
                }
                if (rawMatchStart != -1 && rawMatchEnd != -1) {
                    append(text.substring(0, rawMatchStart))
                    withStyle(highlightStyle) {
                        append(text.substring(rawMatchStart, rawMatchEnd))
                    }
                    append(text.substring(rawMatchEnd))
                } else {
                    append(text)
                }
            } else {
                append(text)
            }
        } else {
            val words = text.split(" ")
            var matchedStart = -1
            var matchedEnd = -1
            var charIndex = 0
            for (word in words) {
                if (word.length >= query.length) {
                    val wordT9 = word.take(query.length).lowercase().map { char ->
                        when (char) {
                            'a', 'b', 'c' -> '2'
                            'd', 'e', 'f' -> '3'
                            'g', 'h', 'i' -> '4'
                            'j', 'k', 'l' -> '5'
                            'm', 'n', 'o' -> '6'
                            'p', 'q', 'r', 's' -> '7'
                            't', 'u', 'v' -> '8'
                            'w', 'x', 'y', 'z' -> '9'
                            else -> ' '
                        }
                    }.joinToString("")
                    if (wordT9 == query) {
                        matchedStart = text.indexOf(word, charIndex)
                        matchedEnd = matchedStart + query.length
                        break
                    }
                }
                charIndex += word.length + 1
            }

            if (matchedStart != -1 && matchedEnd != -1) {
                append(text.substring(0, matchedStart))
                withStyle(highlightStyle) {
                    append(text.substring(matchedStart, matchedEnd))
                }
                append(text.substring(matchedEnd))
            } else {
                append(text)
            }
        }
    }
}

class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.contains("*") || raw.contains("#") || raw.length > 10 || raw.length < 3) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val digits = raw.filter { it.isDigit() }
        if (digits.length != raw.length) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val formatted = StringBuilder()
        val originalToTransformed = mutableListOf<Int>()
        val transformedToOriginal = mutableListOf<Int>()

        var currentTransformedIndex = 0
        for (i in 0..raw.length) {
            originalToTransformed.add(currentTransformedIndex)
            if (i < raw.length) {
                val char = raw[i]
                if (i == 0) {
                    formatted.append("(")
                    currentTransformedIndex++
                }
                formatted.append(char)
                currentTransformedIndex++
                if (i == 2) {
                    formatted.append(") ")
                    currentTransformedIndex += 2
                }
                if (i == 5) {
                    formatted.append("-")
                    currentTransformedIndex++
                }
            }
        }

        for (i in 0..formatted.length) {
            var origIdx = 0
            while (origIdx < originalToTransformed.size && originalToTransformed[origIdx] <= i) {
                origIdx++
            }
            transformedToOriginal.add((origIdx - 1).coerceIn(0, raw.length))
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return originalToTransformed[offset.coerceIn(0, raw.length)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transformedToOriginal[offset.coerceIn(0, formatted.length)]
            }
        }

        return TransformedText(AnnotatedString(formatted.toString()), offsetMapping)
    }
}

