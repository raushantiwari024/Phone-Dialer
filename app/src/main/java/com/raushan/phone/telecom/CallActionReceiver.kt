package com.raushan.phone.telecom

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.util.Log
import com.raushan.phone.telecom.model.AudioRoute

/**
 * Handles notification action taps.
 *
 * This class decides only *where* an action came from; what it does is [CallActionDispatcher]'s job.
 * It previously called [TelecomHelper] directly, which meant the notification's Answer performed a bare
 * `answer()` while the in-app popup ran its own branching over three different helper methods — the
 * same intent, two implementations, and no shared guarantee about what happened to the call already in
 * progress.
 *
 * Declared in the manifest, so R8 keeps it and its name automatically.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = InCallIntents.callIdOf(intent) ?: run {
            Log.w(TAG, "Action ${intent.action} arrived without a call id")
            return
        }
        val appContext = context.applicationContext
        val helper = TelecomHelper(appContext)

        // A stale PendingIntent can outlive the process. When the repository no longer knows the id,
        // fall back to the TelecomManager equivalents rather than silently doing nothing.
        if (CallRepository.modelOf(callId) == null) {
            handleStaleAction(appContext, intent.action)
            return
        }

        val action = intent.action.toCallAction(callId) ?: run {
            Log.w(TAG, "Unhandled action ${intent.action}")
            return
        }

        CallActionDispatcher.dispatch(helper, action)

        // Answering from the notification should also surface the call UI, so the user lands on the
        // active call rather than being left with only a notification.
        if (action is CallAction.AnswerIncomingAndHoldCurrent) {
            runCatching { appContext.startActivity(InCallIntents.callUi(appContext, callId)) }
                .onFailure { Log.d(TAG, "Could not open call UI after answering", it) }
        }
    }

    /**
     * Maps a notification action to the canonical [CallAction].
     *
     * The notification's Answer is [CallAction.AnswerIncomingAndHoldCurrent] — identical to the popup's
     * Answer. "Answer and end the current call" is deliberately *not* offered as a notification action:
     * ending an in-progress call is destructive and needs the fuller context the in-app popup provides.
     */
    private fun String?.toCallAction(callId: String): CallAction? = when (this) {
        ACTION_ANSWER -> CallAction.AnswerIncomingAndHoldCurrent(callId)
        ACTION_DECLINE -> CallAction.DeclineIncoming(callId)
        ACTION_HANG_UP -> CallAction.EndCall(callId)
        ACTION_TOGGLE_MUTE -> CallAction.SetMuted(!CallRepository.state.value.audio.isMuted)
        ACTION_TOGGLE_SPEAKER -> toggleSpeakerAction()
        else -> null
    }

    private fun toggleSpeakerAction(): CallAction {
        val audio = CallRepository.state.value.audio
        val next = if (audio.isSpeakerOn) audio.defaultEarRoute else AudioRoute.SPEAKER
        return CallAction.SetAudioRoute(next)
    }

    /**
     * Fallbacks for an action whose call the repository no longer tracks.
     *
     * Both require default-dialer status, which we hold. Never crash, and never silently no-op.
     */
    private fun handleStaleAction(context: Context, action: String?) {
        Log.w(TAG, "Action $action for an unknown call; using TelecomManager fallback")
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        runCatching {
            when (action) {
                ACTION_ANSWER -> telecom.acceptRingingCallCompat()
                ACTION_DECLINE, ACTION_HANG_UP -> telecom.endCallCompat()
                else -> Unit
            }
        }.onFailure { Log.e(TAG, "TelecomManager fallback failed for $action", it) }
    }

    @Suppress("DEPRECATION")
    private fun TelecomManager.acceptRingingCallCompat() = acceptRingingCall()

    @Suppress("DEPRECATION")
    private fun TelecomManager.endCallCompat() = endCall()

    companion object {
        private const val TAG = "CallActionReceiver"
        private const val ACTION_PREFIX = "com.raushan.phone.telecom.action."

        const val ACTION_ANSWER = ACTION_PREFIX + "ANSWER"
        const val ACTION_DECLINE = ACTION_PREFIX + "DECLINE"
        const val ACTION_HANG_UP = ACTION_PREFIX + "HANG_UP"
        const val ACTION_TOGGLE_MUTE = ACTION_PREFIX + "TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = ACTION_PREFIX + "TOGGLE_SPEAKER"

        private const val REQUEST_CODE = 0

        fun answerIntent(context: Context, callId: String): PendingIntent =
            pendingIntent(context, ACTION_ANSWER, callId)

        fun declineIntent(context: Context, callId: String): PendingIntent =
            pendingIntent(context, ACTION_DECLINE, callId)

        fun hangUpIntent(context: Context, callId: String): PendingIntent =
            pendingIntent(context, ACTION_HANG_UP, callId)

        fun toggleMuteIntent(context: Context, callId: String): PendingIntent =
            pendingIntent(context, ACTION_TOGGLE_MUTE, callId)

        fun toggleSpeakerIntent(context: Context, callId: String): PendingIntent =
            pendingIntent(context, ACTION_TOGGLE_SPEAKER, callId)

        /**
         * The call id goes in `data`, not an extra, so each call gets a distinct `PendingIntent`.
         * See [InCallIntents] for why.
         */
        private fun pendingIntent(context: Context, action: String, callId: String): PendingIntent {
            val intent = Intent(context, CallActionReceiver::class.java).apply {
                this.action = action
                data = InCallIntents.callIdUri(callId)
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
