package com.raushan.phone.telecom

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.util.Log

/**
 * Handles notification action taps.
 *
 * Replaces routing actions back into [MyInCallService] via `PendingIntent.getService`. That approach
 * *started* a service Telecom had already *bound*, and nothing ever called `stopSelf()`, so the started
 * state kept the service alive after Telecom unbound: `onDestroy` never ran, cleanup never happened,
 * and call notifications could not be cancelled. Keeping the service bind-only removes that entirely.
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
        val isKnownCall = CallRepository.modelOf(callId) != null

        when (intent.action) {
            ACTION_ANSWER -> {
                if (isKnownCall) helper.answerCall(callId) else acceptRingingFallback(appContext)
                appContext.startActivity(InCallIntents.callUi(appContext, callId))
            }

            ACTION_DECLINE -> {
                if (isKnownCall) helper.rejectCall(callId) else endCallFallback(appContext)
            }

            ACTION_HANG_UP -> {
                if (isKnownCall) helper.endCall(callId) else endCallFallback(appContext)
            }

            ACTION_TOGGLE_MUTE -> helper.toggleMute()

            ACTION_TOGGLE_SPEAKER -> helper.toggleSpeaker()

            else -> Log.w(TAG, "Unhandled action ${intent.action}")
        }
    }

    /**
     * Fallbacks for a stale [PendingIntent] — the process was killed and restarted, so the repository
     * no longer knows this call id. Both require default-dialer status, which we hold. Never crash and
     * never silently do nothing.
     */
    @Suppress("DEPRECATION")
    private fun acceptRingingFallback(context: Context) {
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.acceptRingingCall()
        }.onFailure { Log.e(TAG, "acceptRingingCall fallback failed", it) }
    }

    private fun endCallFallback(context: Context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.getSystemService(TelecomManager::class.java)?.endCall()
        }.onFailure { Log.e(TAG, "endCall fallback failed", it) }
    }

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
