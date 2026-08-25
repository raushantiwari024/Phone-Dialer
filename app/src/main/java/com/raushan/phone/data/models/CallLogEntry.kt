package com.raushan.phone.data.models

data class CallLogEntry(
    val id: Long,
    val number: String,
    val date: Long,
    val duration: Long,
    val type: Int,
    val cachedName: String? = null,
    val cachedNumberType: Int? = null,
    val cachedNumberLabel: String? = null,
    val photoUri: String? = null
)