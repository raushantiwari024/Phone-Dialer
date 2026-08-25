package com.raushan.phone.telecom.model

/**
 * Outcome of a request to place an outgoing call.
 *
 * Returned rather than logged so the UI can tell the user what went wrong. Mapping these to text stays
 * in the UI layer, where string resources belong.
 */
sealed interface PlaceCallResult {

    data object Placed : PlaceCallResult

    /** `CALL_PHONE` was not granted, or the app is not permitted to place calls. */
    data object MissingPermission : PlaceCallResult

    data object InvalidNumber : PlaceCallResult

    /** Telecom reports no room for another call — already two calls, or one is ringing. */
    data object CannotAddCall : PlaceCallResult

    data class Failed(val reason: String) : PlaceCallResult
}
