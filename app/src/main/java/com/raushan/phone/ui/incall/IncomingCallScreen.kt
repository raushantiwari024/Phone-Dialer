package com.raushan.phone.ui.incall

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.R
import com.raushan.phone.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@Suppress("FunctionName")
fun incomingCallScreen(
    viewModel: InCallViewModel = viewModel()
) {
    val callerName by viewModel.callerName.collectAsStateWithLifecycle()
    val callerNumber by viewModel.callerNumber.collectAsStateWithLifecycle()
    val callerPhotoUri by viewModel.callerPhotoUri.collectAsStateWithLifecycle()
    val context = LocalContext.current

    incomingCallContent(
        callerName = callerName,
        callerNumber = callerNumber,
        callerPhotoUri = callerPhotoUri,
        onAnswer = { viewModel.answerCall() },
        onDecline = { viewModel.endCall() },
        onReplyMessage = { number ->
            viewModel.endCall()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.cannot_open_messages),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )
}

@Composable
fun rememberContactPhoto(photoUriString: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(photoUriString) { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(photoUriString) {
        if (!photoUriString.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(photoUriString)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val androidBitmap = BitmapFactory.decodeStream(stream)
                        if (androidBitmap != null) {
                            bitmap = androidBitmap.asImageBitmap()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ContactPhoto", "Error loading photo from URI: $photoUriString", e)
                }
            }
        } else {
            bitmap = null
        }
    }
    
    return bitmap
}

@Composable
@Suppress("FunctionName")
fun incomingCallContent(
    callerName: String,
    callerNumber: String,
    callerPhotoUri: String? = null,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onReplyMessage: (String) -> Unit
) {
    val loadingText = stringResource(R.string.caller_loading)
    val avatarFallback = stringResource(R.string.avatar_fallback)
    val photoBitmap = rememberContactPhoto(callerPhotoUri)

    // Infinite transition for pulsing ring effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        // Atmospheric background effects from the design brief
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ElectricBlue.copy(alpha = 0.05f),
                            Background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 64.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Status Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.incoming_call_label),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        ),
                        color = OnSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 2. Caller Information Center (Avatar + Name & Number)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar with Pulse effect
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(220.dp)
                    ) {
                        // Pulsing Ring background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = pulseAlpha
                                }
                                .background(
                                    color = ElectricBlue,
                                    shape = CircleShape
                                )
                        )
                        // Main Avatar Circle
                        Surface(
                            modifier = Modifier.size(140.dp),
                            shape = CircleShape,
                            color = AvatarSurface,
                            border = BorderStroke(4.dp, OutlineVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (photoBitmap != null) {
                                    Image(
                                        bitmap = photoBitmap,
                                        contentDescription = "Caller Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = if (callerName.isNotBlank() && callerName != loadingText) {
                                            callerName.take(1).uppercase()
                                        } else {
                                            avatarFallback
                                        },
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = OnSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Name & Number
                    Text(
                        text = callerName,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = OnSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = callerNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 3. Controls Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    // Quick Reply Option (Glassmorphic)
                    Surface(
                        modifier = Modifier.padding(bottom = 32.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier
                                .clickable { onReplyMessage(callerNumber) }
                                .padding(vertical = 12.dp, horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Message,
                                contentDescription = stringResource(R.string.reply_with_message),
                                tint = ElectricBlue,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(bottom = 2.dp)
                            )
                            Text(
                                text = stringResource(R.string.reply_with_message),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = OnSurface
                            )
                        }
                    }

                    // Action Buttons Row (Decline Left, Accept Right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline Button (Left)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onDecline,
                                containerColor = Error,
                                contentColor = OnError,
                                shape = CircleShape,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = stringResource(R.string.decline_label),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.decline_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = OnSurfaceVariant
                            )
                        }

                        // Accept Button (Right)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onAnswer,
                                containerColor = ElectricBlue,
                                contentColor = OnPrimaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = stringResource(R.string.answer_label),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.answer_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
@Suppress("FunctionName")
fun incomingCallContentPreview() {
    incomingCallContent(
        callerName = "Aaron Miller",
        callerNumber = "+1 (555) 0123",
        onAnswer = {},
        onDecline = {},
        onReplyMessage = {}
    )
}
