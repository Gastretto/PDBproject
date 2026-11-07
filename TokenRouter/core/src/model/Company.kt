package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class Company(
    val id: Long,
    val name: String,
    val prefix: String,
    val creditLimit: Double,
    val creditSpend: Double,
    val hasCredits: Boolean,
    val apiTokens: List<ApiToken> = emptyList(),
    val users: List<User> = emptyList(),
)
