package com.raushan.phone.telecom

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.raushan.phone.R
import com.raushan.phone.telecom.model.CallModel
import com.raushan.phone.telecom.model.CallSessionState
import com.raushan.phone.telecom.model.CallState

/**
 * Builds the call notification.
 *
 * The whole class is a **pure function of [CallSessionState]** — it never launches a coroutine, never
 * posts, and holds no mutable state. That is the structural fix for the phantom sticky notification:
 * previously each builder spawned an orphan `CoroutineScope(Dispatchers.IO)` to look up a contact and
 * then re-posted from that coroutine, so a lookup finishing after the call ended resurrected a
 * notification that had already been cancelled. Now rendering always reflects current state, and a late
 * contact resolution merely triggers one more render of what is true *now*.
 *
 * Building is separated from posting because only [MyInCallService] knows which notification currently
 * backs the foreground service.
 */
internal class CallNotificationManager(private val context: Context) {

    sealed interface Request {
        data class Post(
            val id: Int,
            val notification: Notification,
            val isIncoming: Boolean,
        ) : Request

        data object Dismiss : Request
    }

    /**
     * Chooses and builds the single call notification for [state].
     *
     * Only ever one exists at a time, matching standard dialer behaviour: a second line is represented
     * inside the call UI, not as a second notification.
     */
    fun render(state: CallSessionState, avatar: IconCompat?): Request {
        val ringing = state.ringingCall
        if (ringing != null) {
            return Request.Post(
                id = NOTIFICATION_ID_INCOMING,
                notification = incomingNotification(ringing, state, avatar),
                isIncoming = true,
            )
        }

        val ongoing = state.primaryCall ?: return Request.Dismiss
        if (!ongoing.state.isLive) return Request.Dismiss

        return Request.Post(
            id = NOTIFICATION_ID_ONGOING,
            notification = ongoingNotification(ongoing, state, avatar),
            isIncoming = false,
        )
    }

    private fun incomingNotification(
        call: CallModel,
        state: CallSessionState,
        avatar: IconCompat?,
    ): Notification {
        val person = personOf(call, avatar)
        val style = NotificationCompat.CallStyle.forIncomingCall(
            person,
            CallActionReceiver.declineIntent(context, call.id),
            CallActionReceiver.answerIntent(context, call.id),
        ).setIsVideo(call.isVideo)

        val builder = baseBuilder(CallNotificationChannels.INCOMING)
            .setContentTitle(call.displayName)
            .setContentText(context.getString(R.string.notification_incoming_call))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            // Both are set: contentIntent for a tap on the heads-up banner, fullScreenIntent for the
            // locked or screen-off case. The platform itself decides whether to honour the
            // full-screen intent or degrade it to a heads-up notification, which is exactly the
            // behaviour we want and previously fought.
            .setContentIntent(InCallIntents.contentIntent(context, call.id))
            .setDeleteIntent(CallActionReceiver.declineIntent(context, call.id))
            .setStyle(style)
            // Deliberately NOT setOnlyAlertOnce here. A full-screen intent is delivered as part of
            // alerting, and this notification is re-posted whenever details or the avatar resolve, so
            // suppressing subsequent alerts risks suppressing the takeover on some OEM builds.
            .setOnlyAlertOnce(false)

        if (!state.isRingerSilenced) {
            builder.setFullScreenIntent(InCallIntents.fullScreenIntent(context, call.id), true)
        }

        return builder.build()
    }

    private fun ongoingNotification(
        call: CallModel,
        state: CallSessionState,
        avatar: IconCompat?,
    ): Notification {
        val person = personOf(call, avatar)
        val style = NotificationCompat.CallStyle.forOngoingCall(
            person,
            CallActionReceiver.hangUpIntent(context, call.id),
        )

        val builder = baseBuilder(CallNotificationChannels.ONGOING)
            .setContentTitle(call.displayName)
            .setContentText(statusTextOf(call))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setContentIntent(InCallIntents.contentIntent(context, call.id))
            .setStyle(style)
            // Safe here: the ongoing channel is IMPORTANCE_LOW so it never alerts anyway, and this
            // notification is re-posted on every audio-state change.
            .setOnlyAlertOnce(true)

        // The chronometer is only meaningful once the call actually connected; a dialing call would
        // otherwise count up from the epoch.
        if (call.state == CallState.ACTIVE && call.connectTimeMillis > 0L) {
            builder.setWhen(call.connectTimeMillis)
            builder.setUsesChronometer(true)
        } else {
            builder.setShowWhen(false)
        }

        val audio = state.audio
        if (call.capabilities.canMute) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    if (audio.isMuted) R.drawable.ic_notification_mic_off else R.drawable.ic_notification_mic,
                    context.getString(
                        if (audio.isMuted) R.string.mute_off_label else R.string.mute_on_label,
                    ),
                    CallActionReceiver.toggleMuteIntent(context, call.id),
                ).build(),
            )
        }

        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification_speaker,
                context.getString(
                    if (audio.isSpeakerOn) R.string.speaker_off_label else R.string.speaker_on_label,
                ),
                CallActionReceiver.toggleSpeakerIntent(context, call.id),
            ).build(),
        )

        return builder.build()
    }

    private fun baseBuilder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColorized(true)
            .setColor(context.getColor(R.color.call_notification_accent))

    private fun statusTextOf(call: CallModel): String = context.getString(
        when {
            call.state == CallState.HOLDING -> R.string.notification_call_on_hold
            call.state.isOutgoingPending -> R.string.notification_call_dialing
            else -> R.string.notification_ongoing_call
        },
    )

    /**
     * [CallModel.displayName] is already resolved, so the notification and the in-call UI always show
     * the same label — previously the notification showed a raw number while the UI showed a name.
     */
    private fun personOf(call: CallModel, avatar: IconCompat?): Person = Person.Builder()
        .setName(call.displayName)
        .setKey(call.id)
        .setUri(if (call.hasDisplayableNumber) "$TEL_PREFIX${call.number}" else null)
        .setIcon(avatar)
        .setImportant(true)
        .build()

    companion object {
        const val NOTIFICATION_ID_INCOMING = 8888
        const val NOTIFICATION_ID_ONGOING = 8889

        private const val TEL_PREFIX = "tel:"
    }
}
