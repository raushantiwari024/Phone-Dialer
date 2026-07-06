package com.raushan.phone.data.models

data class CallLogEntry(
    val id: Long,
    val number: String,
    val date: Long,
    val duration: Long,
    val type: Int
)