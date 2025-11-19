package com.example.oncalldoc

data class Order(
    val orderId: String = "",
    val patientId: String = "",
    val doctorId: String = "",
    val status: String = "",
    val timestamp: Long = 0
)
