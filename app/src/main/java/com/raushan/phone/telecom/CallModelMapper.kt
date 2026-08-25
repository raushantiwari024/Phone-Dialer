package com.raushan.phone.telecom

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import com.raushan.phone.R
import com.raushan.phone.data.models.Contact
import com.raushan.phone.telecom.model.CallCapabilities
import com.raushan.phone.telecom.model.CallModel
import com.raushan.phone.telecom.model.CallState
import com.raushan.phone.telecom.model.NumberPresentation
import java.util.Locale

/**
 * Converts the mutable framework [Call] into an immutable [CallModel].
 *
 * This is the only place in the app that reads [Call.Details]. Everything downstream — notifications,
 * ViewModels, screens — sees snapshots, which is what lets a single `StateFlow` be the source of truth.
 *
 * Resolving the display label here rather than per-consumer is deliberate: the notification and the
 * in-call UI previously computed it separately and disagreed, showing a raw number in one place and a
 * contact name in the other.
 */
internal class CallModelMapper(private val context: Context) {

    private val telecomManager: TelecomManager? =
        context.getSystemService(TelecomManager::class.java)

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(TelephonyManager::class.java)

    fun map(
        call: Call,
        id: String,
        contact: Contact?,
        idOf: (Call) -> String?,
    ): CallModel {
        val details = call.details
        val handle = details?.handle
        val number = handle?.schemeSpecificPart.orEmpty()
        val presentation = presentationOf(details?.handlePresentation)
        val isEmergency = isEmergencyNumber(number)

        return CallModel(
            id = id,
            state = stateOf(call),
            number = number,
            scheme = handle?.scheme.orEmpty(),
            displayName = displayNameOf(details, number, presentation, contact, isEmergency),
            contactId = contact?.id,
            photoUri = contact?.photoUri,
            presentation = presentation,
            isIncoming = isIncoming(details),
            isConference = details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true,
            isConferenceChild = call.parent != null,
            isExternal = details?.hasProperty(Call.Details.PROPERTY_IS_EXTERNAL_CALL) == true,
            isEmergency = isEmergency,
            isVideo = VideoProfile.isVideo(details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY),
            isWifiCall = details?.hasProperty(Call.Details.PROPERTY_WIFI) == true,
            isHdAudio = details?.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO) == true,
            childIds = call.children.orEmpty().mapNotNull(idOf),
            parentId = call.parent?.let(idOf),
            conferenceableIds = call.conferenceableCalls.orEmpty().mapNotNull(idOf),
            connectTimeMillis = details?.connectTimeMillis ?: 0L,
            creationTimeMillis = details?.creationTimeMillis ?: 0L,
            capabilities = capabilitiesOf(details),
            disconnectCauseCode = details?.disconnectCause?.code,
            disconnectDescription = details?.disconnectCause?.description?.toString(),
            cannedTextResponses = call.cannedTextResponses.orEmpty().filterNotNull(),
            phoneAccountId = details?.accountHandle?.id,
            phoneAccountLabel = phoneAccountLabelOf(details?.accountHandle),
        )
    }

    /**
     * `Call.getState()` is deprecated from API 31 in favour of `Call.Details.getState()`, but
     * `Details.getState()` does not exist below 31 and this project ships `minSdk 30`.
     */
    private fun stateOf(call: Call): CallState {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details?.state ?: legacyState(call)
        } else {
            legacyState(call)
        }
        return CallState.fromTelecom(raw)
    }

    @Suppress("DEPRECATION")
    private fun legacyState(call: Call): Int = call.state

    private fun isIncoming(details: Call.Details?): Boolean =
        details?.callDirection == Call.Details.DIRECTION_INCOMING

    private fun presentationOf(presentation: Int?): NumberPresentation = when (presentation) {
        TelecomManager.PRESENTATION_ALLOWED -> NumberPresentation.ALLOWED
        TelecomManager.PRESENTATION_RESTRICTED -> NumberPresentation.RESTRICTED
        TelecomManager.PRESENTATION_PAYPHONE -> NumberPresentation.PAYPHONE
        TelecomManager.PRESENTATION_UNKNOWN -> NumberPresentation.UNKNOWN
        else -> NumberPresentation.UNAVAILABLE
    }

    /**
     * Label precedence: withheld-number reasons, then the contact name, then the network-supplied
     * caller name (CNAP), then a formatted number, then a generic fallback. Never returns empty.
     */
    private fun displayNameOf(
        details: Call.Details?,
        number: String,
        presentation: NumberPresentation,
        contact: Contact?,
        isEmergency: Boolean,
    ): String {
        if (isEmergency) return context.getString(R.string.caller_emergency)

        when (presentation) {
            NumberPresentation.RESTRICTED -> return context.getString(R.string.caller_private_number)
            NumberPresentation.PAYPHONE -> return context.getString(R.string.caller_payphone)
            else -> Unit
        }

        contact?.name?.takeIf { it.isNotBlank() }?.let { return it }

        if (details?.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
            details.callerDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
        }

        if (number.isNotEmpty()) {
            return PhoneNumberUtils.formatNumber(number, countryIso()) ?: number
        }

        return context.getString(R.string.caller_unknown)
    }

    private fun countryIso(): String {
        val fromNetwork = runCatching { telephonyManager?.networkCountryIso }.getOrNull()
        return fromNetwork?.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
            ?: Locale.getDefault().country
    }

    private fun capabilitiesOf(details: Call.Details?): CallCapabilities {
        if (details == null) return CallCapabilities.NONE
        return CallCapabilities(
            canHold = details.can(Call.Details.CAPABILITY_HOLD),
            canMute = details.can(Call.Details.CAPABILITY_MUTE),
            canSwapConference = details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            canMergeConference = details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canManageConference = details.can(Call.Details.CAPABILITY_MANAGE_CONFERENCE),
            canSeparateFromConference = details.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),
            canRespondViaText = details.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT),
            canSupportHold = details.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canAddParticipant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                details.can(Call.Details.CAPABILITY_ADD_PARTICIPANT),
            canUpgradeToVideo = details.can(Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL),
        )
    }

    /**
     * Emergency numbers are never looked up in contacts and always show a fixed label.
     *
     * `isEmergencyNumber` lives on [TelephonyManager], not [TelecomManager], and needs
     * `READ_PHONE_STATE`. Guarded because a revoked permission must not take down the mapper during an
     * incoming call.
     */
    private fun isEmergencyNumber(number: String): Boolean {
        if (number.isEmpty()) return false
        return runCatching { telephonyManager?.isEmergencyNumber(number) }.getOrNull() ?: false
    }

    private fun phoneAccountLabelOf(handle: PhoneAccountHandle?): String? {
        if (handle == null) return null
        return runCatching {
            telecomManager?.getPhoneAccount(handle)?.label?.toString()
        }.onFailure { Log.d(TAG, "Could not read phone account label", it) }.getOrNull()
    }

    private companion object {
        private const val TAG = "CallModelMapper"
    }
}
