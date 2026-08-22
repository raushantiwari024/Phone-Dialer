package com.raushan.phone.ui.incall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.R
import com.raushan.phone.telecom.model.CallState
import com.raushan.phone.ui.theme.PhoneTheme
import com.raushan.phone.ui.theme.callColors

@Composable
@Suppress("FunctionName")
fun activeCallScreen(
    viewModel: InCallViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    activeCallContent(state = state, onAction = viewModel::onAction)
}

@Composable
@Suppress("FunctionName")
fun activeCallContent(
    state: InCallUiState,
    onAction: (InCallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val call = state.primary ?: return

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(listOf(callColors.accentGlow, MaterialTheme.colorScheme.background))),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                callerIdentity(call = call, avatarSize = 120.dp)

                state.secondary?.let { other ->
                    Spacer(modifier = Modifier.height(20.dp))
                    if (other.state.isRinging) {
                        callWaitingBanner(
                            call = other,
                            mustEndActiveToAnswer = state.mustEndActiveToAnswer,
                            onDecline = { onAction(InCallAction.Decline) },
                            onAnswerHolding = { onAction(InCallAction.AnswerHoldingCurrent) },
                            onAnswerEnding = { onAction(InCallAction.AnswerEndingCurrent) },
                        )
                    } else {
                        heldCallBanner(
                            call = other,
                            canSwap = state.canSwap,
                            onSwap = { onAction(InCallAction.Swap) },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (state.dialpadVisible) {
                    inCallDialpad(
                        digits = state.dialpadDigits,
                        onKeyDown = { onAction(InCallAction.PressDialKey(it)) },
                        onKeyUp = { onAction(InCallAction.ReleaseDialKey) },
                        onClose = { onAction(InCallAction.HideDialpad) },
                    )
                } else {
                    controlGrid(state = state, onAction = onAction)
                }

                Spacer(modifier = Modifier.height(28.dp))

                primaryControlRow(state = state, onAction = onAction)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Secondary controls: mute, add call, and hold or swap.
 *
 * Speaker and the dialpad deliberately live in [primaryControlRow] instead, flanking hang up.
 *
 * There is no video control. The app has no video stack at all — no camera permission, no local
 * preview or remote render surfaces — so requesting a video upgrade would move the call into a video
 * session with nothing to display it, which is worse than omitting the control. The capability is
 * still carried on the call model, so restoring the button later is a UI-only change.
 */
@Composable
@Suppress("FunctionName")
private fun controlGrid(
    state: InCallUiState,
    onAction: (InCallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val holdIsSwap = state.canSwap

    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        callActionButton(
            icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            label = stringResource(if (state.isMuted) R.string.muted_label else R.string.mute_label),
            onClick = { onAction(InCallAction.ToggleMute) },
            isActive = state.isMuted,
        )

        callActionButton(
            icon = Icons.Default.Add,
            label = stringResource(R.string.add_call_label),
            onClick = { onAction(InCallAction.AddCall) },
            enabled = state.canAddCall,
        )

        callActionButton(
            icon = when {
                holdIsSwap -> Icons.Default.SwapCalls
                state.primary?.isOnHold == true -> Icons.Default.PlayArrow
                else -> Icons.Default.Pause
            },
            label = stringResource(
                when {
                    holdIsSwap -> R.string.swap_label
                    state.primary?.isOnHold == true -> R.string.resume_label
                    else -> R.string.hold_label
                },
            ),
            onClick = { onAction(if (holdIsSwap) InCallAction.Swap else InCallAction.ToggleHold) },
            isActive = state.primary?.isOnHold == true,
            enabled = holdIsSwap || state.canHold,
        )

        if (state.canMerge) {
            callActionButton(
                icon = Icons.Default.SwapCalls,
                label = stringResource(R.string.merge_label),
                onClick = { onAction(InCallAction.Merge) },
            )
        }
    }
}

/**
 * Speaker on the left of hang up, dialpad on its right.
 *
 * Hang up is the largest target and sits centred, so the two controls most reached for mid-call are
 * within thumb range of it without being easy to hit by mistake.
 */
@Composable
@Suppress("FunctionName")
private fun primaryControlRow(
    state: InCallUiState,
    onAction: (InCallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        callActionButton(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = stringResource(R.string.speaker_label),
            onClick = { onAction(InCallAction.ToggleSpeaker) },
            isActive = state.isSpeakerOn,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAction(InCallAction.EndCall)
                },
                containerColor = callColors.decline,
                contentColor = callColors.onDecline,
                shape = CircleShape,
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = stringResource(R.string.end_call_label),
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.hang_up_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        callActionButton(
            icon = Icons.Default.Dialpad,
            label = stringResource(R.string.keypad_label),
            onClick = {
                onAction(
                    if (state.dialpadVisible) InCallAction.HideDialpad else InCallAction.ShowDialpad,
                )
            },
            isActive = state.dialpadVisible,
            // DTMF only reaches the network on a connected call.
            enabled = state.primary?.state == CallState.ACTIVE,
        )
    }
}

/**
 * A second caller arriving while a call is already in progress.
 *
 * Sits over the active call rather than replacing it: the person already on the line stays on screen,
 * and their audio is untouched while the user decides.
 *
 * "Hold & Accept" is hidden when the current call reports it cannot be held — some carrier and radio
 * configurations do — leaving "End & Accept" as the only honest option rather than offering an action
 * that would silently fail.
 */
@Composable
@Suppress("FunctionName")
private fun callWaitingBanner(
    call: CallCardUiState,
    mustEndActiveToAnswer: Boolean,
    onDecline: () -> Unit,
    onAnswerHolding: () -> Unit,
    onAnswerEnding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = callColors.glassFill,
        border = BorderStroke(1.dp, callColors.glassBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                callerAvatar(call = call, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = call.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.call_status_call_waiting),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                bannerAction(
                    label = stringResource(R.string.decline_label),
                    containerColor = callColors.decline,
                    contentColor = callColors.onDecline,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDecline()
                    },
                    modifier = Modifier.weight(1f),
                )

                if (!mustEndActiveToAnswer) {
                    bannerAction(
                        label = stringResource(R.string.answer_and_hold_label),
                        containerColor = callColors.accept,
                        contentColor = callColors.onAccept,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswerHolding()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                bannerAction(
                    label = stringResource(R.string.answer_and_end_label),
                    containerColor = if (mustEndActiveToAnswer) {
                        callColors.accept
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (mustEndActiveToAnswer) {
                        callColors.onAccept
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAnswerEnding()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun bannerAction(
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
        )
    }
}

/** The other line once it is on hold, after answering a waiting call or swapping. */
@Composable
@Suppress("FunctionName")
private fun heldCallBanner(
    call: CallCardUiState,
    canSwap: Boolean,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = callColors.glassFill,
        border = BorderStroke(1.dp, callColors.glassBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (call.isOnHold) {
                            R.string.call_status_on_hold
                        } else {
                            R.string.call_status_call_waiting
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canSwap) {
                callActionButton(
                    icon = Icons.Default.SwapCalls,
                    label = stringResource(R.string.swap_label),
                    onClick = onSwap,
                    size = 44.dp,
                )
            }
        }
    }
}

/**
 * Circular in-call control.
 *
 * Gained `modifier`, `size` and `enabled` so it can serve both the secondary grid and the larger
 * primary row, and so capability-gated controls can be shown as unavailable rather than silently
 * doing nothing.
 */
@Composable
@Suppress("FunctionName")
fun callActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 64.dp,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = when {
                !enabled -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = DISABLED_CONTAINER_ALPHA)
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            enabled = enabled,
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
                        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA),
        )
    }
}

private const val DISABLED_CONTAINER_ALPHA = 0.3f
private const val DISABLED_CONTENT_ALPHA = 0.38f

@Preview
@Composable
@Suppress("FunctionName")
private fun callWaitingPreview() {
    PhoneTheme {
        activeCallContent(
            state = InCallUiState(
                mode = InCallUiState.Mode.IncomingWhileOngoing,
                primary = CallCardUiState(
                    callId = "c1",
                    displayName = "Aaron Miller",
                    number = "+1 555 0123",
                    photoUri = null,
                    state = CallState.ACTIVE,
                    durationText = "04:12",
                ),
                secondary = CallCardUiState(
                    callId = "c2",
                    displayName = "Priya Sharma",
                    number = "+1 555 0456",
                    photoUri = null,
                    state = CallState.RINGING,
                ),
                canHold = true,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
@Suppress("FunctionName")
private fun twoOngoingPreview() {
    PhoneTheme {
        activeCallContent(
            state = InCallUiState(
                mode = InCallUiState.Mode.TwoOngoing,
                primary = CallCardUiState(
                    callId = "c2",
                    displayName = "Priya Sharma",
                    number = "+1 555 0456",
                    photoUri = null,
                    state = CallState.ACTIVE,
                    durationText = "00:31",
                ),
                secondary = CallCardUiState(
                    callId = "c1",
                    displayName = "Aaron Miller",
                    number = "+1 555 0123",
                    photoUri = null,
                    state = CallState.HOLDING,
                ),
                canSwap = true,
                canHold = true,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
@Suppress("FunctionName")
private fun activeCallPreview() {
    PhoneTheme {
        activeCallContent(
            state = InCallUiState(
                mode = InCallUiState.Mode.Ongoing,
                primary = CallCardUiState(
                    callId = "c1",
                    displayName = "Aaron Miller",
                    number = "+1 555 0123",
                    photoUri = null,
                    state = CallState.ACTIVE,
                    durationText = "01:24",
                ),
                canAddCall = true,
                canHold = true,
            ),
            onAction = {},
        )
    }
}
