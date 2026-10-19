package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class Company(
    val id: Long,
    val name: String,
    val prefix: String,
    val tokenLimit: Long,
    val tokensSpent: Long,
    val validApiKeys: List<ApiKey> = emptyList(),
    val validLogins: List<User> = emptyList(),
)
