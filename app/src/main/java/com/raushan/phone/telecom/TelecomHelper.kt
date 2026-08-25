package com.raushan.phone.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.content.ContextCompat
import com.raushan.phone.telecom.model.AudioRoute
import com.raushan.phone.telecom.model.PlaceCallResult

/**
 * The one action surface for call control, as the project brief specifies
 * ("wraps TelecomManager.placeCall(), hold/mute/end actions").
 *
 * Every method addresses a call by its stable [com.raushan.phone.telecom.model.CallModel.id], so the
 * mutable framework [android.telecom.Call] never leaves this package. Audio operations delegate to the
 * attached [InCallController] instead of reaching through a static service reference.
 */
class TelecomHelper(private val context: Context) {

    private val telecomManager: TelecomManager? =
        context.getSystemService(TelecomManager::class.java)

    // --- placing calls ---

    /**
     * Places an outgoing call.
     *
     * The old implementation refused outright whenever any call existed, which made "add call"
     * impossible. The gate is now Telecom's own [android.telecom.InCallService.onCanAddCallChanged]
     * signal. Emergency numbers still go through [TelecomManager.placeCall] like everything else — the
     * brief is explicit that they must never be special-cased around it.
     */
    fun makeCall(
        phoneNumber: String,
        accountHandle: PhoneAccountHandle? = null,
        startWithSpeaker: Boolean = false,
    ): PlaceCallResult {
        if (phoneNumber.isBlank()) return PlaceCallResult.InvalidNumber
        if (!hasCallPhonePermission()) return PlaceCallResult.MissingPermission

        val session = CallRepository.state.value
        if (session.hasCalls && !session.canAddCall) return PlaceCallResult.CannotAddCall

        val extras = Bundle().apply {
            accountHandle?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            putInt(
                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_AUDIO_ONLY,
            )
            if (startWithSpeaker) {
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
            }
        }

        return try {
            telecomManager?.placeCall(Uri.fromParts(SCHEME_TEL, phoneNumber, null), extras)
            PlaceCallResult.Placed
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for placeCall", e)
            PlaceCallResult.MissingPermission
        } catch (e: IllegalStateException) {
            Log.e(TAG, "placeCall rejected", e)
            PlaceCallResult.Failed(e.message.orEmpty())
        }
    }

    /** SIMs available for outgoing calls. Empty when the permission is missing. */
    fun callablePhoneAccounts(): List<PhoneAccountHandle> = runCatching {
        telecomManager?.callCapablePhoneAccounts.orEmpty()
    }.getOrDefault(emptyList())

    /**
     * Answers a [com.raushan.phone.telecom.model.CallState.SELECT_PHONE_ACCOUNT] call.
     *
     * With two SIMs and no explicit handle, Telecom parks the call in that state and the default
     * dialer is responsible for choosing.
     */
    fun selectPhoneAccount(callId: String, handle: PhoneAccountHandle, setAsDefault: Boolean = false) {
        CallRepository.rawCall(callId)?.phoneAccountSelected(handle, setAsDefault)
    }

    // --- single call ---

    /**
     * Answers a call.
     *
     * A primitive: it answers and nothing else. Deliberately does **not** hold the existing active call
     * first — Telecom and the `ConnectionService` auto-hold it, and issuing our own `hold()` beforehand
     * races that in-flight hold, after which some `ConnectionService` implementations reject the answer
     * outright.
     *
     * Composing this with ending another call is [CallActionDispatcher]'s job, driven by
     * [CallAction.AnswerIncomingAndEndCurrent].
     */
    fun answerCall(callId: String) {
        CallRepository.rawCall(callId)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /**
     * Rejects a ringing call, or disconnects one that is already up.
     *
     * A ringing call must be rejected rather than disconnected: `disconnect()` does not send the
     * proper release cause on some carriers, and it bypasses reply-with-message entirely.
     */
    fun rejectCall(callId: String, message: String? = null) {
        val model = CallRepository.modelOf(callId) ?: return
        val call = CallRepository.rawCall(callId) ?: return
        when {
            model.isRinging && message != null && model.capabilities.canRespondViaText ->
                call.reject(true, message)

            model.isRinging -> call.reject(false, null)
            else -> call.disconnect()
        }
    }

    fun endCall(callId: String) {
        CallRepository.rawCall(callId)?.disconnect()
    }

    fun holdCall(callId: String) {
        CallRepository.rawCall(callId)?.hold()
    }

    fun unholdCall(callId: String) {
        CallRepository.rawCall(callId)?.unhold()
    }

    fun playDtmfTone(callId: String, digit: Char) {
        CallRepository.rawCall(callId)?.playDtmfTone(digit)
    }

    fun stopDtmfTone(callId: String) {
        CallRepository.rawCall(callId)?.stopDtmfTone()
    }

    fun postDialContinue(callId: String, proceed: Boolean) {
        CallRepository.rawCall(callId)?.postDialContinue(proceed)
    }

    // --- multiple calls ---

    // answerAndEndActive / answerAndHoldActive lived here. They encoded a multi-call transition inside
    // the primitive-operation wrapper, which is how the notification and the popup ended up with
    // different answer behaviour. Composing transitions is now CallActionDispatcher's job; this class
    // exposes only primitives.

    /**
     * Swaps the active and held calls.
     *
     * Only unholds. On GSM that implicitly holds whatever is currently active; also calling `hold()` on
     * the active call is the classic mistake that leaves *both* calls on hold.
     */
    fun swapCalls() {
        val held = CallRepository.state.value.heldCall ?: return
        CallRepository.rawCall(held.id)?.unhold()
    }

    fun mergeCalls() {
        val session = CallRepository.state.value
        val active = session.activeCall ?: return
        val held = session.heldCall ?: return
        // Accepts either signal. Several carriers populate only conferenceableCalls on a plain
        // two-call setup and never set CAPABILITY_MERGE_CONFERENCE, so gating on the capability bit
        // alone made Merge unavailable on networks that do support conferencing.
        val mergeable = active.capabilities.canMergeConference ||
            active.conferenceableIds.isNotEmpty()
        if (!mergeable) {
            Log.w(TAG, "Merge requested but neither the capability nor a conferenceable call is present")
            return
        }
        val activeRaw = CallRepository.rawCall(active.id) ?: return
        val heldRaw = CallRepository.rawCall(held.id) ?: return
        activeRaw.conference(heldRaw)
    }

    fun splitFromConference(callId: String) {
        val model = CallRepository.modelOf(callId) ?: return
        if (!model.capabilities.canSeparateFromConference) return
        CallRepository.rawCall(callId)?.splitFromConference()
    }

    // --- audio: delegated to the bound service ---

    fun setMuted(muted: Boolean) {
        CallRepository.requireController()?.requestMute(muted)
            ?: Log.w(TAG, "setMuted ignored: no bound InCallService")
    }

    fun toggleMute() {
        setMuted(!CallRepository.state.value.audio.isMuted)
    }

    fun setAudioRoute(route: AudioRoute) {
        CallRepository.requireController()?.requestAudioRoute(route)
            ?: Log.w(TAG, "setAudioRoute ignored: no bound InCallService")
    }

    /** Speaker off returns to the wired headset when one is connected, otherwise the earpiece. */
    fun toggleSpeaker() {
        val audio = CallRepository.state.value.audio
        setAudioRoute(if (audio.isSpeakerOn) audio.defaultEarRoute else AudioRoute.SPEAKER)
    }

    fun setBluetoothDevice(address: String) {
        CallRepository.requireController()?.requestBluetoothDevice(address)
    }

    private fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "TelecomHelper"
        private const val SCHEME_TEL = "tel"
    }
}
