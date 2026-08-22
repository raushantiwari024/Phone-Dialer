package com.raushan.phone.ui.incall

import android.app.KeyguardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.R
import com.raushan.phone.telecom.model.CallState
import com.raushan.phone.ui.theme.AcceptGreen
import com.raushan.phone.ui.theme.AvatarSurface
import com.raushan.phone.ui.theme.Background
import com.raushan.phone.ui.theme.CallAccentGlow
import com.raushan.phone.ui.theme.DeclineRed
import com.raushan.phone.ui.theme.ElectricBlue
import com.raushan.phone.ui.theme.GlassBorder
import com.raushan.phone.ui.theme.GlassFill
import com.raushan.phone.ui.theme.OnAcceptGreen
import com.raushan.phone.ui.theme.OnDeclineRed
import com.raushan.phone.ui.theme.OnSurface
import com.raushan.phone.ui.theme.OnSurfaceVariant
import com.raushan.phone.ui.theme.OutlineVariant
import com.raushan.phone.ui.theme.PhoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("FunctionName")
fun incomingCallScreen(
    viewModel: InCallViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Swipe when locked, buttons when unlocked. A drag is deliberate friction that prevents a
    // pocket-answer; once the phone is already in hand and unlocked, that friction is just cost.
    val requireGesture = remember {
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    }

    incomingCallContent(
        state = state,
        onAction = viewModel::onAction,
        requireGesture = requireGesture,
    )
}

@Composable
@Suppress("FunctionName")
fun incomingCallContent(
    state: InCallUiState,
    onAction: (InCallAction) -> Unit,
    modifier: Modifier = Modifier,
    requireGesture: Boolean = false,
) {
    val call = state.primary ?: return

    Surface(modifier = modifier.fillMaxSize(), color = Background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(listOf(CallAccentGlow, Background))),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Real insets. This used to be padding(vertical = 64.dp), which is wrong on a
                    // device with a tall cutout, wrong in landscape, and wrong above the keyguard.
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                callerHeader(labelRes = R.string.call_status_ringing)

                Spacer(modifier = Modifier.weight(1f))

                callerIdentity(call = call, pulsing = true)

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 340.dp),
                ) {
                    if (state.canReplyWithMessage) {
                        quickReplyButton(onClick = { onAction(InCallAction.ReplyWithMessage) })
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (requireGesture) {
                        swipeToAnswerControl(
                            onAnswer = { onAction(InCallAction.Answer) },
                            onDecline = { onAction(InCallAction.Decline) },
                        )
                    } else {
                        answerDeclineButtons(
                            onAnswer = { onAction(InCallAction.Answer) },
                            onDecline = { onAction(InCallAction.Decline) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Drag-to-answer handle for the lock screen.
 *
 * One handle, two directions: up answers, down declines. Both are irreversible actions on a device the
 * user has not authenticated, so neither should be a bare tap that a pocket or a cheek can trigger.
 */
@Composable
@Suppress("FunctionName")
private fun swipeToAnswerControl(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val thresholdPx = remember(density) { with(density) { DRAG_THRESHOLD.toPx() } }
    val offset = remember { Animatable(0f) }
    var committed by remember { mutableStateOf(false) }

    val progressUp = (-offset.value / thresholdPx).coerceIn(0f, 1f)
    val progressDown = (offset.value / thresholdPx).coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = AcceptGreen,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { alpha = HINT_MIN_ALPHA + progressUp * (1f - HINT_MIN_ALPHA) },
        )

        Text(
            text = stringResource(R.string.swipe_up_to_answer),
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Surface(
            shape = CircleShape,
            color = when {
                progressUp > COMMIT_TINT_AT -> AcceptGreen
                progressDown > COMMIT_TINT_AT -> DeclineRed
                else -> AvatarSurface
            },
            border = BorderStroke(1.dp, OutlineVariant),
            modifier = Modifier
                .size(HANDLE_SIZE)
                .graphicsLayer { translationY = offset.value }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (!committed) {
                            scope.launch { offset.snapTo(offset.value + delta) }
                        }
                    },
                    onDragStopped = {
                        when {
                            committed -> Unit
                            offset.value <= -thresholdPx -> {
                                committed = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAnswer()
                            }
                            offset.value >= thresholdPx -> {
                                committed = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDecline()
                            }
                            // Released short of the threshold, so spring back rather than acting.
                            else -> offset.animateTo(0f)
                        }
                    },
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = stringResource(R.string.answer_handle_content_description),
                    tint = if (progressUp > COMMIT_TINT_AT || progressDown > COMMIT_TINT_AT) {
                        OnAcceptGreen
                    } else {
                        OnSurface
                    },
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.swipe_down_to_decline),
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = DeclineRed,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { alpha = HINT_MIN_ALPHA + progressDown * (1f - HINT_MIN_ALPHA) },
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun answerDeclineButtons(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labelledCallFab(
            icon = Icons.Default.CallEnd,
            label = stringResource(R.string.decline_label),
            containerColor = DeclineRed,
            contentColor = OnDeclineRed,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDecline()
            },
        )
        labelledCallFab(
            icon = Icons.Default.Call,
            label = stringResource(R.string.answer_label),
            containerColor = AcceptGreen,
            contentColor = OnAcceptGreen,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onAnswer()
            },
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun labelledCallFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = CircleShape,
            modifier = Modifier.size(80.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
    }
}

@Composable
@Suppress("FunctionName")
private fun quickReplyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GlassFill,
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Message,
                contentDescription = null,
                tint = ElectricBlue,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.reply_with_message),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = OnSurface,
            )
        }
    }
}

