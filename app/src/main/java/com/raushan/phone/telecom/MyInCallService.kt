package com.raushan.phone.telecom

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.raushan.phone.data.ContactsRepository
import com.raushan.phone.telecom.model.AudioRoute
import com.raushan.phone.telecom.model.BluetoothDeviceModel
import com.raushan.phone.telecom.model.CallAudioModel
import com.raushan.phone.telecom.model.CallSessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * The bridge between Telecom and the app.
 *
 * Its whole job is: own the `Call.Callback` registrations, map framework calls into immutable snapshots
 * for [CallRepository], publish audio state, and post the call notification. It holds no UI state and
 * no business logic.
 *
 * This is a **bind-only** service. It used to also handle notification actions through
 * `onStartCommand` + `PendingIntent.getService`, which meant Telecom bound it *and* the notification
 * started it. Nothing ever called `stopSelf()`, so the started state outlived the binding: `onDestroy`
 * never ran, `Call.Callback`s stayed registered, and notifications could not be cancelled. Actions now
 * go to [CallActionReceiver] and `onStartCommand` is gone.
 */
class MyInCallService : InCallService(), InCallController {

    /**
     * Cancelled in [onDestroy], unlike the ad-hoc `CoroutineScope(Dispatchers.IO)` this replaces.
     * `Dispatchers.Main.immediate` keeps all repository mutation single-threaded.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Exactly one callback per call. No other class in the app may register one. */
    private val callbacks = HashMap<Call, Call.Callback>()

    private val contactJobs = HashMap<String, Job>()

    /** Service-local, not part of [CallModel]: a Bitmap is mutable and would break `@Immutable`. */
    private val avatarCache = HashMap<String, IconCompat>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var mapper: CallModelMapper
    private lateinit var contactsRepository: ContactsRepository
    private lateinit var notifications: CallNotificationManager
    private lateinit var notificationManager: NotificationManagerCompat

    private var foregroundNotificationId: Int? = null

    override fun onCreate() {
        super.onCreate()
        mapper = CallModelMapper(this)
        contactsRepository = ContactsRepository(this)
        notifications = CallNotificationManager(this)
        notificationManager = NotificationManagerCompat.from(this)
        CallRepository.attachController(this)
        CallRepository.setServiceConnected(true)
    }

    /**
     * Telecom unbinding is the reliable teardown signal; `onDestroy` may lag behind it. Both call
     * [teardown], which is idempotent.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun teardown() {
        callbacks.forEach { (call, callback) ->
            runCatching { call.unregisterCallback(callback) }
        }
        callbacks.clear()
        contactJobs.values.forEach(Job::cancel)
        contactJobs.clear()
        avatarCache.clear()
        stopCallForeground()
        CallRepository.detachController(this)
        CallRepository.clear()
    }

    // --- Telecom callbacks ---

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val id = CallRepository.assignId(call)
        Log.d(TAG, "onCallAdded: $id")

        val callback = createCallback()
        callbacks[call] = callback
        // Explicit main-thread handler so every repository write happens on one thread.
        call.registerCallback(callback, mainHandler)

        republish(call)
        resolveContact(call, id)
        refreshNotification()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val id = CallRepository.idOf(call)
        Log.d(TAG, "onCallRemoved: $id")

        callbacks.remove(call)?.let { runCatching { call.unregisterCallback(it) } }
        id?.let {
            contactJobs.remove(it)?.cancel()
            avatarCache.remove(it)
        }
        CallRepository.removeCall(call)
        refreshNotification()
    }

    /**
     * The authoritative audio state. Previously this only refreshed the notification and never
     * published, which is why the in-app mute and speaker buttons drifted out of sync with the real
     * route whenever audio changed from the notification or a Bluetooth headset.
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallRepository.setAudio(audioModelOf(audioState))
        refreshNotification()
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) {
        super.onCanAddCallChanged(canAddCall)
        CallRepository.setCanAddCall(canAddCall)
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        CallRepository.setRingerSilenced(true)
        CallRepository.emitEvent(CallSessionEvent.RingerSilenced)
        refreshNotification()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        CallRepository.emitEvent(CallSessionEvent.ShowInCallUi(showDialpad))
        val callId = CallRepository.state.value.primaryCall?.id ?: return
        // Best effort only. Background activity starts are restricted, so the full-screen intent on
        // the notification is the reliable path; this is just the optimisation.
        runCatching { startActivity(InCallIntents.callUi(this, callId, showDialpad)) }
            .onFailure { Log.d(TAG, "Could not bring call UI forward directly", it) }
    }

    /**
     * One callback instance per call, dispatching on the call it was registered for.
     *
     * `onDetailsChanged` matters as much as `onStateChanged` and was previously missing: capabilities,
     * connect time and the caller name all arrive through it, which is why hold and merge affordances
     * could never be enabled correctly.
     */
    private fun createCallback(): Call.Callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = republish(call)

