package com.raushan.phone.telecom.model

/**
 * Builder for [CallModel] test data.
 *
 * [CallModel] has a wide constructor because it snapshots everything the framework exposes; tests only
 * ever care about two or three fields, so everything else gets a neutral default here.
 */
fun callModel(
    id: String,
    state: CallState,
    number: String = "+15551234567",
    displayName: String = "Test Caller",
    isIncoming: Boolean = true,
    isConference: Boolean = false,
    isConferenceChild: Boolean = false,
    isExternal: Boolean = false,
    parentId: String? = null,
    connectTimeMillis: Long = 0L,
    capabilities: CallCapabilities = CallCapabilities.NONE,
    presentation: NumberPresentation = NumberPresentation.ALLOWED,
    conferenceableIds: List<String> = emptyList(),
): CallModel = CallModel(
    id = id,
    state = state,
    number = number,
    scheme = "tel",
    displayName = displayName,
    contactId = null,
    photoUri = null,
    presentation = presentation,
    isIncoming = isIncoming,
    isConference = isConference,
    isConferenceChild = isConferenceChild,
    isExternal = isExternal,
    isEmergency = false,
    isVideo = false,
    isWifiCall = false,
    isHdAudio = false,
    childIds = emptyList(),
    parentId = parentId,
    conferenceableIds = conferenceableIds,
    connectTimeMillis = connectTimeMillis,
    creationTimeMillis = 0L,
    capabilities = capabilities,
    disconnectCauseCode = null,
    disconnectDescription = null,
    cannedTextResponses = emptyList(),
    phoneAccountId = null,
    phoneAccountLabel = null,
)