@Composable
@Suppress("FunctionName")
internal fun callerHeader(labelRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = null,
            tint = ElectricBlue,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            ),
            color = OnSurfaceVariant,
        )
    }
}

/** Avatar, name, and either the elapsed duration or a status label. */
@Composable
@Suppress("FunctionName")
internal fun callerIdentity(
    call: CallCardUiState,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    avatarSize: androidx.compose.ui.unit.Dp = 140.dp,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(avatarSize + 80.dp)) {
            if (pulsing) {
                val transition = rememberInfiniteTransition(label = "pulse")
                val scale by transition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseScale",
                )
                val pulseAlpha by transition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(PULSE_DURATION_MS, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseAlpha",
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Read inside the modifier lambda so the pulse never triggers recomposition.
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = pulseAlpha
                        }
                        .background(color = ElectricBlue, shape = CircleShape),
                )
            }
            callerAvatar(call = call, size = avatarSize)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = call.displayName,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = OnSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = call.secondaryLine(),
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The number while ringing, the elapsed time once connected, or a status while in between. */
@Composable
private fun CallCardUiState.secondaryLine(): String = when {
    showsDuration -> durationText
    state == CallState.HOLDING -> stringResource(R.string.call_status_on_hold)
    state == CallState.DIALING -> stringResource(R.string.call_status_dialing)
    state == CallState.CONNECTING -> stringResource(R.string.call_status_connecting)
    state.isTerminal -> stringResource(R.string.call_ended_label)
    number.isNotEmpty() -> number
    else -> stringResource(R.string.caller_unknown)
}

@Composable
@Suppress("FunctionName")
internal fun callerAvatar(
    call: CallCardUiState,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val photo = rememberContactPhoto(call.photoUri)

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = AvatarSurface,
        border = BorderStroke(4.dp, OutlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = stringResource(R.string.caller_photo_content_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = monogramOf(call),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = OnSurface,
                )
            }
        }
    }
}

/**
 * Initials for the avatar.
 *
 * Takes up to two, matching the call log, rather than the single letter the call screens used to show —
 * so the same contact no longer renders as "A" here and "AM" in history.
 */
private fun monogramOf(call: CallCardUiState): String {
    val words = call.displayName.trim().split(' ').filter { it.isNotBlank() }
    val initials = words.take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
    return initials.ifEmpty { call.number.firstOrNull()?.toString() ?: FALLBACK_MONOGRAM }
}

@Composable
fun rememberContactPhoto(photoUriString: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(photoUriString) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUriString) {
        if (photoUriString.isNullOrBlank()) return@LaunchedEffect
        // Decode off the main thread, then assign back on it. The previous version assigned the
        // Compose state from inside withContext(Dispatchers.IO).
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(photoUriString))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.onFailure { Log.e("ContactPhoto", "Could not load $photoUriString", it) }.getOrNull()
        }
        bitmap = decoded
    }

    return bitmap
}

private val DRAG_THRESHOLD = 96.dp
private val HANDLE_SIZE = 84.dp
private const val HINT_MIN_ALPHA = 0.35f
private const val COMMIT_TINT_AT = 0.5f
private const val PULSE_DURATION_MS = 1500
private const val FALLBACK_MONOGRAM = "?"

@Preview
@Composable
@Suppress("FunctionName")
private fun incomingCallButtonsPreview() {
    PhoneTheme {
        incomingCallContent(
            state = InCallUiState(
                mode = InCallUiState.Mode.Incoming,
                primary = CallCardUiState(
                    callId = "c1",
                    displayName = "Aaron Miller",
                    number = "+1 555 0123",
                    photoUri = null,
                    state = CallState.RINGING,
                ),
                canReplyWithMessage = true,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
@Suppress("FunctionName")
private fun incomingCallSwipePreview() {
    PhoneTheme {
        incomingCallContent(
            state = InCallUiState(
                mode = InCallUiState.Mode.Incoming,
                primary = CallCardUiState(
                    callId = "c1",
                    displayName = "Aaron Miller",
                    number = "+1 555 0123",
                    photoUri = null,
                    state = CallState.RINGING,
                ),
            ),
            onAction = {},
            requireGesture = true,
        )
    }
}
