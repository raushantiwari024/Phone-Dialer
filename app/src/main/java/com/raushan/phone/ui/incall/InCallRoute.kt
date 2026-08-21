package com.raushan.phone.ui.incall

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Chooses which call screen to show for the current state.
 *
 * Lives in its own file rather than inside [InCallActivity] so it stays previewable and so the activity
 * keeps to lifecycle and window concerns.
 */
@Composable
@Suppress("FunctionName")
fun inCallRoute(
    viewModel: InCallViewModel = viewModel(),
    onMinimize: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()

    when {
        callState.isRinging -> {
            // Back must not dismiss a ringing call. Previously no handler was registered on this
            // branch at all, so the press fell through and left the phone ringing behind the app.
            BackHandler(enabled = true) {}
            incomingCallScreen(viewModel = viewModel)
        }

        callState.isOngoing -> {
            BackHandler(enabled = true) { onMinimize() }
            activeCallScreen(viewModel = viewModel)
        }

        else -> {
            // No live call to show. The activity also watches the session itself; this covers the
            // window between the last call ending and that signal arriving.
            LaunchedEffect(activeCall) {
                if (activeCall == null) onFinished()
            }
        }
    }
}
