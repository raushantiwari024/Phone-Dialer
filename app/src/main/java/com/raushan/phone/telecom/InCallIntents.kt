package com.raushan.phone.telecom

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.raushan.phone.MainActivity

/**
 * Builds the intents that bring the call UI on screen.
 *
 * Centralised so the destination is one edit away: the call UI currently lives inside [MainActivity]
 * and moves to a dedicated `InCallActivity` in a later step. Only [callUiTarget] changes then.
 *
 * Every [PendingIntent] carries the call id in `data` rather than an extra. Intent *extras* do not
 * participate in [Intent.filterEquals], so two calls would collapse onto one `PendingIntent` and the
 * second would fire carrying the first call's id — the mechanism behind "decline hung up the wrong
 * call" during call waiting.
 */
internal object InCallIntents {

    const val EXTRA_SHOW_DIALPAD = "com.raushan.phone.extra.SHOW_DIALPAD"

    private val callUiTarget: Class<*> = MainActivity::class.java

    private const val SCHEME_CALL_ID = "phonecall"
    private const val REQUEST_FULL_SCREEN = 100
    private const val REQUEST_CONTENT = 101

    fun callUi(context: Context, callId: String, showDialpad: Boolean = false): Intent =
        Intent(context, callUiTarget).apply {
            // NEW_TASK only. FLAG_ACTIVITY_CLEAR_TOP would destroy and recreate the activity, throwing
            // away the call-scoped ViewModel — and with it the timer and the resolved contact name.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = callIdUri(callId)
            if (showDialpad) putExtra(EXTRA_SHOW_DIALPAD, true)
        }

    fun fullScreenIntent(context: Context, callId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_FULL_SCREEN,
            callUi(context, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun contentIntent(context: Context, callId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            callUi(context, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun callIdUri(callId: String): Uri = Uri.fromParts(SCHEME_CALL_ID, callId, null)

    fun callIdOf(intent: Intent?): String? = intent?.data?.schemeSpecificPart
}
