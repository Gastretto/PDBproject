package com.marlin.tokenrouter.server.influx

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import com.marlin.tokenrouter.model.UsageEvent
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

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

    private val subMilliCounter = AtomicLong(0)

    fun writeUsage(event: UsageEvent) {
        val point = Point
            .measurement(MEASUREMENT)
            .addTag("company_id", event.companyId.toString())
            .addTag("api_token_id", event.apiTokenId.toString())
            .addTag("provider", event.provider)
            .addTag("model", event.model)
            .addField("input_tokens", event.inputTokens)
            .addField("output_tokens", event.outputTokens)
            .addField("total_tokens", event.totalTokens)
            .time(uniqueEventTime(event.timestampMs), WritePrecision.NS)

        client.writeApiBlocking.writePoint(point)
    }

    fun ping(): Boolean = runCatching { client.ping() }.getOrDefault(false)

    fun close() = runCatching { client.close() }

    /**
     * InfluxDB identifies a point by measurement + tag set + timestamp.
     * A per-millisecond atomic offset keeps distinct events from overwriting
     * each other while preserving the event's millisecond timestamp.
     */
    private fun uniqueEventTime(timestampMs: Long): Instant {
        val baseNanos = timestampMs * 1_000_000
        val offset = subMilliCounter.getAndIncrement() % 1_000_000
        return Instant.ofEpochSecond(0, baseNanos + offset)
    }
}
