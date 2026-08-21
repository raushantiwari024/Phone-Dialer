package com.raushan.phone.data.models

data class Contact(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String? = null
)