package com.raushan.phone.telecom.model

import android.telecom.CallAudioState
import androidx.compose.runtime.Immutable

/** Where in-call audio is playing. */
enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET,
    ;

    /** The platform route mask this maps to when requesting a change. */
    val telecomRoute: Int
        get() = when (this) {
            EARPIECE -> CallAudioState.ROUTE_EARPIECE
            SPEAKER -> CallAudioState.ROUTE_SPEAKER
            BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        }
}

/** A connected Bluetooth audio device the call can be routed to. */
@Immutable
data class BluetoothDeviceModel(
    val address: String,
    val name: String,
)

/**
 * Immutable snapshot of the call audio state.
 *
 * Exists so mute and speaker become *derived* from what Telecom actually reports rather than optimistic
 * local booleans. Previously the UI flipped its own flags and fired a request, so toggling speaker from
 * the notification, or connecting a Bluetooth headset, left the in-app buttons showing the wrong state.
 */
@Immutable
data class CallAudioModel(
    val isMuted: Boolean = false,
    val route: AudioRoute = AudioRoute.EARPIECE,
    val supportedRoutes: Set<AudioRoute> = emptySet(),
    val bluetoothDevices: List<BluetoothDeviceModel> = emptyList(),
    val activeBluetoothAddress: String? = null,
) {
    val isSpeakerOn: Boolean get() = route == AudioRoute.SPEAKER

    val isBluetoothOn: Boolean get() = route == AudioRoute.BLUETOOTH

    val isWiredHeadsetConnected: Boolean get() = AudioRoute.WIRED_HEADSET in supportedRoutes

    val canRouteToBluetooth: Boolean get() = AudioRoute.BLUETOOTH in supportedRoutes

    /** Where "speaker off" should return to — the headset when one is plugged in, else the earpiece. */
    val defaultEarRoute: AudioRoute
        get() = if (isWiredHeadsetConnected) AudioRoute.WIRED_HEADSET else AudioRoute.EARPIECE

    companion object {
        val DEFAULT = CallAudioModel()

        /**
         * Decodes the *current* route from a platform route value.
         *
         * Must be priority-ordered bit tests, not equality: `ROUTE_WIRED_OR_EARPIECE` is a combined
         * mask (`ROUTE_EARPIECE or ROUTE_WIRED_HEADSET`), so comparing it as a single value reports
         * earpiece when a headset is actually connected.
         */
        fun routeFromMask(mask: Int): AudioRoute = when {
            mask and CallAudioState.ROUTE_BLUETOOTH != 0 -> AudioRoute.BLUETOOTH
            mask and CallAudioState.ROUTE_SPEAKER != 0 -> AudioRoute.SPEAKER
            mask and CallAudioState.ROUTE_WIRED_HEADSET != 0 -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.EARPIECE
        }

        /**
         * Decodes the set of *available* routes from a supported-route mask.
         *
         * Deliberately separate from [routeFromMask]: here every set bit is a distinct option, whereas
         * there the bits are ranked to pick one winner.
         */
        fun routesFromMask(mask: Int): Set<AudioRoute> = buildSet {
            if (mask and CallAudioState.ROUTE_EARPIECE != 0) add(AudioRoute.EARPIECE)
            if (mask and CallAudioState.ROUTE_SPEAKER != 0) add(AudioRoute.SPEAKER)
            if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) add(AudioRoute.BLUETOOTH)
            if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioRoute.WIRED_HEADSET)
        }
    }
}