        override fun onDetailsChanged(call: Call, details: Call.Details) = republish(call)

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = republish(call)

        override fun onParentChanged(call: Call, parent: Call?) = republish(call)

        override fun onConferenceableCallsChanged(
            call: Call,
            conferenceableCalls: MutableList<Call>,
        ) = republish(call)

        override fun onCannedTextResponsesLoaded(
            call: Call,
            cannedTextResponses: MutableList<String>,
        ) = republish(call)

        override fun onPostDialWait(call: Call, remainingPostDialSequence: String) {
            CallRepository.idOf(call)?.let {
                CallRepository.emitEvent(
                    CallSessionEvent.PostDialWait(it, remainingPostDialSequence),
                )
            }
        }

        override fun onConnectionEvent(call: Call, event: String, extras: android.os.Bundle?) {
            CallRepository.idOf(call)?.let {
                CallRepository.emitEvent(CallSessionEvent.ConnectionEvent(it, event))
            }
        }

        override fun onCallDestroyed(call: Call) {
            callbacks.remove(call)?.let { runCatching { call.unregisterCallback(it) } }
        }
    }

    /** Re-maps one call and publishes. Every callback funnels through here. */
    private fun republish(call: Call) {
        val id = CallRepository.idOf(call) ?: CallRepository.assignId(call)
        val contact = CallRepository.modelOf(id)?.let { existing ->
            existing.contactId?.let {
                com.raushan.phone.data.models.Contact(
                    id = it,
                    name = existing.displayName,
                    number = existing.number,
                    photoUri = existing.photoUri,
                )
            }
        }
        CallRepository.putCall(call, mapper.map(call, id, contact, CallRepository::idOf))
        refreshNotification()
    }

    /**
     * Resolves the caller's contact exactly once per call.
     *
     * Was previously done three times per call — once in the ViewModel and once in each notification
     * builder — each time scanning the whole contacts table.
     */
    private fun resolveContact(call: Call, id: String) {
        val model = CallRepository.modelOf(id) ?: return
        if (!model.hasDisplayableNumber || model.isEmergency) return

        contactJobs[id]?.cancel()
        contactJobs[id] = serviceScope.launch {
            val contact = runCatching { contactsRepository.getContactByNumber(model.number) }
                .onFailure { Log.e(TAG, "Contact lookup failed", it) }
                .getOrNull() ?: return@launch

            if (!isActive) return@launch
            // The call may have ended while we were looking up. Bail rather than reviving it.
            if (CallRepository.rawCall(id) == null) return@launch

            contact.photoUri?.let { uri -> loadAvatar(uri)?.let { avatarCache[id] = it } }

            if (!isActive || CallRepository.rawCall(id) == null) return@launch
            CallRepository.putCall(call, mapper.map(call, id, contact, CallRepository::idOf))
            refreshNotification()
        }
    }

    private suspend fun loadAvatar(photoUri: String): IconCompat? =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let(IconCompat::createWithAdaptiveBitmap)
                }
            }.getOrNull()
        }

    // --- notifications ---

    private fun refreshNotification() {
        val state = CallRepository.state.value
        val avatar = state.notificationCall?.id?.let(avatarCache::get)
        when (val request = notifications.render(state, avatar)) {
            is CallNotificationManager.Request.Post -> post(request)
            CallNotificationManager.Request.Dismiss -> stopCallForeground()
        }
    }

    private fun post(request: CallNotificationManager.Request.Post) {
        val current = foregroundNotificationId
        when {
            current == null -> startCallForeground(request.id, request.notification)

            current == request.id ->
                runCatching { notificationManager.notify(request.id, request.notification) }

            // Incoming -> ongoing handover. Post the new one *before* cancelling the old, so the
            // foreground service is never momentarily without a notification and the shade does not
            // flicker. The old code cancelled first.
            else -> {
                startCallForeground(request.id, request.notification)
                runCatching { notificationManager.cancel(current) }
            }
        }
        foregroundNotificationId = request.id
    }

    private fun startCallForeground(id: Int, notification: android.app.Notification) {
        try {
            // Service.startForeground with a type is API 29, so it is always available at minSdk 30.
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (e: Exception) {
            // A denied foreground start must never take down a call. Fall back to a plain post.
            Log.w(TAG, "startForeground denied; posting without foreground", e)
            runCatching { notificationManager.notify(id, notification) }
        }
    }

    /**
     * `NotificationManager.cancel` does not remove a foreground-service notification, so the
     * foreground state has to be released first. Skipping that is the classic cause of a call
     * notification that will not go away.
     */
    private fun stopCallForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        runCatching {
            notificationManager.cancel(CallNotificationManager.NOTIFICATION_ID_INCOMING)
            notificationManager.cancel(CallNotificationManager.NOTIFICATION_ID_ONGOING)
        }
        foregroundNotificationId = null
    }

    // --- InCallController ---

    override fun requestMute(muted: Boolean) {
        setMuted(muted)
    }

    /**
     * `CallAudioState` and the route APIs are deprecated from API 34 in favour of `CallEndpoint`, but
     * the legacy callbacks are still delivered. Deprecation is suppressed only on the few audio
     * adapter members here and in [audioModelOf], so a future `CallEndpoint` migration is confined to
     * this section rather than spread across a class-level suppression as it was before.
     */
    @Suppress("DEPRECATION")
    override fun requestAudioRoute(route: AudioRoute) {
        setAudioRoute(route.telecomRoute)
    }

    @Suppress("DEPRECATION")
    override fun requestBluetoothDevice(address: String) {
        val device = bluetoothDevices(callAudioState).firstOrNull { it.address == address }
        if (device == null) {
            Log.w(TAG, "No Bluetooth device matching the requested address")
            return
        }
        requestBluetoothAudio(device)
    }

    override fun bringInCallUiToForeground(showDialpad: Boolean) {
        val callId = CallRepository.state.value.primaryCall?.id ?: return
        runCatching { startActivity(InCallIntents.callUi(this, callId, showDialpad)) }
    }

    // --- audio mapping ---

    @Suppress("DEPRECATION")
    private fun audioModelOf(audioState: CallAudioState?): CallAudioModel {
        if (audioState == null) return CallAudioModel.DEFAULT
        val devices = bluetoothDevices(audioState)
        return CallAudioModel(
            isMuted = audioState.isMuted,
            route = CallAudioModel.routeFromMask(audioState.route),
            supportedRoutes = CallAudioModel.routesFromMask(audioState.supportedRouteMask),
            bluetoothDevices = devices.map {
                BluetoothDeviceModel(address = it.address, name = deviceNameOf(it))
            },
            activeBluetoothAddress = audioState.activeBluetoothDevice?.address,
        )
    }

    @Suppress("DEPRECATION")
    private fun bluetoothDevices(audioState: CallAudioState?): List<BluetoothDevice> =
        audioState?.supportedBluetoothDevices?.toList().orEmpty()

    /** `BluetoothDevice.getName` needs `BLUETOOTH_CONNECT` on API 31+; fall back to the address. */
    private fun deviceNameOf(device: BluetoothDevice): String =
        runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: device.address

    private companion object {
        private const val TAG = "MyInCallService"
    }
}
