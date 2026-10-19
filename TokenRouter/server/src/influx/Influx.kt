package com.marlin.tokenrouter.server.influx

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import com.influxdb.query.FluxRecord
import com.marlin.tokenrouter.model.CompanyUsage
import com.marlin.tokenrouter.model.UsageEvent
import com.marlin.tokenrouter.model.UserUsage
import java.time.Instant

object Influx {
    private const val DEFAULT_URL = "http://localhost:8086"
    private const val ORG = "tokenrouter"
    private const val BUCKET = "usage"
    private const val MEASUREMENT = "token_usage"

    private val client by lazy {
        val url = System.getenv("INFLUX_URL") ?: DEFAULT_URL
        val token = System.getenv("INFLUX_TOKEN") ?: "tokenrouter-demo-token"
        InfluxDBClientFactory.create(url, token.toCharArray(), ORG, BUCKET)
    }

    fun writeUsage(event: UsageEvent) {
        val point = Point
            .measurement(MEASUREMENT)
            .addTag("company_id", event.companyId.toString())
            .addTag("user_id", event.userId.toString())
            .addField("prompt_tokens", event.promptTokens.toLong())
            .addField("completion_tokens", event.completionTokens.toLong())
            .addField("total_tokens", event.totalTokens.toLong())
            .time(Instant.ofEpochMilli(event.timestampMs), WritePrecision.MS)

        client.writeApiBlocking.writePoint(point)
    }

    fun usageSummary(range: String = "-1h"): List<CompanyUsage> {
        val perUserQuery = """
            from(bucket: "$BUCKET")
              |> range(start: $range)
              |> filter(fn: (r) => r._measurement == "$MEASUREMENT")
              |> filter(fn: (r) => r._field == "total_tokens")
              |> group(columns: ["company_id", "user_id"])
              |> sum()
        """.trimIndent()

        val byCompany = mutableMapOf<Long, MutableMap<Long, Long>>()

        client.queryApi.query(perUserQuery).forEach { table ->
            table.records.forEach { record: FluxRecord ->
                val companyId = record.values["company_id"]?.toString()?.toLongOrNull() ?: return@forEach
                val userId = record.values["user_id"]?.toString()?.toLongOrNull() ?: return@forEach
                val total = (record.value as? Number)?.toLong() ?: 0L
                byCompany.getOrPut(companyId) { mutableMapOf() }[userId] = total
            }
        }

        return byCompany.map { (companyId, users) ->
            CompanyUsage(
                companyId = companyId,
                totalTokens = users.values.sum(),
                byUser = users.map { (userId, tokens) ->
                    UserUsage(
                        userId = userId,
                        userName = "user #$userId",
                        tokens = tokens,
                    )
                }.sortedByDescending { it.tokens },
            )
        }.sortedBy { it.companyId }
    }

    fun ping(): Boolean = runCatching { client.ping() }.getOrDefault(false)

    fun close() = runCatching { client.close() }
}
