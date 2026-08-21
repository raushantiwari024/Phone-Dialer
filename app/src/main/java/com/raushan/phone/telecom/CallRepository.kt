package com.raushan.phone.telecom

import android.telecom.Call
import android.util.Log
import com.raushan.phone.telecom.model.CallAudioModel
import com.raushan.phone.telecom.model.CallModel
import com.raushan.phone.telecom.model.CallSessionEvent
import com.raushan.phone.telecom.model.CallSessionState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for call state.
 *
 * Stays a Kotlin `object` because there is no DI graph and the service, the ViewModels and the
 * notification layer have no common owner. It is deliberately **Context-free**: every Android lookup
 * happens in [MyInCallService] and is pushed in here.
 *
 * ### Why this re-emits
 *
 * The previous implementation held `MutableStateFlow<List<Call>>` of framework [Call] objects. Because
 * [Call] mutates in place, a state transition changed nothing about the list's identity or contents,
 * `MutableStateFlow` conflated the update away, and nothing downstream ever heard about it. Every
 * consumer then registered its own `Call.Callback` to compensate — inconsistently, and in the
 * ViewModel's case without ever unregistering.
 *
 * Now the store holds immutable [CallModel] snapshots. Any callback event re-runs the mapper and
 * produces a model whose fields differ, so the published [CallSessionState] is unequal and the flow
 * emits. A genuinely unchanged re-map compares equal and is conflated, so there is no recomposition
 * churn either way.
 *
 * ### Threading
 *
 * All mutating functions are `internal` and must be called from the main thread. [MyInCallService]
 * registers its `Call.Callback` with a main-thread `Handler`, so single-threaded access holds without
 * locking.
 */
object CallRepository {

    /**
     * Identity-keyed and insertion-ordered.
     *
     * [Call] does not override `equals`/`hashCode`, so a `LinkedHashMap` keyed by it gives reference
     * identity — which is what we want, since Telecom hands back the same instance — plus
     * "call added order" for free.
     */
    private val entries = LinkedHashMap<Call, CallModel>()
    private val idToCall = HashMap<String, Call>()
    private var idCounter = 0L

    private var audio: CallAudioModel = CallAudioModel.DEFAULT
    private var canAddCall = false
    private var isServiceConnected = false
    private var isRingerSilenced = false

    @Volatile
    private var controller: InCallController? = null

    private val _state = MutableStateFlow(CallSessionState.EMPTY)
    val state: StateFlow<CallSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CallSessionEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<CallSessionEvent> = _events.asSharedFlow()

    // --- writes: MyInCallService only, main thread ---

    /**
     * Allocates a stable id for a newly added call.
     *
     * [Call] exposes no public identifier at `minSdk 30`, so we assign one. Not
     * `System.identityHashCode` (collides) and not `creationTimeMillis` (not unique). The counter is
     * never reset, even across bindings, so a stale notification `PendingIntent` from a previous
     * binding can never resolve onto a new call.
     */
    internal fun assignId(call: Call): String {
        idToCall.entries.firstOrNull { it.value === call }?.let { return it.key }
        val id = "$ID_PREFIX${idCounter++}"
        idToCall[id] = call
        return id
    }

    internal fun putCall(call: Call, model: CallModel) {
        entries[call] = model
        idToCall[model.id] = call
        publish()
    }

    internal fun removeCall(call: Call) {
        val removed = entries.remove(call)
        if (removed != null) idToCall.remove(removed.id)
        publish()
    }

    internal fun setAudio(model: CallAudioModel) {
        if (audio == model) return
        audio = model
        publish()
    }

    internal fun setCanAddCall(value: Boolean) {
        if (canAddCall == value) return
        canAddCall = value
        publish()
    }

    internal fun setRingerSilenced(value: Boolean) {
        if (isRingerSilenced == value) return
        isRingerSilenced = value
        publish()
    }

    internal fun setServiceConnected(value: Boolean) {
        if (isServiceConnected == value) return
        isServiceConnected = value
        publish()
    }

    internal fun attachController(controller: InCallController) {
        this.controller = controller
    }

    /**
     * Identity-checked so a late teardown from a previous service instance cannot clear the
     * controller belonging to a newer one.
     */
    internal fun detachController(controller: InCallController) {
        if (this.controller === controller) this.controller = null
    }

    /**
     * Resets everything on service teardown.
     *
     * Without this a dead [Call] survived for the process lifetime, which permanently blocked placing
     * a new call and left the call UI pinned on screen.
     */
    internal fun clear() {
        entries.clear()
        idToCall.clear()
        audio = CallAudioModel.DEFAULT
        canAddCall = false
        isRingerSilenced = false
        isServiceConnected = false
        publish()
    }

    internal fun emitEvent(event: CallSessionEvent) {
        if (!_events.tryEmit(event)) {
            Log.w(TAG, "Dropped call session event: $event")
        }
    }

    // --- reads: telecom package only. Raw Call never leaves this package. ---

    internal fun rawCall(id: String): Call? = idToCall[id]

    internal fun idOf(call: Call): String? =
        idToCall.entries.firstOrNull { it.value === call }?.key

    internal fun modelOf(id: String): CallModel? = entries.values.firstOrNull { it.id == id }

    internal fun requireController(): InCallController? = controller

    private fun publish() {
        _state.value = CallSessionState(
            calls = entries.values.toList(),
            audio = audio,
            canAddCall = canAddCall,
            isServiceConnected = isServiceConnected,
            isRingerSilenced = isRingerSilenced,
        )
    }

    private const val TAG = "CallRepository"
    private const val EVENT_BUFFER = 8
    private const val ID_PREFIX = "call-"
}
