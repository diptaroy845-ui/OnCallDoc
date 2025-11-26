package com.example.oncalldoc

data class Doctor(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "", // This was the missing property
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    var distance: Double = 0.0
)
