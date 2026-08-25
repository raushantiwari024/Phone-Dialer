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
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
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
 * Secondary controls: mute, add call, hold or swap, and merge.
 *
 * Speaker and the dialpad deliberately live in [primaryControlRow] instead, flanking hang up.
 *
 * Built from a list and chunked into rows rather than hard-coded into one Row, because the control set
 * genuinely changes shape: with two calls up, Hold becomes Swap and Merge appears alongside it, and
 * four 64dp buttons do not fit on one line on a narrow screen.
 *
 * There is no video control. The app has no video stack at all — no camera permission, no local preview
 * or remote render surfaces — so requesting a video upgrade would move the call into a video session
 * with nothing to display it. The capability is still carried on the call model, so restoring the
 * button later is a UI-only change.
 */
@Composable
@Suppress("FunctionName")
private fun controlGrid(
    state: InCallUiState,
    onAction: (InCallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val holdIsSwap = state.canSwap
    val controls = buildList {
        add(
            ControlSpec(
                icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                labelRes = if (state.isMuted) R.string.muted_label else R.string.mute_label,
                action = InCallAction.ToggleMute,
                isActive = state.isMuted,
            ),
        )
        add(
            ControlSpec(
                icon = Icons.Default.Add,
                labelRes = R.string.add_call_label,
                action = InCallAction.AddCall,
                enabled = state.canAddCall,
            ),
        )
        add(
            ControlSpec(
                icon = when {
                    holdIsSwap -> Icons.Default.SwapCalls
                    state.primary?.isOnHold == true -> Icons.Default.PlayArrow
                    else -> Icons.Default.Pause
                },
                labelRes = when {
                    holdIsSwap -> R.string.swap_label
                    state.primary?.isOnHold == true -> R.string.resume_label
                    else -> R.string.hold_label
                },
                action = if (holdIsSwap) InCallAction.Swap else InCallAction.ToggleHold,
                isActive = !holdIsSwap && state.primary?.isOnHold == true,
                enabled = holdIsSwap || state.canHold,
            ),
        )
        // Merge only means anything with two separate calls, and only once the network says they can
        // be conferenced. Shown next to Swap so the two multi-call options sit together.
        if (state.canMerge) {
            add(
                ControlSpec(
                    icon = Icons.AutoMirrored.Filled.MergeType,
                    labelRes = R.string.merge_calls_label,
                    action = InCallAction.Merge,
                ),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        controls.chunked(CONTROLS_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { spec ->
                    callActionButton(
                        icon = spec.icon,
                        label = stringResource(spec.labelRes),
                        onClick = { onAction(spec.action) },
                        isActive = spec.isActive,
                        enabled = spec.enabled,
                    )
                }
            }
        }
    }
}

/** One entry in [controlGrid]. Lets the control set be assembled before it is laid out. */
private data class ControlSpec(
    val icon: ImageVector,
    val labelRes: Int,
    val action: InCallAction,
    val isActive: Boolean = false,
    val enabled: Boolean = true,
)

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
 * Sits over the active call rather than replacing it, so the person already on the line stays on screen
 * and their audio is untouched while the user decides.
 *
 * Laid out as two prominent choices plus one secondary, rather than three equal buttons. Three equal
 * buttons forced "Hold & Accept" and "End & Accept" to wrap onto two lines each, leaving two adjacent
 * controls that both just read "Accept" — the user could not tell them apart. Declining and answering
 * are the common choices, so they get the prominent row; ending a call in progress is destructive and
 * belongs below, spelled out in full.
 *
 * "Hold & Accept" is hidden when the current call reports it cannot be held — some carrier and radio
 * configurations do — leaving the explicit end-and-accept as the only offered way in, rather than an
 * action that would silently fail.
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, callColors.glassBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                callerAvatar(call = call, size = 44.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.incoming_call_banner_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = callColors.accept,
                    )
                    Text(
                        text = call.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                bannerAction(
                    label = stringResource(R.string.decline_label),
                    icon = Icons.Default.CallEnd,
                    containerColor = callColors.decline,
                    contentColor = callColors.onDecline,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDecline()
                    },
                    modifier = Modifier.weight(1f),
                )

                bannerAction(
                    label = stringResource(
                        if (mustEndActiveToAnswer) {
                            R.string.answer_and_end_label
                        } else {
                            R.string.answer_and_hold_label
                        },
                    ),
                    icon = Icons.Default.Call,
                    containerColor = callColors.accept,
                    contentColor = callColors.onAccept,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (mustEndActiveToAnswer) onAnswerEnding() else onAnswerHolding()
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // Only offered as a separate choice when holding is actually possible; otherwise it is
            // already the primary accept action above and repeating it would be noise.
            if (!mustEndActiveToAnswer) {
                Spacer(modifier = Modifier.height(10.dp))
                bannerAction(
                    label = stringResource(R.string.answer_and_end_current_label),
                    icon = null,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAnswerEnding()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun bannerAction(
    label: String,
    icon: ImageVector?,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

private const val CONTROLS_PER_ROW = 3
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
