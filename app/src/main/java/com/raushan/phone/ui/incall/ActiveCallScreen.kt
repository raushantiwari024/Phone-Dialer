package com.raushan.phone.ui.incall

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
fun activeCallScreen(
    viewModel: InCallViewModel = viewModel()
) {
    val callerName by viewModel.callerName.collectAsStateWithLifecycle()
    val callerNumber by viewModel.callerNumber.collectAsStateWithLifecycle()
    val callerPhotoUri by viewModel.callerPhotoUri.collectAsStateWithLifecycle()
    val callDuration by viewModel.callDuration.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()

    val loadingText = stringResource(R.string.caller_loading)
    val avatarFallback = stringResource(R.string.avatar_fallback)
    val photoBitmap = rememberContactPhoto(callerPhotoUri)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        // Atmospheric gradient in background
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(80.dp))

                // Avatar Monogram
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = AvatarSurface,
                    border = BorderStroke(2.dp, OutlineVariant.copy(alpha = 0.5f))
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
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                color = OnSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = OnSurface
                )

                Text(
                    text = callDuration,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    ),
                    color = ElectricBlue,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Call Actions Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        callActionButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isMuted) {
                                stringResource(R.string.muted_label)
                            } else {
                                stringResource(R.string.mute_label)
                            },
                            isActive = isMuted,
                            onClick = { viewModel.toggleMute() }
                        )
                        callActionButton(
                            icon = Icons.Default.Dialpad,
                            label = stringResource(R.string.keypad_label),
                            onClick = {}
                        )
                        callActionButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = stringResource(R.string.speaker_label),
                            isActive = isSpeakerOn,
                            onClick = { viewModel.toggleSpeaker() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        callActionButton(
                            icon = Icons.Default.Add,
                            label = stringResource(R.string.add_call_label),
                            onClick = {}
                        )
                        callActionButton(
                            icon = Icons.Default.VideoCall,
                            label = stringResource(R.string.video_label),
                            onClick = {}
                        )
                        callActionButton(
                            icon = Icons.Default.Pause,
                            label = stringResource(R.string.hold_label),
                            onClick = {}
                        )
                    }
                }

                // End Call Button
                FloatingActionButton(
                    onClick = { viewModel.endCall() },
                    containerColor = Error,
                    contentColor = OnError,
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = stringResource(R.string.end_call_label),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
fun callActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = if (isActive) ElectricBlue else AvatarSurface.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f)),
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) OnPrimaryContainer else OnSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant
        )
    }
}
