package com.raushan.phone.ui.incall

/**
 * Everything the call UI can ask for. State flows down, actions flow up.
 */
sealed interface InCallAction {

    data object Answer : InCallAction

    /** Answer the waiting call and let Telecom hold the current one. */
    data object AnswerHoldingCurrent : InCallAction

    /** Answer the waiting call after hanging up the current one, when it cannot be held. */
    data object AnswerEndingCurrent : InCallAction

    data object Decline : InCallAction

    data object EndCall : InCallAction

    data object ToggleMute : InCallAction

    data object ToggleSpeaker : InCallAction

    data object ToggleHold : InCallAction

    data object Swap : InCallAction

    data object Merge : InCallAction

    data object ShowDialpad : InCallAction

    data object HideDialpad : InCallAction

    data class PressDialKey(val digit: Char) : InCallAction

    data object ReleaseDialKey : InCallAction

    data object AddCall : InCallAction

    data object ReplyWithMessage : InCallAction
}

/** One-shot effects the call UI needs its host activity to carry out. */
sealed interface InCallUiEvent {

    /** Open the dialer so the user can place a second call. */
    data object OpenDialerForSecondCall : InCallUiEvent

    data class OpenSms(val number: String) : InCallUiEvent
}
