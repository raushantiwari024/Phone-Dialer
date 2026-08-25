package com.raushan.phone.ui.incall

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raushan.phone.telecom.CallRepository
import com.raushan.phone.telecom.CallUiCoordinator
import com.raushan.phone.telecom.InCallIntents
import com.raushan.phone.telecom.model.CallState
import com.raushan.phone.ui.theme.PhoneTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the call UI in its own activity and its own task.
 *
 * Replaces rendering the call screens as a Compose overlay inside `MainActivity`. That arrangement is
 * why an incoming call never appeared on the lock screen: the only way to show above the keyguard is
 * `showWhenLocked` on the activity that is actually being launched, and putting that on the launcher
 * activity would expose the call log and contact list to anyone who woke the phone.
 *
 * A dedicated activity separates the two concerns: this one may show over the keyguard and wake the
 * screen because it only ever displays a call, while `MainActivity` may not.
 */
class InCallActivity : ComponentActivity() {

    private var showDialpadOnLaunch by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set in code as well as the manifest. The manifest attributes make the very first frame of a
        // full-screen-intent launch keyguard-eligible with no race against onCreate; the runtime calls
        // exist so the flag can be revoked again before finishing.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        handleIntent(intent)
        enableEdgeToEdge()

        setContent {
            PhoneTheme {
                val model: InCallViewModel = viewModel()
                inCallRoute(
                    viewModel = model,
                    onMinimize = ::minimize,
                    onFinished = ::finishCallUi,
                )
                LaunchedEffect(model) {
                    model.events.collect(::handleUiEvent)
                }
            }
        }

        observeSession()
    }

    /**
     * Tells the notification layer the call UI is on screen, so it does not raise a heads-up
     * notification over the top of a call the user can already see and act on.
     */
    override fun onStart() {
        super.onStart()
        CallRepository.setCallUiVisible(true)
    }

    override fun onStop() {
        super.onStop()
        CallRepository.setCallUiVisible(false)
    }

    /** `launchMode="singleInstance"`, so a second launch is redelivered here rather than recreating. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        showDialpadOnLaunch = intent?.getBooleanExtra(InCallIntents.EXTRA_SHOW_DIALPAD, false) == true
        // Reaching the call screen is an explicit request to see it, so undo any earlier dismissal.
        InCallIntents.callIdOf(intent)?.let(CallUiCoordinator::clearDismissed)
    }

    private fun observeSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Close only once Telecom is bound and every call is gone. Keying on an empty call
                    // list alone would close the screen during the bind window, before the first call
                    // has even been delivered.
                    CallRepository.state
                        .map { it.isSessionFinished }
                        .distinctUntilChanged()
                        .collect { finished -> if (finished) finishCallUi() }
                }
                launch {
                    // Keep the screen awake while ringing so the user can actually reach the buttons.
                    CallRepository.state
                        .map { it.primaryCall?.state == CallState.RINGING }
                        .distinctUntilChanged()
                        .collect(::applyKeepScreenOn)
                }
            }
        }
    }

    private fun applyKeepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Back on an ongoing call hides the call screen without ending the call.
     *
     * `moveTaskToBack` rather than `finish`, so returning via the notification restores this instance
     * with its timer and resolved name intact. The task is excluded from Recents, so backgrounding it
     * is invisible to the user.
     */
    private fun minimize() {
        CallRepository.state.value.primaryCall?.id?.let(CallUiCoordinator::markDismissed)
        moveTaskToBack(true)
    }

    /**
     * Effects the screens cannot perform themselves.
     *
     * Both leave the call activity for another app, so both need the keyguard dismissed first — unlike
     * answering, which deliberately stays above the lock screen and never prompts for a PIN.
     */
    private fun handleUiEvent(event: InCallUiEvent) {
        when (event) {
            InCallUiEvent.OpenDialerForSecondCall -> startDismissingKeyguard(
                Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            is InCallUiEvent.OpenSms -> startDismissingKeyguard(
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", event.number, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun startDismissingKeyguard(target: Intent) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            launchSafely(target)
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launchSafely(target)

                // Cancelled or failed: leave the call exactly as it was. The old quick-reply path
                // hung the caller up first and then tried to open the SMS app, so a cancelled unlock
                // dropped the call and sent nothing.
                override fun onDismissCancelled() = Unit
            },
        )
    }

    private fun launchSafely(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not start $intent", it) }
    }

    private fun finishCallUi() {
        if (isFinishing) return
        // Drop the keyguard exemption before the window goes away, so the lock screen reasserts itself
        // instead of briefly revealing whatever is behind.
        setShowWhenLocked(false)
        CallUiCoordinator.prune(CallRepository.state.value)
        finishAndRemoveTask()
    }

    companion object {
        private const val TAG = "InCallActivity"


        /**
         * Whether a call should take over the whole screen rather than only showing a heads-up banner.
         *
         * The platform makes the real decision for full-screen intents — it honours them when the
         * device is locked or the screen is off and degrades them to a heads-up notification
         * otherwise, which is precisely the behaviour wanted here. This mirrors that rule for the
         * cases where the app itself decides whether to launch.
         */
        fun shouldTakeOverScreen(activity: ComponentActivity): Boolean {
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            return keyguard?.isKeyguardLocked == true
        }
    }
}
