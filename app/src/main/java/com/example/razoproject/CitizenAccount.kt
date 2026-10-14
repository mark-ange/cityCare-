package com.example.razoproject

data class CitizenAccount(
    val name: String,
    val purok: String,
    val identifier: String,
    val password: String,
    val email: String = "",
    val phoneNumber: String = "",
    val barangay: String = "Iponan",
    val isOfficial: Boolean = false
)
