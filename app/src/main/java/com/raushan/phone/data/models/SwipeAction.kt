package com.raushan.phone.data.models

enum class SwipeAction {
    CALL, MESSAGE, DELETE, NONE;

    fun getLabel(): String = when (this) {
        CALL -> "Call"
        MESSAGE -> "Send SMS"
        DELETE -> "Delete from log"
        NONE -> "None"
    }
}
