package com.marlin.tokenrouter.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class ApiKey(
    val key: String,
    val companyId: Long,
    val userId: Long,
    val active: Boolean,
) {
    companion object {
        const val UUID_LENGTH = 32

        fun generate(prefix: String, companyId: Long, userId: Long, active: Boolean = true): ApiKey =
            ApiKey(
                key = "$prefix-${randomUuid()}",
                companyId = companyId,
                userId = userId,
                active = active,
            )

        fun isValidFormat(key: String, prefix: String): Boolean {
            val uuid = key.removePrefix("$prefix-")
            return uuid.length == UUID_LENGTH &&
                uuid != key &&
                uuid.all { it in "0123456789abcdef" }
        }

        private fun randomUuid(): String {
            val hex = "0123456789abcdef"
            return buildString(UUID_LENGTH) {
                repeat(UUID_LENGTH) { append(hex[Random.nextInt(hex.length)]) }
            }
        }
    }
}
