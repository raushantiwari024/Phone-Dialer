package com.raushan.phone.ui.incall

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.R
import kotlinx.coroutines.delay

/**
 * Picks the call screen for the current state.
 *
 * Separate from [InCallActivity] so it stays previewable and the activity keeps to window and lifecycle
 * concerns.
 */
@Composable
@Suppress("FunctionName")
fun inCallRoute(
    viewModel: InCallViewModel = viewModel(),
    onMinimize: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Back must never dismiss a ringing call. There was previously no handler on that branch at all,
    // so the press fell through and left the phone ringing behind the app.
    BackHandler(enabled = true) {
        when {
            state.isRinging -> Unit
            state.dialpadVisible -> viewModel.onAction(InCallAction.HideDialpad)
            state.mode == InCallUiState.Mode.NoCall -> onFinished()
            else -> onMinimize()
        }
    }

    AnimatedContent(
        targetState = state.mode,
        transitionSpec = {
            fadeIn(tween(TRANSITION_IN_MS)) togetherWith fadeOut(tween(TRANSITION_OUT_MS))
        },
        label = "inCallMode",
    ) { mode ->
        when (mode) {
            InCallUiState.Mode.Incoming -> incomingCallScreen(viewModel = viewModel)

            // Call waiting stays on the active call screen with a banner for the new caller.
            // IncomingWhileOngoing used to route to the incoming screen, which renders `primary` — and
            // during call waiting `primary` is the call already in progress, so it showed the person
            // you were talking to behind an Answer button.
            InCallUiState.Mode.IncomingWhileOngoing,
            InCallUiState.Mode.Ongoing,
            InCallUiState.Mode.TwoOngoing,
            -> activeCallScreen(viewModel = viewModel)

            InCallUiState.Mode.Ended -> callEndedContent(state = state)

            InCallUiState.Mode.NoCall -> {
                // The call screen used to vanish abruptly on hang up with no confirmation at all.
                callEndedContent(state = state)
                LaunchedEffect(Unit) {
                    delay(CALL_ENDED_DWELL_MS)
                    onFinished()
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun callEndedContent(
    state: InCallUiState,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.safeDrawingPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.primary?.let { call ->
                    callerAvatar(call = call, size = 120.dp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = call.displayName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.call_ended_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val CALL_ENDED_DWELL_MS = 1200L
private const val TRANSITION_IN_MS = 180
private const val TRANSITION_OUT_MS = 140
