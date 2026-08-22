package com.raushan.phone.telecom.model

/**
 * The explicit phase of the call session.
 *
 * Derived from the calls Telecom reports, never from anything the UI holds — so the notification and
 * the in-app screens read the same value and cannot disagree about whether a call is waiting.
 *
 * ```
 * Idle
 *  ├── IncomingOnly ──answer──▶ SingleCall
 *  │                 ──decline─▶ Idle
 *  └── SingleCall
 *        └── CallWaiting
 *              ├── decline incoming        ──▶ SingleCall   (ongoing untouched)
 *              ├── answer + hold current   ──▶ TwoCalls     (new active, previous held)
 *              └── answer + end current    ──▶ SingleCall   (new active, previous ended)
 * ```
 */
enum class CallPhase {
    /** No live calls. */
    Idle,

    /** A single ringing call and nothing else. */
    IncomingOnly,

    /** Exactly one non-ringing call. */
    SingleCall,

    /** A call in progress plus a second call ringing. */
    CallWaiting,

    /** Two non-ringing calls: one active, one held. */
    TwoCalls,
    ;

    /** Whether an incoming call is waiting for a decision. */
    val hasIncoming: Boolean get() = this == IncomingOnly || this == CallWaiting

    /** Whether declining the incoming call would leave another call behind. */
    val hasCallToPreserve: Boolean get() = this == CallWaiting
}
