package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable

@Serializable
data class UsageEvent(
    val companyId: Long,
    val apiTokenId: Long,
    val provider: String,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val timestampMs: Long,
) {
    val totalTokens: Long get() = inputTokens + outputTokens
}
