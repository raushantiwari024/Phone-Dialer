package com.raushan.phone.telecom.model

import androidx.compose.runtime.Immutable

/**
 * How the network says the remote number may be presented.
 *
 * Drives the display label without ever querying contacts for a number we are not allowed to show.
 */
enum class NumberPresentation {
    ALLOWED,
    RESTRICTED,
    PAYPHONE,
    UNKNOWN,
    UNAVAILABLE,
}

/**
 * Decoded `Call.Details.CAPABILITY_*` bits.
 *
 * Decoded once at mapping time so no consumer has to know about bitmasks, and so the UI can enable or
 * disable controls from real capability rather than guessing.
 */
@Immutable
data class CallCapabilities(
    val canHold: Boolean = false,
    val canMute: Boolean = false,
    val canSwapConference: Boolean = false,
    val canMergeConference: Boolean = false,
    val canManageConference: Boolean = false,
    val canSeparateFromConference: Boolean = false,
    val canRespondViaText: Boolean = false,
    /**
     * `CAPABILITY_SUPPORT_HOLD` — the call *could* be held at some point.
     *
     * Distinct from [canHold], which is whether it can be held *right now*. Both matter: the answer
     * flow needs [canHold] to decide between "Hold & Answer" and "End & Answer".
     */
    val canSupportHold: Boolean = false,
    val canAddParticipant: Boolean = false,
    /**
     * Whether this call could be upgraded to video.
     *
     * Retained in the model even though the video control is not currently surfaced: the app has no
     * video stack (no camera permission, no preview or remote-render surfaces), so offering an upgrade
     * would put the call into a video session with nothing to display it. Keeping the flag means
     * re-introducing the control later is a UI-only change.
     */
    val canUpgradeToVideo: Boolean = false,
) {
    companion object {
        val NONE = CallCapabilities()
    }
}

/**
 * Immutable snapshot of a single call.
 *
 * This is the only call representation that leaves the `telecom` package. The mutable framework
 * [android.telecom.Call] is deliberately kept internal: because it mutates in place, a `StateFlow`
 * holding one never re-emits on a state transition, which forced every consumer to register its own
 * `Call.Callback` to notice changes. Snapshots make the flow the single source of truth.
 *
 * [displayName] is resolved once, at mapping time, so the notification and the in-call UI can never
 * disagree about who is calling.
 */
@Immutable
data class CallModel(
    val id: String,
    val state: CallState,
    /** Raw `schemeSpecificPart` of the handle. Empty when the number is withheld or unavailable. */
    val number: String,
    val scheme: String,
    /** Already-resolved label to show. Never empty; falls back through CNAP, formatting, then "Unknown". */
    val displayName: String,
    val contactId: Long?,
    val photoUri: String?,
    val presentation: NumberPresentation,
    val isIncoming: Boolean,
    val isConference: Boolean,
    val isConferenceChild: Boolean,
    /** `PROPERTY_IS_EXTERNAL_CALL` — a call on another device. Must be hidden from our UI. */
    val isExternal: Boolean,
    val isEmergency: Boolean,
    val isVideo: Boolean,
    val isWifiCall: Boolean,
    val isHdAudio: Boolean,
    val childIds: List<String>,
    val parentId: String?,
    val conferenceableIds: List<String>,
    /** Wall-clock millis when the call connected, or 0 when it never has. */
    val connectTimeMillis: Long,
    val creationTimeMillis: Long,
    val capabilities: CallCapabilities,
    val disconnectCauseCode: Int?,
    val disconnectDescription: String?,
    val cannedTextResponses: List<String>,
    val phoneAccountId: String?,
    val phoneAccountLabel: String?,
) {
    val isOngoing: Boolean get() = state.isOngoing

    val isRinging: Boolean get() = state.isRinging

    val isOnHold: Boolean get() = state == CallState.HOLDING

    val hasContact: Boolean get() = contactId != null

    /** True when we have a number we are permitted to display or dial. */
    val hasDisplayableNumber: Boolean
        get() = number.isNotEmpty() && presentation == NumberPresentation.ALLOWED
}
