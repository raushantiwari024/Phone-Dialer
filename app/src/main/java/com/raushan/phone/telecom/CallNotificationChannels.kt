package com.raushan.phone.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.Notification
import com.raushan.phone.R

/**
 * Owns the call notification channels.
 *
 * Created once from [com.raushan.phone.DialerApplication.onCreate] rather than on every post, which is
 * what the previous implementation did.
 */
object CallNotificationChannels {

    /**
     * Channel ids are versioned on purpose.
     *
     * `NotificationChannel` settings are **immutable once created**. Devices that already ran an
     * earlier build still hold `incoming_calls` with vibration enabled and a notification sound, and no
     * amount of code change alters an existing channel. Publishing under a new id and deleting the old
     * one is the only way the corrected configuration below actually takes effect on an upgrade.
     */
    const val INCOMING = "incoming_calls_v2"
    const val ONGOING = "ongoing_calls_v2"

    private const val LEGACY_INCOMING = "incoming_calls"
    private const val LEGACY_ACTIVE = "active_calls"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // IMPORTANCE_HIGH earns heads-up ranking and full-screen-intent priority.
        //
        // Deliberately SILENT. For a managed InCallService like this one — no ConnectionService, not
        // self-managed — Telecom's own Ringer already plays the ringtone and vibrates, honouring the
        // user's ringtone choice, Do Not Disturb, silent mode and per-contact rules. Giving this
        // channel a sound as well produces two simultaneous ringtones on most devices.
        val incoming = NotificationChannel(
            INCOMING,
            context.getString(R.string.incoming_calls_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.incoming_calls_channel_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(true)
            setShowBadge(false)
            setBypassDnd(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val ongoing = NotificationChannel(
            ONGOING,
            context.getString(R.string.ongoing_calls_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.ongoing_calls_channel_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannels(listOf(incoming, ongoing))

        manager.deleteNotificationChannel(LEGACY_INCOMING)
        manager.deleteNotificationChannel(LEGACY_ACTIVE)
    }
}
