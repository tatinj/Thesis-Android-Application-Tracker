package com.example.dashboard_and_security_module

data class Friend(
    val name: String = "",
    val code: String = "",
    val phone: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)
