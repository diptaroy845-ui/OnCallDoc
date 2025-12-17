package com.example.oncalldoc

data class Doctor(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val isOnline: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    var distance: Double = 0.0
)
