package com.raushan.phone.telecom

import com.raushan.phone.telecom.model.AudioRoute

/**
 * The operations that only a live [android.telecom.InCallService] instance can perform.
 *
 * Exists to replace the mutable `MyInCallService.instance` static that the UI used to reach through.
 * That static leaked a `Service` (and therefore a `Context`), had no lifecycle guard, and let the
 * ViewModel invoke service methods directly.
 *
 * Method names are prefixed `request` rather than mirroring the framework's `setMuted` /
 * `setAudioRoute`, because those are `final` on `InCallService` and cannot be overridden. The prefix
 * also reads more honestly: these are requests whose result arrives asynchronously via
 * `onCallAudioStateChanged`, not direct state writes.
 *
 * Implemented by [MyInCallService] and handed to [CallRepository] for the lifetime of the binding.
 * Callers go through [TelecomHelper] rather than touching this directly.
 */
internal interface InCallController {

    fun requestMute(muted: Boolean)

    fun requestAudioRoute(route: AudioRoute)

    fun requestBluetoothDevice(address: String)

    fun bringInCallUiToForeground(showDialpad: Boolean)
}
