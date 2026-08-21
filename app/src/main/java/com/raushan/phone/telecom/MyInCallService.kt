package com.raushan.phone.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.raushan.phone.MainActivity
import com.raushan.phone.R
import com.raushan.phone.data.ContactsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class MyInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        cancelNotifications()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANSWER -> {
                val call = CallRepository.currentCalls.value.firstOrNull()
                call?.answer(0)
                cancelIncomingCallNotificationOnly()
                
                // Launch MainActivity to show active call UI in foreground
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(launchIntent)
            }
            ACTION_DECLINE -> {
                val call = CallRepository.currentCalls.value.firstOrNull()
                call?.disconnect()
                cancelNotifications()
            }
            ACTION_TOGGLE_SPEAKER -> {
                val currentRoute = callAudioState?.route ?: CallAudioState.ROUTE_WIRED_OR_EARPIECE
                val isSpeakerOn = (currentRoute == CallAudioState.ROUTE_SPEAKER)
                val newRoute = if (isSpeakerOn) {
                    CallAudioState.ROUTE_WIRED_OR_EARPIECE
                } else {
                    CallAudioState.ROUTE_SPEAKER
                }
                setAudioRoute(newRoute)
                
                // Update notification immediately with new route text
                val call = CallRepository.currentCalls.value.firstOrNull()
                if (call != null) {
                    updateCallNotification(call)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "onStateChanged: $call, state: ${callStateToString(state)}")
            updateCallNotification(call)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: $call")
        call.registerCallback(callCallback)
        CallRepository.addCall(call)
        updateCallNotification(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: $call")
        call.unregisterCallback(callCallback)
        CallRepository.removeCall(call)
        cancelNotifications()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        Log.d(TAG, "onCallAudioStateChanged: $audioState")
        val call = CallRepository.currentCalls.value.firstOrNull()
        if (call != null) {
            updateCallNotification(call)
        }
    }

    fun onActivityStateChanged(isForeground: Boolean) {
        val call = CallRepository.currentCalls.value.firstOrNull() ?: return
        val state = call.details?.state ?: call.state
        if (state == Call.STATE_RINGING && !isForeground) {
            // Re-post incoming call notification if activity goes to background while ringing
            showIncomingCallNotification(call)
        }
    }

    private fun updateCallNotification(call: Call) {
        val state = call.details?.state ?: call.state
        if (state == Call.STATE_RINGING) {
            showIncomingCallNotification(call)
        } else if (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_HOLDING) {
            showActiveCallNotification(call)
        } else {
            cancelNotifications()
        }
    }

    private fun showIncomingCallNotification(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_INCOMING_CALLS,
                getString(R.string.incoming_calls_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.incoming_calls_channel_description)
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent to open MainActivity on click
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Answer intent
        val answerIntent = Intent(this, MyInCallService::class.java).apply {
            action = ACTION_ANSWER
        }
        val answerPendingIntent = PendingIntent.getService(
            this,
            1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Decline intent
        val declineIntent = Intent(this, MyInCallService::class.java).apply {
            action = ACTION_DECLINE
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_INCOMING_CALLS)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(getString(R.string.notification_incoming_call))
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            // Left Action: Decline/Reject
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.decline_label),
                declinePendingIntent
            )
            // Right Action: Answer/Accept
            .addAction(
                android.R.drawable.ic_menu_call,
                getString(R.string.answer_label),
                answerPendingIntent
            )
            
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        
        // Asynchronously update notification with contact's name if resolved
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ContactsRepository(this@MyInCallService)
                val contact = repository.getContactByNumber(number)
                if (contact != null) {
                    builder.setContentTitle(contact.name)
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error looking up contact name for notification", e)
            }
        }
    }

    private fun showActiveCallNotification(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ACTIVE_CALLS,
                getString(R.string.active_calls_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.active_calls_channel_description)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent to open MainActivity on click
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            3,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Decline intent (Hang Up)
        val declineIntent = Intent(this, MyInCallService::class.java).apply {
            action = ACTION_DECLINE
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            4,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Speaker toggle intent
        val speakerIntent = Intent(this, MyInCallService::class.java).apply {
            action = ACTION_TOGGLE_SPEAKER
        }
        val speakerPendingIntent = PendingIntent.getService(
            this,
            5,
            speakerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val currentRoute = callAudioState?.route ?: CallAudioState.ROUTE_WIRED_OR_EARPIECE
        val isSpeakerOn = (currentRoute == CallAudioState.ROUTE_SPEAKER)
        val speakerLabel = if (isSpeakerOn) {
            getString(R.string.speaker_off_label)
        } else {
            getString(R.string.speaker_on_label)
        }
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE_CALLS)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(getString(R.string.notification_active_call))
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            
        // Use system chronometer to display call duration seconds automatically
        val connectTime = call.details?.connectTimeMillis ?: 0L
        if (connectTime > 0L) {
            builder.setWhen(connectTime)
            builder.setUsesChronometer(true)
        }
        
        // Add Speaker toggle and Hang up actions
        builder.addAction(
            android.R.drawable.ic_btn_speak_now,
            speakerLabel,
            speakerPendingIntent
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.hang_up_label),
            declinePendingIntent
        )
        
        // Cancel incoming call notification first to prevent overlap
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.notify(NOTIFICATION_ID_ACTIVE, builder.build())
        
        // Asynchronously update contact name if resolved
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ContactsRepository(this@MyInCallService)
                val contact = repository.getContactByNumber(number)
                if (contact != null) {
                    builder.setContentTitle(contact.name)
                    notificationManager.notify(NOTIFICATION_ID_ACTIVE, builder.build())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error looking up contact name for notification", e)
            }
        }
    }

    private fun cancelIncomingCallNotificationOnly() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun cancelNotifications() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(NOTIFICATION_ID_ACTIVE)
    }

    private fun callStateToString(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_ACTIVE -> "ACTIVE"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_DISCONNECTING -> "DISCONNECTING"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
            else -> "UNKNOWN ($state)"
        }
    }

    companion object {
        private const val TAG = "MyInCallService"
        private const val NOTIFICATION_ID = 8888
        private const val NOTIFICATION_ID_ACTIVE = 8889
        private const val CHANNEL_ID_INCOMING_CALLS = "incoming_calls"
        private const val CHANNEL_ID_ACTIVE_CALLS = "active_calls"
        
        const val ACTION_ANSWER = "com.raushan.phone.telecom.action.ANSWER"
        const val ACTION_DECLINE = "com.raushan.phone.telecom.action.DECLINE"
        const val ACTION_TOGGLE_SPEAKER = "com.raushan.phone.telecom.action.TOGGLE_SPEAKER"
        
        var instance: MyInCallService? = null
    }
}