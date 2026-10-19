package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class UsageEvent(
    val companyId: Long,
    val userId: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val timestampMs: Long,
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

@Serializable
data class UserUsage(
    val userId: Long,
    val userName: String,
    val tokens: Long,
)

@Serializable
data class CompanyUsage(
    val companyId: Long,
    val totalTokens: Long,
    val byUser: List<UserUsage>,
)
